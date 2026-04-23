package lv.zakon.tv.animevost.sync

import kotlinx.serialization.Serializable

@Serializable
data class DeviceSyncState(
    val deviceId: String,
    val positions: Map<String, PositionEntry>,
    val pageUrls: Map<String, String>,
    val lastSyncTimestamp: Long
)

@Serializable
data class PositionEntry(
    val storedPosition: Int,
    val watchedPercent: Int,
    val timestamp: Long
)

data class DriveFile(
    val id: String,
    val name: String
)

sealed class SyncError {
    object AuthFailure : SyncError()
    data class NetworkFailure(val message: String) : SyncError()
}

sealed class AuthError {
    object NoAccount : AuthError()
    data class TokenException(val message: String) : AuthError()
}

sealed class DriveError {
    object Unauthorized : DriveError()
    object NotFound : DriveError()
    data class NetworkError(val message: String) : DriveError()
    data class ApiError(val code: Int) : DriveError()
}
