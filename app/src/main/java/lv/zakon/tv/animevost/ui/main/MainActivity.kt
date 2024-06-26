package lv.zakon.tv.animevost.ui.main

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lv.zakon.tv.animevost.model.MovieGenre
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.EventBusException

/**
 * Loads [MainFragment].
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            EventBus.builder().logNoSubscriberMessages(false).sendNoSubscriberEvent(false).installDefaultEventBus()
        } catch (e : EventBusException) {
            // application restart causes this
        }

        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.main_browse_fragment, MainFragment())
                    .commitNow()
        }

        lifecycleScope.launch {
            loadRows()
        }
    }

    private suspend fun loadRows() = withContext(Dispatchers.IO) {
        val recent = AppPrefs.recent.first()
        if (recent.isNotEmpty()) {
            for (id in recent.reversed()) {
                val cache = AppPrefs.cachedMovie.first()
                cache[id.toLong()]?.also { page ->
                    AnimeVostProvider.instance.requestMovieSeriesInfo(page)
                }
            }
        }
        AnimeVostProvider.instance.requestMovieSeriesList()

        for (i in 0 until MovieGenre.entries.size) {
            AnimeVostProvider.instance.requestMovieSeriesListByCategory(MovieGenre.entries[i])
        }
    }
}