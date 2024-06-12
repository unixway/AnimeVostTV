package lv.zakon.tv.animevost.ui.common

import androidx.fragment.app.FragmentActivity

class Util {
    companion object {
        @Suppress( "UNCHECKED_CAST")
        fun <T> getExtra(activity: FragmentActivity, name: String) : T = activity.intent!!.getSerializableExtra(name) as T

        fun <T> ifData(data: T?, act: (T) -> String) : String = if (data == null) "" else act(data)
    }
}