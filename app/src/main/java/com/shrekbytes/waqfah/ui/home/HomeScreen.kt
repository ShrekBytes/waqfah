package com.shrekbytes.waqfah.ui.home

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.shrekbytes.waqfah.ui.reading.ReadingViewModel
import com.shrekbytes.waqfah.ui.reading.WaqfahReadingContent

@Composable
fun HomeScreen(viewModel: ReadingViewModel = hiltViewModel()) {
    WaqfahReadingContent(viewModel = viewModel, bottomBar = {})
}
