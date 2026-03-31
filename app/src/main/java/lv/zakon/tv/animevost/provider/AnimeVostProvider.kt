package lv.zakon.tv.animevost.provider

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lv.zakon.tv.animevost.model.MovieGenre
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.event.request.EventCounterGenerator
import lv.zakon.tv.animevost.provider.event.response.MovieSeriesFetchedEvent
import lv.zakon.tv.animevost.provider.event.response.MovieSeriesInfoEvent
import lv.zakon.tv.animevost.provider.event.response.MovieSeriesSearchFinishedEvent
import lv.zakon.tv.animevost.provider.event.response.PlaylistFetchedEvent
import lv.zakon.tv.animevost.provider.event.response.VideoSourceFetchedEvent
import org.greenrobot.eventbus.EventBus
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.stream.Collectors

typealias RequestId = Int

class AnimeVostProvider private constructor() {
    // Единый клиент для всех сетевых запросов
    private val client = HttpClient(CIO) {
    }

    companion object {
        private const val TAG = "AnimeVostProvider"
        private const val ANIMEVOST_ADDRESS = "https://animevost.org"
        private const val PLAYLIST_URL = "https://api.animevost.org/v1/playlist"
        private const val FRAME5_URL = "$ANIMEVOST_ADDRESS/frame5.php?play="

        val instance: AnimeVostProvider = AnimeVostProvider()
    }

    // Вспомогательный метод для загрузки и парсинга HTML через Ktor+Jsoup
    private suspend fun getJsoupHtml(url: String): Document {
        val response = client.get(url)
        return Jsoup.parse(response.bodyAsText())
    }

    fun requestMovieSeriesList(scope: CoroutineScope, page: Int? = null) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            try {
                requestMovieSeriesListInt(requestId, page)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching series list", e)
            }
        }
        return requestId
    }

    fun requestMovieSeriesListByCategory(scope: CoroutineScope, category : MovieGenre, page: Int? = null) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            try {
                requestMovieSeriesListByCategoryInt(requestId, category, page)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching series by category", e)
            }
        }
        return requestId
    }

    fun requestMovieSeriesSearch(scope: CoroutineScope, query: String, page: Int?) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            try {
                requestMovieSeriesSearchInt(requestId, query, page)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching series", e)
            }
        }
        return requestId
    }

    fun requestPlayList(scope: CoroutineScope, id: Long) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            try {
                requestPlayListInt(requestId, id)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching playlist", e)
            }
        }
        return requestId
    }

    fun requestMovieSeriesInfo(scope: CoroutineScope, pageAddr: String) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            try {
                requestMovieSeriesInfoInt(requestId, pageAddr)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching movie info", e)
            }
        }
        return requestId
    }

    suspend fun requestAlternativeVideoSource(id: Long) = withContext(Dispatchers.IO) {
        try {
            val htmlData = getJsoupHtml(FRAME5_URL + id)
            val videoSources = htmlData.select("a.butt[download=invoice]").map { it.attr("href") }
            if (videoSources.size >= 2) {
                EventBus.getDefault().post(VideoSourceFetchedEvent(id, videoSources[1], videoSources[0]))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching alternative source for $id", e)
        }
    }

    private suspend fun requestMovieSeriesListInt(requestId: RequestId, page: Int? = null) {
        val addr = appendPageToAddress(ANIMEVOST_ADDRESS, page)
        val series = getMovieSeriesList(addr)
        EventBus.getDefault().post(MovieSeriesFetchedEvent(requestId, series = series))
    }

    private suspend fun requestMovieSeriesListByCategoryInt(requestId: RequestId, category : MovieGenre, page: Int? = null) {
        val addr = appendPageToAddress(ANIMEVOST_ADDRESS + "/zhanr/" + category.path, page)
        val series = getMovieSeriesList(addr)
        EventBus.getDefault().post(MovieSeriesFetchedEvent(requestId, category, series))
    }

    suspend fun getMovieSeriesDetails(info: MovieSeriesInfo) : MovieSeriesPageInfo = withContext(Dispatchers.IO) {
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
            var videosJsonStr = it.data().lines().find { line -> line.contains("var data = ") } ?: ""
            if (videosJsonStr.isNotEmpty()) {
                val stopIdx = if (videosJsonStr.endsWith(",};")) 3 else 2
                videosJsonStr = videosJsonStr.substring(12, videosJsonStr.length - stopIdx) + '}'
                if (videosJsonStr != "}") {
                    val videosJson = JSONObject(videosJsonStr)
                    for (key in videosJson.keys()) {
                        videos[key] = videosJson.getLong(key)
                    }
                }
            }
        }

        val episodesCount = locate(meta, "Количество серий")
        val director = locate(meta, "Директор")
        val pageInfo = MovieSeriesPageInfo(info, description, images ?: emptyArray(), related, videos, episodesCount, director)
        if (AppPrefs.recent.first().contains(info.id.toString())) {
            pageInfo.info.watched = true
        }
        pageInfo
    }

    private suspend fun requestMovieSeriesInfoInt(requestId: RequestId, pageAddr: String) = withContext(Dispatchers.IO) {
        val htmlData = getJsoupHtml(pageAddr)
        val info = toMovieSeries(htmlData.selectFirst("div#dle-content")!!.selectFirst("div.shortstory")!!, pageAddr)
        EventBus.getDefault().post(MovieSeriesInfoEvent(requestId, info))
    }

    private suspend fun requestMovieSeriesSearchInt(requestId: RequestId, query: String, page: Int?) = withContext(Dispatchers.IO) {
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
        getMovieSeriesListFromHtmlData(htmlData).also {
            EventBus.getDefault().post(MovieSeriesSearchFinishedEvent(requestId, query, it, page == null))
        }
    }

    private suspend fun requestPlayListInt(requestId: RequestId, id: Long) {
        val response = client.submitForm(
            url = PLAYLIST_URL,
            formParameters = parameters {
                append("id", id.toString())
            }
        )

        val body = response.bodyAsText()
        val playlistJSON = JSONArray(body)
        Log.i(TAG, "requestPlayList[playlist]: $body length: ${playlistJSON.length()}")
        val playlist = Array(playlistJSON.length()) {
            toPlayListEntry(id, playlistJSON.getJSONObject(it))
        }
        EventBus.getDefault().post(PlaylistFetchedEvent(requestId, id, playlist))
    }

    private fun appendPageToAddress(addr: String, page: Int?): String {
        if (page == null || page < 2) {
            return addr
        }
        return "$addr/page/$page"
    }

    private suspend fun getMovieSeriesList(addr: String) : List<MovieSeriesInfo> = withContext(Dispatchers.IO) {
        val htmlData = getJsoupHtml(addr)
        getMovieSeriesListFromHtmlData(htmlData)
    }

    private suspend fun getMovieSeriesListFromHtmlData(htmlData: Document) : List<MovieSeriesInfo> = withContext(Dispatchers.IO) {
        val dleContent = htmlData.selectFirst("div#dle-content") ?: return@withContext emptyList()
        val unparsedMovies = dleContent.select("div.shortstory")
        unparsedMovies.map { toMovieSeries(it) }
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

    private suspend fun toMovieSeries(el : Element) : MovieSeriesInfo {
        val titleAnchor = el.selectFirst("div.shortstoryHead h2 a")!!
        val pageUrl = titleAnchor.attr("href")
        return toMovieSeries(el, titleAnchor, pageUrl)
    }

    private suspend fun toMovieSeries(el : Element, pageUrl: String) : MovieSeriesInfo {
        val titleAnchor = el.selectFirst("div.shortstoryHead h1")!!
        return toMovieSeries(el, titleAnchor, pageUrl)
    }

    private suspend fun toMovieSeries(el : Element, titleAnchor : Element, pageUrl: String) : MovieSeriesInfo {
        val title = titleAnchor.text()
        val meta = el.select("div.shortstoryContent p")

        // Безопасное извлечение года
        val yearText = if (meta.size > 0) {
            val p = meta[0].clone()
            p.child(0).remove()
            p.text()
        } else ""

        val yearVariant = yearText.split("-")
        val yearStart = yearVariant.getOrNull(0)?.toShortOrNull() ?: 0
        val yearEnd = yearVariant.getOrNull(1)?.toShortOrNull()

        // Жанры
        val genres = if (meta.size > 1) {
            val p = meta[1].clone()
            p.child(0).remove()
            p.text().split(',').map { it.trim() }.toTypedArray()
        } else emptyArray()

        // Тип
        val type = if (meta.size > 2) {
            val p = meta[2].clone()
            p.child(0).remove()
            p.text()
        } else ""

        val id = try {
            val a = pageUrl.lastIndexOf('/')
            val b = pageUrl.indexOf('-', a)
            pageUrl.substring(a + 1, b).toLong()
        } catch (e: Exception) {
            0L
        }

        val imgAddr = el.select("div.shortstoryContent img.imgRadius").attr("src")
        val info = MovieSeriesInfo(id, title, yearStart, yearEnd, type, ANIMEVOST_ADDRESS + imgAddr, pageUrl, genres)
        if (AppPrefs.recent.first().contains(info.id.toString())) {
            info.watched = true
        }
        return info
    }

    private fun locate(meta: Elements, paramName: String) : String? {
        val el = meta.firstOrNull {
            it.children().isNotEmpty() && it.child(0).text().startsWith(paramName)
        } ?: return null
        val cloned = el.clone()
        cloned.child(0).remove()
        return cloned.text()
    }
}
