package com.saferenttechnologies.android.presentation.lease

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import com.saferenttechnologies.android.domain.model.ReviewDraft
import com.saferenttechnologies.android.domain.model.ReviewForm
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.ChipFlow
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter

/** Star rating per category (overall required) + text + tags. [form] null → still loading. */
@Composable
fun ReviewFormSection(
    form: ReviewForm?,
    draft: ReviewDraft,
    showError: Boolean,
    enabled: Boolean,
    onRate: (String, Int) -> Unit,
    onText: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    if (form == null) {
        Box(Modifier.fillMaxWidth().padding(16.sdp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AuthColors.Orange, strokeWidth = 2.5.sdp, modifier = Modifier.size(24.sdp))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        form.allCategories.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.label + if (c.required) " *" else "",
                    color = AuthColors.Ink,
                    fontFamily = Inter,
                    fontWeight = if (c.key == ReviewForm.OVERALL) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.ssp,
                    modifier = Modifier.weight(1f),
                )
                Stars(draft.ratings[c.key] ?: 0, enabled) { onRate(c.key, it) }
            }
        }
        if (showError && !draft.isComplete) {
            Text(stringResource(R.string.lease_review_overall_needed), color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)
        }
        OutlinedTextField(
            value = draft.text,
            onValueChange = onText,
            label = { Text(stringResource(R.string.lease_review_text), fontFamily = Inter) },
            enabled = enabled,
            minLines = 3,
            maxLines = 6,
            supportingText = { Text("${draft.text.length} / ${ReviewDraft.MAX_TEXT}", fontFamily = Inter) },
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.tags.isNotEmpty()) {
            Text(stringResource(R.string.lease_review_tags, ReviewDraft.MAX_TAGS), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            ChipFlow(form.tags) { tag -> Chip(tag, selected = tag in draft.tags, enabled = enabled) { onTag(tag) } }
        }
        Spacer(Modifier.height(2.sdp))
    }
}

@Composable
private fun Stars(value: Int, enabled: Boolean, onRate: (Int) -> Unit) {
    Row {
        (1..5).forEach { n ->
            Box(
                Modifier.size(34.sdp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button) { onRate(n) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_star),
                    stringResource(R.string.lease_review_stars, n),
                    tint = if (n <= value) AuthColors.Orange else AuthColors.Border,
                    modifier = Modifier.size(24.sdp),
                )
            }
        }
    }
}
