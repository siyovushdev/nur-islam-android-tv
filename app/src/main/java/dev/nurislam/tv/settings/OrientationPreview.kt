package dev.nurislam.tv.settings

import dev.nurislam.tv.domain.DisplayMode

/** Only confirmed mode is persisted. A process death always returns to that confirmed mode. */
data class OrientationPreview(val candidate: DisplayMode, val deadlineElapsed: Long) {
    fun remainingSeconds(elapsed: Long) = ((deadlineElapsed - elapsed).coerceAtLeast(0) + 999) / 1000
    fun expired(elapsed: Long) = elapsed >= deadlineElapsed
}
