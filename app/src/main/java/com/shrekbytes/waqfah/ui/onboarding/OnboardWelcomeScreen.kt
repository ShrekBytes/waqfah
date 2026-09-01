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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.components.ChevronDirection
import com.shrekbytes.waqfah.ui.components.ChevronIcon
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun OnboardWelcomeScreen(onGetStarted: () -> Unit) {
    val colors = WaqfahTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
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
                    stringResource(R.string.welcome_tagline),
                    textAlign = TextAlign.Center,
                    color = colors.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.welcome_subtitle),
                    textAlign = TextAlign.Center,
                    color = colors.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.welcome_body),
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
}

// Compact content-sized arrow button under the welcome copy.
@Composable
private fun GetStartedButton(onClick: () -> Unit) {
    val colors = WaqfahTheme.colors
    val continueLabel = stringResource(R.string.continue_btn)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = colors.accent,
        contentColor = colors.accentInk,
        modifier = Modifier.height(48.dp).semantics { contentDescription = continueLabel },
    ) {
        Box(Modifier.padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
            ChevronIcon(ChevronDirection.RIGHT, tint = colors.accentInk, modifier = Modifier.size(20.dp))
        }
    }
}
