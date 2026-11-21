package com.saferenttechnologies.android.presentation.invitation

import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.DetailSkeleton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.domain.model.TenantInvitation
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.explore.Badge
import com.saferenttechnologies.android.presentation.explore.ListingCover
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

/** The /invite/{token} screen: home, rent, lease terms → Accept / Decline. */
@Composable
fun InvitationScreen(
    state: InvitationState,
    effects: Flow<InvitationEffect>,
    onIntent: (InvitationIntent) -> Unit,
    onChanged: () -> Unit,
    onOpenHome: (String) -> Unit,
    /** Share step "Add": open the document vault upload (type pre-picked when known). */
    onOpenUpload: (com.saferenttechnologies.android.domain.model.DocType?) -> Unit = {},
) {
    BackHandler { onIntent(InvitationIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        effects.collect {
            when (it) {
                is InvitationEffect.Changed -> onChanged()
                is InvitationEffect.OpenUpload -> onOpenUpload(it.type)
            }
        }
    }
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        onIntent(InvitationIntent.MessageShown)
        if (m.isNotBlank()) snackbar.showSnackbar(m)
    }
    val inv = state.invitation

    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(code = inv?.code.orEmpty()) { onIntent(InvitationIntent.Close) }
            when {
                // Accepted and the owner asked for documents → share them now ("Skip for now" allowed).
                state.outcome != null && inv != null && state.sharingStep -> ShareDocsContent(state, onIntent)
                state.outcome != null && inv != null -> OutcomeContent(state, inv) { onIntent(InvitationIntent.Close) }
                // The owner asks a few details before the lease starts.
                state.answering && inv != null -> AnswersContent(state, inv, onIntent)
                inv == null && (state.loading || state.error == null) ->
                    DetailSkeleton()
                inv == null -> ErrorContent(state, onIntent)
                else -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
                        InvitationContent(inv, onOpenHome)
                        Spacer(Modifier.height(20.sdp))
                    }
                    if (inv.isPending) ActionBar(state, onIntent)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp))
    }

    if (state.confirmDecline) DeclineDialog(state, onIntent)
}

// ---------------- pieces ----------------

@Composable
private fun TopBar(code: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
        Spacer(Modifier.width(12.sdp))
        Text(
            stringResource(R.string.invitation_title),
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.ssp,
            modifier = Modifier.weight(1f),
        )
        if (code.isNotBlank()) Text(code, color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
    }
}

@Composable
private fun InvitationContent(inv: TenantInvitation, onOpenHome: (String) -> Unit) {
    // Hero: 4:3 photo, status badge, title + place on a soft dark fade.
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(18.sdp))) {
        ListingCover(inv.coverImageId, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)),
            ),
        )
        Box(Modifier.align(Alignment.TopStart).padding(12.sdp)) { StatusBadge(inv.status) }
        Column(Modifier.align(Alignment.BottomStart).padding(14.sdp)) {
            Text(
                inv.propertyTitle.ifBlank { stringResource(R.string.invitation_a_home) },
                color = Color.White,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.ssp,
            )
            if (inv.place.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_location), null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(13.sdp))
                    Spacer(Modifier.width(4.sdp))
                    Text(inv.place, color = Color.White.copy(alpha = 0.9f), fontFamily = Inter, fontSize = 12.ssp)
                }
            }
        }
    }

    Spacer(Modifier.height(16.sdp))
    if (inv.recipientName.isNotBlank()) {
        Text(stringResource(R.string.invitation_hi, inv.recipientName.substringBefore(' ')), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
        Spacer(Modifier.height(2.sdp))
    }
    Text(
        when {
            inv.landlordName.isNotBlank() && inv.target.isNotBlank() -> stringResource(R.string.invitation_headline_target, inv.landlordName, inv.target)
            inv.landlordName.isNotBlank() -> stringResource(R.string.invitation_headline, inv.landlordName)
            else -> stringResource(R.string.invitation_headline_plain)
        },
        color = AuthColors.Ink,
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 17.ssp,
    )

    ClosedBanner(inv)

    if (inv.landlordName.isNotBlank()) {
        Spacer(Modifier.height(14.sdp))
        LandlordRow(inv)
    }

    if (inv.monthlyRent.isNotBlank()) {
        Spacer(Modifier.height(14.sdp))
        RentCard(inv)
    }

    Spacer(Modifier.height(18.sdp))
    SectionTitle(stringResource(R.string.invitation_lease_terms))
    Spacer(Modifier.height(8.sdp))
    TermsCard(inv)

    if (inv.isPending && inv.expiresAt.isNotBlank()) {
        Spacer(Modifier.height(12.sdp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(Amber.copy(alpha = 0.08f)).padding(horizontal = 12.sdp, vertical = 10.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_pause_circle), null, tint = Amber, modifier = Modifier.size(16.sdp))
            Spacer(Modifier.width(8.sdp))
            Text(stringResource(R.string.invitation_respond_by, prettyDate(inv.expiresAt)), color = Amber, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
        }
    }

    if (inv.isPending) {
        Spacer(Modifier.height(12.sdp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.sdp))
                .background(AuthColors.GreenBg)
                .border(1.sdp, AuthColors.GreenBorder, RoundedCornerShape(12.sdp))
                .padding(12.sdp),
        ) {
            Icon(painterResource(R.drawable.ic_shield_check), null, tint = AuthColors.Green, modifier = Modifier.size(18.sdp))
            Spacer(Modifier.width(10.sdp))
            Text(stringResource(R.string.invitation_safety), color = AuthColors.GreenText, fontFamily = Inter, fontSize = 13.ssp)
        }
    }

    if (inv.propertyId.isNotBlank()) {
        Spacer(Modifier.height(10.sdp))
        Text(
            stringResource(R.string.invitation_view_home),
            color = AuthColors.OrangeText,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.ssp,
            modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(role = Role.Button) { onOpenHome(inv.propertyId) }.padding(vertical = 8.sdp, horizontal = 4.sdp),
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, bg, fg) = when (status.uppercase()) {
        "PENDING" -> Triple(R.string.invitation_status_pending, AuthColors.OrangeSoft, AuthColors.OrangeText)
        "ACCEPTED" -> Triple(R.string.invitation_status_accepted, AuthColors.GreenBg, AuthColors.GreenText)
        "REJECTED" -> Triple(R.string.invitation_status_declined, AuthColors.Surface, AuthColors.Body)
        "REVOKED" -> Triple(R.string.invitation_status_withdrawn, AuthColors.Surface, AuthColors.Body)
        else -> Triple(R.string.invitation_status_expired, AuthColors.Surface, AuthColors.Body)
    }
    Badge(stringResource(label), background = bg, content = fg)
}

/** Not pending any more → why, in one line (no buttons). */
@Composable
private fun ClosedBanner(inv: TenantInvitation) {
    if (inv.isPending) return
    val text = when (inv.status.uppercase()) {
        "ACCEPTED" -> R.string.invitation_closed_accepted
        "REJECTED" -> R.string.invitation_closed_declined
        "REVOKED" -> R.string.invitation_closed_withdrawn
        else -> R.string.invitation_closed_expired
    }
    Spacer(Modifier.height(12.sdp))
    Text(
        stringResource(text),
        color = AuthColors.Ink,
        fontFamily = Inter,
        fontSize = 13.ssp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp),
    )
}

@Composable
private fun LandlordRow(inv: TenantInvitation) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Text(initials(inv.landlordName), color = AuthColors.OrangeText, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp)
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(inv.landlordName, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            Text(stringResource(R.string.invitation_landlord), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
        }
        if (inv.landlordVerified) Badge(stringResource(R.string.explore_verified), R.drawable.ic_shield_check, background = AuthColors.GreenBg, content = AuthColors.GreenText)
    }
}

@Composable
private fun RentCard(inv: TenantInvitation) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(AuthColors.Cream)
            .border(1.sdp, AuthColors.CreamBorder, RoundedCornerShape(16.sdp))
            .padding(16.sdp),
    ) {
        Text(stringResource(R.string.invitation_monthly_rent), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Money.inr(inv.monthlyRent), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 28.ssp)
            Spacer(Modifier.width(4.sdp))
            Text(stringResource(R.string.invitation_per_month), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.padding(bottom = 5.sdp))
        }
        val figures = listOfNotNull(
            inv.securityDeposit.takeIf { Money.inr(it).isNotBlank() }?.let { R.string.invitation_deposit to Money.inr(it) },
            inv.maintenanceCharges.takeIf { Money.inr(it).isNotBlank() && it.toDoubleOrNull() != 0.0 }?.let { R.string.invitation_maintenance to Money.inr(it) },
            inv.moveInTotal.takeIf { it.isNotBlank() }?.let { R.string.invitation_move_in_total to Money.inr(it) },
        )
        if (figures.isNotEmpty()) {
            Spacer(Modifier.height(12.sdp))
            Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.CreamBorder))
            Spacer(Modifier.height(12.sdp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                figures.forEachIndexed { i, (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(label), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
                        Text(
                            value,
                            color = if (i == figures.lastIndex && label == R.string.invitation_move_in_total) AuthColors.OrangeText else AuthColors.Ink,
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.ssp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsCard(inv: TenantInvitation) {
    val rows = listOfNotNull(
        inv.startDate.takeIf { it.isNotBlank() }?.let { stringResource(R.string.invitation_move_in) to prettyDate(it) },
        stringResource(R.string.invitation_lease_ends) to (inv.endDate.takeIf { it.isNotBlank() }?.let { prettyDate(it) } ?: stringResource(R.string.invitation_no_end)),
        inv.rentDueDay?.let { stringResource(R.string.invitation_rent_due) to stringResource(R.string.invitation_rent_due_value, ordinal(it)) },
        inv.noticePeriodDays?.let { stringResource(R.string.invitation_notice) to stringResource(R.string.invitation_days, it) },
        inv.lockInMonths?.takeIf { it > 0 }?.let { stringResource(R.string.invitation_lock_in) to stringResource(R.string.invitation_months, it) },
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))) {
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.Divider))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.sdp, vertical = 12.sdp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                Text(value, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, textAlign = TextAlign.End)
            }
        }
        if (inv.additionalTerms.isNotBlank()) {
            Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.Divider))
            Column(Modifier.padding(14.sdp)) {
                Text(stringResource(R.string.invitation_other_terms), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                Spacer(Modifier.height(6.sdp))
                Text(
                    inv.additionalTerms,
                    color = AuthColors.Ink,
                    fontFamily = Inter,
                    fontSize = 13.ssp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.Field).padding(12.sdp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
}

/** Pinned bottom: agree checkbox, then Decline + Accept. */
@Composable
private fun ActionBar(state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().background(AuthColors.Background)) {
        Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.Divider))
        Column(Modifier.padding(horizontal = 16.sdp, vertical = 10.sdp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.sdp))
                    .clickable(enabled = !state.busy, role = Role.Checkbox) { onIntent(InvitationIntent.Agree(!state.agreed)) }
                    .padding(vertical = 2.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.agreed,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = AuthColors.Orange, uncheckedColor = AuthColors.Muted, checkmarkColor = Color.White),
                )
                Spacer(Modifier.width(8.sdp))
                Text(stringResource(R.string.invitation_agree), color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
            }
            Spacer(Modifier.height(8.sdp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.sdp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.sdp)
                        .clip(RoundedCornerShape(14.sdp))
                        .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
                        .clickable(enabled = !state.busy, role = Role.Button) { onIntent(InvitationIntent.AskDecline) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.invitation_decline),
                        color = if (state.busy) AuthColors.Muted else AuthColors.Ink,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.ssp,
                    )
                }
                AuthPrimaryButton(
                    text = stringResource(R.string.invitation_accept),
                    enabled = state.canAccept,
                    loading = state.accepting,
                    onClick = { onIntent(InvitationIntent.Accept) },
                    modifier = Modifier.weight(1.5f),
                    leadingIcon = R.drawable.ic_check,
                    fontSize = 15.ssp,
                )
            }
        }
    }
}

@Composable
private fun DeclineDialog(state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.declining) onIntent(InvitationIntent.DismissDecline) },
        containerColor = AuthColors.Card,
        title = { Text(stringResource(R.string.invitation_decline_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 18.ssp) },
        text = {
            Column {
                Text(stringResource(R.string.invitation_decline_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                Spacer(Modifier.height(12.sdp))
                OutlinedTextField(
                    value = state.declineReason,
                    onValueChange = { onIntent(InvitationIntent.DeclineReason(it)) },
                    label = { Text(stringResource(R.string.invitation_decline_reason), fontFamily = Inter) },
                    enabled = !state.declining,
                    minLines = 2,
                    maxLines = 4,
                    colors = propertyFieldColors(),
                    shape = RoundedCornerShape(12.sdp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !state.declining, onClick = { onIntent(InvitationIntent.ConfirmDecline) }) {
                if (state.declining) {
                    CircularProgressIndicator(color = AuthColors.Error, strokeWidth = 2.sdp, modifier = Modifier.size(18.sdp))
                } else {
                    Text(stringResource(R.string.invitation_decline), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !state.declining, onClick = { onIntent(InvitationIntent.DismissDecline) }) {
                Text(stringResource(R.string.invitation_keep), color = AuthColors.Body, fontFamily = Inter)
            }
        },
    )
}

/** After Accept / Decline: big check (or close), what happens next, Done. */
@Composable
private fun OutcomeContent(state: InvitationState, inv: TenantInvitation, onDone: () -> Unit) {
    val accepted = state.outcome == InvitationOutcome.ACCEPTED
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.sdp).clip(CircleShape).background(if (accepted) AuthColors.GreenBg else AuthColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (accepted) R.drawable.ic_check_circle else R.drawable.ic_close),
                null,
                tint = if (accepted) AuthColors.Green else AuthColors.Body,
                modifier = Modifier.size(48.sdp),
            )
        }
        Spacer(Modifier.height(20.sdp))
        Text(
            stringResource(if (accepted) R.string.invitation_done_accepted else R.string.invitation_done_declined),
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.ssp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.sdp))
        Text(
            state.outcomeMessage.ifBlank {
                stringResource(if (accepted) R.string.invitation_done_accepted_body else R.string.invitation_done_declined_body)
            },
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 14.ssp,
            textAlign = TextAlign.Center,
        )
        if (accepted) {
            Spacer(Modifier.height(20.sdp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ListingCover(inv.coverImageId, Modifier.size(56.sdp).clip(RoundedCornerShape(10.sdp)))
                Spacer(Modifier.width(12.sdp))
                Column(Modifier.weight(1f)) {
                    Text(inv.propertyTitle, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                    Text(listOf(inv.target, inv.place).filter { it.isNotBlank() }.joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    if (inv.monthlyRent.isNotBlank()) {
                        Text("${Money.inr(inv.monthlyRent)} ${stringResource(R.string.invitation_per_month)}", color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.sdp))
        AuthPrimaryButton(text = stringResource(R.string.invitation_go_home), enabled = true, loading = false, onClick = onDone, fontSize = 15.ssp)
    }
}

@Composable
private fun ErrorContent(state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_mail), null, tint = AuthColors.OrangeText, modifier = Modifier.size(40.sdp))
        }
        Spacer(Modifier.height(18.sdp))
        Text(
            stringResource(if (state.notFound) R.string.invitation_not_found else R.string.invitation_load_failed),
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.ssp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.sdp))
        Text(
            if (state.notFound) stringResource(R.string.invitation_not_found_body) else state.error.orEmpty(),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 14.ssp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.sdp))
        if (!state.notFound) {
            AuthPrimaryButton(text = stringResource(R.string.invitation_retry), enabled = !state.loading, loading = state.loading, onClick = { onIntent(InvitationIntent.Retry) }, fontSize = 15.ssp)
            Spacer(Modifier.height(8.sdp))
        }
        TextButton(onClick = { onIntent(InvitationIntent.Close) }) {
            Text(stringResource(R.string.invitation_go_home), color = AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Medium)
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

/** 1 → "1st", 2 → "2nd", 11 → "11th", 22 → "22nd". */
internal fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}
