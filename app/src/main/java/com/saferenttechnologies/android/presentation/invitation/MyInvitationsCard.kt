package com.saferenttechnologies.android.presentation.invitation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.domain.model.TenantInvitation
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Tenant dashboard: lease invitations landlords sent me → tap to see the terms and accept / decline. */
@Composable
fun MyInvitationsCard(invitations: List<TenantInvitation>, onOpen: (token: String) -> Unit, modifier: Modifier = Modifier) {
    if (invitations.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.my_invitations_title, invitations.size),
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.ssp,
        )
        Spacer(Modifier.height(8.sdp))
        invitations.forEach { inv ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.sdp).clip(RoundedCornerShape(14.sdp)).background(AuthColors.OrangeSoft)
                    .border(1.sdp, AuthColors.Orange, RoundedCornerShape(14.sdp))
                    .clickable(role = Role.Button) { onOpen(inv.token) }.padding(14.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.sdp).clip(CircleShape).background(AuthColors.Orange), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_home_outline), null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.sdp))
                }
                Spacer(Modifier.width(12.sdp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOf(inv.target, inv.propertyTitle).filter { it.isNotBlank() }.joinToString(", ").ifBlank { stringResource(R.string.invitation_a_home) },
                        color = AuthColors.Ink,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.ssp,
                    )
                    val sub = listOf(
                        inv.landlordName.takeIf { it.isNotBlank() }?.let { stringResource(R.string.my_invitations_from, it) }.orEmpty(),
                        if (inv.monthlyRent.isNotBlank()) Money.inr(inv.monthlyRent) + stringResource(R.string.explore_per_month) else "",
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                }
                Text(stringResource(R.string.my_invitations_view), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.ssp)
            }
        }
    }
}
