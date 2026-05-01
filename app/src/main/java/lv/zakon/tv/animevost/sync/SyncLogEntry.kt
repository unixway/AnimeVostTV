package lv.zakon.tv.animevost.sync

import org.json.JSONObject

/**
 * Immutable data model for a single watch-progress sync event.
 * Represents a logged episode watch action from a specific device.
 *
 * @property deviceId Unique identifier of the device that recorded this event
 * @property timestamp Unix timestamp (milliseconds) when the event was recorded
 * @property seriesId Identifier of the anime series
 * @property episodeId Identifier of the episode within the series
 * @property storedPosition Playback position in milliseconds where the user stopped
 * @property watchedPercent Percentage of episode watched (0-100)
 */
data class SyncLogEntry(
    val deviceId: String,
    val timestamp: Long,
    val seriesId: String,
    val episodeId: String,
    val storedPosition: Int,
    val watchedPercent: Int
) {
    companion object {
        /**
         * Deserializes a JSONObject to SyncLogEntry.
         * Uses explicit key access for all six fields to surface missing-key errors as JSONException.
         *
         * @param jsonObject Source JSON with keys: deviceId, timestamp, seriesId, episodeId, storedPosition, watchedPercent
         * @return Deserialized SyncLogEntry
         * @throws org.json.JSONException if any expected key is absent or type mismatch occurs
         */
        fun fromJson(jsonObject: JSONObject): SyncLogEntry {
            return SyncLogEntry(
                deviceId = jsonObject.getString("deviceId"),
                timestamp = jsonObject.getLong("timestamp"),
                seriesId = jsonObject.getString("seriesId"),
                episodeId = jsonObject.getString("episodeId"),
                storedPosition = jsonObject.getInt("storedPosition"),
                watchedPercent = jsonObject.getInt("watchedPercent")
            )
        }

        /**
         * Resolves conflict between two SyncLogEntry instances according to ADR-002.
         * Returns the entry with higher storedPosition.
         * If storedPosition is equal, returns the entry with higher watchedPercent.
         * If both are equal, returns the first entry (a).
         *
         * @param a First entry
         * @param b Second entry
         * @return The winning entry per conflict resolution rules
         */
        fun conflictWinner(a: SyncLogEntry, b: SyncLogEntry): SyncLogEntry {
            return when {
                a.storedPosition > b.storedPosition -> a
                a.storedPosition < b.storedPosition -> b
                a.watchedPercent > b.watchedPercent -> a
                a.watchedPercent < b.watchedPercent -> b
                else -> a // both storedPosition and watchedPercent are equal
            }
        }
    }

    /**
     * Serializes this SyncLogEntry to a JSONObject.
     * All six fields are serialized using their exact field names as JSON keys.
     *
     * @return JSONObject with keys: deviceId, timestamp, seriesId, episodeId, storedPosition, watchedPercent
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", timestamp)
            put("seriesId", seriesId)
            put("episodeId", episodeId)
            put("storedPosition", storedPosition)
            put("watchedPercent", watchedPercent)
        }
    }
}
