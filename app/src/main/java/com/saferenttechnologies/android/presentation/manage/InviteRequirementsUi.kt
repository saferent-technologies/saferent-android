package com.saferenttechnologies.android.presentation.manage

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.InviteAnswerRules
import com.saferenttechnologies.android.domain.model.InviteRequirements
import com.saferenttechnologies.android.domain.model.TenantDetailsView
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.common.ShimmerHost
import com.saferenttechnologies.android.presentation.common.BoneLine
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Invite form: "Ask the tenant" (answered when they accept). Never asks religion, caste, food, marital status, salary or ID numbers. */
@Composable
internal fun AskTheTenant(r: InviteRequirements, onChange: (InviteRequirements) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        Text(stringResource(R.string.inv_ask_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp)
        SwitchRow(stringResource(R.string.inv_ask_basic), stringResource(R.string.inv_ask_basic_note), r.askDetails) { onChange(r.copy(askDetails = it)) }

        Text(stringResource(R.string.inv_ask_company), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            listOf("OFF" to R.string.inv_company_off, "OPTIONAL" to R.string.inv_company_optional, "REQUIRED" to R.string.inv_company_required).forEach { (v, label) ->
                Chip(stringResource(label), selected = r.companyName == v, modifier = Modifier.weight(1f), maxLines = 1) { onChange(r.copy(companyName = v)) }
            }
        }
        Text(stringResource(R.string.inv_ask_references), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            listOf(0 to R.string.inv_ref_none, 1 to R.string.inv_ref_one, 2 to R.string.inv_ref_two).forEach { (n, label) ->
                Chip(stringResource(label), selected = r.references == n, modifier = Modifier.weight(1f), maxLines = 1) { onChange(r.copy(references = n)) }
            }
        }
        SwitchRow(stringResource(R.string.inv_ask_background), null, r.backgroundVerification) { onChange(r.copy(backgroundVerification = it)) }

        Text(stringResource(R.string.inv_ask_documents), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
        InviteAnswerRules.DOCUMENTS.forEach { key ->
            val checked = key in r.documents
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp))
                    .clickable(role = Role.Checkbox) { onChange(r.copy(documents = if (checked) r.documents - key else (r.documents + key).distinct().take(InviteAnswerRules.MAX_DOCUMENTS))) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked, onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = AuthColors.Orange, uncheckedColor = AuthColors.Border),
                )
                Text(stringResource(documentLabel(key)), color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp)
            }
        }
        Text(stringResource(R.string.inv_ask_privacy), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    }
}

@Composable
private fun SwitchRow(title: String, note: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
            note?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White))
    }
}

/** Owner: what the tenant answered when accepting (private). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TenantDetailsSheet(view: TenantDetailsView?, onClose: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.sdp, end = 20.sdp, bottom = 20.sdp)) {
            Text(stringResource(R.string.inv_tenant_details), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Spacer(Modifier.height(10.sdp))
            if (view == null) {
                ShimmerHost { Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) { repeat(6) { BoneLine(if (it % 2 == 0) 0.7f else 0.5f, 14.sdp) } } }
                return@Column
            }
            // House rule clashes: warning only.
            view.conflicts.forEach { c ->
                Text(
                    c, color = Amber, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.sdp).clip(RoundedCornerShape(10.sdp)).background(Amber.copy(alpha = 0.12f)).padding(10.sdp),
                )
            }
            val d = view.details
            if (d == null) {
                Text(stringResource(R.string.inv_details_none), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
            } else {
                Line(R.string.inv_you_are, d.tenantType.takeIf { it.isNotBlank() }?.let { stringResource(tenantTypeLabel(it)) })
                Line(R.string.inv_employment, d.employmentType.takeIf { it.isNotBlank() }?.let { stringResource(employmentLabel(it)) })
                Line(R.string.inv_company, d.companyName.ifBlank { null })
                Line(R.string.inv_people, listOfNotNull(d.adults?.let { stringResource(R.string.inv_adults_n, it) }, d.children?.takeIf { it > 0 }?.let { stringResource(R.string.inv_children_n, it) }).joinToString(", ").ifBlank { null })
                Line(R.string.inv_pets, d.hasPets?.let { if (it) listOf(stringResource(R.string.inv_yes), d.petDetails).filter { s -> s.isNotBlank() }.joinToString(": ") else stringResource(R.string.inv_no) })
                Line(R.string.inv_smokes, d.smokes?.let { stringResource(if (it) R.string.inv_yes else R.string.inv_no) })
                Line(R.string.inv_stay, d.stayMonths?.let { stringResource(R.string.inv_months_n, it) })
                if (d.backgroundConsentAt.isNotBlank()) Line(R.string.inv_background, stringResource(R.string.inv_background_given, prettyDate(d.backgroundConsentAt)))
                if (!d.leaseActive) {
                    Text(stringResource(R.string.inv_hidden_after_lease), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(vertical = 6.sdp))
                }
                if (d.references.isNotEmpty()) {
                    Spacer(Modifier.height(10.sdp))
                    Text(stringResource(R.string.inv_references), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                    d.references.forEach { ref ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.sdp).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ref.name, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                                Text(listOf(ref.relation, ref.phone).filter { it.isNotBlank() }.joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                            }
                            if (ref.phone.isNotBlank()) {
                                Box(
                                    Modifier.size(36.sdp).clip(CircleShape).background(AuthColors.OrangeSoft)
                                        .clickable(role = Role.Button) { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ref.phone}"))) } },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(painterResource(R.drawable.ic_call), stringResource(R.string.inv_call), tint = AuthColors.OrangeText, modifier = Modifier.size(18.sdp)) }
                            }
                        }
                    }
                }
            }
            if (view.documents.isNotEmpty()) {
                Spacer(Modifier.height(12.sdp))
                Text(stringResource(R.string.inv_documents), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                view.documents.forEach { doc ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
                        Text(doc.label.ifBlank { stringResource(documentLabel(doc.key)) }, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(if (doc.shared) R.string.inv_shared else R.string.inv_not_shared),
                            color = if (doc.shared) AuthColors.GreenText else AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (doc.shared) AuthColors.GreenBg else AuthColors.Surface).padding(horizontal = 10.sdp, vertical = 3.sdp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp)) {
        Text(stringResource(label), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(0.42f))
        Text(value, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(0.58f))
    }
}

fun documentLabel(key: String) = when (key) {
    "ID_PROOF" -> R.string.inv_doc_id
    "PAN" -> R.string.inv_doc_pan
    "EMPLOYMENT_OR_STUDENT_ID" -> R.string.inv_doc_work_id
    else -> R.string.inv_doc_other
}

fun tenantTypeLabel(t: String) = when (t) {
    "STUDENT" -> R.string.inv_tt_student
    "WORKING_PROFESSIONAL" -> R.string.inv_tt_working
    "FAMILY" -> R.string.inv_tt_family
    else -> R.string.inv_tt_other
}

fun employmentLabel(e: String) = when (e) {
    "SALARIED" -> R.string.inv_emp_salaried
    "SELF_EMPLOYED" -> R.string.inv_emp_self
    "STUDENT" -> R.string.inv_emp_student
    "NOT_WORKING" -> R.string.inv_emp_not_working
    else -> R.string.inv_emp_other
}
