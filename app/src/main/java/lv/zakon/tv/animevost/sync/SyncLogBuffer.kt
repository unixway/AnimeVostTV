package lv.zakon.tv.animevost.sync

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException

private val Context.dataStore by preferencesDataStore(name = "sync_log_buffer_prefs")

class SyncLogBuffer(private val context: Context) {
    private val mutex = Mutex()
    private val inMemoryList = mutableListOf<SyncLogEntry>()
    private var loaded = false

    private val bufferKey = stringPreferencesKey("sync_log_buffer")

    /**
     * Appends entry to in-memory list and persists the full list to DataStore.
     * If DataStore write fails, logs error but does not throw (ADR-004).
     * In-memory list retains the entry regardless of persistence failure.
     */
    suspend fun add(entry: SyncLogEntry) {
        mutex.withLock {
            if (!loaded) {
                restoreInternal()
            }
            inMemoryList.add(entry)
            persistToDataStore()
        }
    }

    /**
     * Returns all entries currently buffered.
     * Calls restore() on first access if not yet loaded.
     */
    suspend fun getAll(): List<SyncLogEntry> {
        mutex.withLock {
            if (!loaded) {
                restoreInternal()
            }
            return inMemoryList.toList()
        }
    }

    /**
     * Clears in-memory list and DataStore key.
     * Safe to call on an already-empty buffer.
     */
    suspend fun clearAll() {
        mutex.withLock {
            inMemoryList.clear()
            loaded = true
            persistToDataStore()
        }
    }

    /**
     * Loads buffered entries from DataStore into memory.
     * Idempotent: safe to call multiple times.
     * On empty DataStore or malformed JSON, returns empty list without throwing.
     */
    suspend fun restore() {
        mutex.withLock {
            if (loaded) {
                return
            }
            restoreInternal()
        }
    }

    /**
     * Internal restore implementation (must be called under mutex lock).
     * Reads DataStore pref, parses JSON array, populates in-memory list.
     * On parse error or empty value, sets in-memory list to empty and logs warning.
     */
    private suspend fun restoreInternal() {
        val jsonString = context.dataStore.data.map { prefs ->
            prefs[bufferKey] ?: ""
        }.first()

        if (jsonString.isBlank()) {
            inMemoryList.clear()
            loaded = true
            return
        }

        try {
            val jsonArray = JSONArray(jsonString)
            inMemoryList.clear()
            for (i in 0 until jsonArray.length()) {
                val entryJsonObject = jsonArray.getJSONObject(i)
                val entry = SyncLogEntry.fromJson(entryJsonObject)
                inMemoryList.add(entry)
            }
            loaded = true
        } catch (e: JSONException) {
            Log.w(TAG, "Failed to parse sync log buffer JSON, resetting to empty", e)
            inMemoryList.clear()
            loaded = true
        }
    }

    /**
     * Persists current in-memory list to DataStore as JSON array string.
     * Catches all exceptions, logs with Log.e, and returns normally (ADR-004).
     */
    private suspend fun persistToDataStore() {
        try {
            val jsonArray = JSONArray()
            for (entry in inMemoryList) {
                jsonArray.put(entry.toJson())
            }
            val jsonString = jsonArray.toString()

            context.dataStore.edit { prefs ->
                prefs[bufferKey] = jsonString
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist sync log buffer to DataStore", e)
        }
    }

    companion object {
        private const val TAG = "SyncLogBuffer"
    }
}
