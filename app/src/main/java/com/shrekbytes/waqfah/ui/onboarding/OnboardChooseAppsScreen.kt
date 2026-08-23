package com.shrekbytes.waqfah.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.ui.components.EmptyListNote
import com.shrekbytes.waqfah.ui.components.WaqfahBackButton
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import com.shrekbytes.waqfah.ui.components.WaqfahSearchField
import com.shrekbytes.waqfah.ui.settings.apps.AppRow
import com.shrekbytes.waqfah.ui.settings.apps.AppsListSkeleton
import com.shrekbytes.waqfah.ui.settings.apps.AppsViewModel
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

// Not OnboardingScaffold: the app list needs a LazyColumn (see AppsScreen.kt).
@Composable
fun OnboardChooseAppsScreen(
    viewModel: AppsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        WaqfahBackButton(onClick = onBack)
        Text(
            "Step 2 of 3",
            color = colors.inkMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Choose the apps you'd\nlike a pause for",
            color = colors.ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Waqfah gently appears before these open. You can change this anytime.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(12.dp))
        StepDots(step = 2)
        Spacer(Modifier.height(16.dp))

        WaqfahSearchField(value = state.searchQuery, onValueChange = viewModel::setSearchQuery)
        Spacer(Modifier.height(6.dp))

        when {
            state.isLoading -> AppsListSkeleton(Modifier.weight(1f))
            state.apps.isEmpty() -> EmptyListNote("No apps found")
            else -> LazyColumn(Modifier.weight(1f)) {
                items(state.apps, key = { it.app.packageName }) { row ->
                    AppRow(row, onClick = { viewModel.toggle(row.app) })
                }
            }
        }

        WaqfahPrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.padding(vertical = 16.dp))
    }
}
