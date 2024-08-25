package lv.zakon.tv.animevost.ui.search

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.provider.AnimeVostProvider
import lv.zakon.tv.animevost.provider.RequestId

class SearchActivity : FragmentActivity() {
    private val mHandler = Handler(Looper.getMainLooper())
    private lateinit var mFragment : SearchFragment
    private var mQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        if (savedInstanceState == null) {
            mFragment = SearchFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_fragment, mFragment)
                .commitNow()
        }
    }

    override fun onSearchRequested() : Boolean {
        if (mFragment.hasResults()) {
            startActivity(Intent(this, SearchActivity::class.java))
        } else {
            mFragment.startRecognition()
        }
        return true
    }

    override fun onKeyDown(keyCode : Int, event : KeyEvent) : Boolean {
        // If there are no results found, press the left key to reselect the microphone
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !mFragment.hasResults()) {
            mFragment.focusOnSearch()
        }
        return super.onKeyDown(keyCode, event)
    }

    private var lastPage = 1

    fun loadQuery(query: String? = null, page: Int? = null, delayed: Boolean = false) {
        if (query == null && page != null) {
            mHandler.removeCallbacksAndMessages(null)
            if (lastPage < page) {
                lastPage = page
                AnimeVostProvider.instance.requestMovieSeriesSearch(lifecycleScope, mQuery!!, page)
            }
        } else if (query!!.length > 3 && query != mQuery) {
            mQuery = query
            mHandler.removeCallbacksAndMessages(null)
            val runQuery = Runnable {
                run {
                    lastPage = 1
                    lifecycleScope.launch {
                        AppPrefs.addSearch(query)
                    }
                    AnimeVostProvider.instance.requestMovieSeriesSearch(lifecycleScope, query ,page)
                }
            }
            if (delayed) {
                mHandler.postDelayed(runQuery, 500)
            } else {
                runQuery.run()
            }
        }
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "SearchActivity"
    }
}
