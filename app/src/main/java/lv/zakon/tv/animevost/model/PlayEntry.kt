package lv.zakon.tv.animevost.model

import java.io.Serializable

data class PlayEntry(
    val hd: String,
    val name: String,
    val preview: String,
    val std: String,
) : Serializable {
    val id by lazy {
        hd.substringAfterLast('/').substringBefore('.').toLong()
    }

    val httpsPreview by lazy {
        preview.replaceFirst("http:", "https:")
    }

    var storedPosition: Long = 0
    var watchedPercent: Byte = 0
}