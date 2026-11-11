package com.cmdalton.rocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmdalton.rocket.R
import com.cmdalton.rocket.bank.HistoryEntry
import com.cmdalton.rocket.bank.HistoryKind
import com.cmdalton.rocket.bank.PRESETS_MIN
import com.cmdalton.rocket.bank.Pin
import com.cmdalton.rocket.bank.RocketState
import com.cmdalton.rocket.bank.formatCountdown
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The grown-up gate. With no PIN yet, Matt picks one (typed twice). Afterwards the PIN opens the
 * grown-up side. A big on-screen keypad, so no keyboard pops up.
 */
@Composable
fun PinScreen(hasPin: Boolean, setPin: (String) -> Unit, checkPin: (String) -> Boolean, onUnlocked: () -> Unit, onBack: () -> Unit) {
    var entry by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val title = when {
        hasPin -> R.string.pin_enter_title
        first == null -> R.string.pin_set_title
        else -> R.string.pin_confirm_title
    }

    fun complete(pin: String) {
        entry = ""
        if (hasPin) {
            if (checkPin(pin)) onUnlocked() else error = R.string.pin_wrong
        } else if (first == null) {
            first = pin
            error = null
        } else if (first == pin) {
            setPin(pin)
            onUnlocked()
        } else {
            first = null
            error = R.string.pin_mismatch
        }
    }

    Column(
        Modifier.fillMaxSize().background(RocketColors.Deep).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(title), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(
            "●".repeat(entry.length) + "○".repeat(Pin.LENGTH - entry.length),
            color = RocketColors.Yellow, fontSize = 48.sp, letterSpacing = 12.sp,
        )
        Text(error?.let { stringResource(it) } ?: " ", color = RocketColors.Orange, fontSize = 24.sp)
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("back", "0", "del")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "back" -> BigButton(stringResource(R.string.back), RocketColors.Grey, onBack, Modifier.weight(1f), height = 88.dp, fontSize = 24.sp)
                            "del" -> BigButton(stringResource(R.string.delete), RocketColors.Grey, { entry = entry.dropLast(1) }, Modifier.weight(1f), height = 88.dp, fontSize = 24.sp)
                            else -> BigButton(key, RocketColors.Blue, {
                                if (entry.length < Pin.LENGTH) {
                                    entry += key
                                    if (entry.length == Pin.LENGTH) complete(entry)
                                }
                            }, Modifier.weight(1f), height = 88.dp, fontSize = 36.sp)
                        }
                    }
                }
            }
        }
    }
}

class ParentActions(
    val approve: () -> Unit,
    val deny: () -> Unit,
    val add: (Int) -> Unit,
    val openSetting: (SetupItem) -> Unit,
    val done: () -> Unit,
)

enum class SetupItem { NOTIFICATIONS, OVERLAY, FULL_SCREEN }

@Composable
fun ParentScreen(s: RocketState, nowMs: Long, setup: Map<SetupItem, Boolean>, a: ParentActions) {
    Column(
        Modifier.fillMaxSize().background(RocketColors.Deep).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.parent_title), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            BigButton(stringResource(R.string.done), RocketColors.Green, a.done, height = 72.dp, fontSize = 26.sp)
        }
        Text(
            stringResource(R.string.balance_line, stringResource(R.string.minutes, s.balanceMin.toInt())),
            color = RocketColors.Yellow, fontSize = 34.sp, fontWeight = FontWeight.Bold,
        )

        Card {
            val req = s.pendingRequestMin
            val f = s.flight
            when {
                req != null -> {
                    Text(stringResource(R.string.request_line, req), color = Color.White, fontSize = 30.sp)
                    if (!s.canApprove()) Text(stringResource(R.string.not_enough), color = RocketColors.Orange, fontSize = 22.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigButton(stringResource(R.string.approve), RocketColors.Green, a.approve, Modifier.weight(1f), enabled = s.canApprove(), height = 80.dp, fontSize = 28.sp)
                        BigButton(stringResource(R.string.deny), RocketColors.Red, a.deny, Modifier.weight(1f), height = 80.dp, fontSize = 28.sp)
                    }
                }
                f != null && !f.launched ->
                    Text(stringResource(R.string.flight_line_ready, (f.totalSec / 60).toInt()), color = Color.White, fontSize = 26.sp)
                f != null && f.running ->
                    Text(stringResource(R.string.flight_line_flying, formatCountdown(f.remainingAt(nowMs))), color = Color.White, fontSize = 26.sp)
                f != null ->
                    Text(stringResource(R.string.flight_line_paused, formatCountdown(f.remainingAt(nowMs))), color = Color.White, fontSize = 26.sp)
                else -> Text(stringResource(R.string.no_request), color = Color.White, fontSize = 26.sp)
            }
        }

        Card {
            Text(stringResource(R.string.add_time), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            PRESETS_MIN.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { m ->
                        BigButton(stringResource(R.string.add_amount, m), RocketColors.Blue, { a.add(m) }, Modifier.weight(1f), height = 72.dp, fontSize = 26.sp)
                    }
                }
            }
        }

        Card {
            Text(stringResource(R.string.setup), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            listOf(
                SetupItem.NOTIFICATIONS to R.string.setup_notifications,
                SetupItem.OVERLAY to R.string.setup_overlay,
                SetupItem.FULL_SCREEN to R.string.setup_fullscreen,
            ).forEach { (item, label) ->
                val on = setup[item] ?: true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    if (on) {
                        Text(stringResource(R.string.setup_on), color = RocketColors.Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    } else {
                        BigButton(stringResource(R.string.setup_turn_on), RocketColors.Orange, { a.openSetting(item) }, height = 64.dp, fontSize = 20.sp)
                    }
                }
            }
        }

        Card {
            Text(stringResource(R.string.history), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (s.history.isEmpty()) Text(stringResource(R.string.history_empty), color = Color.White, fontSize = 20.sp)
            s.history.asReversed().take(100).forEach { e -> HistoryRow(e) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")

@Composable
private fun HistoryRow(e: HistoryEntry) {
    val amount = formatAmount(e.seconds)
    val text = when (e.kind) {
        HistoryKind.ADDED -> stringResource(R.string.hist_added, amount)
        HistoryKind.REQUESTED -> stringResource(R.string.hist_requested, amount)
        HistoryKind.CANCELLED -> stringResource(R.string.hist_cancelled, amount)
        HistoryKind.APPROVED -> stringResource(R.string.hist_approved, amount)
        HistoryKind.DENIED -> stringResource(R.string.hist_denied, amount)
        HistoryKind.LAUNCHED -> stringResource(R.string.hist_launched, amount)
        HistoryKind.LANDED -> stringResource(R.string.hist_landed, amount)
        HistoryKind.CLEARED -> stringResource(R.string.hist_cleared, amount)
    }
    val whenText = timeFormat.format(Instant.ofEpochMilli(e.atMs).atZone(ZoneId.systemDefault()))
    Row {
        Text(whenText, color = RocketColors.Yellow, fontSize = 18.sp, modifier = Modifier.widthIn(min = 200.dp))
        Text(text, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Start)
    }
}

/** "25 min" or "12 min 30 s" (refunds can have seconds). */
fun formatAmount(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (s == 0L) "$m min" else "$m min $s s"
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(RocketColors.Navy, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
