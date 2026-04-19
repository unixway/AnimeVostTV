package lv.zakon.tv.animevost.ui.detail

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.ui.common.Util
import lv.zakon.tv.animevost.provider.AnimeVostProvider

class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        val movie = Util.getExtra<MovieSeriesInfo>(this, MOVIE)

        lifecycleScope.launch {
            try {
                // 1. Сначала грузим только детали, чтобы открыть экран мгновенно
                val details = AnimeVostProvider.instance.getMovieSeriesDetails(movie)

                val fragment = VideoDetailsFragment(details)
                if (savedInstanceState == null) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.details_fragment, fragment)
                        .commitNow()
                }

                // 2. Плейлист грузим параллельно
                launch {
                    try {
                        val playlist = AnimeVostProvider.instance.getPlayList(movie.id)
                        fragment.setPlaylist(playlist)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading playlist", e)
                    }
                }

                // 3. Связанные серии: запускаем все задачи ПАРАЛЛЕЛЬНО
                val relatedDeferreds = details.relatedSeries.map { related ->
                    async {
                        try {
                            AnimeVostProvider.instance.getMovieSeriesInfo(related.value)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading related series: ${related.key}", e)
                            null
                        }
                    }
                }

                // Добавляем их во фрагмент СТРОГО ПО ПОРЯДКУ
                launch {
                    relatedDeferreds.forEach { deferred ->
                        // await() дождется завершения конкретной задачи.
                        // Если она уже готова - вернет результат сразу.
                        deferred.await()?.let { info ->
                            fragment.addRelatedSeries(info)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading basic details", e)
            }
        }
    }

    companion object {
        private const val TAG = "DetailsActivity"
        const val SHARED_ELEMENT_NAME = "hero"
        const val MOVIE = "Movie"
        const val MOVIE_SERIES_DETAILS = "MovieSeriesDetails"
        const val PLAY_DESC = "PlayIdentifier"
    }
}
