package lv.zakon.tv.animevost.ui.splash

import kotlinx.coroutines.flow.first
import lv.zakon.tv.animevost.data.provider.AnimeVostProvider
import lv.zakon.tv.animevost.prefs.AppPrefs

class SplashPosterProvider(
    private val provider: AnimeVostProvider,
    private val prefs: AppPrefs
) {
    suspend fun getPosters(): List<String> {
        return try {
            val seriesList = provider.getLatestSeriesPage()
            val posterUrls = seriesList
                .mapNotNull { it.poster }
                .filterNot { it.isBlank() }

            if (posterUrls.isNotEmpty()) {
                prefs.splashPosterCache.emit(posterUrls)
                posterUrls
            } else {
                prefs.splashPosterCache.first()
            }
        } catch (e: Throwable) {
            prefs.splashPosterCache.first()
        }
    }
}
