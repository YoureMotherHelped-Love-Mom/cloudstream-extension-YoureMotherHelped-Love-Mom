package com.kimcartoon2

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class Kimcartoon2 : MainAPI() {
    override var mainUrl = "https://kimcartoon.si"
    override var name = "Kimcartoon2"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Cartoon)

    companion object {
        private const val TAG = "Kimcartoon2"
        private val gson = Gson()
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun Element.toSearchResult(): SearchResponse {
        val href = fixUrl(selectFirst("a.thumb")?.attr("href") ?: "")
        val title = selectFirst("h2.title")?.text() ?: selectFirst("a.thumb img")?.attr("alt") ?: ""
        val posterUrl = fixUrl(selectFirst("a.thumb img")?.attr("src") ?: "")
        val hasEpisodes = !select("div.ep-bg a").isEmpty()
        val type = if (hasEpisodes) TvType.TvSeries else TvType.Movie

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/CartoonList/LatestUpdate?page=" to "Latest Updates",
        "$mainUrl/CartoonList/MostPopular?page=" to "Most Popular",
        "$mainUrl/CartoonList/Newest?page=" to "Newest",
        "$mainUrl/Status/Ongoing?page=" to "Ongoing",
    )

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = "$mainUrl/Search/?s=${query}&page=$page"
        val doc = app.get(url).document
        val items = doc.select("div.item")
        if (items.isEmpty()) return null
        return items.map { it.toSearchResult() }.toNewSearchResponseList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page"
        val doc = app.get(url).document
        val items = doc.select("div.item")
        return newHomePageResponse(request.name, items.map { it.toSearchResult() })
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1 a.bigChar")?.text()
            ?: doc.selectFirst("h1")?.text()
            ?: throw ErrorLoadingException("Could not find title")

        val poster = doc.selectFirst("div.left_movie img")?.attr("src")?.let { fixUrl(it) } ?: ""

        val genres = doc.select("p.info a.dotUnder").map { it.text().replace(" Cartoon", "") }

        val plot = doc.selectFirst("div.summary p")?.text() ?: ""

        val statusText = doc.selectFirst("p.item_static span.info:contains(Status)")?.parent()?.text() ?: ""
        val showStatus = if (statusText.contains("Ongoing", true)) ShowStatus.Ongoing else ShowStatus.Completed

        val year = doc.selectFirst("span.info:contains(Date aired)")?.parent()?.text()
            ?.replace("Date aired:", "")?.trim()?.take(4)?.toIntOrNull()

        val episodes = doc.select("div.listing div.item_ep").mapNotNull { ep ->
            val link = ep.selectFirst("a") ?: return@mapNotNull null
            val epHref = link.attr("href")
            val epTitle = link.text().trim()
            val epNum = extractEpisodeNumber(epHref, epTitle)
            val epDate = ep.select("div").last()?.text()?.trim() ?: ""

            val episodeId = extractEpisodeId(epHref)
            val videoUrl = if (episodeId != null) {
                fetchVideoUrl(episodeId) ?: epHref
            } else epHref

            newEpisode(videoUrl) {
                this.name = epTitle
                this.episode = epNum
                if (epDate.isNotEmpty()) {
                    this.addDate(epDate)
                }
            }
        }.reversed()

        val recommendations = doc.select("div.series_links h4.tit a").map { a ->
            val recUrl = fixUrl(a.attr("href"))
            val recTitle = a.text().replace("Watch ", "").replace(" online free", "")
            newAnimeSearchResponse(recTitle, recUrl, TvType.TvSeries) {
                this.posterUrl = poster
            }
        }

        return newAnimeLoadResponse(title, url, TvType.TvSeries) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = genres
            this.year = year
            this.showStatus = showStatus
            addEpisodes(DubStatus.Subbed, episodes)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return loadExtractor(data, "$mainUrl/", subtitleCallback, callback)
    }

    private fun fetchVideoUrl(episodeId: String): String? {
        val servers = listOf("hserver", "tserver")
        for (server in servers) {
            try {
                val result = fetchFromServer(episodeId, server)
                if (result != null) return result
            } catch (e: Exception) {
                Log.e(TAG, "Server $server failed: ${e.message}")
            }
        }
        return null
    }

    private fun fetchFromServer(episodeId: String, server: String): String? {
        val response = postRequest(
            "$mainUrl/ajax/anime/load_episodes_v2?s=$server",
            "episode_id=$episodeId&s=$server"
        ) ?: return null

        val json = gson.fromJson(response, LoadEpisodeResponse::class.java)
        if (!json.status || json.value.isNullOrEmpty()) return null

        val value = json.value
        val embed = json.embed ?: false

        return if (embed) {
            val doc = Jsoup.parse(value)
            doc.selectFirst("iframe")?.attr("src")
        } else {
            if (json.type == "mediafire") return null
            if (value.contains(".m3u8") || value.contains(".mp4")) value
            else {
                try {
                    val playlistJson = getJson(value) ?: return null
                    val playlist = gson.fromJson(playlistJson, PlaylistResponse::class.java)
                    playlist.playlist?.firstOrNull()?.let { item ->
                        item.sources?.firstOrNull()?.file ?: item.file
                    } ?: value
                } catch (_: Exception) {
                    value
                }
            }
        }
    }

    private fun extractEpisodeNumber(href: String, title: String): Int {
        val fromHref = Regex("""Episode-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
        if (fromHref != null) return fromHref
        val fromTitle = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()
        return fromTitle ?: 1
    }

    private fun extractEpisodeId(data: String): String? {
        Regex("""[?&]id=(\d+)""").find(data)?.groupValues?.get(1)?.let { return it }
        Regex("""[?&]episode_id=(\d+)""").find(data)?.groupValues?.get(1)?.let { return it }
        Regex("""Episode-(\d+)""").find(data)?.groupValues?.get(1)?.let { return it }
        return null
    }

    private fun postRequest(url: String, body: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Referer", "$mainUrl/")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post(body.toRequestBody(null))
                .build()
            val resp = client.newCall(req).execute()
            resp.body.string()
        } catch (e: Exception) {
            Log.e(TAG, "POST failed: ${e.message}")
            null
        }
    }

    private fun getJson(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Referer", "$mainUrl/")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            resp.body.string()
        } catch (e: Exception) {
            Log.e(TAG, "GET failed: ${e.message}")
            null
        }
    }

    data class LoadEpisodeResponse(
        @SerializedName("status") val status: Boolean,
        @SerializedName("value") val value: String?,
        @SerializedName("embed") val embed: Boolean?,
        @SerializedName("type") val type: String?,
        @SerializedName("html5") val html5: Boolean?,
        @SerializedName("download_get") val downloadGet: String?
    )

    data class PlaylistItem(
        @SerializedName("file") val file: String?,
        @SerializedName("sources") val sources: List<PlaylistSource>?,
        @SerializedName("type") val type: String?
    )

    data class PlaylistSource(
        @SerializedName("file") val file: String?,
        @SerializedName("type") val type: String?,
        @SerializedName("label") val label: String?
    )

    data class PlaylistResponse(
        @SerializedName("playlist") val playlist: List<PlaylistItem>?,
        @SerializedName("status") val status: Boolean?
    )
}
