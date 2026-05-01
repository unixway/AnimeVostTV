package lv.zakon.tv.animevost.ui.main

import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.model.MovieSeriesInfo

/**
 * Data class for 'Continue Watching' card model.
 */
data class ContinueWatchingItem(
    val seriesId: Long,
    val episodeId: Long,
    val movieInfo: MovieSeriesInfo,
    val watchedPercent: Int,
    val storedPosition: Long,
    val lastWatchedAt: Long
)

/**
 * Presenter for 'Continue Watching' cards in MainFragment.
 * Renders poster, anime title, and watched percentage via ImageCardView.
 */
class ContinueWatchingCardPresenter : Presenter() {

    companion object {
        private const val CARD_WIDTH = 313
        private const val CARD_HEIGHT = 176
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            cardType = ImageCardView.CARD_TYPE_INFO_UNDER
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val continueItem = item as ContinueWatchingItem
        val cardView = viewHolder.view as ImageCardView
        val context = cardView.context

        cardView.titleText = continueItem.movieInfo.title
        cardView.contentText = "${continueItem.watchedPercent}%"

        val posterUrl = continueItem.movieInfo.cardImageUrl
        if (!posterUrl.isNullOrBlank()) {
            Glide.with(context)
                .load(posterUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.movie_placeholder)
                .centerCrop()
                .into(cardView.mainImageView)
        } else {
            cardView.mainImageView.setImageResource(R.drawable.movie_placeholder)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        Glide.with(cardView.context).clear(cardView.mainImageView)
        cardView.mainImageView.setImageDrawable(null)
    }
}
