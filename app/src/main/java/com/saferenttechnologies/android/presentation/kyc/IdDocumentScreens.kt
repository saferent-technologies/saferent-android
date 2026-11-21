package com.saferenttechnologies.android.presentation.kyc

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.IdDocType
import com.saferenttechnologies.android.domain.model.IdForm
import com.saferenttechnologies.android.domain.model.IdRules
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** "Choose your ID": Aadhaar (offline e-KYC / e-Aadhaar) first, PAN / passport as backup, DigiLocker if on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdPickerSheet(digiLockerOff: Boolean, allows: (IdDocType) -> Boolean, hasTutorial: Boolean, onIntent: (KycIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(KycIntent.CloseIdPicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.sdp, end = 20.sdp, bottom = 16.sdp)) {
            Text(stringResource(R.string.kyc_choose_id_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Text(stringResource(R.string.kyc_choose_id_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            Spacer(Modifier.height(12.sdp))
            // Guide from SafeRent (video, PDF or picture) before choosing.
            if (hasTutorial) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 5.sdp).clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface)
                        .clickable(role = Role.Button) { onIntent(KycIntent.OpenTutorials) }.padding(14.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_play), null, tint = AuthColors.OrangeText, modifier = Modifier.size(18.sdp))
                    }
                    Spacer(Modifier.width(12.sdp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.kyc_tutorial), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                        Text(stringResource(R.string.kyc_tutorial_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), null, tint = AuthColors.Body, modifier = Modifier.size(18.sdp))
                }
            }
            // Only the IDs the server can check right now (all when it doesn't say).
            var first = true
            if (allows(IdDocType.AADHAAR_XML)) { IdOption(stringResource(R.string.kyc_id_aadhaar_xml), stringResource(R.string.kyc_id_aadhaar_xml_note), recommended = first) { onIntent(KycIntent.ChooseId(IdDocType.AADHAAR_XML)) }; first = false }
            if (allows(IdDocType.EAADHAAR_PDF)) { IdOption(stringResource(R.string.kyc_id_eaadhaar), stringResource(R.string.kyc_id_eaadhaar_note), recommended = first) { onIntent(KycIntent.ChooseId(IdDocType.EAADHAAR_PDF)) }; first = false }
            if (allows(IdDocType.PAN)) IdOption(stringResource(R.string.kyc_id_pan), stringResource(R.string.kyc_id_pan_note)) { onIntent(KycIntent.ChooseId(IdDocType.PAN)) }
            if (allows(IdDocType.PASSPORT)) IdOption(stringResource(R.string.kyc_id_passport), stringResource(R.string.kyc_id_passport_note)) { onIntent(KycIntent.ChooseId(IdDocType.PASSPORT)) }
            if (!digiLockerOff) {
                IdOption(stringResource(R.string.kyc_step_digilocker), stringResource(R.string.kyc_digilocker_body)) {
                    onIntent(KycIntent.CloseIdPicker)
                    onIntent(KycIntent.StartDigiLocker)
                }
            }
        }
    }
}

@Composable
private fun IdOption(title: String, note: String, recommended: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.sdp).clip(RoundedCornerShape(14.sdp))
            .border(1.sdp, if (recommended) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(role = Role.Button, onClick = onClick).padding(14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                if (recommended) {
                    Spacer(Modifier.width(8.sdp))
                    Text(
                        stringResource(R.string.kyc_recommended),
                        color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.ssp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AuthColors.OrangeSoft).padding(horizontal = 8.sdp, vertical = 2.sdp),
                    )
                }
            }
            Text(note, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = AuthColors.Body, modifier = Modifier.size(18.sdp))
    }
}

/** The chosen ID: how to get it + file / number / photos, then "Upload". */
@Composable
internal fun IdFormScreen(form: IdForm, busy: Boolean, onIntent: (KycIntent) -> Unit, onCamera: (side: String) -> Unit) {
    BackHandler(enabled = !busy) { onIntent(KycIntent.CloseIdForm) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        onIntent(KycIntent.IdFilePicked(uri.toString(), name, mime))
    }
    val err = form.showErrors
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(enabled = !busy, role = Role.Button) { onIntent(KycIntent.CloseIdForm) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Text(stringResource(form.type.title()), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.sdp), verticalArrangement = Arrangement.spacedBy(12.sdp)) {
            // How to get it.
            Text(
                stringResource(form.type.howTo()),
                color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp),
            )
            when (form.type) {
                IdDocType.AADHAAR_XML, IdDocType.EAADHAAR_PDF -> {
                    val xml = form.type == IdDocType.AADHAAR_XML
                    PickBox(
                        label = if (form.file == null) stringResource(if (xml) R.string.kyc_pick_zip else R.string.kyc_pick_pdf) else form.fileName.ifBlank { stringResource(R.string.kyc_file_chosen) },
                        done = form.file != null,
                        error = form.wrongFile || (err && form.file == null),
                        enabled = !busy,
                    ) { pick.launch(if (xml) arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream") else arrayOf("application/pdf")) }
                    if (form.wrongFile) {
                        Text(
                            stringResource(if (xml) R.string.kyc_wrong_zip else R.string.kyc_wrong_pdf),
                            color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp,
                        )
                    }
                    OutlinedTextField(
                        value = form.secret,
                        onValueChange = { onIntent(KycIntent.IdSecret(it)) },
                        label = { Text(stringResource(if (xml) R.string.kyc_share_code else R.string.kyc_pdf_password), fontFamily = Inter) },
                        singleLine = true,
                        enabled = !busy,
                        isError = err && !IdRules.secretOk(form.type, form.secret),
                        supportingText = { Text(stringResource(if (xml) R.string.kyc_share_code_hint else R.string.kyc_pdf_password_hint), fontFamily = Inter) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = if (xml) KeyboardType.NumberPassword else KeyboardType.Password),
                        colors = propertyFieldColors(),
                        shape = RoundedCornerShape(12.sdp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IdDocType.PAN, IdDocType.PASSPORT -> {
                    OutlinedTextField(
                        value = form.number,
                        onValueChange = { onIntent(KycIntent.IdNumber(it)) },
                        label = { Text(stringResource(if (form.type == IdDocType.PAN) R.string.kyc_pan_number else R.string.kyc_passport_number), fontFamily = Inter) },
                        singleLine = true,
                        enabled = !busy,
                        isError = err && !IdRules.numberOk(form.type, form.number),
                        supportingText = { Text(stringResource(if (form.type == IdDocType.PAN) R.string.kyc_pan_hint else R.string.kyc_passport_hint), fontFamily = Inter) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        colors = propertyFieldColors(),
                        shape = RoundedCornerShape(12.sdp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PickBox(
                        label = stringResource(if (form.front == null) (if (form.type == IdDocType.PAN) R.string.kyc_photo_pan else R.string.kyc_photo_passport_front) else R.string.kyc_photo_taken),
                        done = form.front != null,
                        error = err && form.front == null,
                        enabled = !busy,
                    ) { onCamera("front") }
                    if (form.type == IdDocType.PASSPORT) {
                        PickBox(
                            label = stringResource(if (form.back == null) R.string.kyc_photo_passport_back else R.string.kyc_photo_taken),
                            done = form.back != null,
                            error = err && form.back == null,
                            enabled = !busy,
                        ) { onCamera("back") }
                    }
                }
            }
            Text(stringResource(R.string.kyc_privacy_note), color = AuthColors.Muted, fontFamily = Inter, fontSize = 11.ssp)
        }
        Box(Modifier.padding(16.sdp)) {
            AuthPrimaryButton(
                text = stringResource(R.string.kyc_upload_id),
                enabled = IdRules.isComplete(form) && !busy,
                loading = busy,
                onClick = { onIntent(KycIntent.SubmitId) },
                fontSize = 15.ssp,
            )
        }
    }
}

@Composable
private fun PickBox(label: String, done: Boolean, error: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp))
            .border(1.sdp, if (error) AuthColors.Error else if (done) AuthColors.Green else AuthColors.Border, RoundedCornerShape(12.sdp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_add),
            null,
            tint = if (done) AuthColors.Green else AuthColors.OrangeText,
            modifier = Modifier.size(20.sdp),
        )
        Spacer(Modifier.width(10.sdp))
        Text(
            label, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp,
            maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f),
        )
    }
}

private fun IdDocType.title(): Int = when (this) {
    IdDocType.AADHAAR_XML -> R.string.kyc_id_aadhaar_xml
    IdDocType.EAADHAAR_PDF -> R.string.kyc_id_eaadhaar
    IdDocType.PAN -> R.string.kyc_id_pan
    IdDocType.PASSPORT -> R.string.kyc_id_passport
}

private fun IdDocType.howTo(): Int = when (this) {
    IdDocType.AADHAAR_XML -> R.string.kyc_howto_xml
    IdDocType.EAADHAAR_PDF -> R.string.kyc_howto_eaadhaar
    IdDocType.PAN -> R.string.kyc_howto_pan
    IdDocType.PASSPORT -> R.string.kyc_howto_passport
}
