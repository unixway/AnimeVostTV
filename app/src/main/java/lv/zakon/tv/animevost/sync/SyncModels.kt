package lv.zakon.tv.animevost.sync

data class SyncLogEntry(
    val entryId: String,
    val timestamp: Long,
    val eventType: SyncEventType,
    val seriesId: String,
    val episodeId: String?,
    val storedPosition: Int?,
    val watchedPercent: Int?,
    val seriesTitle: String?
)

enum class SyncEventType {
    PLAY_PROGRESS,
    RECENT_ADD
}

data class PlayEntryChangeEvent(
    val seriesId: String,
    val episodeId: String,
    val storedPosition: Int,
    val watchedPercent: Int,
    val timestamp: Long
)

data class RecentItemChangeEvent(
    val seriesId: String,
    val seriesTitle: String,
    val timestamp: Long
)

data class MergeStats(
    val playProgressApplied: Int,
    val recentItemsApplied: Int
)

sealed class SyncError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoAccount(message: String) : SyncError(message)
    class DriveApiFailure(message: String, cause: Throwable? = null) : SyncError(message, cause)
    class NetworkUnavailable(message: String, cause: Throwable? = null) : SyncError(message, cause)
}
