package com.shrekbytes.waqfah.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.ui.components.WaqfahBackButton
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun OnboardingScaffold(
    step: Int,
    title: String,
    onBack: () -> Unit,
    bottomContent: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WaqfahTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        WaqfahBackButton(onClick = onBack)
        Text(
            "Step $step of 3",
            color = colors.inkMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = colors.ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(12.dp))
        StepDots(step)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
        bottomContent()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun StepDots(step: Int) {
    val colors = WaqfahTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            Spacer(
                Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index < step) colors.accent else colors.line),
            )
        }
    }
}
