package com.cmdalton.rocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.cmdalton.rocket.bank.PRESETS_MIN
import com.cmdalton.rocket.bank.RocketState
import com.cmdalton.rocket.bank.formatCountdown

class KidActions(
    val request: (Int) -> Unit,
    val clear: () -> Unit,
    val play: () -> Unit,
    val pause: () -> Unit,
    val openGrownUps: () -> Unit,
)

/** Asher's side: big, few choices, one thing at a time. Landscape puts the sky beside the controls. */
@Composable
fun KidScreen(s: RocketState, nowMs: Long, message: String?, actions: KidActions) {
    val f = s.flight
    val altitude = when {
        f != null && f.launched -> f.remainingAt(nowMs).toFloat() / (f.totalSec * 1000f)
        f != null -> 1f
        s.balanceSec > 0 -> 1f
        else -> 0f
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Sky(altitude, Modifier.weight(0.42f).fillMaxHeight())
                Controls(s, nowMs, message, actions, Modifier.weight(0.58f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Sky(altitude, Modifier.weight(0.38f).fillMaxWidth())
                Controls(s, nowMs, message, actions, Modifier.weight(0.62f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Controls(s: RocketState, nowMs: Long, message: String?, a: KidActions, modifier: Modifier) {
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val f = s.flight
    if (f == null && s.pendingRequestMin == null && picked != null && picked!! * 60L > s.balanceSec) picked = null

    Column(
        modifier.background(RocketColors.Navy).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bank_label), color = RocketColors.Yellow, fontSize = 26.sp)
                Text(
                    stringResource(R.string.minutes, s.balanceMin.toInt()),
                    color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold,
                )
            }
            BigButton(
                stringResource(R.string.grownups), RocketColors.Grey, a.openGrownUps,
                height = 72.dp, fontSize = 22.sp,
            )
        }

        when {
            // Flying or paused: the big countdown is the main thing on screen.
            f != null && f.launched -> {
                Text(
                    formatCountdown(f.remainingAt(nowMs)),
                    color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold,
                )
                if (!f.running) {
                    Text(
                        stringResource(if (f.autoPaused) R.string.paused_screen_off else R.string.paused),
                        color = RocketColors.Yellow, fontSize = 30.sp, textAlign = TextAlign.Center,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                    if (f.running) {
                        BigButton(stringResource(R.string.pause), RocketColors.Orange, a.pause, Modifier.weight(1f), height = 120.dp, fontSize = 44.sp)
                    } else {
                        BigButton(stringResource(R.string.play), RocketColors.Green, a.play, Modifier.weight(1f), height = 120.dp, fontSize = 44.sp)
                    }
                    BigButton(stringResource(R.string.clear), RocketColors.Red, { confirmClear = true }, Modifier.weight(1f), height = 120.dp, fontSize = 44.sp)
                }
            }
            // Approved, waiting for the big play button.
            f != null -> {
                Text(
                    stringResource(R.string.ready, (f.totalSec / 60).toInt()),
                    color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                BigButton(stringResource(R.string.play), RocketColors.Green, a.play, Modifier.fillMaxWidth(), height = 160.dp, fontSize = 64.sp)
                BigButton(stringResource(R.string.clear), RocketColors.Red, a.clear, Modifier.fillMaxWidth(0.6f))
            }
            // Asked, waiting for Dad.
            s.pendingRequestMin != null -> {
                Text(
                    stringResource(R.string.asked, s.pendingRequestMin),
                    color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.waiting), color = RocketColors.Yellow, fontSize = 30.sp, textAlign = TextAlign.Center)
                BigButton(stringResource(R.string.clear), RocketColors.Red, { picked = null; a.clear() }, Modifier.fillMaxWidth(0.6f))
            }
            // Pick an amount and ask.
            else -> {
                if (s.balanceMin < PRESETS_MIN.min()) {
                    Text(stringResource(R.string.bank_empty), color = RocketColors.Yellow, fontSize = 32.sp)
                } else {
                    Text(stringResource(R.string.pick_prompt), color = Color.White, fontSize = 34.sp)
                }
                PRESETS_MIN.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { m ->
                            BigButton(
                                stringResource(R.string.minutes, m),
                                if (picked == m) RocketColors.Yellow else RocketColors.Blue,
                                { picked = m },
                                Modifier.weight(1f),
                                enabled = m * 60L <= s.balanceSec,
                                textColor = if (picked == m) RocketColors.Deep else Color.White,
                            )
                        }
                    }
                }
                val p = picked
                if (p != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                        BigButton(stringResource(R.string.ask_button, p), RocketColors.Green, { a.request(p); picked = null }, Modifier.weight(2f), height = 120.dp, fontSize = 40.sp)
                        BigButton(stringResource(R.string.clear), RocketColors.Red, { picked = null }, Modifier.weight(1f), height = 120.dp, fontSize = 40.sp)
                    }
                }
            }
        }
        if (message != null) {
            Text(message, color = RocketColors.Yellow, fontSize = 22.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_confirm_title), fontSize = 32.sp) },
            text = { Text(stringResource(R.string.clear_confirm_body), fontSize = 24.sp) },
            confirmButton = {
                BigButton(stringResource(R.string.land_now), RocketColors.Red, { confirmClear = false; a.clear() }, height = 80.dp, fontSize = 26.sp)
            },
            dismissButton = {
                BigButton(stringResource(R.string.keep_flying), RocketColors.Green, { confirmClear = false }, height = 80.dp, fontSize = 26.sp)
            },
        )
    }
}

@Composable
fun LandingScreen(onOk: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Sky(0f, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                stringResource(R.string.landed_title),
                color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                lineHeight = 72.sp,
            )
            Spacer(Modifier.height(32.dp))
            BigButton(stringResource(R.string.ok), RocketColors.Green, onOk, Modifier.fillMaxWidth(0.5f), height = 120.dp, fontSize = 48.sp)
        }
    }
}
