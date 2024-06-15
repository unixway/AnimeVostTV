package lv.zakon.tv.animevost.ui.common

import androidx.fragment.app.FragmentActivity

class Util {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> getExtra(activity: FragmentActivity, name: String): T =
            activity.intent!!.getSerializableExtra(name) as T

    }

    object IfExt {
        inline fun <T, R> T?.ifData(fb: R, block: (T) -> R): R = if (this == null) fb else block(this)
        fun <R> Boolean.ifc(trueBranch: R, falseBranch: R): R = if (this) trueBranch else falseBranch
    }
}