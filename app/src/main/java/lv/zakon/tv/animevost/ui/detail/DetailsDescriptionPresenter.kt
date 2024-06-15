package lv.zakon.tv.animevost.ui.detail

import android.text.Html
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import lv.zakon.tv.animevost.model.MovieSeriesPageInfo
import lv.zakon.tv.animevost.ui.common.Util.IfExt.ifData

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(
            viewHolder: ViewHolder,
            item: Any) {
        val movieFull = item as MovieSeriesPageInfo

        viewHolder.title.text = movieFull.info.title
        viewHolder.subtitle.text = movieFull.info.genres.contentToString()
        viewHolder.body.text = Html.fromHtml(
            "<b>Год выхода:</b> ${movieFull.info.yearStart}" + movieFull.info.yearEnd.ifData("") { "-$it" } + "<br>" +
                    "<b>Тип:</b> ${movieFull.info.type}<br>" +
                    "<b>Количество серий:</b> ${movieFull.episodesCount}<br>" +
                    (movieFull.director?.let {"<b>Режиссёр:</b> $it <br>" } ?: "") +
                    movieFull.description,
            Html.FROM_HTML_MODE_COMPACT)

    }
}