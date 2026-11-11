package com.cmdalton.rocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmdalton.rocket.R

object RocketColors {
    val Navy = Color(0xFF003278)
    val Deep = Color(0xFF001A40)
    val Red = Color(0xFFF21C1F)
    val Orange = Color(0xFFFF8A00)
    val Yellow = Color(0xFFFCBF09)
    val Green = Color(0xFF1E9E45)
    val Blue = Color(0xFF0190F8)
    val Grey = Color(0xFF5B6B85)
    val Earth = Color(0xFF2F8F4E)
    val Ocean = Color(0xFF1C6FD1)
}

/** The kid buttons: well over the spec's ~72dp, big text, rounded. */
@Composable
fun BigButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 96.dp,
    fontSize: TextUnit = 34.sp,
    textColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor,
            disabledContainerColor = color.copy(alpha = 0.25f),
            disabledContentColor = Color.White.copy(alpha = 0.5f),
        ),
    ) {
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/**
 * Space, stars, Earth at the bottom, and the rocket. [altitude] 1 = high in space, 0 = landed.
 * Time is the rocket's height: when it reaches Earth, time is zero.
 */
@Composable
fun Sky(altitude: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val h = maxHeight
        val earthH = h * 0.16f
        val rocketH = h * 0.30f
        val top = h * 0.04f
        val bottom = h - earthH - rocketH + earthH * 0.25f
        val y = bottom + (top - bottom) * altitude.coerceIn(0f, 1f)
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(RocketColors.Deep, RocketColors.Navy)))
            val stars = listOf(
                0.08f to 0.10f, 0.22f to 0.30f, 0.85f to 0.08f, 0.70f to 0.25f, 0.92f to 0.45f,
                0.12f to 0.55f, 0.35f to 0.12f, 0.60f to 0.50f, 0.80f to 0.65f, 0.05f to 0.75f,
                0.45f to 0.35f, 0.30f to 0.65f,
            )
            stars.forEachIndexed { i, (sx, sy) ->
                drawCircle(
                    if (i % 3 == 0) RocketColors.Yellow else Color.White,
                    radius = if (i % 3 == 0) 6f else 3.5f,
                    center = Offset(size.width * sx, size.height * sy * 0.8f),
                )
            }
            val eh = earthH.toPx()
            // A wide curve of Earth along the bottom.
            drawOval(
                RocketColors.Ocean,
                topLeft = Offset(-size.width * 0.25f, size.height - eh),
                size = Size(size.width * 1.5f, eh * 3f),
            )
            drawOval(
                RocketColors.Earth,
                topLeft = Offset(size.width * 0.05f, size.height - eh * 0.85f),
                size = Size(size.width * 0.45f, eh * 1.4f),
            )
            drawOval(
                RocketColors.Earth,
                topLeft = Offset(size.width * 0.62f, size.height - eh * 0.7f),
                size = Size(size.width * 0.3f, eh * 1.2f),
            )
        }
        Image(
            painter = painterResource(R.drawable.rocket),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = y)
                .height(rocketH),
        )
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { content() }
}
