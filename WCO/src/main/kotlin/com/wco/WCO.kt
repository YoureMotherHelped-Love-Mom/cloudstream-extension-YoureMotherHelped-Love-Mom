package com.wco

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody
import org.jsoup.nodes.Element

class WCO : MainAPI() {
    override var mainUrl = "https://wco.tv"
    override var name = "WCO"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.Movie)

    companion object {
        private const val TAG = "WCO"
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val formBody = FormBody.Builder()
            .add("keyword", query)
            .add("page", page.toString())
            .build()
        val doc = app.post(
            "$mainUrl/search",
            requestBody = formBody,
            headers = mapOf("Referer" to "$mainUrl/")
        ).document

        val items = doc.select("#sidebar_right2 ul.items li")
        if (items.isEmpty()) return null
        return items.mapNotNull { it.toSearchResponse() }.toNewSearchResponseList()
    }

    override val mainPage = mainPageOf(
        "$mainUrl/cartoon-list/page/" to "Latest Updates",
        "$mainUrl/cartoon-list/most-popular/page/" to "Most Popular",
        "$mainUrl/cartoon-list/new-updates/page/" to "Newest",
        "$mainUrl/anime-list/most-popular/page/" to "Anime",
        "$mainUrl/subbed-anime-list/page/" to "Subbed Anime",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page/"
        val doc = app.get(url, referer = "$mainUrl/").document

        val section = doc.selectFirst(".recent-release-main, .recent-release")

        val items = if (section != null) {
            section.select("ul.items li")
        } else {
            doc.select("#sidebar_right2 ul.items li, ul.items li")
        }

        return newHomePageResponse(request.name, items.mapNotNull { it.toSearchResponse() })
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = "$mainUrl/").document

        val title = doc.selectFirst("h1.entry-title")?.text()
            ?: doc.selectFirst("h1")?.text()
            ?: throw ErrorLoadingException("Could not find title")

        val poster = doc.selectFirst("div.thumb img")?.attr("src")
            ?: doc.selectFirst("div.entry-content img")?.attr("src")
            ?: ""

        val genres = doc.select("div.genres a, div.genre a, span.cat a").map { it.text().trim() }.filter { it.isNotBlank() }

        val plot = doc.selectFirst("div.entry-content p, div.description p")?.text()
            ?: doc.selectFirst("div.entry-content")?.text()?.take(500)
            ?: ""

        val statusText = doc.text()
        val showStatus = if (statusText.contains("ongoing", true)) ShowStatus.Ongoing else ShowStatus.Completed

        val year = Regex("""(?:19|20)\d{2}""").find(title)?.value?.toIntOrNull()

        val episodes = doc.select("div.episodios a, ul.episodes li a, div.episode-list a, li.episode a").mapNotNull { a ->
            val epHref = a.attr("href")
            if (epHref.isBlank() || epHref == "#") return@mapNotNull null
            val epTitle = a.text().trim()
            val epNum = extractEpisodeNumber(epHref, epTitle)
            newEpisode(epHref) {
                this.name = epTitle
                this.episode = epNum
            }
        }

        return newAnimeLoadResponse(title, url, TvType.TvSeries) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = genres
            this.year = year
            this.showStatus = showStatus
            addEpisodes(DubStatus.Subbed, episodes)
            addEpisodes(DubStatus.Dubbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var found = false

        // Method 1: Try decoding obfuscated iframe URL
        val decodedIframe = decodeIframeUrl(data)
        if (decodedIframe != null) {
            loadExtractor(decodedIframe, "$mainUrl/", subtitleCallback, callback)
            found = true
        }

        // Method 2: Try finding iframes directly on the page
        if (!found) {
            try {
                val doc = app.get(data, referer = "$mainUrl/").document
                doc.select("iframe[src]").forEach { iframe ->
                    val src = iframe.attr("abs:src")
                    if (src.isNotBlank()) {
                        loadExtractor(src, "$mainUrl/", subtitleCallback, callback)
                        found = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct iframe extraction failed: ${e.message}")
            }
        }

        return found
    }

    private suspend fun decodeIframeUrl(episodeUrl: String): String? {
        return try {
            val doc = app.get(episodeUrl, referer = "$mainUrl/").document
            val scripts = doc.select("script").map { it.html() }

            // First try standard iframe src
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank() && !src.startsWith("about:")) return src
            }

            // Then try decoding obfuscated scripts
            for (script in scripts) {
                val iframe = decodeWCOIframe(script)
                if (iframe != null) return iframe
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Iframe decode failed: ${e.message}")
            null
        }
    }

    private fun decodeWCOIframe(script: String): String? {
        if (script.isBlank()) return null
        if (!script.contains(".replace") && !script.contains("fromCharCode")) return null

        try {
            val arrayVar = Regex("""var\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*\[""").find(script)?.groupValues?.get(1)
                ?: return null

            val arrayValues = Regex("""var\s+$arrayVar\s*=\s*\[([^\]]+)\]""").find(script)?.groupValues?.get(1)
                ?: return null

            val base64Items = Regex("""'(.*?)'""").findAll(arrayValues).map { it.groupValues[1] }.toList()
            if (base64Items.isEmpty()) return null

            val offsetRegex = Regex("""\+\((\d+)\)""")
            val offset = offsetRegex.find(script)?.groupValues?.get(1)?.toIntOrNull() ?: return null

            val decoded = base64Items.mapNotNull { b64 ->
                try {
                    val decoded = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                    val num = decoded.replace(Regex("""\D"""), "").toIntOrNull()
                    if (num != null) (num - offset).toChar() else null
                } catch (_: Exception) {
                    null
                }
            }.joinToString("")

            val iframeRegex = Regex("""iframe\s*src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            return iframeRegex.find(decoded)?.groupValues?.get(1)
        } catch (_: Exception) {
            return null
        }
    }

    private fun extractEpisodeNumber(href: String, title: String): Int {
        val patterns = listOf(
            Regex("""-(\d+)$"""),
            Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*$"""),
        )
        for (pattern in patterns) {
            pattern.find(href)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            pattern.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return 1
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val link = selectFirst("a") ?: return null
        val href = fixUrl(link.attr("href"))
        if (href.isBlank() || href == "#") return null
        val title = link.attr("title").ifBlank { link.text().trim() }.ifBlank { return null }
        val poster = selectFirst("img")?.attr("src")?.let { fixUrl(it) } ?: ""

        return newAnimeSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }
}
