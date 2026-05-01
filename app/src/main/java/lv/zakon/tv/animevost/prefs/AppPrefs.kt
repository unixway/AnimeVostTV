package lv.zakon.tv.animevost.prefs

import android.util.Log
import com.s_h_y_a.kotlindatastore.DataStorePrefDataConverter
import com.s_h_y_a.kotlindatastore.KotlinDataStoreModel
import com.s_h_y_a.kotlindatastore.pref.saveAsStringFlowPref
import com.s_h_y_a.kotlindatastore.pref.stringSetFlowPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AppPrefs : KotlinDataStoreModel<AppPrefs>() {
    val searches by stringSetFlowPref(setOf("isekai", "academia", "iruma"), key = "searches")
    val recent by stringSetFlowPref(key = "recentA")
    val cachedMovie by jsonFlowPref(mapOf(), "cachedD", MapLongStringDeser())
    val watchedEps by jsonFlowPref(mapOf(), "watchedE", MapLongMapLongTripleLongByteLongNullableDeser())

    suspend fun addSearch(search: String) {
        val searchesSoFar = searches.first()
        if (searchesSoFar.contains(search).not()) {
            val mutateSearches = searchesSoFar.toMutableSet().also {
                it.add(search)
            }
            searches.emit(mutateSearches)
        }
    }

    suspend fun markWatch(id: Long, pageUrl: String, episodeId: Long, position: Long = 0, percent: Byte = 0, lastWatchedAt: Long? = null) = withContext(Dispatchers.IO) {
        val recently = recent.first()
        val strId = id.toString()
        if ((strId == recently.lastOrNull()).not()) {
            val mutateRecent = recently.toMutableSet()
            if (mutateRecent.contains(strId)) {
                mutateRecent.remove(strId)
                // пришлось писать два раза, потому что y LinkedHashSet хешфункция совпадает с HashSet,
                // порядок не имеет значения, и изменение порядка элементов не записывается в DataStore
                recent.emit(mutateRecent)
            }
            mutateRecent.add(strId)
            recent.emit(mutateRecent)
        }
        val cache = cachedMovie.first()
        if (cache.contains(id).not()) {
            val mutateCache = cache.toMutableMap().also {
                it[id] = pageUrl
            }
            cachedMovie.emit(mutateCache)
        }
        val watched = watchedEps.first()
        val prePosition = watched[id]?.get(episodeId)?.first
        if (prePosition == null || prePosition < position) {
            val mutateWatched = watched.toMutableMap()
            mutateWatched.compute(id, fun(_:Long, map: Map<Long, Triple<Long, Byte, Long?>>?): Map<Long, Triple<Long, Byte, Long?>> =
                with(Triple(position, percent, lastWatchedAt)) {
                    map?.toMutableMap()?.also { it[episodeId] = this } ?: mapOf(Pair(episodeId, this))
                })
            watchedEps.emit(mutateWatched)
        }
    }

    private fun <KT, VT, V> KotlinDataStoreModel<V>.jsonFlowPref(
        default: Map<KT, VT>,
        key: String? = null,
        deser: JSONDeser<KT, VT>
    ) = saveAsStringFlowPref(
        default,
        key,
        object : DataStorePrefDataConverter<Map<KT, VT>, String> {
            override suspend fun encode(value: Map<KT, VT>?) = withContext(Dispatchers.Default) {
                value?.let {
                    deser.toJSON(value).toString()
                } ?: "{}"
            }

            override suspend fun decode(savedValue: String?, defaultValue: Map<KT, VT>) : Map<KT, VT> = withContext(Dispatchers.Default) {
                if (savedValue == null) {
                    return@withContext defaultValue
                }
                deser.fromJSON(JSONObject(savedValue))
            }

        }
    )

    interface JSONDeser<KT, VT> {
        fun toJSON(value : Map<KT, VT>) : JSONObject
        fun fromJSON(serialized : JSONObject) : Map<KT, VT>
    }

    class MapLongStringDeser : JSONDeser<Long, String> {
        override fun toJSON(value: Map<Long, String>): JSONObject =
            JSONObject(value.map { Pair(it.key.toString(), it.value) }.toMap())

        override fun fromJSON(serialized: JSONObject): Map<Long, String> {
            val result = LinkedHashMap<Long, String>()
            serialized.keys().forEach {
                result[it.toLong()] = serialized.getString(it)
            }
            return result
        }
    }

    class MapLongMapLongTripleLongByteLongNullableDeser : JSONDeser<Long, Map<Long, Triple<Long, Byte, Long?>>> {
        override fun toJSON(value: Map<Long, Map<Long, Triple<Long, Byte, Long?>>>): JSONObject =
            JSONObject(value.map { Pair(it.key.toString(),
                JSONObject(it.value.map { ep ->
                    val array = if (ep.value.third != null) {
                        JSONArray(arrayOf<Number>(ep.value.first, ep.value.second, ep.value.third!!))
                    } else {
                        JSONArray(arrayOf<Number>(ep.value.first, ep.value.second))
                    }
                    Pair(ep.key.toString(), array)
                }.toMap())) }.toMap())

        override fun fromJSON(serialized: JSONObject): Map<Long, Map<Long, Triple<Long, Byte, Long?>>> {
            val result = LinkedHashMap<Long, Map<Long, Triple<Long, Byte, Long?>>>()
            serialized.keys().forEach {
                val vMap = serialized.getJSONObject(it)
                val res = mutableMapOf<Long, Triple<Long, Byte, Long?>>()
                vMap.keys().forEach { ep ->
                    val arr = vMap.getJSONArray(ep)
                    val position = arr.getLong(0)
                    val percent = arr.getLong(1).toByte()
                    val lastWatchedAt = if (arr.length() >= 3) arr.getLong(2) else null
                    res[ep.toLong()] = Triple(position, percent, lastWatchedAt)
                }
                result[it.toLong()] = res
            }
            return result
        }
    }
}
