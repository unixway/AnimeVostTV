package lv.zakon.tv.animevost.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import lv.zakon.tv.animevost.R

class SearchActivity : FragmentActivity() {
    private lateinit var mFragment : SearchFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        if (savedInstanceState == null) {
            mFragment = SearchFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_fragment, mFragment)
                .commitNow()
        } else {
            mFragment = supportFragmentManager.findFragmentById(R.id.search_fragment) as SearchFragment
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

    companion object {
        private const val TAG = "SearchActivity"
    }
}
