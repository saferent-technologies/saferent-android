package com.saferenttechnologies.android.presentation.owner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AuthorityType
import com.saferenttechnologies.android.domain.model.OwnerType
import com.saferenttechnologies.android.domain.model.Ownership
import com.saferenttechnologies.android.domain.model.OwnershipRules
import com.saferenttechnologies.android.domain.model.Representative
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.common.Bone
import com.saferenttechnologies.android.presentation.common.BoneLine
import com.saferenttechnologies.android.presentation.common.ShimmerHost
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.SectionTitle
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Wizard "Ownership" step body (the wizard's own scroll + bottom bar around it). */
@Composable
fun OwnershipForm(state: OwnershipState, onIntent: (OwnershipIntent) -> Unit) {
    when {
        state.loading || state.propertyId == null -> OwnershipSkeleton()
        state.loadError != null -> Column(Modifier.fillMaxWidth().padding(vertical = 24.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.loadError, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
            Spacer(Modifier.height(12.sdp))
            OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh) { onIntent(OwnershipIntent.Retry) }
        }
        else -> Fields(state, onIntent)
    }
}

@Composable
private fun Fields(state: OwnershipState, onIntent: (OwnershipIntent) -> Unit) {
    val f = state.form
    val edit = state.canEdit && !state.saving
    // Local checks show after the first Save; server messages always (see errText).

    Hint(stringResource(R.string.own_private_note))
    if (!state.canEdit) {
        Spacer(Modifier.height(10.sdp))
        Note(stringResource(R.string.own_read_only), AuthColors.Body, AuthColors.Surface, R.drawable.ic_lock)
    }

    // 1. Who owns it
    Spacer(Modifier.height(16.sdp))
    SectionTitle(stringResource(R.string.own_who))
    Spacer(Modifier.height(8.sdp))
    listOf(
        OwnerType.INDIVIDUAL to (R.string.own_type_individual to R.string.own_type_individual_note),
        OwnerType.JOINT to (R.string.own_type_joint to R.string.own_type_joint_note),
        OwnerType.COMPANY to (R.string.own_type_company to R.string.own_type_company_note),
    ).forEach { (type, text) ->
        RadioCard(stringResource(text.first), stringResource(text.second), selected = f.ownerType == type, enabled = edit) { onIntent(OwnershipIntent.SetOwnerType(type)) }
        Spacer(Modifier.height(8.sdp))
    }
    ErrorLine(err(state, "ownerType") ?: err(state, "ownershipType"))

    // 2. Company: name + sole / joint
    if (f.ownerType == OwnerType.COMPANY) {
        Spacer(Modifier.height(8.sdp))
        Field(stringResource(R.string.own_company_name), f.companyName, err(state, "companyName"), edit, capitalization = KeyboardCapitalization.Words) { onIntent(OwnershipIntent.CompanyName(it)) }
        Spacer(Modifier.height(12.sdp))
        Text(stringResource(R.string.own_ownership), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
        Spacer(Modifier.height(6.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            Chip(stringResource(R.string.own_sole), selected = !f.isJoint, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(OwnershipIntent.SetOwnershipType("SOLE")) }
            Chip(stringResource(R.string.own_joint), selected = f.isJoint, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { onIntent(OwnershipIntent.SetOwnershipType("JOINT")) }
        }
    }

    // 3. Joint: my share + other owners + live total
    if (f.isJoint) {
        Spacer(Modifier.height(16.sdp))
        Field(stringResource(R.string.own_my_share), f.ownerSharePercent, err(state, "ownerSharePercent"), edit, keyboard = KeyboardType.Decimal) { onIntent(OwnershipIntent.MyShare(it)) }
        Spacer(Modifier.height(14.sdp))
        SectionTitle(stringResource(R.string.own_other_owners))
        Spacer(Modifier.height(8.sdp))
        f.coOwners.forEachIndexed { i, c ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp),
                verticalArrangement = Arrangement.spacedBy(8.sdp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.own_owner_n, i + 2), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                    if (edit && f.coOwners.size > 1) {
                        Box(
                            Modifier.size(32.sdp).clip(CircleShape).clickable(role = Role.Button) { onIntent(OwnershipIntent.RemoveCoOwner(i)) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.own_remove_owner), tint = AuthColors.Error, modifier = Modifier.size(18.sdp)) }
                    }
                }
                Field(stringResource(R.string.own_name), c.name, err(state, "coOwners.$i.name"), edit, capitalization = KeyboardCapitalization.Words) { onIntent(OwnershipIntent.EditCoOwner(i, c.copy(name = it))) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    Field(stringResource(R.string.own_relation_optional), c.relation, err(state, "coOwners.$i.relation"), edit, Modifier.weight(1f), capitalization = KeyboardCapitalization.Words) { onIntent(OwnershipIntent.EditCoOwner(i, c.copy(relation = it))) }
                    Field(stringResource(R.string.own_share), c.sharePercent, err(state, "coOwners.$i.sharePercent"), edit, Modifier.weight(0.7f), keyboard = KeyboardType.Decimal) { onIntent(OwnershipIntent.EditCoOwner(i, c.copy(sharePercent = it))) }
                }
                Field(stringResource(R.string.own_phone_optional), c.phone, err(state, "coOwners.$i.phone"), edit, keyboard = KeyboardType.Phone) { onIntent(OwnershipIntent.EditCoOwner(i, c.copy(phone = it.take(20)))) }
                Field(stringResource(R.string.own_email_optional), c.email, err(state, "coOwners.$i.email"), edit, keyboard = KeyboardType.Email) { onIntent(OwnershipIntent.EditCoOwner(i, c.copy(email = it.take(120)))) }
            }
            Spacer(Modifier.height(8.sdp))
        }
        if (edit && f.coOwners.size < OwnershipRules.MAX_CO_OWNERS) {
            OutlineAction(stringResource(R.string.own_add_owner), R.drawable.ic_add, modifier = Modifier.fillMaxWidth(), compact = true) { onIntent(OwnershipIntent.AddCoOwner) }
        }
        // Live total: green at 100 %, red otherwise.
        Spacer(Modifier.height(8.sdp))
        val total = OwnershipRules.total(OwnershipRules.normalized(f))
        val ok = OwnershipRules.sharesOk(OwnershipRules.normalized(f))
        Text(
            if (ok) stringResource(R.string.own_total_ok) else stringResource(R.string.own_total_bad, total?.stripTrailingZeros()?.toPlainString() ?: "0"),
            color = if (ok) AuthColors.GreenText else AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
        )
        state.serverErrors["coOwners"]?.let { ErrorLine(it) }
    }

    // 4. Someone manages it for the owner (always on for a company)
    Spacer(Modifier.height(18.sdp))
    val company = f.ownerType == OwnerType.COMPANY
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.own_rep_switch), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            Text(stringResource(if (company) R.string.own_rep_company_note else R.string.own_rep_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        Switch(
            checked = f.representative != null,
            onCheckedChange = { onIntent(OwnershipIntent.RepresentativeOn(it)) },
            enabled = edit && !company,
            colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = androidx.compose.ui.graphics.Color.White),
        )
    }
    ErrorLine(err(state, "representative"))
    f.representative?.let { r ->
        Spacer(Modifier.height(10.sdp))
        RepresentativeFields(state, r, edit, onIntent)
    }

    // 5. Power of Attorney document
    if (state.needsPoa) {
        Spacer(Modifier.height(14.sdp))
        PoaCard(state, onIntent)
    } else if (f.representative?.authorityType == AuthorityType.POWER_OF_ATTORNEY && state.powerOfAttorneyUploaded) {
        Spacer(Modifier.height(10.sdp))
        Note(stringResource(R.string.own_poa_done), AuthColors.GreenText, AuthColors.GreenBg, R.drawable.ic_check)
    }

    // 6. Save (Submit for review waits for this)
    if (state.canEdit) {
        Spacer(Modifier.height(18.sdp))
        com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton(
            text = stringResource(R.string.own_save),
            enabled = state.canSave,
            loading = state.saving,
            onClick = { onIntent(OwnershipIntent.Save) },
            fontSize = 15.ssp,
        )
        if (state.dirty) {
            Spacer(Modifier.height(6.sdp))
            Text(stringResource(R.string.own_save_first), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
    }
}

@Composable
private fun RepresentativeFields(state: OwnershipState, r: Representative, edit: Boolean, onIntent: (OwnershipIntent) -> Unit) {
    fun change(x: Representative) = onIntent(OwnershipIntent.EditRepresentative(x))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp),
        verticalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        Text(stringResource(R.string.own_rep_title), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
        Field(stringResource(R.string.own_name), r.name, errText(state, "representative.name"), edit, capitalization = KeyboardCapitalization.Words) { change(r.copy(name = it)) }
        Field(stringResource(R.string.own_phone), r.phone, errText(state, "representative.phone"), edit, keyboard = KeyboardType.Phone) { change(r.copy(phone = it.take(20))) }
        Field(stringResource(R.string.own_email_optional), r.email, errText(state, "representative.email"), edit, keyboard = KeyboardType.Email) { change(r.copy(email = it.take(120))) }
        Field(stringResource(R.string.own_rep_relation), r.relation, errText(state, "representative.relation"), edit, capitalization = KeyboardCapitalization.Words) { change(r.copy(relation = it)) }
        Text(stringResource(R.string.own_authority), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
        listOf(
            AuthorityType.POWER_OF_ATTORNEY to R.string.own_auth_poa,
            AuthorityType.BOARD_RESOLUTION to R.string.own_auth_board,
            AuthorityType.AUTHORIZATION_LETTER to R.string.own_auth_letter,
            AuthorityType.OTHER to R.string.own_auth_other,
        ).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                row.forEach { (type, label) ->
                    Chip(stringResource(label), selected = r.authorityType == type, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) { change(r.copy(authorityType = type)) }
                }
            }
        }
        ErrorLine(errText(state, "representative.authorityType"))
    }
}

/** "Upload the Power of Attorney document" with a file picker (JPG, PNG or PDF). View only for staff. */
@Composable
private fun PoaCard(state: OwnershipState, onIntent: (OwnershipIntent) -> Unit) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onIntent(OwnershipIntent.PoaPicked(it.toString())) }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.OrangeSoft)
            .border(1.sdp, AuthColors.Orange.copy(alpha = 0.5f), RoundedCornerShape(14.sdp)).padding(14.sdp),
    ) {
        Text(stringResource(R.string.own_poa_title), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
        Text(stringResource(R.string.own_poa_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Spacer(Modifier.height(10.sdp))
        if (state.uploadingPoa) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AuthColors.Orange, strokeWidth = 2.sdp, modifier = Modifier.size(18.sdp))
                Spacer(Modifier.width(8.sdp))
                Text(stringResource(R.string.own_poa_uploading), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            }
        } else {
            OutlineAction(stringResource(R.string.own_poa_upload), R.drawable.ic_document, enabled = !state.saving, modifier = Modifier.fillMaxWidth(), compact = true) {
                pick.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
            }
        }
    }
}

// ---------------- property detail card ----------------

/** Owner's property detail: who owns it (private, owner only). [onEdit] opens the wizard's Ownership step. */
@Composable
fun OwnershipCard(state: OwnershipState, onEdit: () -> Unit) {
    if (state.loading && state.saved == null) {
        ShimmerHost { Column(Modifier.fillMaxWidth().padding(vertical = 6.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) { BoneLine(0.4f, 16.sdp); BoneLine(0.8f); BoneLine(0.6f) } }
        return
    }
    if (state.loadError != null && state.saved == null) return
    val o = state.saved
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.sdp).clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.own_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 15.ssp, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_lock), null, tint = AuthColors.Muted, modifier = Modifier.size(14.sdp))
            Spacer(Modifier.width(4.sdp))
            Text(stringResource(R.string.own_private), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
        }
        Spacer(Modifier.height(6.sdp))
        if (o == null) {
            Text(stringResource(R.string.own_not_added), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        } else {
            Text(summary(o), color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
            if (o.isJoint) {
                Text(
                    (listOf(stringResource(R.string.own_you_share, o.ownerSharePercent)) + o.coOwners.map { "${it.name} ${it.sharePercent}%" }).joinToString(", "),
                    color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp,
                )
            }
            o.representative?.let { r ->
                Text(stringResource(R.string.own_rep_line, r.name, stringResource(authorityLabel(r.authorityType))), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
            if (state.needsPoa) {
                Spacer(Modifier.height(4.sdp))
                Text(stringResource(R.string.own_poa_title), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp)
            }
        }
        if (state.canEdit) {
            Spacer(Modifier.height(10.sdp))
            OutlineAction(stringResource(if (o == null) R.string.own_add else R.string.own_edit), modifier = Modifier.fillMaxWidth(), compact = true, onClick = onEdit)
        } else {
            Spacer(Modifier.height(6.sdp))
            Text(stringResource(R.string.own_read_only), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
        }
    }
}

@Composable
private fun summary(o: Ownership): String = when (o.ownerType) {
    OwnerType.INDIVIDUAL -> stringResource(R.string.own_sum_individual)
    OwnerType.JOINT -> stringResource(R.string.own_sum_joint, o.coOwners.size + 1)
    OwnerType.COMPANY -> stringResource(if (o.isJoint) R.string.own_sum_company_joint else R.string.own_sum_company, o.companyName)
}

private fun authorityLabel(t: AuthorityType) = when (t) {
    AuthorityType.POWER_OF_ATTORNEY -> R.string.own_auth_poa
    AuthorityType.BOARD_RESOLUTION -> R.string.own_auth_board
    AuthorityType.AUTHORIZATION_LETTER -> R.string.own_auth_letter
    AuthorityType.OTHER -> R.string.own_auth_other
}

// ---------------- small parts ----------------

/** Server message for [path] first; local check only after Save was tapped. */
@Composable
private fun errText(state: OwnershipState, path: String): String? =
    state.serverErrors[path] ?: if (state.showErrors) state.problems[path]?.let { localText(it) } else null

@Composable
private fun err(state: OwnershipState, path: String): String? = errText(state, path)

@Composable
private fun localText(p: OwnershipRules.Problem): String = stringResource(
    when (p) {
        OwnershipRules.Problem.REQUIRED -> R.string.own_err_required
        OwnershipRules.Problem.COMPANY_NAME -> R.string.own_err_company
        OwnershipRules.Problem.SHARE -> R.string.own_err_share
        OwnershipRules.Problem.SHARES_TOTAL -> R.string.own_err_total
        OwnershipRules.Problem.PHONE -> R.string.owner_err_phone
        OwnershipRules.Problem.RELATION_LENGTH -> R.string.own_err_relation
        OwnershipRules.Problem.NO_CO_OWNERS -> R.string.own_err_no_co_owners
        OwnershipRules.Problem.TOO_MANY -> R.string.own_err_too_many
    },
)

@Composable
private fun RadioCard(title: String, note: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp))
            .border(if (selected) 2.sdp else 1.sdp, if (selected) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.sdp).clip(CircleShape).border(2.sdp, if (selected) AuthColors.Orange else AuthColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(10.sdp).clip(CircleShape).background(AuthColors.Orange)) }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            Text(note, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ErrorLine(text: String?) {
    if (text != null) Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 4.sdp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
}

@Composable
internal fun Note(text: String, color: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color, icon: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(background).padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(16.sdp))
        Spacer(Modifier.width(8.sdp))
        Text(text, color = color, fontFamily = Inter, fontSize = 12.ssp)
    }
}

@Composable
private fun OwnershipSkeleton() {
    ShimmerHost {
        Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            BoneLine(0.9f)
            Spacer(Modifier.height(6.sdp))
            BoneLine(0.4f, 16.sdp)
            repeat(3) { Bone(Modifier.fillMaxWidth().height(58.sdp), RoundedCornerShape(14.sdp)) }
            BoneLine(0.5f, 16.sdp)
            Bone(Modifier.fillMaxWidth().height(48.sdp), RoundedCornerShape(12.sdp))
        }
    }
}
