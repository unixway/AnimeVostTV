package lv.zakon.tv.animevost.model

data class PlayEntry(
    val hd: String,
    val name: String,
    val preview: String,
    val std: String,
) {
    val id by lazy {
        hd.substringAfterLast('/').substringBefore('.').toLong()
    }

    val httpsPreview by lazy {
        preview.replaceFirst("http:", "https:")
    }

    var watched: Boolean = false
}