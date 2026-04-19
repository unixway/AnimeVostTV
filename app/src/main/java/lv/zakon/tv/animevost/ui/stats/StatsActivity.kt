package lv.zakon.tv.animevost.ui.stats

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import lv.zakon.tv.animevost.R

class StatsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details) // Используем тот же контейнер, что и в деталях

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_fragment, StatsFragment())
                .commitNow()
        }
    }
}
