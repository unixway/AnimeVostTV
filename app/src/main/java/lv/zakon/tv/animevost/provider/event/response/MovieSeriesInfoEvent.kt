package lv.zakon.tv.animevost.provider.event.response

import lv.zakon.tv.animevost.model.MovieSeriesInfo
import lv.zakon.tv.animevost.provider.RequestId

class MovieSeriesInfoEvent(requestId: RequestId, val info : MovieSeriesInfo) : AResponseEvent(requestId)