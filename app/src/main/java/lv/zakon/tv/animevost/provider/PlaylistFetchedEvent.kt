package lv.zakon.tv.animevost.provider

import lv.zakon.tv.animevost.model.PlayEntry

data class PlaylistFetchedEvent(val movieId: Long, val playlist : Array<PlayEntry>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlaylistFetchedEvent

        if (movieId != other.movieId) return false
        if (!playlist.contentEquals(other.playlist)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = movieId.hashCode()
        result = 31 * result + playlist.contentHashCode()
        return result
    }
}