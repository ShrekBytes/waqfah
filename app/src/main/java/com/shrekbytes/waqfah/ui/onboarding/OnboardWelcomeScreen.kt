package com.shrekbytes.waqfah.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.ui.components.ChevronDirection
import com.shrekbytes.waqfah.ui.components.ChevronIcon
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun OnboardWelcomeScreen(onGetStarted: () -> Unit) {
    val colors = WaqfahTheme.colors
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("اقرأ", color = colors.ink, fontSize = 54.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "WAQFAH",
                color = colors.inkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "A quiet pause,\nbefore you continue.",
                textAlign = TextAlign.Center,
                color = colors.ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 30.sp,
                letterSpacing = (-0.2).sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Read an ayah, every time you open an app.",
                textAlign = TextAlign.Center,
                color = colors.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Waqfah shows an ayah before the apps you choose. No streaks, no pressure, no restrictions — just a moment, then you continue.",
                textAlign = TextAlign.Center,
                color = colors.inkMuted,
                fontSize = 14.sp,
                lineHeight = 23.sp,
            )
            Spacer(Modifier.height(32.dp))
            GetStartedButton(onClick = onGetStarted)
        }
    }
}

// A compact rounded-corner "->" rather than the app's usual full-width
// WaqfahPrimaryButton pinned to the screen's bottom edge — sitting right
// under the welcome copy, as part of the same centered block, a small arrow
// reads as "get started" without needing a label, and keeps this first
// screen feeling minimal rather than form-like. Rounded corners rather than
// a full circle (a 50%-radius square is just a circle) so it reads as the
// same button language as the rest of the app — Mark Read, WaqfahPrimaryButton,
// chips — rather than a one-off shape.
@Composable
private fun GetStartedButton(onClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.accent,
        contentColor = colors.accentInk,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ChevronIcon(ChevronDirection.RIGHT, tint = colors.accentInk, modifier = Modifier.size(20.dp))
        }
    }
}
