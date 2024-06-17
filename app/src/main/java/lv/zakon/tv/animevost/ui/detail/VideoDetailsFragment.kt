package lv.zakon.tv.animevost.ui.detail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.graphics.drawable.Drawable
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import lv.zakon.tv.animevost.ui.CardPresenter
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.ui.playback.PlaybackActivity
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.ui.common.Util
import lv.zakon.tv.animevost.provider.MovieSeriesInfoEvent
import lv.zakon.tv.animevost.provider.PlaylistFetchedEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


/**
 * A wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its metadata plus related videos.
 */
class VideoDetailsFragment(private val details: MovieSeriesPageInfo) : DetailsSupportFragment() {
    private var mSelectedMovie: MovieSeriesInfo? = null

    private lateinit var mDetailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var mPresenterSelector: ClassPresenterSelector
    private lateinit var mAdapter: ArrayObjectAdapter
    private lateinit var mActionAdapter: ArrayObjectAdapter
    private lateinit var relatedRowAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mDetailsBackground = DetailsSupportFragmentBackgroundController(this)

        mSelectedMovie = Util.getExtra(activity!!, DetailsActivity.MOVIE) as MovieSeriesInfo
        mPresenterSelector = ClassPresenterSelector()
        mAdapter = ArrayObjectAdapter(mPresenterSelector)
        setupDetailsOverviewRow()
        setupDetailsOverviewRowPresenter()
        if (details.relatedSeries.isNotEmpty()) {
            setupRelatedMovieListRow()
        }
        adapter = mAdapter
        initializeBackground(mSelectedMovie)
        onItemViewClickedListener = ItemViewClickedListener()
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDetailEvent(event: MovieSeriesInfoEvent) {
        relatedRowAdapter.add(event.info)
    }

    private class FatAction(val entry: PlayEntry, watchedMark : CharSequence) : Action(entry.id, entry.name, watchedMark)

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaylistEvent(event: PlaylistFetchedEvent) {
        for (entry in event.playlist) {
            val watchedMark = eyeify(entry.watchedPercent)
            val action = FatAction(entry, watchedMark)
            mActionAdapter.add(action)
        }
    }

    private fun eyeify(watchedPercent: Byte): CharSequence {
        return when (watchedPercent.toInt()) {
            0 -> ""
            in 1 until 100 -> "👁 ${watchedPercent}%"
            else -> "👁💯"
        }
    }

    private fun initializeBackground(movie: MovieSeriesInfo?) {
        mDetailsBackground.enableParallax()
        Glide.with(context!!)
                .asBitmap()
                .centerCrop()
                .error(R.drawable.default_background)
                .load(movie?.cardImageUrl)
                .into<CustomTarget<Bitmap>>(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(bitmap: Bitmap,
                                                 transition: Transition<in Bitmap>?) {
                        mDetailsBackground.coverBitmap = bitmap
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size())
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        mDetailsBackground.coverBitmap = null
                    }
                })
    }

    private fun setupDetailsOverviewRow() {
        val row = DetailsOverviewRow(details)
        row.imageDrawable = ContextCompat.getDrawable(context!!, R.drawable.default_background)
        val width = convertDpToPixel(context!!, DETAIL_THUMB_WIDTH)
        val height = convertDpToPixel(context!!, DETAIL_THUMB_HEIGHT)
        Glide.with(context!!)
                .load(mSelectedMovie?.cardImageUrl)
                .centerCrop()
                .error(R.drawable.default_background)
                .into<CustomTarget<Drawable>>(object : CustomTarget<Drawable>(width, height) {
                    override fun onResourceReady(drawable: Drawable,
                                                 transition: Transition<in Drawable>?) {
                        row.imageDrawable = drawable
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size())
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        row.imageDrawable = null
                    }
                })

        mActionAdapter = ArrayObjectAdapter()

        row.actionsAdapter = mActionAdapter

        mAdapter.add(row)
    }

    private fun setupDetailsOverviewRowPresenter() {
        // Set detail background.
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        detailsPresenter.backgroundColor =
                ContextCompat.getColor(context!!, R.color.selected_background)

        // Hook up transition element.
        val sharedElementHelper = FullWidthDetailsOverviewSharedElementHelper()
        sharedElementHelper.setSharedElementEnterTransition(
                activity, DetailsActivity.SHARED_ELEMENT_NAME
        )
        detailsPresenter.setListener(sharedElementHelper)
        detailsPresenter.isParticipatingEntranceTransition = true

        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            val intent = Intent(context!!, PlaybackActivity::class.java)
            intent.putExtra(DetailsActivity.MOVIE_SERIES_DETAILS, details)
            action as FatAction
            intent.putExtra(DetailsActivity.PLAY_DESC, action.entry)
            startActivity(intent)
        }
        mPresenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
    }

    private fun setupRelatedMovieListRow() {
        val subcategories = arrayOf(getString(R.string.related_movies))

        relatedRowAdapter = ArrayObjectAdapter(CardPresenter())

        val header = HeaderItem(0, subcategories[0])
        mAdapter.add(ListRow(header, relatedRowAdapter))
        mPresenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())
    }

    private fun convertDpToPixel(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
                itemViewHolder: Presenter.ViewHolder?,
                item: Any?,
                rowViewHolder: RowPresenter.ViewHolder,
                row: Row) {
            if (item is MovieSeriesInfo) {
                val intent = Intent(context!!, DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.MOVIE, item)

                val bundle =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                                activity!!,
                                (itemViewHolder?.view as ImageCardView).mainImageView,
                            DetailsActivity.SHARED_ELEMENT_NAME
                        )
                                .toBundle()
                startActivity(intent, bundle)
            }
        }
    }

    companion object {
        private const val TAG = "VideoDetailsFragment"

        private const val DETAIL_THUMB_WIDTH = 274
        private const val DETAIL_THUMB_HEIGHT = 384
    }
}