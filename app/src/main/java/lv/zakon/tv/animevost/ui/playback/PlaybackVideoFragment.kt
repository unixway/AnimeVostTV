package lv.zakon.tv.animevost.ui.playback

import android.media.MediaPlayer.SEEK_CLOSEST_SYNC
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.MediaPlayerAdapter
import androidx.leanback.media.PlaybackGlue
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.PlaybackControlsRow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import lv.zakon.tv.animevost.ui.common.Util
import lv.zakon.tv.animevost.ui.common.Util.ifext.ifData
import lv.zakon.tv.animevost.ui.detail.DetailsActivity

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue : VideoPlayerGlue
    private lateinit var movieSeriesPageInfo : MovieSeriesPageInfo
    private lateinit var videoDesc : PlayEntry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        movieSeriesPageInfo = Util.getExtra(activity!!, DetailsActivity.MOVIE_SERIES_DETAILS)
        videoDesc = Util.getExtra(activity!!, DetailsActivity.PLAY_DESC)

        val videoUrl = AnimeVostProvider.instance.getMovieSource(videoDesc.id)

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
        val playerAdapter = MediaPlayerAdapter(context)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = VideoPlayerGlue(context, playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = videoDesc.name + " (" + movieSeriesPageInfo.info.title + ")"
        mTransportControlGlue.subtitle = "Год: ${movieSeriesPageInfo.info.yearStart}" + movieSeriesPageInfo.info.yearEnd.ifData("") { "-$it" } + ", Жанр: " + movieSeriesPageInfo.info.genres.contentToString()
        mTransportControlGlue.isSeekEnabled = true

        playerAdapter.setDataSource(Uri.parse(videoUrl))
        Log.i("Player", "storedPosition: ${videoDesc.storedPosition}")
        if (videoDesc.storedPosition > 0) {
            mTransportControlGlue.seekToAndPlayWhenPrepared(videoDesc.storedPosition)
        } else {
            mTransportControlGlue.playWhenPrepared()
        }

        lifecycleScope.launch {
            AppPrefs.markWatch(movieSeriesPageInfo.info.id, movieSeriesPageInfo.info.pageUrl, videoDesc.id)
        }
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
        Log.i("VideoPlayer", "onPause: ${mTransportControlGlue.currentPosition}/${mTransportControlGlue.duration}")
        lifecycleScope.launch {
            val duration = mTransportControlGlue.duration
            if (duration > 0) {
                val percent = (mTransportControlGlue.currentPosition * 100 / duration).toByte()
                AppPrefs.markWatch(movieSeriesPageInfo.info.id, movieSeriesPageInfo.info.pageUrl, videoDesc.id, mTransportControlGlue.currentPosition, percent)
            }
        }
    }
}