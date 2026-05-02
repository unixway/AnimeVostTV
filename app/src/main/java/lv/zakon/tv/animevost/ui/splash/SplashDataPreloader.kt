package lv.zakon.tv.animevost.ui.splash

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import lv.zakon.tv.animevost.data.network.AnimeVostProvider
import lv.zakon.tv.animevost.prefs.AppPrefs
import org.json.JSONArray

class SplashDataPreloader(
    private val provider: AnimeVostProvider,
    private val prefs: AppPrefs
) {
    companion object {
        private const val TAG = "SplashDataPreloader"
    }

    suspend fun preloadMainData(onComplete: () -> Unit) {
        try {
            supervisorScope {
                val latestSeriesJob = async {
                    try {
                        val series = provider.getLatestSeriesPage(page = 1)
                        val jsonArray = JSONArray()
                        series.forEach { jsonArray.put(it.toJson()) }
                        prefs.latestSeriesCache = jsonArray.toString()
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to preload latest series", e)
                    }
                }

                val genresJob = async {
                    try {
                        val genres = provider.getGenres()
                        val jsonArray = JSONArray()
                        genres.forEach { jsonArray.put(it.toJson()) }
                        prefs.genresCache = jsonArray.toString()
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to preload genres", e)
                    }
                }

                val categoriesJob = async {
                    try {
                        val categories = provider.getCategories()
                        val jsonArray = JSONArray()
                        categories.forEach { jsonArray.put(it.toJson()) }
                        prefs.categoriesCache = jsonArray.toString()
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to preload categories", e)
                    }
                }

                awaitAll(latestSeriesJob, genresJob, categoriesJob)
            }
        } finally {
            onComplete()
        }
    }
}