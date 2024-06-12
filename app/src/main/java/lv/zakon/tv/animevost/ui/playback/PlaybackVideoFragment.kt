package lv.zakon.tv.animevost.ui.playback

import android.net.Uri
import android.os.Bundle
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.MediaPlayerAdapter
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.PlaybackControlsRow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import lv.zakon.tv.animevost.ui.common.Util
import lv.zakon.tv.animevost.ui.detail.DetailsActivity

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: PlaybackTransportControlGlue<MediaPlayerAdapter>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val movieSeriesPageInfo = Util.getExtra<MovieSeriesPageInfo>(activity!!, DetailsActivity.MOVIE_SERIES_DETAILS)
        val videoDesc = Util.getExtra<Pair<String, Long>>(activity!!, DetailsActivity.PLAY_DESC)

        val videoUrl = AnimeVostProvider.instance.getMovieSource(videoDesc.second)

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
        val playerAdapter = MediaPlayerAdapter(context)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = VideoPlayerGlue(context, playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = movieSeriesPageInfo.info.title
        mTransportControlGlue.subtitle = "Год: ${movieSeriesPageInfo.info.yearStart}" + Util.ifData(movieSeriesPageInfo.info.yearEnd) { "-$it" } + ", Жанр: " + movieSeriesPageInfo.info.genres.contentToString()
        mTransportControlGlue.playWhenPrepared()
        mTransportControlGlue.isSeekEnabled = true

        playerAdapter.setDataSource(Uri.parse(videoUrl))

        lifecycleScope.launch {
            AppPrefs.markWatch(movieSeriesPageInfo.info.id, movieSeriesPageInfo.info.pageUrl, videoDesc.second)
        }
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }
}