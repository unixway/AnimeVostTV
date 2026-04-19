package lv.zakon.tv.animevost.provider

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import lv.zakon.tv.animevost.model.MovieGenre
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.prefs.AppPrefs
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class MethodStats(
    val count: AtomicLong = AtomicLong(0),
    val totalTime: AtomicLong = AtomicLong(0),
    val minTime: AtomicLong = AtomicLong(Long.MAX_VALUE),
    val maxTime: AtomicLong = AtomicLong(0)
) {
    val average: Long get() = if (count.get() == 0L) 0 else totalTime.get() / count.get()
}

class AnimeVostProvider private constructor() {
    private val client = HttpClient(CIO) {
        defaultRequest {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9,en-US;q=0.8")
        }
    }

    val stats = ConcurrentHashMap<String, MethodStats>()

    companion object {
        private const val TAG = "AnimeVostProvider"
        private const val ANIMEVOST_ADDRESS = "https://animevost.org"
        private const val PLAYLIST_URL = "https://api.animevost.org/v1/playlist"
        private const val FRAME5_URL = "$ANIMEVOST_ADDRESS/frame5.php?play="

        val instance: AnimeVostProvider = AnimeVostProvider()
    }

    private suspend fun getJsoupHtml(url: String, onStatus: ((String) -> Unit)? = null): Document {
        onStatus?.invoke("[Сеть]: Запрос отправлен...")
        val response = client.get(url)
        onStatus?.invoke("[Сеть]: Ответ получен, чтение данных...")
        val body = response.bodyAsText()
        onStatus?.invoke("Разбор HTML (${body.length / 1024} KB)...")
        return Jsoup.parse(body)
    }

    suspend fun getMovieSeriesList(page: Int? = null, onStatus: ((String) -> Unit)? = null): List<MovieSeriesInfo> = withContext(Dispatchers.IO) {
        val addr = appendPageToAddress(ANIMEVOST_ADDRESS, page)
        val htmlData = getJsoupHtml(addr, onStatus)
        getMovieSeriesListFromHtmlData(htmlData)
    }

    suspend fun getMovieSeriesListByCategory(category: MovieGenre, page: Int? = null, onStatus: ((String) -> Unit)? = null): List<MovieSeriesInfo> = withContext(Dispatchers.IO) {
        val addr = appendPageToAddress("$ANIMEVOST_ADDRESS/zhanr/${category.path}", page)
        val htmlData = getJsoupHtml(addr, onStatus)
        getMovieSeriesListFromHtmlData(htmlData)
    }

    suspend fun getMovieSeriesSearch(query: String, page: Int?): List<MovieSeriesInfo> = withContext(Dispatchers.IO) {
        val response = client.submitForm(
            url = ANIMEVOST_ADDRESS,
            formParameters = parameters {
                append("do", "search")
                append("subaction", "search")
                if (page == null) {
                    append("story", query)
                    append("x", "0")
                    append("y", "0")
                } else {
                    append("search_start", page.toString())
                    append("full_search", "0")
                    append("result_from", (page * 10 - 9).toString())
                    append("story", query)
                }
            }
        )
        val htmlData = Jsoup.parse(response.bodyAsText())
        getMovieSeriesListFromHtmlData(htmlData)
    }

    suspend fun getPlayList(id: Long): Array<PlayEntry> = withContext(Dispatchers.IO) {
        val response = client.submitForm(
            url = PLAYLIST_URL,
            formParameters = parameters {
                append("id", id.toString())
            }
        )

        val body = response.bodyAsText()
        if (body.isEmpty() || body == "[]") return@withContext emptyArray<PlayEntry>()

        val playlistJSON = JSONArray(body)
        Array(playlistJSON.length()) {
            toPlayListEntry(id, playlistJSON.getJSONObject(it))
        }
    }

    suspend fun getMovieSeriesInfo(pageAddr: String, onStatus: ((String) -> Unit)? = null): MovieSeriesInfo = withContext(Dispatchers.IO) {
        val htmlData = getJsoupHtml(pageAddr, onStatus)
        val el = htmlData.selectFirst("div#dle-content")!!.selectFirst("div.shortstory")!!
        val recentIds = AppPrefs.recent.first()
        toMovieSeries(el, pageAddr, recentIds)
    }

    suspend fun getAlternativeVideoSource(id: Long): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val htmlData = getJsoupHtml(FRAME5_URL + id)
            val videoSources = htmlData.select("a.butt[download=invoice]").map { it.attr("href") }
            if (videoSources.size >= 2) {
                Pair(videoSources[1], videoSources[0])
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching alternative source", e)
            null
        }
    }

    suspend fun getMovieSeriesDetails(info: MovieSeriesInfo): MovieSeriesPageInfo = withContext(Dispatchers.IO) {
        val htmlData = getJsoupHtml(info.pageUrl)
        val el = htmlData.selectFirst("div#dle-content")!!.selectFirst("div.shortstory")!!
        val meta = el.select("div.shortstoryContent p")
        var last = el.selectFirst("div.shortstoryContent p span[itemprop=\"description\"]")
        if (last == null) {
            last = meta.last()
            last!!.child(0).remove()
        }
        val description = last.html()
        val related = htmlData.selectFirst("div.text_spoiler")?.select("li")?.associate {
            val href = ANIMEVOST_ADDRESS + it.selectFirst("a")!!.attr("href")
            val title = it.text()
            Pair(title, href)
        } ?: emptyMap()

        val images = htmlData.selectFirst("fieldSet.skrin")?.select("img")?.map { ANIMEVOST_ADDRESS + it.attr("src") }?.toTypedArray()

        val scriptTag = htmlData.select("script").find { it.data().contains("var data = ") }
        val videos = mutableMapOf<String, Long>()

        scriptTag?.let {
            val dataLine = it.data().lines().find { line -> line.contains("var data = ") } ?: ""
            if (dataLine.isNotEmpty()) {
                val jsonPart = dataLine.substringAfter("var data = ").substringBeforeLast(";")
                val cleanedJson = jsonPart.trim().removeSuffix(",")
                if (cleanedJson.isNotEmpty() && cleanedJson != "{}") {
                    try {
                        val videosJson = JSONObject(cleanedJson)
                        for (key in videosJson.keys()) {
                            videos[key] = videosJson.getLong(key)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON parse error", e)
                    }
                }
            }
        }

        val episodesCount = locate(meta, "Количество серий")
        val director = locate(meta, "Директор")
        val pageInfo = MovieSeriesPageInfo(info, description, images ?: emptyArray(), related, videos, episodesCount, director)
        val recentIds = AppPrefs.recent.first()
        if (recentIds.contains(info.id.toString())) {
            pageInfo.info.watched = true
        }
        pageInfo
    }

    private fun appendPageToAddress(addr: String, page: Int?): String {
        if (page == null || page < 2) return addr
        return if (addr.endsWith("/")) "${addr}page/$page/" else "$addr/page/$page/"
    }

    private suspend fun getMovieSeriesListFromHtmlData(htmlData: Document): List<MovieSeriesInfo> = withContext(Dispatchers.IO) {
        val dleContent = htmlData.selectFirst("div#dle-content") ?: return@withContext emptyList()
        val unparsedMovies = dleContent.select("div.shortstory")
        val recentIds = AppPrefs.recent.first()
        unparsedMovies.map { toMovieSeries(it, recentIds) }
    }

    private suspend fun toPlayListEntry(movieId: Long, jsonObject: JSONObject): PlayEntry {
        return PlayEntry(
            jsonObject.getString("hd"),
            jsonObject.getString("name"),
            jsonObject.getString("preview"),
            jsonObject.getString("std")
        ).also { entry ->
            AppPrefs.watchedEps.first()[movieId]?.get(entry.id)?.let {
                entry.storedPosition = it.first
                entry.watchedPercent = it.second
            }
        }
    }

    private fun toMovieSeries(el: Element, recentIds: Set<String>): MovieSeriesInfo {
        val titleAnchor = el.selectFirst("div.shortstoryHead h2 a")!!
        val pageUrl = titleAnchor.attr("href")
        return toMovieSeries(el, titleAnchor, pageUrl, recentIds)
    }

    private fun toMovieSeries(el: Element, pageUrl: String, recentIds: Set<String>): MovieSeriesInfo {
        val titleAnchor = el.selectFirst("div.shortstoryHead h1")!!
        return toMovieSeries(el, titleAnchor, pageUrl, recentIds)
    }

    private fun toMovieSeries(el: Element, titleAnchor: Element, pageUrl: String, recentIds: Set<String>): MovieSeriesInfo {
        val title = titleAnchor.text()
        val meta = el.select("div.shortstoryContent p")

        val yearText = if (meta.isNotEmpty()) {
            val p = meta[0].clone()
            if (p.childrenSize() > 0) p.child(0).remove()
            p.text()
        } else ""

        val yearVariant = yearText.split("-")
        val yearStart = yearVariant.getOrNull(0)?.toShortOrNull() ?: 0
        val yearEnd = yearVariant.getOrNull(1)?.toShortOrNull()

        val genres = if (meta.size > 1) {
            val p = meta[1].clone()
            if (p.childrenSize() > 0) p.child(0).remove()
            p.text().split(',').map { it.trim() }.toTypedArray()
        } else emptyArray()

        val type = if (meta.size > 2) {
            val p = meta[2].clone()
            if (p.childrenSize() > 0) p.child(0).remove()
            p.text()
        } else ""

        val id = try {
            val a = pageUrl.lastIndexOf('/')
            val b = pageUrl.indexOf('-', a)
            if (a != -1 && b != -1) pageUrl.substring(a + 1, b).toLong() else 0L
        } catch (e: Exception) {
            0L
        }

        val imgAddr = el.select("div.shortstoryContent img.imgRadius").attr("src")
        val info = MovieSeriesInfo(id, title, yearStart, yearEnd, type, ANIMEVOST_ADDRESS + imgAddr, pageUrl, genres)
        if (recentIds.contains(info.id.toString())) {
            info.watched = true
        }
        return info
    }

    private fun locate(meta: Elements, paramName: String): String? {
        val el = meta.firstOrNull {
            it.children().isNotEmpty() && it.child(0).text().startsWith(paramName)
        } ?: return null
        val cloned = el.clone()
        cloned.child(0).remove()
        return cloned.text()
    }
}
