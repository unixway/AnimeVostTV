package lv.zakon.tv.animevost.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lv.zakon.tv.animevost.prefs.AppPrefs
import lv.zakon.tv.animevost.ui.main.MainActivity
import lv.zakon.tv.animevost.api.AnimeVostProvider

/**
 * Entry point Activity for cold start.
 * Displays [SplashFragment] with poster animation for ~2.5 seconds,
 * launches [SplashDataPreloader] in parallel to pre-fetch main screen data,
 * then navigates to [MainActivity].
 *
 * Back button is disabled during splash screen.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Avoid duplicate fragment commits on configuration changes
        if (savedInstanceState == null) {
            val splashFragment = SplashFragment().apply {
                onSplashComplete = { navigateToMain() }
            }
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, splashFragment)
                .commit()

            // Start preloading main screen data in parallel with splash animation
            val appPrefs = AppPrefs(applicationContext)
            val dataPreloader = SplashDataPreloader(
                provider = AnimeVostProvider,
                prefs = appPrefs
            )
            lifecycleScope.launch {
                dataPreloader.preloadMainData {
                    // onComplete callback: no-op here, navigation is driven by SplashFragment timer
                    // If preloader finishes before 2500 ms, we still wait for the fragment callback
                    // If fragment calls navigateToMain() first, this Activity finishes and lifecycleScope cancels this job
                }
            }
        }
    }

    /**
     * Navigate to [MainActivity] and finish this Activity.
     * Called from [SplashFragment.onSplashComplete] lambda.
     */
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Disable back button during splash screen.
     * Prevents user from exiting the app during the mandatory splash animation.
     */
    override fun onBackPressed() {
        // No-op: do not call super.onBackPressed()
    }
}
