package com.saferenttechnologies.android.presentation.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.humanize
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter

private val Blue = Color(0xFF2563EB)
private val Teal = Color(0xFF0D9488)
private val Grey = Color(0xFF64748B)

/** activeStatus wins when it isn't ACTIVE; otherwise publicationStatus. */
@Composable
fun PropertyStatusChip(publicationStatus: String, activeStatus: String, modifier: Modifier = Modifier) {
    val (label, color) = when (activeStatus.uppercase()) {
        "INACTIVE" -> stringResource(R.string.status_inactive) to Grey
        "SUSPENDED" -> stringResource(R.string.status_suspended) to AuthColors.Error
        "ARCHIVED" -> stringResource(R.string.status_archived) to Grey
        else -> when (publicationStatus.uppercase()) {
            "DRAFT" -> stringResource(R.string.status_draft) to Grey
            "PENDING_VERIFICATION" -> stringResource(R.string.status_under_verification) to Blue
            "APPROVED" -> stringResource(R.string.status_approved) to Teal
            "REJECTED" -> stringResource(R.string.status_needs_changes) to AuthColors.OrangeText
            "PUBLISHED" -> stringResource(R.string.status_live) to AuthColors.Green
            else -> humanize(publicationStatus) to Grey
        }
    }
    StatusPill(label, color, modifier)
}

/** Unit / bed availability: AVAILABLE green, OCCUPIED blue, others grey / orange. */
@Composable
fun AvailabilityChip(status: String, modifier: Modifier = Modifier) {
    val color = when (status.uppercase()) {
        "AVAILABLE" -> AuthColors.Green
        "OCCUPIED" -> Blue
        "UNDER_MAINTENANCE" -> AuthColors.OrangeText
        else -> Grey
    }
    StatusPill(humanize(status.ifBlank { "UNKNOWN" }), color, modifier)
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.ssp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.sdp, vertical = 4.sdp),
    )
}

/** "₹18,000 – ₹22,000" (or one value). */
fun rentRange(from: String, to: String): String {
    fun fmt(v: String) = v.toDoubleOrNull()?.let { "₹" + String.format(java.util.Locale("en", "IN"), "%,.0f", it) } ?: v
    return when {
        from.isBlank() && to.isBlank() -> ""
        to.isBlank() || from == to -> fmt(from.ifBlank { to })
        from.isBlank() -> fmt(to)
        else -> "${fmt(from)} – ${fmt(to)}"
    }
}

fun money(v: String): String = rentRange(v, "")

/** "2026-11-30" / "2026-11-30T00:00:00Z" → "30 Nov 2026" — read as a calendar date (no time-zone shift). */
fun calendarDate(iso: String): String {
    val d = com.saferenttechnologies.android.domain.model.SimpleDate.parse(iso.take(10)) ?: return iso
    val c = java.util.Calendar.getInstance().apply { clear(); set(d.year, d.month - 1, d.day) }
    return java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(c.time)
}

/**
 * Flat / unit chip: the server's availabilityLabel ("Available now", "Available from 30 Nov 2026",
 * "Occupied") — or the same text built here for older responses. Green now, orange later, grey occupied.
 */
@Composable
fun UnitAvailabilityChip(status: String, label: String, availableFrom: String, availableNow: Boolean?, modifier: Modifier = Modifier) {
    val tone = com.saferenttechnologies.android.domain.model.UnitAvailability.tone(
        status, availableNow, availableFrom, com.saferenttechnologies.android.domain.model.SimpleDate.today(),
    )
    val text = label.ifBlank {
        when (tone) {
            com.saferenttechnologies.android.domain.model.UnitAvailability.Tone.NOW -> stringResource(R.string.unit_available_now)
            com.saferenttechnologies.android.domain.model.UnitAvailability.Tone.LATER -> stringResource(R.string.unit_available_from, calendarDate(availableFrom))
            else -> humanize(status.ifBlank { "UNKNOWN" })
        }
    }
    val color = when (tone) {
        com.saferenttechnologies.android.domain.model.UnitAvailability.Tone.NOW -> AuthColors.Green
        com.saferenttechnologies.android.domain.model.UnitAvailability.Tone.LATER -> AuthColors.OrangeText
        com.saferenttechnologies.android.domain.model.UnitAvailability.Tone.OCCUPIED -> Grey
        com.saferenttechnologies.android.domain.model.UnitAvailability.Tone.OTHER -> AuthColors.Muted
    }
    StatusPill(text, color, modifier)
}

/** Same, from a unit's values map (landlord screens). */
@Composable
fun UnitAvailabilityChip(values: Map<String, String>, modifier: Modifier = Modifier) = UnitAvailabilityChip(
    status = values["availabilityStatus"].orEmpty(),
    label = values["availabilityLabel"].orEmpty(),
    availableFrom = values["availableFrom"].orEmpty(),
    availableNow = values["availableNow"]?.takeIf { it.isNotBlank() }?.equals("true", true),
    modifier = modifier,
)
