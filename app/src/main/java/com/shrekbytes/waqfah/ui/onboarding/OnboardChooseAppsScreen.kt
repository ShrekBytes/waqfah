package com.shrekbytes.waqfah.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.components.EmptyListNote
import com.shrekbytes.waqfah.ui.components.WaqfahPrimaryButton
import com.shrekbytes.waqfah.ui.components.WaqfahSearchField
import com.shrekbytes.waqfah.ui.settings.apps.AppRow
import com.shrekbytes.waqfah.ui.settings.apps.AppsListSkeleton
import com.shrekbytes.waqfah.ui.settings.apps.AppsViewModel
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

// Uses OnboardingScaffold: since the scaffold's content slot is container-
// agnostic, the LazyColumn fits without duplicating the header.
@Composable
fun OnboardChooseAppsScreen(
    viewModel: AppsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = WaqfahTheme.colors

    OnboardingScaffold(
        step = 2,
        title = stringResource(R.string.onboard_apps_title),
        onBack = onBack,
        bottomContent = {
            WaqfahPrimaryButton(
                text = stringResource(R.string.continue_btn),
                onClick = onContinue,
                modifier = Modifier.padding(top = 16.dp),
            )
        },
    ) {
        Text(
            stringResource(R.string.onboard_apps_body),
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(12.dp))

        WaqfahSearchField(value = state.searchQuery, onValueChange = viewModel::setSearchQuery, placeholder = stringResource(R.string.search_apps_hint))
        Spacer(Modifier.height(6.dp))

        when {
            state.isLoading -> AppsListSkeleton(Modifier.weight(1f))
            state.apps.isEmpty() -> EmptyListNote(stringResource(R.string.no_apps_found))
            else -> LazyColumn(Modifier.weight(1f)) {
                items(state.apps, key = { it.app.packageName }) { row ->
                    AppRow(row, onClick = { viewModel.toggle(row.app) })
                }
            }
        }
    }
}
