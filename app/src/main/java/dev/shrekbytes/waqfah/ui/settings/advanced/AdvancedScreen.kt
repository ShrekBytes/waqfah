package dev.shrekbytes.waqfah.ui.settings.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shrekbytes.waqfah.R
import dev.shrekbytes.waqfah.ui.components.SettingsScaffold
import dev.shrekbytes.waqfah.ui.components.SettingsToggleRow

// The refinements page: settings that tune behavior rather than set the app
// up. The main Settings tab links here with a nav row so this page can grow
// without bloating it; auto-next (#33) is the first resident, and the page
// carries its own ViewModel like the other settings sub-pages.
@Composable
fun AdvancedScreen(
    viewModel: AdvancedViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.section_advanced), onBack = onBack) {
        SettingsToggleRow(
            title = stringResource(R.string.auto_next_title),
            subtitle = stringResource(R.string.auto_next_sub),
            checked = state.autoNextOnMark,
            onToggle = { viewModel.setAutoNextOnMark(!state.autoNextOnMark) },
        )
    }
}
