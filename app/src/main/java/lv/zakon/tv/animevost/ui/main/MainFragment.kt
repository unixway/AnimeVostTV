package lv.zakon.tv.animevost.ui.main

import java.util.Timer
import java.util.TimerTask

import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import lv.zakon.tv.animevost.ui.error.BrowseErrorActivity
import lv.zakon.tv.animevost.ui.CardPresenter
import lv.zakon.tv.animevost.ui.detail.DetailsActivity
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.ui.search.SearchActivity
import lv.zakon.tv.animevost.provider.MovieSeriesFetchedEvent
import lv.zakon.tv.animevost.provider.MovieSeriesInfoEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {

    private val mHandler = Handler(Looper.myLooper()!!)
    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mBounds: Rect
    private var mBackgroundTimer: Timer? = null
    private var mBackgroundUri: String? = null
    private var cardPresenter: CardPresenter? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        prepareBackgroundManager()

        setupUIElements()

        setupEventListeners()
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        EventBus.getDefault().unregister(this)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mBackgroundTimer?.cancel()
    }

    private fun prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(activity)
        mBackgroundManager.attach(activity!!.window)
        mDefaultBackground = ContextCompat.getDrawable(context!!, R.drawable.default_background)
        mBounds = activity!!.windowManager.currentWindowMetrics.bounds
    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)
        // over title
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // set fastLane (or headers) background color
        brandColor = ContextCompat.getColor(context!!, R.color.fastlane_background)
        // set search icon color
        searchAffordanceColor = ContextCompat.getColor(context!!, R.color.search_opaque)

        adapter = ArrayObjectAdapter(ListRowPresenter())
        cardPresenter = CardPresenter()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event : MovieSeriesInfoEvent) {
        val rowsAdapter = adapter as ArrayObjectAdapter

        if (rowsAdapter.size() == 0 || (rowsAdapter[0] as ListRow).id != 0L) {
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            listRowAdapter.add(event.info)
            val header = HeaderItem(0, getString(R.string.recent))
            rowsAdapter.add(0, ListRow(header, listRowAdapter))
            return
        }
        ((rowsAdapter[0] as ListRow).adapter as ArrayObjectAdapter).add(event.info)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event : MovieSeriesFetchedEvent) {
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)
        for (movie in event.series) {
            listRowAdapter.add(movie)
        }
        val header = if (event.genre == null) {
            HeaderItem(1, getString(R.string.last))
        } else {
            HeaderItem(2 + event.genre.ordinal.toLong(), event.genre.l10n)
        }
        val rowsAdapter = adapter as ArrayObjectAdapter
        rowsAdapter.add(ListRow(header, listRowAdapter))
    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
                val intent = Intent(activity, SearchActivity::class.java)
                startActivity(intent)
       }

        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
                itemViewHolder: Presenter.ViewHolder,
                item: Any,
                rowViewHolder: RowPresenter.ViewHolder,
                row: Row) {

            when(item) {
                is MovieSeriesInfo -> {
                    val intent = Intent(context!!, DetailsActivity::class.java)
                    intent.putExtra(DetailsActivity.MOVIE, item)

                    val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity!!,
                        (itemViewHolder.view as ImageCardView).mainImageView,
                        DetailsActivity.SHARED_ELEMENT_NAME
                    ).toBundle()
                    startActivity(intent, bundle)
                }
                is String -> {
                    if (item.contains(getString(R.string.error_fragment))) {
                        val intent = Intent(context!!, BrowseErrorActivity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(context!!, item, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?,
                                    rowViewHolder: RowPresenter.ViewHolder, row: Row) {
            if (item is MovieSeriesInfo) {
                mBackgroundUri = item.cardImageUrl
                startBackgroundTimer()
            }
        }
    }

    private fun updateBackground(uri: String?) {
        val width = mBounds.width()
        val height = mBounds.height()
        Glide.with(context!!)
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
        mBackgroundTimer?.cancel()
    }

    private fun startBackgroundTimer() {
        mBackgroundTimer?.cancel()
        mBackgroundTimer = Timer()
        mBackgroundTimer?.schedule(UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY.toLong())
    }

    private inner class UpdateBackgroundTask : TimerTask() {
        override fun run() {
            mHandler.post { updateBackground(mBackgroundUri) }
        }
    }

    companion object {
        private const val TAG = "MainFragment"

        private const val BACKGROUND_UPDATE_DELAY = 300
    }
}