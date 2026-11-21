package com.saferenttechnologies.android.presentation.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.PropertyActions
import com.saferenttechnologies.android.domain.model.PropertyDetail
import com.saferenttechnologies.android.domain.model.PropertyRevision
import com.saferenttechnologies.android.domain.model.RevisionStatus
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

private val InfoBlue = Color(0xFF2563EB)

/** changedSections key → label. */
@Composable
fun changedSectionLabel(key: String): String = when (key.lowercase()) {
    "basic" -> stringResource(R.string.changes_section_basic)
    "location" -> stringResource(R.string.changes_section_location)
    "building" -> stringResource(R.string.changes_section_building)
    "units" -> stringResource(R.string.changes_section_units)
    "rooms" -> stringResource(R.string.changes_section_rooms)
    "pricing" -> stringResource(R.string.changes_section_pricing)
    "utilities" -> stringResource(R.string.changes_section_utilities)
    "policies" -> stringResource(R.string.changes_section_policies)
    "amenities" -> stringResource(R.string.changes_section_amenities)
    "photos" -> stringResource(R.string.changes_section_photos)
    else -> com.saferenttechnologies.android.domain.model.humanize(key)
}

/**
 * Change request of a live listing, under the header:
 * DRAFT (amber) unsent · PENDING_VERIFICATION (blue) being verified · REJECTED (red) asked for changes.
 * Or a green note when the last changes were approved within 7 days.
 */
@Composable
fun ChangeRequestCard(detail: PropertyDetail, busy: Boolean, onIntent: (PropertyDetailIntent) -> Unit) {
    val revision = detail.pendingRevision
    if (revision == null) {
        val last = detail.lastRevision
        if (last?.status == RevisionStatus.APPROVED && last.decidedAt != null && withinDays(last.decidedAt, 7)) {
            Note(stringResource(R.string.changes_approved_note, prettyDay(last.decidedAt)), AuthColors.GreenText, AuthColors.GreenBg)
        }
        return
    }
    val (color, title, body) = when (revision.status) {
        RevisionStatus.PENDING_VERIFICATION -> Triple(
            InfoBlue,
            stringResource(R.string.changes_pending_title),
            stringResource(R.string.changes_pending_body, revision.submittedAt?.let(::prettyDay) ?: stringResource(R.string.changes_recently)),
        )
        RevisionStatus.REJECTED -> Triple(AuthColors.Error, stringResource(R.string.changes_rejected_title), revision.moderationNote.orEmpty())
        else -> Triple(Amber, stringResource(R.string.changes_draft_title), stringResource(R.string.changes_draft_body))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.sdp)
            .clip(RoundedCornerShape(14.sdp))
            .background(color.copy(alpha = 0.08f))
            .border(1.sdp, color.copy(alpha = 0.35f), RoundedCornerShape(14.sdp))
            .padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.sdp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.sdp))
            Text(title, color = color, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp)
        }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(4.sdp))
            Text(body, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
        }
        if (revision.status == RevisionStatus.REJECTED) {
            Text(stringResource(R.string.changes_draft_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        if (revision.changedSections.isNotEmpty()) {
            Spacer(Modifier.height(6.sdp))
            Text(
                stringResource(R.string.changes_changed, revision.changedSections.map { changedSectionLabel(it) }.joinToString(", ")),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
            )
        }
        if (revision.open) {
            Spacer(Modifier.height(10.sdp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                if (detail.can.editRevision) {
                    OutlineAction(stringResource(R.string.changes_continue), enabled = !busy, modifier = Modifier.weight(1f), compact = true) {
                        onIntent(PropertyDetailIntent.ContinueChanges)
                    }
                }
                if (PropertyActions.canDiscardChanges(detail)) {
                    OutlineAction(stringResource(R.string.changes_discard), enabled = !busy, modifier = Modifier.weight(1f), compact = true) {
                        onIntent(PropertyDetailIntent.DiscardChanges)
                    }
                }
            }
            if (PropertyActions.canSubmitChanges(detail)) {
                Spacer(Modifier.height(8.sdp))
                Text(
                    stringResource(R.string.changes_submit),
                    color = Color.White,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.ssp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.sdp))
                        .background(if (busy) AuthColors.Border else AuthColors.Orange)
                        .clickable(enabled = !busy, role = Role.Button) { onIntent(PropertyDetailIntent.SubmitChanges) }
                        .padding(vertical = 10.sdp),
                )
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color, background: Color) {
    Text(
        text,
        color = color,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.ssp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.sdp).clip(RoundedCornerShape(12.sdp)).background(background).padding(12.sdp),
    )
}

/** "Your unsent changes are deleted…" */
@Composable
fun DiscardChangesDialog(onIntent: (PropertyDetailIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(PropertyDetailIntent.DismissDiscard) },
        title = { Text(stringResource(R.string.changes_discard_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
        text = { Text(stringResource(R.string.changes_discard_body), fontFamily = Inter, color = AuthColors.Body) },
        confirmButton = {
            TextButton(onClick = { onIntent(PropertyDetailIntent.ConfirmDiscard) }) {
                Text(stringResource(R.string.changes_discard), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(PropertyDetailIntent.DismissDiscard) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
        containerColor = AuthColors.Card,
    )
}

/** Submit 412 → what to fix; tapping one opens that wizard step. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeProblemsSheet(problems: List<Pair<String, Int>>, onIntent: (PropertyDetailIntent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onIntent(PropertyDetailIntent.DismissChangeProblems) }, containerColor = AuthColors.Background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Text(stringResource(R.string.add_property_fix_before_submit), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Spacer(Modifier.height(10.sdp))
            problems.forEach { (problem, step) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.sdp)
                        .clip(RoundedCornerShape(12.sdp))
                        .background(AuthColors.Error.copy(alpha = 0.08f))
                        .clickable(role = Role.Button) {
                            onIntent(PropertyDetailIntent.DismissChangeProblems)
                            onIntent(PropertyDetailIntent.Edit(step))
                        }
                        .padding(12.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.sdp).clip(CircleShape).border(2.sdp, AuthColors.Error, CircleShape))
                    Spacer(Modifier.width(10.sdp))
                    Text(problem, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.add_property_fix), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
                }
            }
        }
    }
}

/** ISO → "14 Nov 2026" (date only). */
fun prettyDay(iso: String): String = prettyDate(iso.take(10))

/** decidedAt within [days] of now. */
fun withinDays(iso: String, days: Int): Boolean {
    val millis = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX").firstNotNullOfOrNull { p ->
        runCatching { java.text.SimpleDateFormat(p, java.util.Locale.US).parse(iso)?.time }.getOrNull()
    } ?: return false
    return System.currentTimeMillis() - millis <= days * 24L * 60 * 60 * 1000
}
