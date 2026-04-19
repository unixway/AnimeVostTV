package lv.zakon.tv.animevost.ui.main

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import lv.zakon.tv.animevost.R

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.main_browse_fragment, MainFragment())
                    .commitNow()
        }
    }
}
