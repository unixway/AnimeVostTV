package lv.zakon.tv.animevost.prefs

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
    val recent by stringSetFlowPref(key = "recent")
    val cachedMovie by jsonFlowPref(mapOf<Long, String>(), "cachedD", MapLongStringDeser())
    val watchedEps by jsonFlowPref(mapOf<Long, Set<Long>>(), "watchedO", MapLongSetLongDeser())

    suspend fun addSearch(search: String) {
        val searchesSoFar = searches.first()
        if (searchesSoFar.contains(search).not()) {
            val mutateSearches = searchesSoFar.toMutableSet().also {
                it.add(search)
            }
            searches.emit(mutateSearches)
        }
    }

    suspend fun markWatch(id: Long, pageUrl: String, episodeId: Long) = withContext(Dispatchers.IO) {
        val recently = recent.first()
        if (recently.contains(id.toString()).not()) {
            val mutateRecent = recently.toMutableSet().also {
                it.add(id.toString())
            }
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
        if (watched[id]?.contains(episodeId) != true) {
            val mutateWatched = watched.toMutableMap()
            mutateWatched.compute(id, fun(_:Long, set: Set<Long>?): Set<Long> =
                set?.toMutableSet()?.also { it.add(episodeId) } ?: setOf(episodeId)
            )
            watchedEps.emit(mutateWatched)
        }
    }

    private inline fun <KT, VT, V> KotlinDataStoreModel<V>.jsonFlowPref(
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

    class MapLongSetLongDeser : JSONDeser<Long, Set<Long>> {
        override fun toJSON(value: Map<Long, Set<Long>>): JSONObject =
            JSONObject(value.map { Pair(it.key.toString(), JSONArray(it.value.toTypedArray())) }.toMap())

        override fun fromJSON(serialized: JSONObject): Map<Long, Set<Long>> {
            val result = LinkedHashMap<Long, Set<Long>>()
            serialized.keys().forEach {
                val arr = serialized.getJSONArray(it)
                val res = mutableSetOf<Long>()
                for (i in 0 until arr.length()) {
                    res.add(arr.getLong(i))
                }
                result[it.toLong()] = res
            }
            return result
        }
    }
}