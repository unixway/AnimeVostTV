package lv.zakon.tv.animevost.ui.detail

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.graphics.drawable.Drawable
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.BackgroundManager
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
import kotlin.math.roundToInt

class VideoDetailsFragment(private val details: MovieSeriesPageInfo) : DetailsSupportFragment() {
    private var mSelectedMovie: MovieSeriesInfo? = null

    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mBounds: Rect

    private lateinit var mPresenterSelector: ClassPresenterSelector
    private lateinit var mAdapter: ArrayObjectAdapter
    private lateinit var mPlaylistAdapter: ArrayObjectAdapter
    private lateinit var mActionAdapter: ArrayObjectAdapter
    private var relatedRowAdapter: ArrayObjectAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prepareBackgroundManager()

        mSelectedMovie = Util.getExtra(requireActivity(), DetailsActivity.MOVIE) as MovieSeriesInfo
        mPresenterSelector = ClassPresenterSelector()
        mAdapter = ArrayObjectAdapter(mPresenterSelector)

        setupDetailsOverviewRow()
        setupDetailsOverviewRowPresenter()
        setupPlaylistRow()

        adapter = mAdapter

        // Сразу ставим фон
        mSelectedMovie?.cardImageUrl?.let { updateBackground(it) }

        onItemViewClickedListener = ItemViewClickedListener()
    }

    private fun prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(requireActivity())
        if (!mBackgroundManager.isAttached) {
            mBackgroundManager.attach(requireActivity().window)
        }
        mBackgroundManager.isAutoReleaseOnStop = false
        mDefaultBackground = ContextCompat.getDrawable(requireContext(), R.drawable.default_background)
        mBounds = requireActivity().windowManager.currentWindowMetrics.bounds
    }

    private fun updateBackground(uri: String?) {
        val width = mBounds.width()
        val height = mBounds.height()
        Glide.with(requireContext())
                .load(uri)
                .centerCrop()
                .error(mDefaultBackground)
                .into<CustomTarget<Drawable>>(
                        object : CustomTarget<Drawable>(width, height) {
                            override fun onResourceReady(drawable: Drawable,
                                                         transition: Transition<in Drawable>?) {
                                mBackgroundManager.drawable = drawable
                            }
                            override fun onLoadCleared(placeholder: Drawable?) {
                                mBackgroundManager.drawable = null
                            }
                        })
    }

    // Метод для динамического добавления плейлиста (вызывается из Activity)
    fun setPlaylist(playlist: Array<PlayEntry>) {
        mActionAdapter.clear()
        mPlaylistAdapter.clear()
        for (entry in playlist) {
            val watchedMark = PlayEntryPresenter.eyeify(entry.watchedPercent)
            val action = FatAction(entry, watchedMark)
            mActionAdapter.add(action) // Появятся кнопки наверху
            mPlaylistAdapter.add(entry) // Появятся карточки внизу
        }
    }

    // Метод для динамического добавления связанных серий
    fun addRelatedSeries(info: MovieSeriesInfo) {
        if (relatedRowAdapter == null) {
            setupRelatedMovieListRow()
        }
        relatedRowAdapter?.add(info)
    }

    private class FatAction(val entry: PlayEntry, watchedMark : CharSequence) : Action(entry.id, entry.name, watchedMark)

    private fun setupPlaylistRow() {
        mPlaylistAdapter = ArrayObjectAdapter(PlayEntryPresenter())
        val header = HeaderItem(0, getString(R.string.series_list))
        mAdapter.add(ListRow(header, mPlaylistAdapter))
    }

    private fun setupRelatedMovieListRow() {
        relatedRowAdapter = ArrayObjectAdapter(CardPresenter())
        val header = HeaderItem(1, getString(R.string.related_movies))
        mAdapter.add(ListRow(header, relatedRowAdapter))
    }

    private fun setupDetailsOverviewRow() {
        val row = DetailsOverviewRow(details)
        row.imageDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.default_background)
        val width = convertDpToPixel(requireContext(), DETAIL_THUMB_WIDTH)
        val height = convertDpToPixel(requireContext(), DETAIL_THUMB_HEIGHT)
        Glide.with(requireContext())
                .load(mSelectedMovie?.cardImageUrl)
                .centerCrop()
                .error(R.drawable.default_background)
                .into<CustomTarget<Drawable>>(object : CustomTarget<Drawable>(width, height) {
                    override fun onResourceReady(drawable: Drawable, transition: Transition<in Drawable>?) {
                        row.imageDrawable = drawable
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
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())
        detailsPresenter.backgroundColor = ContextCompat.getColor(requireContext(), R.color.selected_background)

        val sharedElementHelper = FullWidthDetailsOverviewSharedElementHelper()
        sharedElementHelper.setSharedElementEnterTransition(activity, DetailsActivity.SHARED_ELEMENT_NAME)
        detailsPresenter.setListener(sharedElementHelper)
        detailsPresenter.isParticipatingEntranceTransition = true

        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            val intent = Intent(requireContext(), PlaybackActivity::class.java)
            intent.putExtra(DetailsActivity.MOVIE_SERIES_DETAILS, details)
            action as FatAction
            intent.putExtra(DetailsActivity.PLAY_DESC, action.entry)
            startActivity(intent)
        }
        mPresenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
        mPresenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())
    }

    private fun convertDpToPixel(context: Context, dp: Int): Int {
        val density = context.applicationContext.resources.displayMetrics.density
        return (dp.toFloat() * density).roundToInt()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
                itemViewHolder: Presenter.ViewHolder?,
                item: Any?,
                rowViewHolder: RowPresenter.ViewHolder,
                row: Row) {
            when(item) {
                is MovieSeriesInfo -> {
                    val intent = Intent(context!!, DetailsActivity::class.java)
                    intent.putExtra(DetailsActivity.MOVIE, item)
                    val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            activity!!,
                            (itemViewHolder?.view as ImageCardView).mainImageView!!,
                            DetailsActivity.SHARED_ELEMENT_NAME
                        ).toBundle()
                    startActivity(intent, bundle)
                }
                is PlayEntry -> {
                    val intent = Intent(requireContext(), PlaybackActivity::class.java)
                    intent.putExtra(DetailsActivity.MOVIE_SERIES_DETAILS, details)
                    intent.putExtra(DetailsActivity.PLAY_DESC, item)
                    startActivity(intent)
                }
            }
        }
    }

    companion object {
        private const val DETAIL_THUMB_WIDTH = 274
        private const val DETAIL_THUMB_HEIGHT = 384
    }
}
