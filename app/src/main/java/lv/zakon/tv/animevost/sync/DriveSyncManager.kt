package lv.zakon.tv.animevost.sync

import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import lv.zakon.tv.animevost.prefs.AppPrefs
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

enum class SyncStatus {
    IDLE, SYNCING, ERROR
}

class DriveSyncManager(
    private val context: Context,
    private val appPrefs: AppPrefs
) {
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    private val appendMutex = Mutex()

    fun observeSyncStatus(): Flow<SyncStatus> = _syncStatus.asStateFlow()

    suspend fun appendLogEntry(logEntry: SyncLogEntry): Result<Unit> = withContext(Dispatchers.IO) {
        appendMutex.withLock {
            try {
                _syncStatus.value = SyncStatus.SYNCING

                val credential = getGoogleAccount()
                    ?: return@withContext Result.failure(
                        SyncError.NoAccount("No Google account found on device")
                    )

                val driveService = buildDriveService(credential)
                val logFileName = "sync_log.jsonl"

                // Serialize log entry to JSON line
                val jsonLine = logEntry.toJson().toString() + "\n"

                // Search for existing log file in appDataFolder
                var attempt = 0
                val maxRetries = 3
                val retryDelays = listOf(1000L, 2000L, 4000L)

                while (attempt <= maxRetries) {
                    try {
                        val query = "name='$logFileName' and 'appDataFolder' in parents and trashed=false"
                        val fileList: FileList = driveService.files().list()
                            .setSpaces("appDataFolder")
                            .setQ(query)
                            .setFields("files(id, name)")
                            .execute()

                        val files = fileList.files
                        if (files.isNullOrEmpty()) {
                            // Create new log file
                            val fileMetadata = File().apply {
                                name = logFileName
                                parents = listOf("appDataFolder")
                                mimeType = "text/plain"
                            }
                            driveService.files().create(fileMetadata, com.google.api.client.http.ByteArrayContent.fromString("text/plain", jsonLine))
                                .setFields("id")
                                .execute()
                            Log.d(TAG, "Created new sync log file: $logFileName")
                        } else {
                            // Append to existing file
                            val fileId = files[0].id
                            val outputStream = ByteArrayOutputStream()
                            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                            val existingContent = outputStream.toString("UTF-8")
                            val updatedContent = existingContent + jsonLine

                            driveService.files().update(
                                fileId,
                                null,
                                com.google.api.client.http.ByteArrayContent.fromString("text/plain", updatedContent)
                            ).execute()
                            Log.d(TAG, "Appended entry to sync log file: $fileId")
                        }

                        _syncStatus.value = SyncStatus.IDLE
                        return@withContext Result.success(Unit)
                    } catch (e: GoogleJsonResponseException) {
                        if (e.statusCode == 429 && attempt < maxRetries) {
                            Log.w(TAG, "HTTP 429 rate limit, retry attempt ${attempt + 1}/$maxRetries")
                            delay(retryDelays[attempt])
                            attempt++
                        } else {
                            Log.e(TAG, "Drive API error: ${e.message}", e)
                            _syncStatus.value = SyncStatus.ERROR
                            return@withContext Result.failure(
                                SyncError.DriveApiFailure("Drive API request failed: ${e.message}", e)
                            )
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Drive I/O error: ${e.message}", e)
                        _syncStatus.value = SyncStatus.ERROR
                        return@withContext Result.failure(
                            SyncError.DriveApiFailure("Drive I/O error: ${e.message}", e)
                        )
                    }
                }

                // All retries exhausted
                _syncStatus.value = SyncStatus.ERROR
                Result.failure(
                    SyncError.DriveApiFailure("Drive API rate limit exceeded after $maxRetries retries", null)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in appendLogEntry: ${e.message}", e)
                _syncStatus.value = SyncStatus.ERROR
                Result.failure(
                    SyncError.DriveApiFailure("Unexpected error: ${e.message}", e)
                )
            }
        }
    }

    suspend fun downloadAndMergeLog(): Result<MergeStats> = withContext(Dispatchers.IO) {
        try {
            withTimeout(3_000L) {
                try {
                    _syncStatus.value = SyncStatus.SYNCING

                    val credential = getGoogleAccount()
                        ?: return@withTimeout Result.failure(
                            SyncError.NoAccount("No Google account found on device")
                        )

                    val driveService = buildDriveService(credential)
                    val logFileName = "sync_log.jsonl"

                    // Search for log file
                    val query = "name='$logFileName' and 'appDataFolder' in parents and trashed=false"
                    val fileList: FileList = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setQ(query)
                        .setFields("files(id, name)")
                        .execute()

                    val files = fileList.files
                    if (files.isNullOrEmpty()) {
                        Log.d(TAG, "No sync log file found, returning empty merge stats")
                        _syncStatus.value = SyncStatus.IDLE
                        return@withTimeout Result.success(MergeStats(0, 0))
                    }

                    // Download log content
                    val fileId = files[0].id
                    val outputStream = ByteArrayOutputStream()
                    driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                    val logContent = outputStream.toString("UTF-8")

                    // Parse log entries
                    val playProgressEntries = mutableListOf<SyncLogEntry>()
                    val recentAddEntries = mutableListOf<SyncLogEntry>()

                    logContent.split("\n").forEach { line ->
                        if (line.isBlank()) return@forEach
                        try {
                            val entry = SyncLogEntry.fromJson(JSONObject(line))
                            when (entry.eventType) {
                                SyncEventType.PLAY_PROGRESS -> playProgressEntries.add(entry)
                                SyncEventType.RECENT_ADD -> recentAddEntries.add(entry)
                            }
                        } catch (e: JSONException) {
                            Log.w(TAG, "Skipping malformed JSON line: $line", e)
                        }
                    }

                    // Merge via AppPrefs
                    appPrefs.mergeWatchedEpisodes(playProgressEntries)
                    appPrefs.mergeRecentItems(recentAddEntries)

                    val stats = MergeStats(
                        playProgressApplied = playProgressEntries.size,
                        recentItemsApplied = recentAddEntries.size
                    )

                    _syncStatus.value = SyncStatus.IDLE
                    Log.d(TAG, "Merge completed: $stats")
                    Result.success(stats)
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.e(TAG, "Download timeout after 3s", e)
                    _syncStatus.value = SyncStatus.ERROR
                    Result.failure(
                        SyncError.NetworkUnavailable("Timeout")
                    )
                } catch (e: IOException) {
                    Log.e(TAG, "Drive I/O error during download: ${e.message}", e)
                    _syncStatus.value = SyncStatus.ERROR
                    Result.failure(
                        SyncError.DriveApiFailure("Drive I/O error: ${e.message}", e)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error in downloadAndMergeLog: ${e.message}", e)
                    _syncStatus.value = SyncStatus.ERROR
                    Result.failure(
                        SyncError.DriveApiFailure("Unexpected error: ${e.message}", e)
                    )
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Outer timeout in downloadAndMergeLog", e)
            _syncStatus.value = SyncStatus.ERROR
            Result.failure(
                SyncError.NetworkUnavailable("Timeout")
            )
        }
    }

    private fun getGoogleAccount(): GoogleAccountCredential? {
        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType("com.google")
        if (accounts.isEmpty()) {
            Log.w(TAG, "No Google accounts found on device")
            return null
        }
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = accounts[0]
        Log.d(TAG, "Using Google account: ${accounts[0].name}")
        return credential
    }

    private fun buildDriveService(credential: GoogleAccountCredential): Drive {
        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("AnimeVostTV")
            .build()
    }

    private fun SyncLogEntry.toJson(): JSONObject {
        return JSONObject().apply {
            put("entryId", entryId)
            put("timestamp", timestamp)
            put("eventType", eventType.name)
            put("seriesId", seriesId)
            put("episodeId", episodeId ?: JSONObject.NULL)
            put("storedPosition", storedPosition ?: JSONObject.NULL)
            put("watchedPercent", watchedPercent ?: JSONObject.NULL)
            put("seriesTitle", seriesTitle ?: JSONObject.NULL)
        }
    }

    companion object {
        private const val TAG = "DriveSyncManager"

        fun SyncLogEntry.Companion.fromJson(json: JSONObject): SyncLogEntry {
            return SyncLogEntry(
                entryId = json.getString("entryId"),
                timestamp = json.getLong("timestamp"),
                eventType = SyncEventType.valueOf(json.getString("eventType")),
                seriesId = json.getInt("seriesId"),
                episodeId = if (json.isNull("episodeId")) null else json.optInt("episodeId"),
                storedPosition = if (json.isNull("storedPosition")) null else json.optInt("storedPosition"),
                watchedPercent = if (json.isNull("watchedPercent")) null else json.optInt("watchedPercent"),
                seriesTitle = if (json.isNull("seriesTitle")) null else json.optString("seriesTitle")
            )
        }
    }
}
