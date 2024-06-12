package lv.zakon.tv.animevost.provider

import lv.zakon.tv.animevost.model.MovieSeriesInfo

data class MovieSeriesSearchFinishedEvent(val query : String, val series : List<MovieSeriesInfo>, val start: Boolean)