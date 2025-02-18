package lv.zakon.tv.animevost.provider

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
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
    companion object {
        private const val TAG = "AnimeVostProvider"
        private const val ANIMEVOST_ADDRESS = "https://animevost.org"
        private const val HD_TRN_SU = "https://hd.trn.su/720/"
        private const val PLAYLIST_URL = "https://api.animevost.org/v1/playlist"
        private const val FRAME5_URL = ANIMEVOST_ADDRESS + "/frame5.php?play="

        val instance: AnimeVostProvider = AnimeVostProvider()
    }

    fun requestMovieSeriesList(scope: CoroutineScope, page: Int? = null) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            requestMovieSeriesListInt(requestId, page)
        }
        return requestId
    }

    fun requestMovieSeriesListByCategory(scope: CoroutineScope, category : MovieGenre, page: Int? = null) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            requestMovieSeriesListByCategoryInt(requestId, category, page)
        }
        return requestId
    }

    fun requestMovieSeriesSearch(scope: CoroutineScope, query: String, page: Int?) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            requestMovieSeriesSearchInt(requestId, query, page)
        }
        return requestId
    }

    fun requestPlayList(scope: CoroutineScope, id: Long) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            requestPlayListInt(requestId, id)
        }
        return requestId
    }

    fun requestMovieSeriesInfo(scope: CoroutineScope, pageAddr: String) : RequestId {
        val requestId = EventCounterGenerator.generate()
        scope.launch {
            requestMovieSeriesInfoInt(requestId, pageAddr)
        }
        return requestId
    }

    suspend fun requestAlternativeVideoSource(id: Long) = withContext(Dispatchers.IO) {
        val htmlData = Jsoup.connect(FRAME5_URL + id).get()
        val videoSource = htmlData.select("a.butt[download=invoice]")[1].attr("href")

        EventBus.getDefault().post(VideoSourceFetchedEvent(id, videoSource))
    }

    private suspend fun requestMovieSeriesListInt(requestId: RequestId, page: Int? = null) {
        appendPageToAddress(ANIMEVOST_ADDRESS, page).also { addr ->
            getMovieSeriesList(addr).also {
                EventBus.getDefault().post(MovieSeriesFetchedEvent(requestId, series = it))
            }
        }
    }

    private suspend fun requestMovieSeriesListByCategoryInt(requestId: RequestId, category : MovieGenre, page: Int? = null) {
        appendPageToAddress(ANIMEVOST_ADDRESS + "/zhanr/" + category.path, page).also { addr ->
            getMovieSeriesList(addr).also {
                EventBus.getDefault().post(MovieSeriesFetchedEvent(requestId, category, it))
            }
        }
    }

    suspend fun getMovieSeriesDetails(info: MovieSeriesInfo) : MovieSeriesPageInfo = withContext(Dispatchers.IO) {
        val htmlData = Jsoup.connect(info.pageUrl).get()
        val el = htmlData.selectFirst("div#dle-content").selectFirst("div.shortstory")
        val meta = el.select("div.shortstoryContent p")
        var last = el.selectFirst("div.shortstoryContent p span[itemprop=\"description\"]")
        if (last == null) {
            last = meta.last()
            last.child(0).remove()
        }
        val description = last.html()
        val related = htmlData.selectFirst("div.text_spoiler")?.select("li")?.associate {
            val href = ANIMEVOST_ADDRESS + it.selectFirst("a").attr("href")
            val title = it.text()
            Pair(title, href)
        }?:emptyMap<String, String>()
        val images = htmlData.selectFirst("fieldSet.skrin")?.select("img")?.map { ANIMEVOST_ADDRESS + it.attr("src") }?.toTypedArray()
        var videosJsonStr = htmlData.select("script").find { it.data().contains("var data = ") }!!.data().lines().find { it.contains("var data = ") }!!
        val stopIdx = if (videosJsonStr.endsWith(",};")) 3 else 2
        videosJsonStr = videosJsonStr.substring(12, videosJsonStr.length - stopIdx) + '}'
        val videosJson = JSONObject(videosJsonStr)
        val videos = mutableMapOf<String, Long>()
        for (key in videosJson.keys()) {
            videos[key] = videosJson.getLong(key)
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
        val htmlData = Jsoup.connect(pageAddr).get()
        val info = toMovieSeries(htmlData.selectFirst("div#dle-content").selectFirst("div.shortstory"), pageAddr)
        EventBus.getDefault().post(MovieSeriesInfoEvent(requestId, info))
    }

    private suspend fun requestMovieSeriesSearchInt(requestId: RequestId, query: String, page: Int?) = withContext(Dispatchers.IO) {
        val htmlData = Jsoup.connect(ANIMEVOST_ADDRESS).let {
            if (page == null) {
                it.data("do", "search", "subaction", "search", "story", query, "x", "0", "y", "0")
            } else {
                it.data("do", "search", "subaction", "search", "search_start", page.toString(),
                    "full_search", "0", "result_from", (page * 10 - 9).toString(), "story", query)
            }
        }.post()
        getMovieSeriesListFromHtmlData(htmlData).also {
            EventBus.getDefault().post(MovieSeriesSearchFinishedEvent(requestId, query, it, page == null))
        }
    }

    private suspend fun requestPlayListInt(requestId: RequestId, id: Long) {
        val client = HttpClient(CIO)

        val response = client.submitForm(
            url = PLAYLIST_URL,
            formParameters = parameters {
                append("id", id.toString())
            }
        )

        val playlistJSON = JSONArray(response.bodyAsText())
        Log.i(TAG, "requestPlayList[playlist]: $playlistJSON " + playlistJSON.length())
        val playlist = Array(playlistJSON.length()) {
            val entry = toPlayListEntry(id, playlistJSON.getJSONObject(it))
            entry
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
        Jsoup.connect(addr).get().let { htmlData ->
            getMovieSeriesListFromHtmlData(htmlData)
        }
    }

    private suspend fun getMovieSeriesListFromHtmlData(htmlData: Document) : List<MovieSeriesInfo> = withContext(Dispatchers.IO) {
        val unparsedMovies = htmlData.selectFirst("div#dle-content").select("div.shortstory")
        val acc = mutableListOf<MovieSeriesInfo>()
        for (el in unparsedMovies) {
            acc.add(toMovieSeries(el))
        }
        acc
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
        return el.selectFirst("div.shortstoryHead h2 a").let { titleAnchor ->
            val pageUrl = titleAnchor.attr("href")
            toMovieSeries(el, titleAnchor, pageUrl)
        }
    }

    private suspend fun toMovieSeries(el : Element, pageUrl: String) : MovieSeriesInfo {
        val titleAnchor = el.selectFirst("div.shortstoryHead h1")
        return toMovieSeries(el, titleAnchor, pageUrl)
    }

    private suspend fun toMovieSeries(el : Element, titleAnchor : Element, pageUrl: String) : MovieSeriesInfo {
        val title = titleAnchor.text().toString()
        val meta = el.select("div.shortstoryContent p")
        meta[0].child(0).remove()
        val yearVariant = meta[0].text().split("-")
        val yearStart = yearVariant[0].toShort()
        val yearEnd = if (yearVariant.size == 2) {
            yearVariant[1].toShort()
        } else {
            null
        }
        meta[1].child(0).remove()
        val genres = meta[1].text().split(',').stream().map(String::trim).collect(Collectors.toList()).toTypedArray()
        meta[2].child(0).remove()
        val type = meta[2].text()
        val id = let {
            val a = pageUrl.lastIndexOf('/')
            val b = pageUrl.indexOf('-', a)
            pageUrl.substring(a + 1, b).toLong()
        }
        val imgAddr = el.select("div.shortstoryContent img.imgRadius").attr("src")
        val info = MovieSeriesInfo(id, title, yearStart, yearEnd, type, ANIMEVOST_ADDRESS + imgAddr, pageUrl, genres)
        if (AppPrefs.recent.first().contains(info.id.toString())) {
            info.watched = true
        }
        return info
    }

    fun getMovieSource(id: Long) : String = "$HD_TRN_SU$id.mp4"

    private fun locate(meta: Elements, paramName: String) : String? {
        val el = meta.firstOrNull {
            it.children().isNotEmpty() && it.child(0)?.text()?.startsWith(paramName) ?: false
        }
        if (el == null) {
            return null
        }
        el.child(0).remove()
        return el.text()
    }
}