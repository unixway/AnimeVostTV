package lv.zakon.tv.animevost.provider

import lv.zakon.tv.animevost.model.MovieGenre
import lv.zakon.tv.animevost.model.MovieSeriesInfo

data class MovieSeriesFetchedEvent(val genre : MovieGenre? = null, val series : List<MovieSeriesInfo>)