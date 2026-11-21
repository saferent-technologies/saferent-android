package com.saferenttechnologies.android.presentation.tenantcheck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.Money
import com.saferenttechnologies.android.domain.model.PersonPreview
import com.saferenttechnologies.android.domain.model.TenantReport
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import java.util.Locale

@Composable
fun TenantCheckScreen(state: TenantCheckState, onIntent: (TenantCheckIntent) -> Unit) {
    BackHandler { onIntent(TenantCheckIntent.Close) }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(TenantCheckIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Text(stringResource(R.string.tc_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.sdp)) {
            Text(stringResource(R.string.tc_hint), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            Spacer(Modifier.height(12.sdp))
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(TenantCheckIntent.Query(it)) },
                label = { Text(stringResource(R.string.tc_field), fontFamily = Inter) },
                singleLine = true,
                isError = state.invalid,
                supportingText = { if (state.invalid) Text(stringResource(R.string.tc_invalid), fontFamily = Inter) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onIntent(TenantCheckIntent.Search) }),
                colors = propertyFieldColors(),
                shape = RoundedCornerShape(12.sdp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.sdp))
            AuthPrimaryButton(
                text = stringResource(R.string.tc_search),
                enabled = state.query.isNotBlank() && !state.loading,
                loading = state.loading,
                onClick = { onIntent(TenantCheckIntent.Search) },
                fontSize = 15.ssp,
            )
            Spacer(Modifier.height(16.sdp))
            when {
                state.loading -> com.saferenttechnologies.android.presentation.common.TenantReportSkeleton()
                state.error != null -> Text(state.error, color = AuthColors.Error, fontFamily = Inter, fontSize = 14.ssp)
                state.notFound -> Text(stringResource(R.string.tc_not_found), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                state.report != null -> Report(state.report)
            }
        }
    }
}

@Composable
fun PersonCard(p: PersonPreview, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(14.sdp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Text(
                p.fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" },
                color = AuthColors.OrangeText, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp,
            )
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.fullName.ifBlank { stringResource(R.string.word_tenant) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.ssp)
                if (p.verified) {
                    Spacer(Modifier.width(6.sdp))
                    Icon(painterResource(R.drawable.ic_shield_check), stringResource(R.string.explore_verified), tint = AuthColors.GreenText, modifier = Modifier.size(16.sdp))
                }
            }
            // No phone number on the card (privacy): SafeRent ID + city only.
            val sub = listOf(p.safeRentId, p.city).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            if (p.memberSince.isNotBlank()) Text(stringResource(R.string.tc_member_since, prettyDate(p.memberSince)), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
        }
    }
}

@Composable
private fun Report(r: TenantReport) {
    PersonCard(r.person)
    // Rent record
    Section(stringResource(R.string.tc_rent_record))
    val pct = r.payments.onTimePercent
    if (pct == null) {
        Text(stringResource(R.string.tc_no_payments), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            Figure(stringResource(R.string.tc_on_time), "$pct%", AuthColors.GreenText, Modifier.weight(1f))
            Figure(stringResource(R.string.tc_late), r.payments.late.toString(), AuthColors.OrangeText, Modifier.weight(1f))
            Figure(stringResource(R.string.tc_missed), r.payments.missed.toString(), AuthColors.Error, Modifier.weight(1f))
        }
        r.recentPayments.forEach { p ->
            Spacer(Modifier.height(6.sdp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(10.sdp)).padding(10.sdp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.period.ifBlank { prettyDate(p.paidOn) }, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
                    if (p.amount.isNotBlank()) Text(Money.inr(p.amount), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                }
                StatusPill(optionLabel(p.status), statusColor(p.status))
            }
        }
    }
    // Reviews from earlier landlords
    Section(stringResource(R.string.tc_reviews))
    if (r.reviews.isEmpty()) {
        Text(stringResource(R.string.tc_no_reviews), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
    } else {
        r.rating?.let {
            Text("★ " + String.format(Locale.US, "%.1f", it) + " · " + stringResource(R.string.listing_reviews_n, r.reviewCount), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            Spacer(Modifier.height(6.sdp))
        }
        r.reviews.forEach { rv ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.sdp).clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rv.landlordName.ifBlank { stringResource(R.string.tc_a_landlord) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                    rv.rating?.let { Text("★ " + String.format(Locale.US, "%.1f", it), color = AuthColors.OrangeText, fontFamily = Inter, fontSize = 12.ssp) }
                }
                if (rv.comment.isNotBlank()) Text(rv.comment, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                if (rv.tags.isNotEmpty()) Text(rv.tags.joinToString(" · "), color = AuthColors.OrangeText, fontFamily = Inter, fontSize = 12.ssp)
                val meta = listOf(rv.place, if (rv.createdAt.isNotBlank()) prettyDate(rv.createdAt) else "").filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
            }
        }
    }
    // Lease history
    Section(stringResource(R.string.tc_leases))
    if (r.leases.isEmpty()) Text(stringResource(R.string.tc_no_leases), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
    r.leases.forEach { l ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.sdp).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listOf(l.propertyTitle, l.city).filter { it.isNotBlank() }.joinToString(", "), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                if (l.status.isNotBlank()) StatusPill(optionLabel(l.status), if (l.status == "ACTIVE") AuthColors.Green else AuthColors.Body)
            }
            val dates = listOf(l.startDate, l.endDate).filter { it.isNotBlank() }.joinToString(" to ") { prettyDate(it) }
            val line = listOf(dates, if (l.monthlyRent.isNotBlank()) Money.inr(l.monthlyRent) + stringResource(R.string.explore_per_month) else "").filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) Text(line, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
    }
    Spacer(Modifier.height(24.sdp))
}

@Composable
private fun statusColor(s: String): Color = when (s) {
    "PAID", "ON_TIME" -> AuthColors.Green
    "LATE" -> AuthColors.OrangeText
    "MISSED", "UNPAID", "OVERDUE" -> AuthColors.Error
    else -> AuthColors.Body
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.sdp))
    Text(title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
    Spacer(Modifier.height(8.sdp))
}

@Composable
private fun Figure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp)) {
        Text(value, color = color, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 11.ssp)
    }
}

/** Landlord dashboard card → "Review tenant" screen. */
@Composable
fun TenantCheckCard(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp)).background(AuthColors.Surface)
            .clickable(role = Role.Button, onClick = onOpen).padding(16.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_people), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tc_card_title), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            Text(stringResource(R.string.tc_card_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        Spacer(Modifier.width(8.sdp))
        Text(stringResource(R.string.tc_title), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 13.ssp)
    }
}
