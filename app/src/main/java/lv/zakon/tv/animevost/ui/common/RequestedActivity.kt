package lv.zakon.tv.animevost.ui.common

import androidx.fragment.app.FragmentActivity
import lv.zakon.tv.animevost.provider.RequestId

open class RequestedActivity : FragmentActivity() {
    private val requests = linkedMapOf<RequestId, Runnable?>()

    fun appendRequestId(requestId: RequestId) {
        requests[requestId] = null
    }

    fun placeResponseAction(requestId: RequestId, action: Runnable) {
        if (requests.isNotEmpty()) {
            var place = true
            with(requests.entries.first()) {
                if (this.key == requestId) {
                    requests.remove(requestId)
                    action.run()
                    place = false
                }
            }
            if (place) {
                if (requests.containsKey(requestId)) {
                    requests[requestId] = action
                }
            } else {
                val it = requests.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    if (entry.value == null) {
                        break
                    }
                    entry.value!!.run()
                    it.remove()
                }
            }
        }
    }
}