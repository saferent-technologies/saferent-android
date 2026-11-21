package com.saferenttechnologies.android.presentation.invitation

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.InviteAnswerRules
import com.saferenttechnologies.android.domain.model.TenantAnswers
import com.saferenttechnologies.android.domain.model.TenantInvitation
import com.saferenttechnologies.android.domain.model.TenantReference
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.documentLabel
import com.saferenttechnologies.android.presentation.manage.employmentLabel
import com.saferenttechnologies.android.presentation.manage.tenantTypeLabel
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** "A few details for the owner": only the parts the invitation asks. Pre-filled from my last answers. */
@Composable
internal fun ColumnScope.AnswersContent(state: InvitationState, inv: TenantInvitation, onIntent: (InvitationIntent) -> Unit) {
    val req = inv.requirements ?: return
    BackHandler(enabled = !state.accepting) { onIntent(InvitationIntent.CloseAnswers) }
    val a = state.answers
    val edit = !state.accepting
    fun set(x: TenantAnswers) = onIntent(InvitationIntent.EditAnswers(x))
    @Composable fun e(path: String): String? = state.serverErrors[path] ?: if (state.showErrors) state.problems[path]?.let { problemText(it) } else null
    val context = LocalContext.current
    // Phone from contacts (a picked phone row is readable without the contacts permission).
    var pickFor by remember { mutableIntStateOf(-1) }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        val i = pickFor
        if (res.resultCode == Activity.RESULT_OK && uri != null && i >= 0) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val number = c.getString(0).orEmpty()
                        val name = c.getString(1).orEmpty()
                        val refs = a.references.mapIndexed { idx, r -> if (idx == i) r.copy(phone = number, name = r.name.ifBlank { name }) else r }
                        set(a.copy(references = refs))
                    }
                }
            }
        }
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        Text(stringResource(R.string.inv_answers_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
        Text(stringResource(R.string.inv_answers_note, inv.landlordName.ifBlank { stringResource(R.string.inv_the_owner) }), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)

        if (req.askDetails) {
            Label(stringResource(R.string.inv_you_are))
            InviteAnswerRules.TENANT_TYPES.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    row.forEach { t -> Chip(stringResource(tenantTypeLabel(t)), selected = a.tenantType == t, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { set(a.copy(tenantType = t)) } }
                }
            }
            e("details.tenantType")?.let { ErrorLine(it) }

            Label(stringResource(R.string.inv_employment))
            Dropdown(a.employmentType.takeIf { it.isNotBlank() }?.let { stringResource(employmentLabel(it)) }.orEmpty(), InviteAnswerRules.EMPLOYMENT_TYPES, edit, e("details.employmentType")) { set(a.copy(employmentType = it)) }

            Label(stringResource(R.string.inv_people))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inv_adults), color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                Stepper(a.adults, 1, 20, edit) { set(a.copy(adults = it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inv_children), color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                Stepper(a.children, 0, 20, edit) { set(a.copy(children = it)) }
            }

            Label(stringResource(R.string.inv_have_pet))
            YesNo(a.hasPets, edit) { set(a.copy(hasPets = it, petDetails = if (it) a.petDetails else "")) }
            e("details.hasPets")?.let { ErrorLine(it) }
            if (a.hasPets == true) Input(stringResource(R.string.inv_which_pet), a.petDetails, e("details.petDetails"), edit) { set(a.copy(petDetails = it)) }

            Label(stringResource(R.string.inv_do_smoke))
            YesNo(a.smokes, edit) { set(a.copy(smokes = it)) }
            e("details.smokes")?.let { ErrorLine(it) }

            Input(stringResource(R.string.inv_stay_months), a.stayMonths, e("details.stayMonths"), edit, keyboard = KeyboardType.Number) { set(a.copy(stayMonths = it)) }

            // Not blocking: answers that clash with the house rules.
            InviteAnswerRules.warnings(req, a, inv.houseRules).forEach { w ->
                Text(
                    when (w) {
                        InviteAnswerRules.Warning.PETS -> stringResource(R.string.inv_warn_pets)
                        InviteAnswerRules.Warning.SMOKING -> stringResource(R.string.inv_warn_smoking)
                        InviteAnswerRules.Warning.LOCK_IN -> stringResource(R.string.inv_warn_lock_in, inv.houseRules.lockInMonths ?: 0)
                    },
                    color = Amber, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(Amber.copy(alpha = 0.12f)).padding(10.sdp),
                )
            }
        }

        if (req.companyName != "OFF") {
            val required = InviteAnswerRules.companyRequired(req, a)
            Input(stringResource(if (required) R.string.inv_company_label else R.string.inv_company_label_optional), a.companyName, e("details.companyName"), edit, capitalization = KeyboardCapitalization.Words) { set(a.copy(companyName = it)) }
        }

        if (req.references > 0) {
            Label(stringResource(R.string.inv_references))
            a.references.forEachIndexed { i, ref ->
                fun setRef(r: TenantReference) = set(a.copy(references = a.references.mapIndexed { idx, old -> if (idx == i) r else old }))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp),
                    verticalArrangement = Arrangement.spacedBy(6.sdp),
                ) {
                    Text(stringResource(R.string.inv_reference_n, i + 1), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp)
                    Input(stringResource(R.string.inv_ref_name), ref.name, e("details.references.$i.name"), edit, capitalization = KeyboardCapitalization.Words) { setRef(ref.copy(name = it)) }
                    Input(stringResource(R.string.inv_ref_relation), ref.relation, e("details.references.$i.relation"), edit, capitalization = KeyboardCapitalization.Words) { setRef(ref.copy(relation = it)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Input(stringResource(R.string.inv_ref_phone), ref.phone, e("details.references.$i.phone"), edit, keyboard = KeyboardType.Phone) { setRef(ref.copy(phone = it)) } }
                        Spacer(Modifier.width(6.sdp))
                        Box(
                            Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft).clickable(enabled = edit, role = Role.Button) {
                                pickFor = i
                                runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_person), stringResource(R.string.inv_pick_contact), tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp)) }
                    }
                }
            }
            e("details.references")?.let { ErrorLine(it) }
        }

        if (req.backgroundVerification) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).clickable(enabled = edit, role = Role.Checkbox) { set(a.copy(backgroundConsent = !a.backgroundConsent)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = a.backgroundConsent, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = AuthColors.Orange, uncheckedColor = AuthColors.Border))
                Text(stringResource(R.string.inv_background_consent), color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp)
            }
            e("details.backgroundConsent")?.let { ErrorLine(it) }
        }

        if (req.documents.isNotEmpty()) {
            val labels = req.documents.map { k -> inv.documentOptions.firstOrNull { it.key == k }?.label?.ifBlank { null } ?: stringResource(documentLabel(k)) }
            Text(
                stringResource(R.string.inv_docs_after, labels.joinToString(", ")), color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface).padding(10.sdp),
            )
        }
        Text(stringResource(R.string.inv_answers_footer), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Spacer(Modifier.height(12.sdp))
    }
    Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
        AuthPrimaryButton(stringResource(R.string.inv_accept_with_details), enabled = !state.accepting, loading = state.accepting, onClick = { onIntent(InvitationIntent.SubmitAnswers) }, fontSize = 15.ssp)
    }
}

/** After accepting: share the documents the owner asked for (ticks per item; "Skip for now"). */
@Composable
internal fun ColumnScope.ShareDocsContent(state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    val lease = state.leaseId ?: return
    BackHandler { onIntent(InvitationIntent.FinishSharing) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        Text(stringResource(R.string.inv_share_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
        Text(stringResource(R.string.inv_share_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        state.shareDocs.forEach { opt ->
            val matching = state.vault?.documents.orEmpty().filter { it.type.uppercase() in opt.vaultTypes }
            val done = matching.any { it.sharedWith(lease) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, if (done) AuthColors.Green else AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(opt.label.ifBlank { stringResource(documentLabel(opt.key)) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                    if (done) Icon(painterResource(R.drawable.ic_check_circle), stringResource(R.string.inv_shared), tint = AuthColors.GreenText, modifier = Modifier.size(20.sdp))
                }
                if (state.vault == null) {
                    Text(stringResource(R.string.inv_loading_docs), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                } else if (matching.isEmpty()) {
                    Text(stringResource(R.string.inv_no_matching_doc), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                }
                // Document type only, never numbers.
                matching.forEach { doc ->
                    val shared = doc.sharedWith(lease)
                    Row(Modifier.fillMaxWidth().padding(top = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
                        Text(doc.typeLabel.ifBlank { doc.type }, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(if (shared) R.string.inv_unshare else R.string.inv_share),
                            color = if (state.sharing) AuthColors.Muted else if (shared) AuthColors.Body else AuthColors.OrangeText,
                            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
                            modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(enabled = !state.sharing, role = Role.Button) { onIntent(InvitationIntent.ToggleShare(doc.id, !shared)) }
                                .padding(horizontal = 8.sdp, vertical = 6.sdp),
                        )
                    }
                }
                Spacer(Modifier.height(6.sdp))
                OutlineAction(stringResource(R.string.inv_add_doc), R.drawable.ic_add, enabled = !state.sharing, modifier = Modifier.fillMaxWidth(), compact = true) { onIntent(InvitationIntent.AddDocument(opt)) }
            }
        }
        Spacer(Modifier.height(12.sdp))
    }
    Column(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
        val all = state.shareDocs.all { opt -> state.vault?.documents.orEmpty().any { it.type.uppercase() in opt.vaultTypes && it.sharedWith(lease) } }
        AuthPrimaryButton(stringResource(if (all) R.string.inv_share_done else R.string.inv_share_skip), enabled = true, loading = false, onClick = { onIntent(InvitationIntent.FinishSharing) }, fontSize = 15.ssp)
    }
}

@Composable
private fun Label(text: String) = Text(text, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.padding(top = 6.sdp))

@Composable
private fun ErrorLine(text: String) = Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)

@Composable
private fun YesNo(value: Boolean?, enabled: Boolean, onPick: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        Chip(stringResource(R.string.inv_yes), selected = value == true, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 1) { onPick(true) }
        Chip(stringResource(R.string.inv_no), selected = value == false, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 1) { onPick(false) }
    }
}

@Composable
private fun Stepper(value: String, min: Int, max: Int, enabled: Boolean, onChange: (String) -> Unit) {
    val n = value.toIntOrNull() ?: min
    Row(Modifier.clip(RoundedCornerShape(10.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(10.sdp)), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled && n > min) { onChange((n - 1).toString()) }
        Text(n.toString(), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.padding(horizontal = 12.sdp))
        StepButton("+", enabled && n < max) { onChange((n + 1).toString()) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.sdp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.ssp)
    }
}

@Composable
private fun Dropdown(shown: String, values: List<String>, enabled: Boolean, error: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = shown, onValueChange = {}, readOnly = true, enabled = false,
            placeholder = { Text(stringResource(R.string.inv_choose), fontFamily = Inter) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_chevron_down), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) },
            isError = error != null,
            supportingText = error?.let { e -> { Text(e, fontFamily = Inter, color = AuthColors.Error) } },
            colors = propertyFieldColors(), shape = RoundedCornerShape(12.sdp), modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.sdp)).clickable(enabled = enabled, role = Role.Button) { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(AuthColors.Card)) {
            values.forEach { v -> DropdownMenuItem(text = { Text(stringResource(employmentLabel(v)), fontFamily = Inter) }, onClick = { open = false; onPick(v) }) }
        }
    }
}

@Composable
private fun Input(
    label: String,
    value: String,
    error: String?,
    enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        singleLine = true, enabled = enabled, isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
        colors = propertyFieldColors(), shape = RoundedCornerShape(12.sdp), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun problemText(p: InviteAnswerRules.Problem): String = stringResource(
    when (p) {
        InviteAnswerRules.Problem.REQUIRED -> R.string.inv_err_required
        InviteAnswerRules.Problem.RANGE -> R.string.inv_err_range
        InviteAnswerRules.Problem.LENGTH -> R.string.inv_err_length
        InviteAnswerRules.Problem.PHONE -> R.string.owner_err_phone
        InviteAnswerRules.Problem.CONSENT -> R.string.inv_err_consent
    },
)
