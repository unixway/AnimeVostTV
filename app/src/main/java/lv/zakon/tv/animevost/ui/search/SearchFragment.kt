package lv.zakon.tv.animevost.ui.search
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.ui.CardPresenter
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.MovieSeriesSearchFinishedEvent
import lv.zakon.tv.animevost.ui.detail.DetailsActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {
    private lateinit var mRowsAdapter: ArrayObjectAdapter
    private val mMovieSeriesAdapter = ArrayObjectAdapter(CardPresenter())

    private var mResultsFound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())

        setSearchResultProvider(this)
        setOnItemViewClickedListener(ItemViewClickedListener())
        setOnItemViewSelectedListener(ItemViewSelectedListener())

        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            REQUEST_SPEECH -> when (resultCode) {
                Activity.RESULT_OK -> setSearchQuery(data, true)
                else ->                         // If recognizer is canceled or failed, keep focus on the search orb
                    if (FINISH_ON_RECOGNIZER_CANCELED) {
                        if (!hasResults()) {
                            view!!.findViewById<View>(androidx.leanback.R.id.lb_search_bar_speech_orb).requestFocus()
                        }
                    }
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter {
        return mRowsAdapter
    }

    override fun onQueryTextChange(newQuery: String): Boolean {
        lifecycleScope.launch {
            displayCompletions(AppPrefs.searches.first().filter { it.contains(newQuery) })
        }
        (activity as SearchActivity).loadQuery(newQuery, delayed = true)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    fun hasResults(): Boolean {
        return mRowsAdapter.size() > 0 && mResultsFound
    }


    fun focusOnSearch() {
        view!!.findViewById<View>(androidx.leanback.R.id.lb_search_bar).requestFocus()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSearchFinishedEvent(event : MovieSeriesSearchFinishedEvent) {
        val header = if (event.start) {
            val titleRes = if (event.series.isEmpty()) {
                mResultsFound = false
                R.string.no_search_results
            } else {
                mResultsFound = true
                R.string.search_results
            }
            mRowsAdapter.clear()
            mMovieSeriesAdapter.clear()
            HeaderItem(getString(titleRes, event.query))
        } else {
            null
        }
        event.series.forEach(mMovieSeriesAdapter::add)
        header?.let {
            val row = ListRow(header, mMovieSeriesAdapter)
            mRowsAdapter.add(row)
        }
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder, item: Any,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            if (item is MovieSeriesInfo) {
                val intent = Intent(activity, DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.MOVIE, item)

                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    (activity)!!,
                    (itemViewHolder.view as ImageCardView).mainImageView,
                    DetailsActivity.SHARED_ELEMENT_NAME
                ).toBundle()
                activity!!.startActivity(intent, bundle)
            } else {
                Toast.makeText(activity, (item as String), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?
        ) {
            if (item == null) {
                return
            }
            val count = mMovieSeriesAdapter.size()
            if (mMovieSeriesAdapter[count - 1] == item) {
                (activity as SearchActivity).loadQuery(page = (count / ROWS_PER_PAGE + 1))
            }
        }
    }

    companion object {
        private const val TAG = "SearchFragment"
        private const val FINISH_ON_RECOGNIZER_CANCELED = true
        private const val REQUEST_SPEECH = 0x00000010

        private const val ROWS_PER_PAGE = 10
    }
}
