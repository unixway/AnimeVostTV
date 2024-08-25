package lv.zakon.tv.animevost.provider.event.response

import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.provider.RequestId

class MovieSeriesSearchFinishedEvent(requestId: RequestId, val query : String, val series : List<MovieSeriesInfo>, val start: Boolean) : AResponseEvent(requestId)