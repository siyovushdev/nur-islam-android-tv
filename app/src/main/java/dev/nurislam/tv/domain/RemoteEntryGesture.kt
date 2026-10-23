package dev.nurislam.tv.domain

/** Five complete OK presses in five seconds: no MENU key, key-repeat or touchscreen needed. */
class RemoteEntryGesture {
    private var first = Long.MIN_VALUE
    private var count = 0
    fun click(elapsedMillis: Long): Boolean {
        if (count == 0 || elapsedMillis < first || elapsedMillis - first > 5000) {
            first = elapsedMillis; count = 0
        }
        count++
        return if (count >= 5) { reset(); true } else false
    }
    fun reset() { count = 0 }
}
