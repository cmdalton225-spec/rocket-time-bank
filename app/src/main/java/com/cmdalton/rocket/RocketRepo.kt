package com.cmdalton.rocket

import android.content.Context
import android.content.SharedPreferences
import com.cmdalton.rocket.bank.Pin
import com.cmdalton.rocket.bank.RocketState
import com.cmdalton.rocket.bank.StateJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one bank on the tablet. The app, the flight service and the widget all read and write here,
 * so there is a single truth. Saved on every change (SharedPreferences, included in Android backup).
 */
object RocketRepo {
    private const val PREFS = "rocket"
    private const val KEY_STATE = "state"
    private const val KEY_PIN_HASH = "pinHash"
    private const val KEY_PIN_SALT = "pinSalt"

    private var prefs: SharedPreferences? = null
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(RocketState())
    val state: StateFlow<RocketState> = _state.asStateFlow()

    /** Test hook: the clock every rule uses. */
    var clock: () -> Long = { System.currentTimeMillis() }

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _state.value = StateJson.decode(p.getString(KEY_STATE, null))
    }

    /** Tests only: forget the loaded state so the next [init] reads the saved file again. */
    @Synchronized
    fun resetForTest() {
        prefs = null
        _state.value = RocketState()
        clock = { System.currentTimeMillis() }
    }

    fun now(): Long = clock()

    /**
     * Apply one rule. [refreshWidget] false is used by the once-a-few-seconds heartbeat so the
     * widget is only redrawn when something it shows changes.
     */
    @Synchronized
    fun update(refreshWidget: Boolean = true, rule: (RocketState, Long) -> RocketState): RocketState {
        val before = _state.value
        val after = rule(before, now())
        if (after != before) {
            _state.value = after
            prefs?.edit()?.putString(KEY_STATE, StateJson.encode(after))?.commit()
            if (refreshWidget) RocketWidget.refresh(appContext)
        }
        return after
    }

    val hasPin: Boolean get() = prefs?.getString(KEY_PIN_HASH, null) != null

    fun setPin(pin: String): Boolean {
        if (!Pin.isValidFormat(pin)) return false
        val salt = Pin.newSalt()
        prefs?.edit()?.putString(KEY_PIN_SALT, salt)?.putString(KEY_PIN_HASH, Pin.hash(pin, salt))?.commit()
        return true
    }

    fun checkPin(pin: String): Boolean =
        Pin.matches(pin, prefs?.getString(KEY_PIN_SALT, null), prefs?.getString(KEY_PIN_HASH, null))
}
