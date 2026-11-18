package com.saferenttechnologies.android.presentation.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter

/** Small "Report a problem" link → New ticket to SafeRent (category / property pre-filled by the host). */
@Composable
fun ReportProblemLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(8.sdp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.sdp, vertical = 6.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_flag), null, tint = AuthColors.Body, modifier = Modifier.size(14.sdp))
        Spacer(Modifier.width(4.sdp))
        Text(stringResource(R.string.sup_report_problem), color = AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp)
    }
}
