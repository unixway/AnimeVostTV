package lv.zakon.tv.animevost.ui.playback

import android.content.Context
import androidx.leanback.media.MediaPlayerAdapter
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow

class VideoPlayerGlue(context: Context?, adapter: MediaPlayerAdapter) : PlaybackTransportControlGlue<MediaPlayerAdapter>(context, adapter) {
    private val mFastForwardAction = PlaybackControlsRow.FastForwardAction(context)
    private val mRewindAction = PlaybackControlsRow.RewindAction(context)

    private var seekts = 0L;
    private var fftime = THIRTY_SECONDS;
    private var rewtime = TEN_SECONDS;
    override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
        super.onCreatePrimaryActions(adapter)
        adapter.add(mRewindAction)
        adapter.add(mFastForwardAction)
    }

    override fun onActionClicked(action: Action) {
        if (shouldDispatchAction(action)) {
            dispatchAction(action)
            return
        }
        // Super class handles play/pause and delegates to abstract methods next()/previous().
        super.onActionClicked(action)
    }

    // Should dispatch actions that the super class does not supply callbacks for.
    private fun shouldDispatchAction(action: Action): Boolean {
        return action === mRewindAction || action === mFastForwardAction
    }

    private fun dispatchAction(action: Action) {
        // Primary actions are handled manually.
        if (action === mRewindAction) {
            rewind()
        } else if (action === mFastForwardAction) {
            fastForward()
        } else if (action is PlaybackControlsRow.MultiAction) {
            action.nextIndex()
            // Notify adapter of action changes to handle secondary actions, such as, thumbs up/down
            // and repeat.
            notifyActionChanged(
                action,
                controlsRow.secondaryActionsAdapter as ArrayObjectAdapter
            )
        }
    }

    private fun notifyActionChanged(
        action: PlaybackControlsRow.MultiAction, adapter: ArrayObjectAdapter?
    ) {
        if (adapter != null) {
            val index = adapter.indexOf(action)
            if (index >= 0) {
                adapter.notifyArrayItemRangeChanged(index, 1)
            }
        }
    }

    /** Skips backwards 10 seconds.  */
    private fun rewind() {
        val newseekts = System.currentTimeMillis()
        if (newseekts - seekts < 1000) {
            rewtime += rewtime / 2
        } else {
            rewtime = TEN_SECONDS
        }
        var newPosition: Long =  currentPosition - rewtime
        newPosition = if ((newPosition < 0)) 0 else newPosition
        playerAdapter.seekTo(newPosition)
        seekts = newseekts
    }

    /** Skips forward 30 seconds.  */
    private fun fastForward() {
        if (duration > -1) {
            val newseekts = System.currentTimeMillis()
            if (newseekts - seekts < 1000) {
                fftime += fftime / 2
            } else {
                fftime = THIRTY_SECONDS
            }
            var newPosition: Long = currentPosition + fftime
            newPosition = if ((newPosition > duration)) duration else newPosition
            playerAdapter.seekTo(newPosition)
            seekts = newseekts
        }
    }

    companion object {
        private const val TEN_SECONDS = 10_000
        private const val THIRTY_SECONDS = 30_000
    }

}