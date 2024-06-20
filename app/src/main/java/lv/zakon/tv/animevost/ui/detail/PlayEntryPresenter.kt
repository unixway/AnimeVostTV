package lv.zakon.tv.animevost.ui.detail

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.ui.common.Util.IfExt.ifc

class PlayEntryPresenter internal constructor() : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playentry_preview, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        item as PlayEntry
        with(viewHolder.view.findViewById<ImageView>(R.id.preview)) {
            Glide.with(this).load(item.httpsPreview).into(this)
        }
        with(viewHolder.view.findViewById<TextView>(R.id.title)) {
            text = (item.watchedPercent > 0).ifc("${item.name}\n${eyeify(item.watchedPercent)}", item.name)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
        viewHolder?.view?.findViewById<ImageView>(R.id.preview)?.setImageDrawable(null)
        if (viewHolder != null) {
            setOnClickListener(viewHolder, null)
        }
    }

    companion object {
        fun eyeify(watchedPercent: Byte): CharSequence {
            return when (watchedPercent.toInt()) {
                0 -> ""
                in 1 until 100 -> "👁 ${watchedPercent}%"
                else -> "👁💯"
            }
        }
    }
}