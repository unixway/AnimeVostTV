package lv.zakon.tv.animevost.model

/**
 * Represents playback progress for a single episode.
 *
 * @property id Episode ID
 * @property name Episode name/title
 * @property hd HD video source URL
 * @property std Standard quality video source URL
 * @property storedPosition Last playback position in milliseconds
 * @property watchedPercent Watched percentage (0-100)
 * @property lastWatchedAt Timestamp (milliseconds since epoch) when episode was last watched, null for legacy entries
 */
data class PlayEntry(
    val id: Long,
    val name: String,
    val hd: String,
    val std: String,
    val storedPosition: Long = 0,
    val watchedPercent: Byte = 0,
    val lastWatchedAt: Long? = null
)
