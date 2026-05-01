package lv.zakon.tv.animevost.ui.playback

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
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import lv.zakon.tv.animevost.ui.common.Util
import lv.zakon.tv.animevost.ui.common.Util.IfExt.ifData
import lv.zakon.tv.animevost.ui.detail.DetailsActivity
import androidx.core.net.toUri
import lv.zakon.tv.animevost.ui.common.Util.IfExt.ifc

/** Handles video playback with media controls. */
class PlaybackVideoFragment : Fragment(), Player.Listener {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var movieSeriesPageInfo: MovieSeriesPageInfo
    private lateinit var videoDesc: PlayEntry
    private lateinit var playlistIterator: Iterator<PlayEntry>

    private var videoUrls: List<String>? = null
    private var currentSourceIndex = 0
    private var lastDuration: Long = 0

    // Задача для периодического сохранения прогресса
    private val updateProgressAction = object : Runnable {
        override fun run() {
            player?.let {
                if (it.isPlaying) {
                    handlePositionMarked(it)
                }
            }
            view?.postDelayed(this, 60000) // Сохраняем каждые 60 секунд
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = view.findViewById(R.id.player_view)
        val videoInfoLayout: LinearLayout = view.findViewById(R.id.video_info_layout)

        player = ExoPlayer.Builder(requireContext()).build()
        playerView.setPlayer(player)
        player!!.pauseAtEndOfMediaItems = true

        updateUIInfo()

        playerView.addOnLayoutChangeListener { _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
            videoInfoLayout.visibility = playerView.isControllerFullyVisible.ifc(View.VISIBLE, View.GONE)
        }

        player!!.addListener(this)
        loadSourcesAndPlay()

        // Включаем фоновое сохранение раз в минуту (на случай вылета или выключения)
        // view.postDelayed(updateProgressAction, 180000)
    }

    private fun updateUIInfo() {
        view?.findViewById<TextView>(R.id.video_title)?.text = "${videoDesc.name} (${movieSeriesPageInfo.info.title})"
        val description = getString(R.string.playback_info, movieSeriesPageInfo.info.yearStart.toString() + movieSeriesPageInfo.info.yearEnd.ifData("") { "-$it" }, movieSeriesPageInfo.info.genres.contentToString())
        view?.findViewById<TextView>(R.id.video_description)?.text = description
    }

    private fun playNext() {
        if (playlistIterator.hasNext()) {
            val nextEpisode = playlistIterator.next()

            player?.stop()
            videoDesc = nextEpisode
            currentSourceIndex = 0

            updateUIInfo()
            loadSourcesAndPlay()

            Log.i("Player", "Switched to next episode: ${videoDesc.name}")
        }
    }

    private fun loadSourcesAndPlay() {
        lifecycleScope.launch {
            try {
                val alternative = AnimeVostProvider.instance.getAlternativeVideoSource(videoDesc.id)
                videoUrls = if (alternative != null) {
                    listOf(alternative.first, videoDesc.hd, alternative.second)
                } else {
                    listOf(videoDesc.hd, videoDesc.std)
                }
                playVideoFromSource(currentSourceIndex)

                AppPrefs.markWatch(movieSeriesPageInfo.info.id, movieSeriesPageInfo.info.pageUrl, videoDesc.id)
            } catch (e: Exception) {
                Log.e("Player", "Error loading video sources", e)
                Toast.makeText(requireContext(), "Ошибка загрузки источников", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (videoUrls != null && currentSourceIndex < videoUrls!!.size - 1) {
            currentSourceIndex++
            Toast.makeText(requireContext(), "Переключение на резервный источник", Toast.LENGTH_SHORT).show()
            playVideoFromSource(currentSourceIndex)
        } else {
            Toast.makeText(requireContext(), "Ошибка воспроизведения: все источники недоступны", Toast.LENGTH_SHORT).show()
            playerView.keepScreenOn = false
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        movieSeriesPageInfo = Util.getExtra(requireActivity(), DetailsActivity.MOVIE_SERIES_DETAILS)
        videoDesc = Util.getExtra(requireActivity(), DetailsActivity.PLAY_DESC)
        playlistIterator = PlayNextIteratorBridge.consume()
    }

    private fun playVideoFromSource(idx: Int) {
        videoUrls?.getOrNull(idx)?.let { playVideoFromSource(it) }
    }

    private fun buildMediaItem(videoDesc: PlayEntry, videoSource: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(videoDesc.id.toString())
            .setUri(videoSource.toUri())
            .build()
    }

    private fun playVideoFromSource(videoSource: String) {
        player!!.clearMediaItems()
        player!!.addMediaItem(buildMediaItem(videoDesc, videoSource))
        if (playlistIterator.hasNext()) {
            player!!.addMediaItem(MediaItem.fromUri("next://episode"))
        }

        if (videoDesc.storedPosition > 0) {
            player!!.seekTo(videoDesc.storedPosition)
        }
        player!!.prepare()
        player!!.playWhenReady = true
        playerView.keepScreenOn = true
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Если перешли на заглушку - значит текущая серия (videoDesc) точно закончилась.
        // Сохраняем её как 100% перед тем как итератор даст следующую.
        if (mediaItem?.localConfiguration?.uri?.toString() == "next://episode") {
            playNext()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        playerView.keepScreenOn = playWhenReady
        if (!playWhenReady) {
            handlePositionMarked(player!!)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            lastDuration = player?.duration ?: 0
        }
        if (playbackState == Player.STATE_ENDED) {
            playerView.keepScreenOn = false
            handlePositionMarked(player!!)
        }
        super.onPlaybackStateChanged(playbackState)
    }

    @OptIn(UnstableApi::class)
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        // Если произошел переход между медиа-файлами (авто или кнопкой Next)
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == Player.DISCONTINUITY_REASON_SEEK) {
            // Пытаемся достать честную длительность из Timeline для старого айтема
            var duration = lastDuration
            player?.currentTimeline?.let { timeline ->
                if (!timeline.isEmpty && oldPosition.mediaItemIndex < timeline.windowCount) {
                    val window = Timeline.Window()
                    timeline.getWindow(oldPosition.mediaItemIndex, window)
                    if (window.durationMs > 0) duration = window.durationMs
                }
            }
            val videoId = oldPosition.mediaItem!!.mediaId.toLong()
            handlePositionMarked(videoId, oldPosition.contentPositionMs, duration)
        }
        playerView.keepScreenOn = true
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        player?.let { handlePositionMarked(it) }
    }

    private fun handlePositionMarked(player: Player) {
        // Если мы на заглушке, player.duration будет 0, используем lastDuration
        val dur = if (player.duration > 0) player.duration else lastDuration
        handlePositionMarked(videoDesc.id, player.currentPosition, dur)
    }

    private fun handlePositionMarked(id: Long, position: Long, duration: Long) {
        if (duration <= 0) return
        lifecycleScope.launch {
            // Защита: не сохраняем позицию "в будущем"
            val safePos = if (position > duration) duration else position
            val percent = (safePos * 100 / duration).coerceIn(0, 100).toByte()
            AppPrefs.markWatch(
                movieSeriesPageInfo.info.id,
                movieSeriesPageInfo.info.pageUrl,
                id,
                safePos,
                percent,
                System.currentTimeMillis()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        view?.removeCallbacks(updateProgressAction)
        player?.release()
        player = null
    }
}
