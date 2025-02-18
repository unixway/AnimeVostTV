package lv.zakon.tv.animevost.ui.playback

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
class PlaybackVideoFragment : Fragment(), Player.Listener {

    private var player: ExoPlayer? = null
    private lateinit var movieSeriesPageInfo: MovieSeriesPageInfo
    private lateinit var videoDesc: PlayEntry

    private var videoUrls: List<String>? = null
    private var currentSourceIndex = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    @OptIn(UnstableApi::class) override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val playerView: PlayerView = view.findViewById(R.id.player_view)
        val videoInfoLayout: LinearLayout = view.findViewById(R.id.video_info_layout)

        // Инициализация ExoPlayer
        player = ExoPlayer.Builder(requireContext()).build()
        playerView.setPlayer(player)
        (view.findViewById<TextView>(R.id.video_title)!!).text = "${videoDesc.name} (${movieSeriesPageInfo.info.title})"
        val description = getString(R.string.playback_info, movieSeriesPageInfo.info.yearStart.toString() + movieSeriesPageInfo.info.yearEnd.ifData("") { "-$it" }, movieSeriesPageInfo.info.genres.contentToString())
        (view.findViewById<TextView>(R.id.video_description)!!).text = description

        Log.i("Player", "playing ${videoDesc.name} (${movieSeriesPageInfo.info.title})")
        // Обработка видимости контролов
        playerView.addOnLayoutChangeListener { _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
            val controlsVisible: Boolean = playerView.isControllerFullyVisible
            if (controlsVisible) {
                videoInfoLayout.visibility = View.VISIBLE
            } else {
                videoInfoLayout.visibility = View.GONE
            }
        }

        player!!.addListener(this)
    }

    override fun onPlayerError(error: PlaybackException) {
        if (currentSourceIndex < videoUrls!!.size - 1) {
            currentSourceIndex++;
            Toast.makeText(requireContext(), "Переключение на резервный источник", Toast.LENGTH_SHORT).show();
            playVideoFromSource(currentSourceIndex);
        } else {
            Toast.makeText(requireContext(), "Ошибка воспроизведения: все источники недоступны", Toast.LENGTH_SHORT).show();
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        movieSeriesPageInfo = Util.getExtra(activity!!, DetailsActivity.MOVIE_SERIES_DETAILS)
        videoDesc = Util.getExtra(activity!!, DetailsActivity.PLAY_DESC)

        lifecycleScope.launch {
            AnimeVostProvider.instance.requestAlternativeVideoSource(videoDesc.id)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event : VideoSourceFetchedEvent) {
        if (videoDesc.id == event.movieId) {
            Log.i("Player","fetched alternative data source: ${event.videoSource}")

            videoUrls = listOf(event.videoSource, videoDesc.hd)
            playVideoFromSource(currentSourceIndex)

            lifecycleScope.launch {
                AppPrefs.markWatch(movieSeriesPageInfo.info.id, movieSeriesPageInfo.info.pageUrl, videoDesc.id)
            }
        }
    }

    private fun playVideoFromSource(idx: Int) {
        playVideoFromSource(videoUrls!![idx])
    }

    private fun playVideoFromSource(videoSource: String) {
        val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(videoSource))
        player!!.setMediaItem(mediaItem)
        Log.i("Player", "storedPosition: ${videoDesc.storedPosition}")
        if (videoDesc.storedPosition > 0) {
            player!!.seekTo(videoDesc.storedPosition)
        }
        player!!.prepare()
        player!!.play()
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
        super.onPause()
        player!!.pause()
        Log.i("VideoPlayer", "onPause: ${player!!.currentPosition}/${player!!.duration}")
        lifecycleScope.launch {
            val duration = player!!.duration
            if (duration > 0) {
                val percent = (player!!.currentPosition * 100 / duration).toByte()
                AppPrefs.markWatch(movieSeriesPageInfo.info.id, movieSeriesPageInfo.info.pageUrl, videoDesc.id, player!!.currentPosition, percent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}