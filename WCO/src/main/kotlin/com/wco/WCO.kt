package com.wco

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.TimeUnit

class WCO(
    private var savedDomain: String?,
    private val sharedPref: SharedPreferences?
) : MainAPI() {
    override var mainUrl: String = savedDomain ?: DEFAULT_DOMAIN

    override var name = "WCO"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.Movie)

    companion object {
        private const val TAG = "WCO"
        private val gson = Gson()
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        private const val DOMAINS_URL = "https://www.wcostatus.com/check.php"
        private const val DEFAULT_DOMAIN = "https://www.wco.tv"
    }

    data class WCODomain(
        @SerializedName("domain") val domain: String,
        @SerializedName("status") val status: Int
    )

    suspend fun fetchDomains(): List<WCODomain> {
        return try {
            val req = Request.Builder().url(DOMAINS_URL)
                .addHeader("User-Agent", "Mozilla/5.0")
                .get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()
            gson.fromJson(body, Array<WCODomain>::class.java).toList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch domains: ${e.message}")
            emptyList()
        }
    }

    fun updateDomain(domain: String) {
        savedDomain = domain
        mainUrl = domain
        sharedPref?.edit()?.putString("selected_domain", domain)?.apply()
    }

    override val mainPage
        get() = mainPageOf(
            "$mainUrl/" to "Recent Releases",
            "$mainUrl/" to "Dubbed Anime",
            "$mainUrl/" to "Cartoons",
            "$mainUrl/" to "Subbed Anime",
            "$mainUrl/" to "Movies"
        )

    private fun Element.toSearchResult(): SearchResponse {
        val linkEl = selectFirst("div.img a") ?: selectFirst("a")
        val href = linkEl?.attr("href") ?: ""
        val img = selectFirst("div.img img, img")
        val posterUrl = img?.attr("src")?.let { fixUrl(it) } ?: ""
        val title = img?.attr("alt")?.ifEmpty {
            selectFirst(".recent-release-episodes a")?.text()
        } ?: selectFirst(".recent-release-episodes a")?.text() ?: ""

        val showUrl = if (href.contains("/anime/") || !href.contains("-episode-")) {
            fixUrl(href)
        } else {
            episodeUrlToShowUrl(href)
        }

        val typeBadge = selectFirst(".badge2")
        val type = when (typeBadge?.attr("data-type")) {
            "dub" -> TvType.TvSeries
            "cartoon" -> TvType.Cartoon
            "sub" -> TvType.Anime
            else -> TvType.TvSeries
        }

        return newAnimeSearchResponse(title, showUrl, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/").document
        val sectionName = request.name

        val items = when (sectionName) {
            "Recent Releases" -> {
                val sections = doc.select(".recent-release-main")
                if (sections.isNotEmpty()) {
                    sections.first()!!.select("#sidebar_right ul.items li")
                } else {
                    doc.select("ul.items li")
                }
            }
            "Dubbed Anime" -> doc.select(".recent-release-main:has(a[name=dubbed]) #sidebar_right ul.items li")
            "Cartoons" -> doc.select(".recent-release-main:has(a[name=cartoon]) #sidebar_right ul.items li")
            "Subbed Anime" -> doc.select(".recent-release-main:has(a[name=subbed]) #sidebar_right ul.items li")
            "Movies" -> doc.select(".recent-release-main:has(a[name=movies]) #sidebar_right ul.items li")
            else -> doc.select("ul.items li")
        }

        return newHomePageResponse(request.name, items.map { it.toSearchResult() })
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val response = postRequest("$mainUrl/search", "catara=$query&konuara=series")
            ?: return null
        val doc = Jsoup.parse(response)
        val items = doc.select("#sidebar_right2 ul.items li")
        if (items.isEmpty()) return null
        return items.map { it.toSearchResult() }.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val showUrl = if (url.contains("/anime/")) url else episodeUrlToShowUrl(url)
        val doc = app.get(showUrl).document

        val titleEl = doc.selectFirst("span.film-name.dynamic-name")
        val title = titleEl?.attr("data-jname") ?: titleEl?.text()
            ?: doc.selectFirst("div.video-title h1")?.text()
            ?: throw ErrorLoadingException("Could not find title")

        val poster = doc.selectFirst("#sidebar_cat img.img5")?.attr("src")?.let { fixUrl(it) } ?: ""

        val genres = doc.select("a.genre-buton").map { it.text() }

        val plot = doc.selectFirst("#sidebar_cat p")?.text() ?: ""

        val episodes = doc.select("#episodeList a.dark-episode-item").mapNotNull { ep ->
            val href = fixUrl(ep.attr("href"))
            val epName = ep.selectFirst("span")?.text() ?: ""
            val epNum = extractEpisodeNumber(href, epName)

            newEpisode(href) {
                this.name = epName
                this.episode = epNum
            }
        }.reversed()

        val dubEpisodes = episodes.filter {
            val href = it.data ?: ""
            href.contains("dubbed")
        }
        val subEpisodes = episodes.filter {
            val href = it.data ?: ""
            href.contains("subbed")
        }

        return newAnimeLoadResponse(title, showUrl, TvType.TvSeries) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = genres
            this.year = null
            this.showStatus = ShowStatus.Completed
            if (dubEpisodes.isNotEmpty()) {
                addEpisodes(DubStatus.Dubbed, dubEpisodes)
            }
            if (subEpisodes.isNotEmpty()) {
                addEpisodes(DubStatus.Subbed, subEpisodes)
            }
            if (dubEpisodes.isEmpty() && subEpisodes.isEmpty()) {
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val html = doc.html()

        val decoded = decodeObfuscatedJs(html)
        if (decoded != null) {
            val iframeSrc = Jsoup.parse(decoded).selectFirst("iframe")?.attr("src")
            if (iframeSrc != null) {
                loadExtractor(iframeSrc, "$mainUrl/", subtitleCallback) { link ->
                    callback(link)
                }
                return true
            }
        }

        val iframe = doc.selectFirst("iframe")
        if (iframe != null) {
            val src = iframe.attr("src")
            loadExtractor(src, "$mainUrl/", subtitleCallback) { link ->
                callback(link)
            }
            return true
        }

        return false
    }

    private fun episodeUrlToShowUrl(episodeUrl: String): String {
        val path = episodeUrl.substringAfter("$mainUrl/")
        val slug = if (path.contains("-episode-")) {
            path.substringBefore("-episode-")
        } else {
            path.removeSuffix("/")
        }
        return "$mainUrl/anime/$slug"
    }

    private fun extractEpisodeNumber(href: String, title: String): Int {
        val fromHref = Regex("""-episode-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
        if (fromHref != null) return fromHref
        val fromTitle = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(title)?.groupValues?.get(1)?.toIntOrNull()
        return fromTitle ?: 1
    }

    private fun decodeObfuscatedJs(html: String): String? {
        try {
            val arrayRegex = Regex("""var\s+(\w+)\s*=\s*\[([^\]]+)\]""")
            val arrayMatch = arrayRegex.find(html) ?: return null
            val arrayContent = arrayMatch.groupValues[2]

            val offsetRegex = Regex(
                """fromCharCode\(\s*parseInt\s*\(\s*atob\s*\(\s*\w+\s*\)\s*\.\s*replace\s*\(\s*/\D/g\s*,\s*[''""]\s*\)\s*\)\s*-\s*(\d+)\s*\)"""
            )
            val offsetMatch = offsetRegex.find(html)
            val offset = offsetMatch?.groupValues?.get(1)?.toIntOrNull() ?: 38909312

            val base64Values = Regex("""['"]([A-Za-z0-9+/=]+)['"]""").findAll(arrayContent)
                .map { it.groupValues[1] }.toList()

            if (base64Values.isEmpty()) return null

            val decoded = StringBuilder()
            for (b64 in base64Values) {
                try {
                    val bytes = Base64.getDecoder().decode(b64)
                    val str = String(bytes, Charsets.UTF_8)
                    val numStr = str.replace(Regex("""\D"""), "")
                    if (numStr.isNotEmpty()) {
                        val charCode = numStr.toInt() - offset
                        decoded.append(charCode.toChar())
                    }
                } catch (_: Exception) { }
            }

            return URLDecoder.decode(decoded.toString(), "UTF-8")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode obfuscated JS: ${e.message}")
            return null
        }
    }

    private fun postRequest(url: String, body: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Referer", "$mainUrl/")
                .post(RequestBody.create(null, body))
                .build()
            val resp = client.newCall(req).execute()
            resp.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "POST failed: ${e.message}")
            null
        }
    }
}
