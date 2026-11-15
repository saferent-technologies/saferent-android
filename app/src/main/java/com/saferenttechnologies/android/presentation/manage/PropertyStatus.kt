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
