package com.saferenttechnologies.android.presentation.compliance

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.ComplianceDocument
import com.saferenttechnologies.android.domain.model.ComplianceRules
import com.saferenttechnologies.android.domain.model.OwnershipDeclaration
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.calendarDate
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

private val Blue = Color(0xFF2563EB)

// ======================= owner: Compliance =======================

@Composable
fun ComplianceScreen(state: ComplianceState, effects: Flow<ComplianceEffect>, onIntent: (ComplianceIntent) -> Unit) {
    BackHandler { onIntent(ComplianceIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) { effects.collect { e -> if (e is ComplianceEffect.Message && e.text.isNotBlank()) snackbar.showSnackbar(e.text) } }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar(stringResource(R.string.comp_title), state.title) { onIntent(ComplianceIntent.Close) }
            val data = state.data
            when {
                data == null && state.error == null -> Box(Modifier.weight(1f)) { ListSkeleton(rows = 6, thumbnail = null, lines = 2) }
                data == null -> Box(Modifier.weight(1f)) { ErrorBox(state.error.orEmpty()) { onIntent(ComplianceIntent.Retry) } }
                else -> {
                    ComplianceForm(state, onIntent)
                    Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
                        AuthPrimaryButton(stringResource(R.string.comp_save), enabled = state.changed && !state.saving, loading = state.saving, onClick = { onIntent(ComplianceIntent.Save) }, fontSize = 15.ssp)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.sdp, start = 16.sdp, end = 16.sdp))
    }
}

@Composable
private fun ColumnScope.ComplianceForm(state: ComplianceState, onIntent: (ComplianceIntent) -> Unit) {
    val data = state.data ?: return
    val f = state.form
    val edit = !state.saving
    fun set(x: com.saferenttechnologies.android.domain.model.PropertyCompliance) = onIntent(ComplianceIntent.Edit(x))
    val problemTexts = state.problems.mapValues { (_, p) -> problemText(p) }
    fun e(path: String): String? = state.serverErrors[path] ?: if (state.showErrors) problemTexts[path] else null
    var approvalDate by remember { mutableStateOf(false) }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        // Expiring / expired documents.
        if (data.expiring.isNotEmpty()) {
            val expired = data.expiring.any { it.expiryState == "EXPIRED" }
            val fg = if (expired) AuthColors.Error else Amber
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(fg.copy(alpha = 0.1f)).padding(12.sdp), verticalArrangement = Arrangement.spacedBy(4.sdp)) {
                Text(stringResource(if (expired) R.string.comp_banner_expired else R.string.comp_banner_expiring), color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                data.expiring.forEach { x ->
                    Text(
                        "${docLabel(x.docType, x.name)}: ${expiryText(x.expiryState, x.expiryDate)}",
                        color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp,
                    )
                }
            }
        }

        // Ownership declaration
        DeclarationCard(data.declaration, state.agree, state.declaring, state.ownerOnly, onAgree = { onIntent(ComplianceIntent.Agree(it)) }) { onIntent(ComplianceIntent.Declare) }

        // Property tax
        Section(stringResource(R.string.comp_tax))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            ComplianceRules.TAX_STATUSES.forEach { k ->
                Chip(stringResource(taxLabel(k)), selected = f.propertyTaxStatus == k, enabled = edit, maxLines = 1) {
                    set(f.copy(propertyTaxStatus = if (f.propertyTaxStatus == k) null else k))
                }
            }
        }
        if (f.propertyTaxStatus == "PAID") {
            Label(stringResource(R.string.comp_tax_year))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                ComplianceRules.financialYears().forEach { y -> Chip(y, selected = f.propertyTaxPaidYear == y, enabled = edit, maxLines = 1) { set(f.copy(propertyTaxPaidYear = y)) } }
            }
            e("propertyTaxPaidYear")?.let { ErrorLine(it) }
            Input(stringResource(R.string.comp_tax_amount), f.propertyTaxAmount, edit, error = e("propertyTaxAmount"), keyboard = KeyboardType.Decimal, prefix = "₹ ") { set(f.copy(propertyTaxAmount = it)) }
        }
        e("propertyTaxStatus")?.let { ErrorLine(it) }

        // Municipal assessment number
        Section(stringResource(R.string.comp_assessment))
        Input(stringResource(R.string.comp_assessment_label), f.assessmentNumber, edit, error = e("assessmentNumber"), placeholder = stringResource(R.string.comp_assessment_hint)) { set(f.copy(assessmentNumber = it)) }

        // Building approval
        Section(stringResource(R.string.comp_approval))
        Input(stringResource(R.string.comp_approval_number), f.buildingApprovalNumber, edit, error = e("buildingApprovalNumber"), placeholder = stringResource(R.string.comp_approval_number_hint)) { set(f.copy(buildingApprovalNumber = it)) }
        Input(stringResource(R.string.comp_approval_authority), f.buildingApprovalAuthority, edit, error = e("buildingApprovalAuthority")) { set(f.copy(buildingApprovalAuthority = it)) }
        // Suggestions (free text stays possible).
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            ComplianceRules.AUTHORITIES.forEach { a -> Chip(a, selected = f.buildingApprovalAuthority.equals(a, true), enabled = edit, maxLines = 1) { set(f.copy(buildingApprovalAuthority = a)) } }
        }
        DateRow(stringResource(R.string.comp_approval_date), f.buildingApprovalDate, edit, onPick = { approvalDate = true }) { set(f.copy(buildingApprovalDate = null)) }
        e("buildingApprovalDate")?.let { ErrorLine(it) }

        // Documents checklist
        Section(stringResource(R.string.comp_documents))
        Text(stringResource(R.string.comp_documents_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        data.documents.forEach { d -> DocumentRow(d, state.uploading, edit) { uri, expiry -> onIntent(ComplianceIntent.Upload(d.docType, uri, expiry)) } }
        Spacer(Modifier.height(8.sdp))
    }
    if (approvalDate) {
        PickDate(f.buildingApprovalDate, pastOnly = true, onDismiss = { approvalDate = false }) { d -> approvalDate = false; set(f.copy(buildingApprovalDate = d)) }
    }
}

/** Full text; declared → green line; else checkbox + Declare (owner) / "Waiting for the owner" (manager). */
@Composable
fun DeclarationCard(d: OwnershipDeclaration, agree: Boolean, busy: Boolean, ownerOnly: Boolean, buttonText: String? = null, onAgree: (Boolean) -> Unit, onDeclare: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, if (d.current) AuthColors.GreenText else AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
        verticalArrangement = Arrangement.spacedBy(8.sdp),
    ) {
        Text(stringResource(R.string.comp_declaration), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp)
        Text(d.text, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
        when {
            d.current -> Text(
                stringResource(R.string.comp_declared_by, d.declaredBy.orEmpty().ifBlank { stringResource(R.string.comp_the_owner) }, d.declaredAt?.let { prettyDate(it) }.orEmpty()),
                color = AuthColors.GreenText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.sdp)).background(AuthColors.GreenBg).padding(10.sdp),
            )
            ownerOnly -> Text(
                stringResource(R.string.comp_waiting_owner), color = Amber, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.sdp)).background(Amber.copy(alpha = 0.12f)).padding(10.sdp),
            )
            else -> {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.sdp)).clickable(enabled = !busy, role = Role.Checkbox) { onAgree(!agree) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = agree, onCheckedChange = { onAgree(it) }, enabled = !busy, colors = CheckboxDefaults.colors(checkedColor = AuthColors.Orange))
                    Text(stringResource(R.string.comp_i_agree), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                }
                AuthPrimaryButton(buttonText ?: stringResource(R.string.comp_declare), enabled = agree && !busy, loading = busy, onClick = onDeclare, fontSize = 15.ssp)
            }
        }
    }
}

/** Wizard: the declaration before "Submit". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclarationSheet(
    declaration: OwnershipDeclaration,
    agree: Boolean,
    busy: Boolean,
    ownerOnly: Boolean,
    buttonText: String,
    onAgree: (Boolean) -> Unit,
    onDeclare: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            Text(stringResource(R.string.comp_declaration_sheet_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            DeclarationCard(declaration, agree, busy, ownerOnly, buttonText, onAgree, onDeclare)
        }
    }
}

@Composable
private fun DocumentRow(d: ComplianceDocument, uploading: String?, enabled: Boolean, onUpload: (uri: String, expiry: String?) -> Unit) {
    var expiry by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf(false) }
    var pickExpiry by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onUpload(it.toString(), expiry) }; expiry = null }
    fun openFiles() = runCatching { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.sdp)) {
            Text(docLabel(d.docType, d.label), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (d.whereApplicable) Text(stringResource(R.string.comp_where_applicable), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                if (d.uploaded) {
                    when (d.status) {
                        "APPROVED" -> Pill(stringResource(R.string.comp_doc_verified), AuthColors.GreenText, AuthColors.GreenBg)
                        "REJECTED" -> Pill(stringResource(R.string.comp_doc_rejected), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
                        else -> Pill(stringResource(R.string.comp_doc_uploaded), Blue, Blue.copy(alpha = 0.1f))
                    }
                }
                d.expiryDate?.let { date ->
                    val (fg, bg) = expiryColors(d.expiryState)
                    Pill(expiryText(d.expiryState, date), fg, bg)
                }
            }
        }
        Spacer(Modifier.width(8.sdp))
        OutlineAction(
            stringResource(
                when {
                    uploading == d.docType -> R.string.comp_uploading
                    d.uploaded -> R.string.comp_replace
                    else -> R.string.comp_upload
                },
            ),
            R.drawable.ic_document, enabled = enabled && uploading == null, compact = true,
        ) { if (d.docType in ComplianceRules.EXPIRY_DOCS) ask = true else openFiles() }
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(stringResource(R.string.comp_has_expiry), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.comp_has_expiry_note), fontFamily = Inter, color = AuthColors.Body) },
            confirmButton = { TextButton(onClick = { ask = false; pickExpiry = true }) { Text(stringResource(R.string.comp_add_expiry), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { ask = false; expiry = null; openFiles() }) { Text(stringResource(R.string.comp_no_expiry), color = AuthColors.Body, fontFamily = Inter) } },
            containerColor = AuthColors.Card,
        )
    }
    if (pickExpiry) {
        PickDate(d.expiryDate, pastOnly = false, onDismiss = { pickExpiry = false }) { date -> pickExpiry = false; expiry = date; openFiles() }
    }
}

// ======================= lease: agreement + police verification =======================

/** Lease detail (landlord and tenant): two cards + Save. Hidden until loaded; nothing on errors except a retry line. */
@Composable
fun LeaseComplianceSection(state: LeaseComplianceState, landlord: Boolean, onIntent: (LeaseComplianceIntent) -> Unit) {
    val data = state.data
    if (data == null) {
        if (state.error != null && state.error.isNotBlank()) {
            Text(
                stringResource(R.string.comp_lease_retry), color = AuthColors.OrangeText, fontFamily = Inter, fontSize = 13.ssp,
                modifier = Modifier.padding(top = 12.sdp).clip(RoundedCornerShape(8.sdp)).clickable(role = Role.Button) { state.leaseId?.let { onIntent(LeaseComplianceIntent.Load(it, state.propertyId)) } }.padding(6.sdp),
            )
        }
        return
    }
    val f = state.form
    val problemTexts = state.problems.mapValues { (_, p) -> problemText(p) }
    fun e(path: String): String? = state.serverErrors[path] ?: if (state.showErrors) problemTexts[path] else null
    fun set(x: com.saferenttechnologies.android.domain.model.LeaseCompliance) = onIntent(LeaseComplianceIntent.Edit(x))
    var dateFor by remember { mutableStateOf<String?>(null) }
    var uploadType by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val t = uploadType
        uploadType = null
        if (uri != null && t != null) onIntent(LeaseComplianceIntent.Upload(t, uri.toString()))
    }
    fun upload(type: String) { uploadType = type; runCatching { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) } }
    val estampError = stringResource(R.string.comp_err_estamp_no)
    val busy = state.saving
    val canUpload = landlord && state.propertyId.isNotBlank()

    Column(Modifier.fillMaxWidth().padding(top = 12.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        // Rental agreement
        Card {
            Text(stringResource(R.string.comp_agreement), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp)
            val a = f.agreement
            val editA = f.canEditAgreement && !busy
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                ComplianceRules.AGREEMENT_TYPES.forEach { t ->
                    Chip(stringResource(agreementLabel(t)), selected = a.type == t, enabled = editA || a.type == t, maxLines = 1) { if (editA) set(f.copy(agreement = a.copy(type = t))) }
                }
            }
            if (f.canEditAgreement) {
                if (a.type != null && a.type != "UNREGISTERED") {
                    Input(
                        stringResource(if (a.type == "E_STAMP") R.string.comp_estamp_no else if (a.type == "REGISTERED") R.string.comp_registration_no else R.string.comp_document_no),
                        a.registrationNumber, editA,
                        error = state.serverErrors["agreementRegistrationNumber"]
                            ?: e("agreementRegistrationNumber")?.let { msg -> if (a.type == "E_STAMP" && state.problems["agreementRegistrationNumber"] == ComplianceRules.Problem.AGREEMENT_NUMBER) estampError else msg },
                    ) { set(f.copy(agreement = a.copy(registrationNumber = it))) }
                    Input(stringResource(R.string.comp_office), a.office, editA, error = e("agreementOffice"), placeholder = stringResource(R.string.comp_office_hint)) { set(f.copy(agreement = a.copy(office = it))) }
                }
                DateRow(stringResource(R.string.comp_agreement_date), a.date, editA, onPick = { dateFor = "agreement" }) { set(f.copy(agreement = a.copy(date = null))) }
                e("agreementDate")?.let { ErrorLine(it) }
                Input(stringResource(R.string.comp_stamp_duty), a.stampDutyAmount, editA, error = e("stampDutyAmount"), keyboard = KeyboardType.Decimal, prefix = "₹ ") { set(f.copy(agreement = a.copy(stampDutyAmount = it))) }
            } else {
                // Read-only (tenant).
                if (a.type == null) Text(stringResource(R.string.comp_not_added), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                a.registrationNumber.takeIf { it.isNotBlank() }?.let { LabelValue(stringResource(if (a.type == "E_STAMP") R.string.comp_estamp_no else R.string.comp_registration_no), it) }
                a.office.takeIf { it.isNotBlank() }?.let { LabelValue(stringResource(R.string.comp_office), it) }
                a.date?.let { LabelValue(stringResource(R.string.comp_agreement_date), calendarDate(it)) }
                a.stampDutyAmount.takeIf { it.isNotBlank() }?.let { LabelValue(stringResource(R.string.comp_stamp_duty), "₹$it") }
            }
            if (canUpload) {
                OutlineAction(
                    stringResource(if (state.uploading == "RENTAL_AGREEMENT") R.string.comp_uploading else R.string.comp_upload_agreement), R.drawable.ic_document,
                    enabled = !busy && state.uploading == null, modifier = Modifier.fillMaxWidth(), compact = true,
                ) { upload("RENTAL_AGREEMENT") }
            }
        }

        // Police verification
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.comp_police), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, modifier = Modifier.weight(1f))
                PoliceChip(data.police.status)
            }
            val p = f.police
            val editP = f.canEditPolice && !busy
            if (f.canEditPolice) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
                    ComplianceRules.POLICE_STATUSES.forEach { s ->
                        Chip(stringResource(policeLabel(s)), selected = p.status == s, enabled = editP, maxLines = 1) {
                            // "Submitted" → today as the submitted date (changeable).
                            set(f.copy(police = p.copy(status = s, submittedOn = if (s == "SUBMITTED" && p.submittedOn == null) SimpleDate.today().iso() else p.submittedOn)))
                        }
                    }
                }
                if (p.status == "SUBMITTED" || p.status == "VERIFIED") {
                    Input(stringResource(R.string.comp_police_ref), p.reference, editP, error = e("policeVerificationRef"), placeholder = stringResource(R.string.comp_police_ref_hint)) { set(f.copy(police = p.copy(reference = it))) }
                    DateRow(stringResource(R.string.comp_submitted_on), p.submittedOn, editP, onPick = { dateFor = "submitted" }) { set(f.copy(police = p.copy(submittedOn = null))) }
                    e("policeVerificationSubmittedOn")?.let { ErrorLine(it) }
                }
                if (p.status == "VERIFIED") {
                    DateRow(stringResource(R.string.comp_verified_on), p.verifiedOn, editP, onPick = { dateFor = "verified" }) { set(f.copy(police = p.copy(verifiedOn = null))) }
                    e("policeVerificationVerifiedOn")?.let { ErrorLine(it) }
                }
            } else {
                p.reference.takeIf { it.isNotBlank() }?.let { LabelValue(stringResource(R.string.comp_police_ref), it) }
                p.submittedOn?.let { LabelValue(stringResource(R.string.comp_submitted_on), calendarDate(it)) }
                p.verifiedOn?.let { LabelValue(stringResource(R.string.comp_verified_on), calendarDate(it)) }
            }
            if (canUpload) {
                OutlineAction(
                    stringResource(if (state.uploading == "POLICE_VERIFICATION") R.string.comp_uploading else R.string.comp_upload_ack), R.drawable.ic_document,
                    enabled = !busy && state.uploading == null, modifier = Modifier.fillMaxWidth(), compact = true,
                ) { upload("POLICE_VERIFICATION") }
            }
            Text(stringResource(R.string.comp_police_tip), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        if (f.canEditAgreement || f.canEditPolice) {
            AuthPrimaryButton(stringResource(R.string.comp_save), enabled = state.changes.isNotEmpty() && !busy, loading = busy, onClick = { onIntent(LeaseComplianceIntent.Save) }, fontSize = 15.ssp)
        }
    }
    dateFor?.let { which ->
        val current = when (which) { "agreement" -> f.agreement.date; "submitted" -> f.police.submittedOn; else -> f.police.verifiedOn }
        PickDate(current, pastOnly = which != "agreement", onDismiss = { dateFor = null }) { d ->
            dateFor = null
            when (which) {
                "agreement" -> set(f.copy(agreement = f.agreement.copy(date = d)))
                "submitted" -> set(f.copy(police = f.police.copy(submittedOn = d)))
                else -> set(f.copy(police = f.police.copy(verifiedOn = d)))
            }
        }
    }
}

// ======================= small parts =======================

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
    verticalArrangement = Arrangement.spacedBy(8.sdp),
    content = content,
)

@Composable
private fun TopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Section(text: String) =
    Text(text, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, modifier = Modifier.padding(top = 8.sdp))

@Composable
private fun Label(text: String) = Text(text, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)

@Composable
private fun ErrorLine(text: String) = Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)

@Composable
private fun LabelValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Text(value, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
    }
}

@Composable
private fun ErrorBox(text: String, onRetry: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
    Spacer(Modifier.height(12.sdp))
    OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh, onClick = onRetry)
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) = Text(
    text, color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp, maxLines = 1,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.sdp, vertical = 3.sdp),
)

/** Not started amber, Submitted blue, Verified green, Not required grey. */
@Composable
private fun PoliceChip(status: String) = when (status) {
    "SUBMITTED" -> Pill(stringResource(R.string.comp_police_submitted), Blue, Blue.copy(alpha = 0.1f))
    "VERIFIED" -> Pill(stringResource(R.string.comp_police_verified), AuthColors.GreenText, AuthColors.GreenBg)
    "NOT_REQUIRED" -> Pill(stringResource(R.string.comp_police_not_required), AuthColors.Body, AuthColors.Surface)
    else -> Pill(stringResource(R.string.comp_police_not_started), Amber, Amber.copy(alpha = 0.12f))
}

@Composable
private fun expiryColors(state: String?): Pair<Color, Color> = when (state) {
    "EXPIRED" -> AuthColors.Error to AuthColors.Error.copy(alpha = 0.1f)
    "EXPIRING" -> Amber to Amber.copy(alpha = 0.12f)
    else -> AuthColors.GreenText to AuthColors.GreenBg
}

/** "Valid till …" / "Expires …" / "Expired …". */
@Composable
private fun expiryText(state: String?, date: String) = stringResource(
    when (state) {
        "EXPIRED" -> R.string.comp_expired_on
        "EXPIRING" -> R.string.comp_expires_on
        else -> R.string.comp_valid_till
    },
    calendarDate(date),
)

@Composable
private fun DateRow(label: String, value: String?, enabled: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    Column {
        Label(label)
        Spacer(Modifier.height(4.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), verticalAlignment = Alignment.CenterVertically) {
            OutlineAction(value?.let { calendarDate(it) } ?: stringResource(R.string.comp_pick_date), R.drawable.ic_edit, enabled = enabled, modifier = Modifier.weight(1f), compact = true, onClick = onPick)
            if (value != null && enabled) OutlineAction(stringResource(R.string.req_clear), compact = true, onClick = onClear)
        }
    }
}

@Composable
private fun Input(label: String, value: String, enabled: Boolean, error: String? = null, placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text, prefix: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        placeholder = placeholder?.let { p -> { Text(p, fontFamily = Inter) } },
        prefix = prefix?.let { p -> { Text(p, fontFamily = Inter) } },
        singleLine = true, enabled = enabled, isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = propertyFieldColors(), shape = RoundedCornerShape(12.sdp), modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDate(current: String?, pastOnly: Boolean, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val utc = TimeZone.getTimeZone("UTC")
    fun ms(d: SimpleDate) = Calendar.getInstance(utc).apply { clear(); set(d.year, d.month - 1, d.day) }.timeInMillis
    val today = ms(SimpleDate.today())
    val state = rememberDatePickerState(
        initialSelectedDateMillis = ms(current?.let { SimpleDate.parse(it) } ?: SimpleDate.today()),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !pastOnly || utcTimeMillis <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPicked(SimpleDate.fromMillis(it).iso()) } ?: onDismiss() }) { Text(stringResource(R.string.insp_done), color = AuthColors.OrangeText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
    ) { DatePicker(state = state) }
}

/** Server label first; ours as a fallback. */
@Composable
private fun docLabel(docType: String, server: String): String = server.ifBlank {
    when (docType) {
        "PROPERTY_TAX_RECEIPT" -> stringResource(R.string.comp_doc_tax)
        "BUILDING_PERMISSION" -> stringResource(R.string.comp_doc_building)
        "OCCUPANCY_CERTIFICATE" -> stringResource(R.string.comp_doc_occupancy)
        "FIRE_NOC" -> stringResource(R.string.comp_doc_fire_noc)
        "TRADE_LICENSE" -> stringResource(R.string.comp_doc_trade)
        "SOCIETY_NOC" -> stringResource(R.string.comp_doc_society)
        else -> docType.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

private fun taxLabel(k: String) = when (k) {
    "PAID" -> R.string.comp_tax_paid
    "PENDING" -> R.string.comp_tax_pending
    else -> R.string.comp_tax_na
}

private fun agreementLabel(t: String) = when (t) {
    "REGISTERED" -> R.string.comp_agr_registered
    "NOTARIZED" -> R.string.comp_agr_notarized
    "E_STAMP" -> R.string.comp_agr_estamp
    else -> R.string.comp_agr_unregistered
}

private fun policeLabel(s: String) = when (s) {
    "SUBMITTED" -> R.string.comp_police_submitted
    "VERIFIED" -> R.string.comp_police_verified
    "NOT_REQUIRED" -> R.string.comp_police_not_required
    else -> R.string.comp_police_not_started
}

@Composable
private fun problemText(p: ComplianceRules.Problem) = stringResource(
    when (p) {
        ComplianceRules.Problem.YEAR_REQUIRED -> R.string.comp_err_year
        ComplianceRules.Problem.YEAR_FORMAT -> R.string.comp_err_year_format
        ComplianceRules.Problem.AMOUNT -> R.string.comp_err_amount
        ComplianceRules.Problem.APPROVAL_NUMBER -> R.string.comp_err_approval
        ComplianceRules.Problem.FUTURE_DATE -> R.string.comp_err_future
        ComplianceRules.Problem.TOO_LONG -> R.string.comp_err_too_long
        ComplianceRules.Problem.AGREEMENT_NUMBER -> R.string.comp_err_agreement_no
        ComplianceRules.Problem.VERIFIED_DATE -> R.string.comp_err_verified_date
        ComplianceRules.Problem.VERIFIED_BEFORE -> R.string.comp_err_verified_before
    },
)
