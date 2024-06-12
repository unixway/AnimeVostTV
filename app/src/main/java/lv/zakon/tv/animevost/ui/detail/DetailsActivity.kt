package lv.zakon.tv.animevost.ui.detail

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.ui.common.Util
import lv.zakon.tv.animevost.provider.AnimeVostProvider

/**
 * Details activity class that loads [VideoDetailsFragment] class.
 */
class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        val mSelectedMovie = Util.getExtra<MovieSeriesInfo>(this, MOVIE)

        lifecycleScope.launch {
            val details = AnimeVostProvider.instance.getMovieSeriesDetails(mSelectedMovie)
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.details_fragment, VideoDetailsFragment(details))
                    .commitNow()
            }
            AnimeVostProvider.instance.requestPlayList(mSelectedMovie.id)
            for (related in details.relatedSeries) {
                AnimeVostProvider.instance.requestMovieSeriesInfo(related.value)
            }
        }
    }

    companion object {
        const val SHARED_ELEMENT_NAME = "hero"
        const val MOVIE = "Movie"
        const val MOVIE_SERIES_DETAILS = "MovieSeriesDetails"
        const val PLAY_DESC = "PlayIdentifier"
    }
}