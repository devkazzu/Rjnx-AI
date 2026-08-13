package com.rjnx.ai

/**
 * Isolated wake-word component for Mio.
 *
 * This class deliberately does not open the microphone yet. A real low-power
 * wake-word engine (for example a local keyword-spotting SDK/model) can be
 * connected here without touching MainActivity or the command router.
 */
class MioWakeWordDetector(
    private val onWake: () -> Unit
) {
    var isRunning: Boolean = false
        private set

    fun start() {
        isRunning = true
    }

    fun stop() {
        isRunning = false
    }

    fun destroy() {
        stop()
    }
}
