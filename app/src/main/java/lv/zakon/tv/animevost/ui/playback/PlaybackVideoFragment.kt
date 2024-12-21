package lv.zakon.tv.animevost.ui.playback

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.MediaPlayerAdapter
import androidx.leanback.widget.PlaybackControlsRow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import lv.zakon.tv.animevost.provider.event.response.VideoSourceFetchedEvent
import lv.zakon.tv.animevost.ui.common.Util
import lv.zakon.tv.animevost.ui.common.Util.IfExt.ifData
import lv.zakon.tv.animevost.ui.detail.DetailsActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue : VideoPlayerGlue
    private lateinit var movieSeriesPageInfo : MovieSeriesPageInfo
    private lateinit var videoDesc : PlayEntry
    private lateinit var playerAdapter : MediaPlayerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        movieSeriesPageInfo = Util.getExtra(activity!!, DetailsActivity.MOVIE_SERIES_DETAILS)
        videoDesc = Util.getExtra(activity!!, DetailsActivity.PLAY_DESC)

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)
        playerAdapter = MediaPlayerAdapter(context)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = VideoPlayerGlue(context, playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = videoDesc.name + " (" + movieSeriesPageInfo.info.title + ")"
        mTransportControlGlue.subtitle = getString(R.string.playback_info, movieSeriesPageInfo.info.yearStart.toString() + movieSeriesPageInfo.info.yearEnd.ifData("") { "-$it" }, movieSeriesPageInfo.info.genres.contentToString())
        mTransportControlGlue.isSeekEnabled = true

        lifecycleScope.launch {
            AnimeVostProvider.instance.requestAlternativeVideoSource(videoDesc.id)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event : VideoSourceFetchedEvent) {
        if (videoDesc.id == event.movieId) {
            playerAdapter.setDataSource(Uri.parse(event.videoSource))
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
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
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