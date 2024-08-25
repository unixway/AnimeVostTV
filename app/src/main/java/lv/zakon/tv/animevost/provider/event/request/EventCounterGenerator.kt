package lv.zakon.tv.animevost.provider.event.request

import lv.zakon.tv.animevost.provider.RequestId
import java.util.concurrent.atomic.AtomicInteger

object EventCounterGenerator {
    private val state = AtomicInteger()
    fun generate() : RequestId = state.getAndIncrement()
}