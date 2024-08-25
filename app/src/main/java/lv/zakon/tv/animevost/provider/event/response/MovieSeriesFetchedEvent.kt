package lv.zakon.tv.animevost.provider.event.response

import lv.zakon.tv.animevost.model.MovieGenre
import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.provider.RequestId

class MovieSeriesFetchedEvent(requestId: RequestId, val genre : MovieGenre? = null, val series : List<MovieSeriesInfo>) : AResponseEvent(requestId)