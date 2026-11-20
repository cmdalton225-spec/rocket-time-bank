package com.cmdalton.rocket.tablet

/** What the widget shows, decided without Android types so it runs as a plain JVM test. */
data class WidgetView(
    /** Running flight: a ticking countdown (Chronometer) ending at [chronoBase] (elapsedRealtime ms). */
    val ticking: Boolean,
    val chronoBase: Long,
    /** Frozen clock for paused / ready / idle. */
    val clock: String,
    val label: String,
    val button: Button,
) {
    enum class Button { PAUSE, PLAY, NONE }
}

object WidgetPlan {
    /** A bridge sync may move the ticking countdown only when it is this far off (else no visible jump). */
    const val KEEP_WITHIN_MS = 1_000L

    /** The widget's clock: M:SS-style "MM:SS" under an hour, "H:MM:SS" from an hour — Android Chronometer's own format. */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /**
     * Where a countdown Chronometer must be based so it shows what the cockpit's session clock shows.
     * The cockpit shows whole seconds rounded UP (fmt() ceils); a countdown Chronometer rounds DOWN, so the
     * base sits [ROUND_UP_MS] past the true end. [remainingMs] is the flight's true time left at [nowElapsed].
     */
    fun freshBase(nowElapsed: Long, remainingMs: Long): Long = nowElapsed + remainingMs + ROUND_UP_MS

    /** Keep the countdown already on screen unless the truth moved by a second or more (Dad added time…). */
    fun base(stored: Long, fresh: Long): Long =
        if (stored > 0 && kotlin.math.abs(stored - fresh) < KEEP_WITHIN_MS) stored else fresh

    /**
     * The widget for [s] (already extrapolated to now). [remainingMs] = the running flight's precise time
     * left (for the countdown); [storedBase] = the countdown base currently on screen (0 = none).
     */
    fun view(s: WidgetState, nowElapsed: Long, remainingMs: Long, storedBase: Long): WidgetView {
        val canPlay = s.bankSeconds > 0 && s.sessionSeconds > 0
        return when {
            s.running && s.sessionSeconds > 0 -> WidgetView(
                ticking = true,
                chronoBase = base(storedBase, freshBase(nowElapsed, remainingMs)),
                clock = clock(s.sessionSeconds.toLong()),
                label = "flying",
                button = WidgetView.Button.PAUSE,
            )
            s.sessionStatus == "paused" && canPlay ->
                WidgetView(false, 0, clock(s.sessionSeconds.toLong()), "paused", WidgetView.Button.PLAY)
            s.sessionStatus == "approved" && canPlay ->
                WidgetView(false, 0, clock(s.sessionSeconds.toLong()), "ready to play", WidgetView.Button.PLAY)
            else -> WidgetView(false, 0, clock(s.bankSeconds.toLong()), "in the bank", WidgetView.Button.NONE)
        }
    }

    /**
     * A bridge push carries whole seconds. The cockpit re-renders when its number changes, i.e. just after
     * the true time left crossed that whole second, so the true time left is a little under it.
     */
    fun remainingFromPush(sessionSeconds: Long): Long = (sessionSeconds * 1000 - PUSH_LAG_MS).coerceAtLeast(0)

    const val ROUND_UP_MS = 999L
    const val PUSH_LAG_MS = 100L
}
