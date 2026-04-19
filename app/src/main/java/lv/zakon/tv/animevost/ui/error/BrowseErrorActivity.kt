package lv.zakon.tv.animevost.ui.error

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.ui.main.MainFragment

/**
 * BrowseErrorActivity показывает, как использовать ErrorFragment.
 * Используется в демонстрационных целях.
 */
class BrowseErrorActivity : FragmentActivity() {

    private lateinit var mErrorFragment: ErrorFragment
    private lateinit var mSpinnerFragment: SpinnerFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainFragment())
                .commitNow()
        }
        testError()
    }

    private fun testError() {
        mErrorFragment = ErrorFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.main_browse_fragment, mErrorFragment)
            .commit()

        mSpinnerFragment = SpinnerFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.main_browse_fragment, mSpinnerFragment)
            .commit()

        // Заменяем Handler на Coroutine
        lifecycleScope.launch {
            delay(TIMER_DELAY)
            if (!isFinishing) {
                supportFragmentManager.beginTransaction()
                    .remove(mSpinnerFragment)
                    .commit()
                mErrorFragment.setErrorContent()
            }
        }
    }

    class SpinnerFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val progressBar = ProgressBar(container?.context)
            if (container is FrameLayout) {
                val layoutParams = FrameLayout.LayoutParams(
                    SPINNER_WIDTH,
                    SPINNER_HEIGHT,
                    Gravity.CENTER
                )
                progressBar.layoutParams = layoutParams
            }
            return progressBar
        }
    }

    companion object {
        private const val TIMER_DELAY = 3000L
        private const val SPINNER_WIDTH = 100
        private const val SPINNER_HEIGHT = 100
    }
}
