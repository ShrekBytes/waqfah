package com.shrekbytes.waqfah.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrekbytes.waqfah.ui.components.ChevronDirection
import com.shrekbytes.waqfah.ui.components.ChevronIcon
import com.shrekbytes.waqfah.ui.components.SectionTitle
import com.shrekbytes.waqfah.ui.components.SettingsScaffold
import com.shrekbytes.waqfah.ui.theme.WaqfahTheme

@Composable
fun GratitudeScreen(onBack: () -> Unit) {
    val colors = WaqfahTheme.colors
    val context = LocalContext.current

    SettingsScaffold(title = "Gratitude", onBack = onBack) {
        Text(
            "Waqfah is made by people, for people. JazakumAllahu khairan to everyone who gave " +
                "their time to test, critique, and carry it along.",
            color = colors.inkMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(18.dp))
        SupportInfo.contributors.forEachIndexed { index, contributor ->
            if (index > 0) HorizontalDivider(color = colors.line)
            Row(
                Modifier
                    .fillMaxWidth()
                    .let {
                        if (contributor.url != null) {
                            it.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contributor.url)))
                            }
                        } else {
                            it
                        }
                    }
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(contributor.name, color = colors.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(contributor.role, color = colors.inkMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (contributor.url != null) {
                    // Outward-tilted chevron signals "opens a link".
                    ChevronIcon(
                        direction = ChevronDirection.RIGHT,
                        tint = colors.inkSoft,
                        modifier = Modifier.size(14.dp).rotate(-45f),
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("Credits")
        Text(
            "Quran text, translations (including Sahih International and Taisirul), and Arabic fonts " +
                "(Digital Khatt, Me Quran, Amiri, KFGQPC Nastaleeq) belong to their respective publishers " +
                "and authors. Waqfah is grateful to make use of their work.",
            color = colors.inkMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            "And to every user who pauses for an ayah before continuing — that pause is the whole " +
                "point. May it weigh heavy on the scale.",
            color = colors.inkMuted,
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))
    }
}
