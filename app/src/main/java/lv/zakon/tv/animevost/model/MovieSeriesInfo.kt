package lv.zakon.tv.animevost.model

import lv.zakon.tv.animevost.ui.common.Util
import java.io.Serializable

/**
 * Movie class represents video entity with title, description, image thumbs and page url.
 */
data class MovieSeriesInfo(
        val id: Long,
        val title: String,
        val yearStart: Short,
        val yearEnd: Short?,
        val type: String,
        val cardImageUrl: String,
        val pageUrl: String,
        val genres: Array<String> = arrayOf()
) : Serializable {

    var watched: Boolean = false

    override fun toString(): String {
        return "MovieSeries{" +
                "id='$id'" +
                ", title='$title'" +
                ", year='$yearStart'" + Util.ifData (yearEnd) { "-$it" } +
                ", pageUrl='$pageUrl'" +
                ", cardImageUrl='$cardImageUrl'" +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MovieSeriesInfo

        if (id != other.id) return false
        if (title != other.title) return false
        if (yearStart != other.yearStart) return false
        if (yearEnd != other.yearEnd) return false
        if (cardImageUrl != other.cardImageUrl) return false
        if (pageUrl != other.pageUrl) return false
        if (!genres.contentEquals(other.genres)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + yearStart
        yearEnd?.let {
            result = 31 * result + it
        }
        result = 31 * result + cardImageUrl.hashCode()
        result = 31 * result + pageUrl.hashCode()
        result = 31 * result + genres.contentHashCode()
        return result
    }

    companion object {
        @Suppress("ConstPropertyName")
        internal const val serialVersionUID = 1L
    }
}