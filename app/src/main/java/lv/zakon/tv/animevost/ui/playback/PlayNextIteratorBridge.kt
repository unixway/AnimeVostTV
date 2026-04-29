package lv.zakon.tv.animevost.ui.playback

import lv.zakon.tv.animevost.model.PlayEntry

/**
 * передаёт состояние для кнопочки следующее видео
 * через Intent невозможно передать iterator
 */
object PlayNextIteratorBridge {
    private var iterator: Iterator<PlayEntry>? = null

    fun prepare(playlist: List<PlayEntry>, id: Long) {
        val iterator = playlist.iterator()
        do {
            if (iterator.next().id == id) {
                break
            }
        } while (iterator.hasNext())
        this.iterator = iterator
    }

    fun consume(): Iterator<PlayEntry> = iterator!!.also { iterator = null }
}
