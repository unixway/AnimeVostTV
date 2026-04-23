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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import lv.zakon.tv.animevost.sync.DriveAuthProvider
import lv.zakon.tv.animevost.sync.DriveFileRepository
import lv.zakon.tv.animevost.sync.DriveSyncManager
import lv.zakon.tv.animevost.sync.PositionEntry
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

    private var videoUrls: List<String>? = null
    private var currentSourceIndex = 0

    private lateinit var driveSyncManager: DriveSyncManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    @OptIn(UnstableApi::class) override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = view.findViewById(R.id.player_view)
        val videoInfoLayout: LinearLayout = view.findViewById(R.id.video_info_layout)

        // Инициализация ExoPlayer
        player = ExoPlayer.Builder(requireContext()).build()
        playerView.setPlayer(player)
        (view.findViewById<TextView>(R.id.video_title)!!).text = "${videoDesc.name} (${movieSeriesPageInfo.info.title})"
        val description = getString(R.string.playback_info, movieSeriesPageInfo.info.yearStart.toString() + movieSeriesPageInfo.info.yearEnd.ifData("") { "-$it" }, movieSeriesPageInfo.info.genres.contentToString())
        (view.findViewById<TextView>(R.id.video_description)!!).text = description

        Log.i("Player", "playing ${videoDesc.name} (${movieSeriesPageInfo.info.title})")

        playerView.addOnLayoutChangeListener { _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
            videoInfoLayout.visibility = playerView.isControllerFullyVisible.ifc(View.VISIBLE, View.GONE)
        }

        player!!.addListener(this)

        // Initialize DriveSyncManager
        try {
            val httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                defaultRequest {
                    url("https://www.googleapis.com/")
                }
            }
            val authProvider = DriveAuthProvider(requireContext())
            val driveRepo = DriveFileRepository(authProvider, httpClient)
            driveSyncManager = DriveSyncManager(requireContext(), driveRepo, AppPrefs)
        } catch (e: Exception) {
            Log.e("Player", "Failed to initialize DriveSyncManager", e)
        }

        loadSourcesAndPlay()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        movieSeriesPageInfo = Util.getExtra(requireActivity(), DetailsActivity.MOVIE_SERIES_DETAILS)
        videoDesc = Util.getExtra(requireActivity(), DetailsActivity.PLAY_DESC)
    }

    private fun playVideoFromSource(idx: Int) {
        videoUrls?.getOrNull(idx)?.let { playVideoFromSource(it) }
    }

    private fun playVideoFromSource(videoSource: String) {
        val mediaItem: MediaItem = MediaItem.fromUri(videoSource.toUri())
        player!!.setMediaItem(mediaItem)
        if (videoDesc.storedPosition > 0) {
            player!!.seekTo(videoDesc.storedPosition)
        }
        player!!.prepare()
        player!!.playWhenReady = true
        playerView.keepScreenOn = true
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        playerView.keepScreenOn = playWhenReady
        if (!playWhenReady) {
            handlePositionMarked(player!!)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            playerView.keepScreenOn =  false
            handlePositionMarked(player!!)
        }
        super.onPlaybackStateChanged(playbackState)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        playerView.keepScreenOn = true
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        player?.let { handlePositionMarked(it) }
    }

    private fun handlePositionMarked(player: ExoPlayer) {
        lifecycleScope.launch {
            val duration = player.duration
            if (duration > 0) {
                val percent = (player.currentPosition * 100 / duration).toByte()
                AppPrefs.markWatch(
                    movieSeriesPageInfo.info.id,
                    movieSeriesPageInfo.info.pageUrl,
                    videoDesc.id,
                    player.currentPosition,
                    percent
                )

                // Schedule Drive upload after position saved
                if (::driveSyncManager.isInitialized) {
                    driveSyncManager.schedulePositionUpload(
                        episodeId = videoDesc.id.toString(),
                        entry = PositionEntry(
                            storedPosition = player.currentPosition.toInt(),
                            watchedPercent = percent.toInt(),
                            timestamp = System.currentTimeMillis()
                        ),
                        seriesPageUrl = movieSeriesPageInfo.info.pageUrl
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}
