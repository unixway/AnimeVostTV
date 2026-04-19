package lv.zakon.tv.animevost.ui.search

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import lv.zakon.tv.animevost.ui.detail.DetailsActivity

/**
 * Фрагмент поиска, отображающий результаты в виде сетки (несколько строк по 5 элементов).
 * Реализует бесконечное листание при переходе на нижний ряд.
 */
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {
    private lateinit var mRowsAdapter: ArrayObjectAdapter

    private var mResultsFound = false
    private var mCurrentQuery: String = ""
    private var mCurrentPage = 1
    private var mIsLoading = false
    private var mEndOfData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Используем стандартный ListRowPresenter для управления строками
        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())

        setSearchResultProvider(this)
        setOnItemViewClickedListener(ItemViewClickedListener())
        setOnItemViewSelectedListener(ItemViewSelectedListener())
    }

    override fun getResultsAdapter(): ObjectAdapter {
        return mRowsAdapter
    }

    override fun onQueryTextChange(newQuery: String): Boolean {
        mCurrentQuery = newQuery
        lifecycleScope.launch {
            // Показываем подсказки из истории поиска (листание истории)
            val searches = AppPrefs.searches.first()
            displayCompletions(searches.filter { it.contains(newQuery, ignoreCase = true) })
        }
        startNewSearch(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        startNewSearch(query)
        return true
    }

    private fun startNewSearch(query: String) {
        if (query.isEmpty()) {
            mRowsAdapter.clear()
            return
        }
        mCurrentPage = 1
        mEndOfData = false
        loadSearch(query, mCurrentPage, clearAdapter = true)
    }

    /**
     * Загружает результаты поиска (поддерживает листание страниц).
     */
    private fun loadSearch(query: String, page: Int, clearAdapter: Boolean = false) {
        if (mIsLoading || mEndOfData) return

        mIsLoading = true
        lifecycleScope.launch {
            try {
                val series = AnimeVostProvider.instance.getMovieSeriesSearch(query, page)

                if (clearAdapter) {
                    mRowsAdapter.clear()
                    mResultsFound = series.isNotEmpty()
                    if (!mResultsFound) {
                        val header = HeaderItem(getString(R.string.no_search_results, query))
                        mRowsAdapter.add(ListRow(header, ArrayObjectAdapter()))
                    }
                }

                if (series.isNotEmpty()) {
                    addResultsToGrid(series, isFirstPage = clearAdapter)
                    mCurrentPage = page
                    // Если вернулось меньше 10, значит листать больше некуда
                    if (series.size < PAGE_SIZE) {
                        mEndOfData = true
                    }
                } else {
                    mEndOfData = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during search loading", e)
                if (clearAdapter) {
                    Toast.makeText(requireContext(), R.string.error_loading_results, Toast.LENGTH_SHORT).show()
                }
            } finally {
                mIsLoading = false
            }
        }
    }

    /**
     * Распределяет список аниме по строкам, имитируя вертикальную сетку.
     */
    private fun addResultsToGrid(series: List<MovieSeriesInfo>, isFirstPage: Boolean) {
        var currentAdapter: ArrayObjectAdapter

        if (isFirstPage) {
            val header = HeaderItem(getString(R.string.search_results, mCurrentQuery))
            currentAdapter = ArrayObjectAdapter(CardPresenter())
            mRowsAdapter.add(ListRow(header, currentAdapter))
        } else {
            // Берём адаптер самой последней строки, чтобы дозаполнить её, если там есть место
            val lastRow = mRowsAdapter[mRowsAdapter.size() - 1] as ListRow
            currentAdapter = lastRow.adapter as ArrayObjectAdapter
        }

        for (item in series) {
            // Если в строке уже 5 элементов, создаем новую строку (сетка 5xN)
            if (currentAdapter.size() >= COLUMNS_COUNT) {
                currentAdapter = ArrayObjectAdapter(CardPresenter())
                mRowsAdapter.add(ListRow(currentAdapter))
            }
            currentAdapter.add(item)
        }
    }

    fun hasResults(): Boolean {
        return mRowsAdapter.size() > 0 && mResultsFound
    }

    fun focusOnSearch() {
        view?.findViewById<View>(androidx.leanback.R.id.lb_search_bar)?.requestFocus()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder, item: Any,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            if (item is MovieSeriesInfo) {
                val intent = Intent(requireContext(), DetailsActivity::class.java)
                intent.putExtra(DetailsActivity.MOVIE, item)

                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    (itemViewHolder.view as ImageCardView).mainImageView!!,
                    DetailsActivity.SHARED_ELEMENT_NAME
                ).toBundle()
                startActivity(intent, bundle)
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
            if (item == null || row == null) return

            // Если фокус перешёл на любую карточку в последней строке — запускаем листание (подгрузку)
            val lastRowIndex = mRowsAdapter.size() - 1
            if (mRowsAdapter[lastRowIndex] == row) {
                loadSearch(mCurrentQuery, mCurrentPage + 1)
            }
        }
    }

    companion object {
        private const val TAG = "SearchFragment"
        private const val COLUMNS_COUNT = 5
        private const val PAGE_SIZE = 10    // Размер страницы на сайте
    }
}
