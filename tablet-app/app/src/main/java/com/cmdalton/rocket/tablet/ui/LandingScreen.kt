package com.cmdalton.rocket.tablet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmdalton.rocket.tablet.R

@Composable
fun LandingScreen(onOk: () -> Unit) {
    val bg = Brush.radialGradient(
        colors = listOf(Color(0xFF553023), Color(0xFF14100F), Color(0xFF090909)),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                "✦",
                color = Color(0xFFD8C3A1),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.landed_title),
                color = Color(0xFFD8C3A1),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
            )
            Text(
                stringResource(R.string.landed_subtitle),
                color = Color(0xFFF6BB90),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOk, modifier = Modifier.padding(top = 24.dp)) {
                Text(
                    stringResource(R.string.ok),
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}