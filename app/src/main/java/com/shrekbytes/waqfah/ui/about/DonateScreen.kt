package com.shrekbytes.waqfah.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.R
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme
import kotlinx.coroutines.delay

@Composable
fun DonateScreen(onBack: () -> Unit) {
    val colors = WaqfahTheme.colors
    val clipboard = LocalClipboardManager.current
    var copiedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(copiedIndex) {
        if (copiedIndex != null) {
            delay(2000)
            copiedIndex = null
        }
    }

    SettingsScaffold(title = stringResource(R.string.donate_title), onBack = onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(26.dp))
            Text(
                stringResource(R.string.donate_intro1),
                color = colors.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.donate_intro2),
                color = colors.inkMuted,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(24.dp))
        }

        SupportInfo.donations.forEachIndexed { index, account ->
            val copied = copiedIndex == index
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accentSoft)
                    .clickable {
                        clipboard.setText(AnnotatedString(account.number))
                        copiedIndex = index
                    }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = account.iconRes),
                    contentDescription = account.method,
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text(
                        account.method +
                            if (account.accountType.isNotBlank()) " (${account.accountType})" else "",
                        color = colors.inkMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        account.number,
                        color = colors.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                if (copied) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.copied), color = colors.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(stringResource(R.string.tap_to_copy), color = colors.inkMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Text(
            stringResource(R.string.donate_dua),
            color = colors.inkMuted,
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp),
        )
    }
}
