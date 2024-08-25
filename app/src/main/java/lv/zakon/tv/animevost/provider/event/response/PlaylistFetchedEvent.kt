package lv.zakon.tv.animevost.provider.event.response

import lv.zakon.tv.animevost.model.PlayEntry
import lv.zakon.tv.animevost.provider.RequestId

class PlaylistFetchedEvent(requestId: RequestId, val movieId: Long, val playlist : Array<PlayEntry>) : AResponseEvent(requestId)