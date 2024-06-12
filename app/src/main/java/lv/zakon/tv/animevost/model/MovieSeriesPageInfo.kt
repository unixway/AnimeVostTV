package lv.zakon.tv.animevost.model

import java.io.Serializable

@Suppress("ArrayInDataClass")
data class MovieSeriesPageInfo(
    val info: MovieSeriesInfo,
    val description: String?,
    val imageInfo : Array<String>,
    val relatedSeries: Map<String, String>,
    val videos: Map<String, Long>,
    val episodesCount: String?,
    val director: String?
) : Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        internal const val serialVersionUID = 1L
    }
}