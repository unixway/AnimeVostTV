package lv.zakon.tv.animevost.ui.main

import java.util.Timer
import java.util.TimerTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
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
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.lifecycle.lifecycleScope

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import lv.zakon.tv.animevost.ui.CardPresenter
import lv.zakon.tv.animevost.ui.detail.DetailsActivity
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.model.MovieGenre
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import lv.zakon.tv.animevost.sync.DriveAuthProvider
import lv.zakon.tv.animevost.sync.DriveFileRepository
import lv.zakon.tv.animevost.sync.DriveSyncManager
import lv.zakon.tv.animevost.ui.common.Util.IfExt.isIt
import lv.zakon.tv.animevost.ui.search.SearchActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Основной фрагмент для отображения сетки карточек с аниме.
 */
class MainFragment : BrowseSupportFragment() {

    private val mHandler = Handler(Looper.myLooper()!!)
    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mBounds: Rect
    private var mBackgroundTimer: Timer? = null
    private var mBackgroundUri: String? = null
    private var cardPresenter: CardPresenter? = null
    private lateinit var mRowsAdapter: ArrayObjectAdapter

    private val mPagesMap = mutableMapOf<Long, Int>()
    private val mLoadingMap = mutableMapOf<Long, Boolean>()
    private val mEndOfDataMap = mutableMapOf<Long, Boolean>()

    private var mLogText: TextView? = null
    private var mLogContainer: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareBackgroundManager()
        setupUIElements()
        setupEventListeners()

        mLogText = requireActivity().findViewById(R.id.log_text)
        mLogContainer = requireActivity().findViewById(R.id.log_container)

        val version = getString(R.string.app_version)
        addLog("ЗАПУСК: AnimeVostTV $version")

        loadData()

        // Drive sync: parallel coroutine, does not block loadData or CountDownLatch
        lifecycleScope.launch {
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
                val driveSyncManager = DriveSyncManager(requireContext(), driveRepo, AppPrefs)

                driveSyncManager.syncOnAppStart { status ->
                    addLog("Drive: $status")
                }
            } catch (e: Exception) {
                addLog("Drive: Ошибка инициализации (${e.message})")
            }
        }
    }

    private fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        mHandler.post {
            mLogText?.append("[$time] $msg\n")
            val scrollView = mLogContainer as? ScrollView
            scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun hideLog() {
        mHandler.postDelayed({
            if (mLogContainer?.visibility == View.VISIBLE) {
                addLog("СИСТЕМА: Интерфейс готов.")
                mLogContainer?.visibility = View.GONE
            }
        }, TimeUnit.SECONDS.toMillis(1))
    }

    private fun prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(activity)
        if (!mBackgroundManager.isAttached) {
            mBackgroundManager.attach(requireActivity().window)
        }
        // Запрещаем Leanback очищать фон при Stop (уходе в детали)
        mBackgroundManager.isAutoReleaseOnStop = false

        mDefaultBackground = ContextCompat.getDrawable(requireContext(), R.drawable.default_background)
        mBounds = requireActivity().windowManager.currentWindowMetrics.bounds
    }

    private fun setupUIElements() {
        val version = getString(R.string.app_version)
        title = getString(R.string.browse_title, version)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        brandColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.search_opaque)

        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = mRowsAdapter
        cardPresenter = CardPresenter()
    }

    private fun loadData() {
        addLog("СИСТЕМА: Запуск очередей загрузки...")

        val barrier = CountDownLatch(2 + MovieGenre.entries.size)

        // 1. Недавние
        lifecycleScope.launch {
            try {
                val recentIds = AppPrefs.recent.first()
                if (recentIds.isEmpty()) {
                    barrier.countDown()
                    return@launch
                }

                addLog("Недавние: [Локально]: Чтение истории из БД...")
                val cache = AppPrefs.cachedMovie.first()
                val reversedIds = recentIds.reversed()
                addLog("Недавние: [Локально]: Прочитал истории из БД (${reversedIds.size} шт.)")

                val deferreds = reversedIds.map { idStr ->
                    async {
                        try {
                            val id = idStr.toLongOrNull() ?: return@async null
                            val pageUrl = cache[id] ?: return@async null
                            AnimeVostProvider.instance.getMovieSeriesInfo(pageUrl)
                        } catch (e: Exception) { null }
                    }
                }
                addLog("Недавние: [Сеть]: Запросил инфо по истории (${reversedIds.size} шт.)")
                barrier.countDown()

                var recentAdapter: ArrayObjectAdapter? = null
                for (deferred in deferreds) {
                    val info = deferred.await()
                    if (info != null) {
                        if (recentAdapter == null) {
                            recentAdapter = ArrayObjectAdapter(cardPresenter!!)
                            insertRowSorted(ListRow(HeaderItem(0, getString(R.string.recent)), recentAdapter))
                            mEndOfDataMap[0L] = true
                        }
                        recentAdapter.add(info)
                    }
                }
                addLog("Недавние: [Сеть]: История загружена (${recentAdapter?.size()} шт.)")
                hideLog()
            } catch (e: Exception) {
                addLog("Недавние: [Локально]: ОШИБКА")
            }
        }

        // 2. Последние
        lifecycleScope.launch {
            try {
                val series = AnimeVostProvider.instance.getMovieSeriesList { status ->
                    if (status.contains("Запрос")){
                        barrier.countDown()
                    }
                    addLog("Последние: $status")
                }
                if (series.isNotEmpty()) {
                    addLog("Последние: Загружено (${series.size} шт.)")
                    hideLog()
                    val adapter = ArrayObjectAdapter(cardPresenter!!)
                    adapter.addAll(0, series)
                    insertRowSorted(ListRow(HeaderItem(1, getString(R.string.last)), adapter))
                    mPagesMap[1L] = 1
                    if (series.size < PAGE_SIZE) mEndOfDataMap[1L] = true
                }
            } catch (e: Exception) {
                addLog("Последние: ОШИБКА (${e.message})")
            }
        }

        // 3. Жанры
        for (genre in MovieGenre.entries) {
            lifecycleScope.launch {
                try {
                    val series = AnimeVostProvider.instance.getMovieSeriesListByCategory(genre) { status ->
                        if (status.contains("Запрос")){
                            barrier.countDown()
                        }
                        // Логируем только важные этапы для каждого жанра

                        if (status.contains("Запрос") || status.contains("Разбор")) {
                            addLog("Жанр «${genre.l10n}»: $status")
                        }
                    }
                    if (series.isNotEmpty()) {
                        addLog("Жанр «${genre.l10n}»: Готово")
                        hideLog()
                        val headerId = (2 + genre.ordinal).toLong()
                        val adapter = ArrayObjectAdapter(cardPresenter!!)
                        adapter.addAll(0, series)
                        insertRowSorted(ListRow(HeaderItem(headerId, genre.l10n), adapter))
                        mPagesMap[headerId] = 1
                        if (series.size < PAGE_SIZE) mEndOfDataMap[headerId] = true
                    }
                } catch (e: Exception) {
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            barrier.await()
            addLog("Ожидание ответов")
        }
    }

    private fun insertRowSorted(newRow: ListRow) {
        val newId = newRow.headerItem.id
        var index = 0
        for (i in 0 until mRowsAdapter.size()) {
            val currentRow = mRowsAdapter[i] as ListRow
            if (currentRow.headerItem.id > newId) {
                break
            }
            if (currentRow.headerItem.id == newId) return
            index++
        }
        mRowsAdapter.add(index, newRow)
    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
            startActivity(Intent(activity, SearchActivity::class.java))
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
        override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?,
                                    rowViewHolder: RowPresenter.ViewHolder, row: Row) {
            if (item is MovieSeriesInfo) {
                mBackgroundUri = item.cardImageUrl
                startBackgroundTimer()

                if (row is ListRow) {
                    val adapter = row.adapter as ArrayObjectAdapter
                    if (adapter.indexOf(item) >= adapter.size() - SIGN_LOADMORE) {
                        loadNextPage(row)
                    }
                }
            }
        }
    }

    private fun loadNextPage(row: ListRow) {
        val headerId = row.headerItem.id
        if (mLoadingMap[headerId].isIt() || mEndOfDataMap[headerId].isIt()) return
        val currentPage = mPagesMap[headerId] ?: return
        val nextPage = currentPage + 1
        mLoadingMap[headerId] = true

        lifecycleScope.launch {
            try {
                val series = when {
                    headerId == 1L -> AnimeVostProvider.instance.getMovieSeriesList(nextPage)
                    headerId >= 2L -> {
                        val genreIndex = (headerId - 2).toInt()
                        val genre = MovieGenre.entries[genreIndex]
                        AnimeVostProvider.instance.getMovieSeriesListByCategory(genre, nextPage)
                    }
                    else -> emptyList()
                }

                if (series.isNotEmpty()) {
                    val adapter = row.adapter as ArrayObjectAdapter
                    series.forEach { adapter.add(it) }
                    mPagesMap[headerId] = nextPage
                    if (series.size < PAGE_SIZE) mEndOfDataMap[headerId] = true
                } else {
                    mEndOfDataMap[headerId] = true
                }
            } catch (e: Exception) {
            } finally {
                mLoadingMap[headerId] = false
            }
        }
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
        private const val PAGE_SIZE = 10
        private const val SIGN_LOADMORE = 2
    }
}
