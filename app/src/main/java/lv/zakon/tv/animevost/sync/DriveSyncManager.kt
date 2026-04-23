package lv.zakon.tv.animevost.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lv.zakon.tv.animevost.prefs.AppPrefs

/**
 * Coordinates Drive sync lifecycle:
 * - Downloads all device state files on app start and merges into AppPrefs
 * - Schedules debounced position uploads with in-memory session tracking for pageUrl inclusion
 *
 * ADR-003: sentPageUrlEpisodeIds is in-memory Set, not persisted.
 */
class DriveSyncManager(
    private val context: Context,
    private val driveRepo: DriveFileRepository,
    private val appPrefs: AppPrefs
) {
    // In-memory session state, reset when object is recreated (ADR-003)
    private val sentPageUrlEpisodeIds = mutableSetOf<String>()
    private val debounceJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "DriveSyncManager"
        private const val DEBOUNCE_MS = 2000L
        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Loads progress from all devices on app start.
     * Never returns Err — all DriveErrors are reported via onStatus callback.
     *
     * @param onStatus Callback for status messages (loading, errors, completion)
     * @return Always Ok(Unit) — errors are logged via onStatus and processing continues
     */
    suspend fun syncOnAppStart(onStatus: (String) -> Unit): Result<Unit, SyncError> {
        onStatus("Drive: загружаем прогресс со всех устройств...")

        // Step 1: List all device files
        val listResult = driveRepo.listDeviceFiles()
        when (listResult) {
            is Result.Err -> {
                onStatus("Drive: ошибка листинга: ${listResult.error}")
                return Result.Ok(Unit)
            }
            is Result.Ok -> {
                val files = listResult.value

                // Step 2: Download and merge each file
                for (file in files) {
                    val downloadResult = driveRepo.downloadFile(file.id)
                    when (downloadResult) {
                        is Result.Err -> {
                            onStatus("Drive: ошибка загрузки ${file.name}")
                            continue
                        }
                        is Result.Ok -> {
                            try {
                                val state = json.decodeFromString<DeviceSyncState>(downloadResult.value)
                                appPrefs.mergeWatchedEpisodes(state.positions)
                            } catch (e: Exception) {
                                // Invalid JSON — skip this file and continue
                                Log.e(TAG, "Failed to parse ${file.name}: ${e.message}")
                                continue
                            }
                        }
                    }
                }
            }
        }

        onStatus("Drive: синхронизация завершена")
        return Result.Ok(Unit)
    }

    /**
     * Schedules a debounced upload of current position for given episodeId.
     * Cancels previous Job for same episodeId if exists.
     * Includes pageUrl in upload only on first call per episodeId in session (ADR-003).
     *
     * @param episodeId Episode identifier
     * @param entry Position entry (not directly used, but context for understanding)
     * @param seriesPageUrl Optional series page URL (included only once per episodeId per session)
     */
    fun schedulePositionUpload(episodeId: String, entry: PositionEntry, seriesPageUrl: String?) {
        // Cancel existing job for this episodeId
        debounceJobs[episodeId]?.cancel()

        // Launch new debounced job
        debounceJobs[episodeId] = scope.launch {
            delay(DEBOUNCE_MS)

            // Read all watched episodes
            val positions = appPrefs.getAllWatchedEpisodes()

            // ADR-003: pageUrl only once per session per episodeId
            val pageUrls: Map<String, String> = if (seriesPageUrl != null && !sentPageUrlEpisodeIds.contains(episodeId)) {
                sentPageUrlEpisodeIds.add(episodeId)
                mapOf(episodeId to seriesPageUrl)
            } else {
                emptyMap()
            }

            // Get device ID
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            // Build device state
            val state = DeviceSyncState(
                deviceId = deviceId,
                positions = positions,
                pageUrls = pageUrls,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            // Upload to Drive
            val fileName = "animevost_sync_${deviceId}.json"
            val jsonContent = json.encodeToString(state)
            val uploadResult = driveRepo.uploadOrUpdateFile(fileName, jsonContent)

            // Log errors only, no crash (medium budget, no retry policy)
            when (uploadResult) {
                is Result.Err -> {
                    Log.e(TAG, "Failed to upload $fileName: ${uploadResult.error}")
                }
                is Result.Ok -> {
                    Log.d(TAG, "Successfully uploaded $fileName")
                }
            }
        }
    }

    /**
     * Uploads current device state with given positions and pageUrls.
     * Helper method extracted for testability and potential future direct use.
     *
     * @param positions Map of episodeId to PositionEntry
     * @param pageUrls Map of episodeId to series page URL
     * @return Ok(Unit) on success, Err(SyncError.NetworkFailure) on DriveError
     */
    suspend fun uploadDeviceState(
        positions: Map<String, PositionEntry>,
        pageUrls: Map<String, String>
    ): Result<Unit, SyncError> {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        val state = DeviceSyncState(
            deviceId = deviceId,
            positions = positions,
            pageUrls = pageUrls,
            lastSyncTimestamp = System.currentTimeMillis()
        )

        val fileName = "animevost_sync_${deviceId}.json"
        val jsonContent = json.encodeToString(state)

        return when (val result = driveRepo.uploadOrUpdateFile(fileName, jsonContent)) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> Result.Err(SyncError.NetworkFailure(result.error.toString()))
        }
    }
}
