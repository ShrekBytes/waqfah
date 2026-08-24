package com.shrekbytes.waqfah.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.components.rowHighlight
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

private data class FaqItem(val questionRes: Int, val answerRes: Int)

private val items = listOf(
    FaqItem(R.string.faq_q1, R.string.faq_a1),
    FaqItem(R.string.faq_q2, R.string.faq_a2),
    FaqItem(R.string.faq_q3, R.string.faq_a3),
    FaqItem(R.string.faq_q4, R.string.faq_a4),
    FaqItem(R.string.faq_q5, R.string.faq_a5),
    FaqItem(R.string.faq_q6, R.string.faq_a6),
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    val colors = WaqfahTheme.colors

    SettingsScaffold(title = stringResource(R.string.faq_title), onBack = onBack) {
        Text(
            stringResource(R.string.faq_intro),
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { index, item ->
            ExpandableRow(item)
            if (index < items.lastIndex) HorizontalDivider(color = colors.line)
        }
        Text(
            stringResource(R.string.faq_still_stuck, SupportInfo.CONTACT_EMAIL),
            color = colors.inkMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        )
    }
}

// Flat full-width row with the same press highlight as every other list row in
// the app — no cards or borders.
@Composable
private fun ExpandableRow(item: FaqItem) {
    val colors = WaqfahTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .rowHighlight(onClick = { expanded = !expanded })
                .padding(horizontal = 6.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(item.questionRes),
                color = if (expanded) colors.accent else colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            // Same thin › glyph as the settings rows; rotates to point down
            // while the answer is open.
            Text(
                "\u203a",
                color = if (expanded) colors.accent else colors.inkSoft,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                stringResource(item.answerRes),
                color = colors.inkMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 13.dp),
            )
        }
    }
}
