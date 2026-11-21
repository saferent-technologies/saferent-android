package com.saferenttechnologies.android.presentation.safety

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.EmergencyContact
import com.saferenttechnologies.android.domain.model.EmergencyInfo
import com.saferenttechnologies.android.domain.model.ListingSafety
import com.saferenttechnologies.android.domain.model.SafetyPlace
import com.saferenttechnologies.android.domain.model.SafetyRules
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.manage.calendarDate
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// ======================= owner: safety form =======================

@Composable
fun SafetyScreen(state: SafetyState, effects: Flow<SafetyEffect>, onIntent: (SafetyIntent) -> Unit) {
    BackHandler { onIntent(SafetyIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.safety_saved)
    val uploaded = stringResource(R.string.safety_uploaded)
    LaunchedEffect(effects) {
        effects.collect { e ->
            if (e is SafetyEffect.Message && e.text.isNotBlank()) {
                snackbar.showSnackbar(
                    when (e.text) {
                        SafetyViewModel.SAVED -> saved
                        SafetyViewModel.UPLOADED -> uploaded
                        else -> e.text
                    },
                )
            }
        }
    }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar(stringResource(R.string.safety_title), state.title) { onIntent(SafetyIntent.Close) }
            val data = state.data
            when {
                data == null && state.error == null -> Box(Modifier.weight(1f)) { ListSkeleton(rows = 6, thumbnail = null, lines = 2) }
                data == null -> Box(Modifier.weight(1f)) { ErrorBox(state.error.orEmpty()) { onIntent(SafetyIntent.Retry) } }
                else -> {
                    SafetyForm(state, onIntent)
                    Box(Modifier.padding(horizontal = 16.sdp, vertical = 12.sdp)) {
                        AuthPrimaryButton(stringResource(R.string.safety_save), enabled = state.changed && !state.saving, loading = state.saving, onClick = { onIntent(SafetyIntent.Save) }, fontSize = 15.ssp)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.sdp, start = 16.sdp, end = 16.sdp))
    }
}

@Composable
private fun ColumnScope.SafetyForm(state: SafetyState, onIntent: (SafetyIntent) -> Unit) {
    val context = LocalContext.current
    val f = state.form
    val data = state.data ?: return
    val edit = !state.saving
    fun set(x: com.saferenttechnologies.android.domain.model.Safety) = onIntent(SafetyIntent.Edit(x))
    val problemTexts = state.problems.mapValues { (_, p) -> problemText(p) }
    fun e(path: String): String? = state.serverErrors[path] ?: if (state.showErrors) problemTexts[path] else null
    var datePicker by remember { mutableStateOf(false) }
    // Contact pick → which field gets the number ("security" or "contact.{i}").
    var contactFor by remember { mutableStateOf<String?>(null) }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        val target = contactFor
        contactFor = null
        if (res.resultCode == Activity.RESULT_OK && uri != null && target != null) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val phone = c.getString(0).orEmpty()
                        val name = c.getString(1).orEmpty()
                        if (target == "security") {
                            set(f.copy(securityPhone = phone, securityName = f.securityName.ifBlank { name }))
                        } else {
                            val i = target.substringAfter('.').toIntOrNull() ?: return@use
                            f.emergencyContacts.getOrNull(i)?.let { old -> onIntent(SafetyIntent.EditContact(i, old.copy(phone = phone, label = old.label.ifBlank { name }))) }
                        }
                    }
                }
            }
        }
    }
    fun pick(target: String) {
        contactFor = target
        runCatching { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        // Progress
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(14.sdp)) {
            Text(stringResource(R.string.safety_complete, data.score), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            Spacer(Modifier.height(8.sdp))
            LinearProgressIndicator(progress = { data.score / 100f }, color = AuthColors.Orange, trackColor = AuthColors.Border, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.sdp))
            Text(stringResource(R.string.safety_why), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }

        // Fire safety
        Section(stringResource(R.string.safety_fire))
        YesNo(stringResource(R.string.safety_extinguisher), f.fireExtinguisher, edit) { set(f.copy(fireExtinguisher = it)) }
        if (f.fireExtinguisher == true) {
            Input(
                stringResource(R.string.safety_extinguisher_count), f.fireExtinguisherCount?.toString().orEmpty(), edit,
                error = e("fireExtinguisherCount"), keyboard = KeyboardType.Number,
            ) { t -> set(f.copy(fireExtinguisherCount = t.filter(Char::isDigit).take(3).toIntOrNull())) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlineAction(
                    f.fireExtinguisherExpiry?.let { stringResource(R.string.safety_expiry_value, calendarDate(it)) } ?: stringResource(R.string.safety_expiry_pick),
                    R.drawable.ic_edit, enabled = edit, modifier = Modifier.weight(1f), compact = true,
                ) { datePicker = true }
                state.extinguisherState?.let { Spacer(Modifier.width(8.sdp)); StateChip(it) }
            }
            e("fireExtinguisherExpiry")?.let { ErrorLine(it) }
        }
        YesNo(stringResource(R.string.safety_smoke), f.smokeDetectors, edit) { set(f.copy(smokeDetectors = it)) }
        if (f.smokeDetectors == true) {
            Input(stringResource(R.string.safety_smoke_where), f.smokeDetectorLocations, edit, error = e("smokeDetectorLocations"), placeholder = stringResource(R.string.safety_smoke_hint)) { set(f.copy(smokeDetectorLocations = it)) }
        }
        StepRow(stringResource(R.string.safety_exits), f.emergencyExits, 0, SafetyRules.MAX_EXITS, edit) { set(f.copy(emergencyExits = it)) }
        e("emergencyExits")?.let { ErrorLine(it) }
        YesNo(stringResource(R.string.safety_staircase), f.emergencyStaircase, edit) { set(f.copy(emergencyStaircase = it)) }

        // First aid
        Section(stringResource(R.string.safety_first_aid_title))
        YesNo(stringResource(R.string.safety_first_aid), f.firstAidKit, edit) { set(f.copy(firstAidKit = it)) }
        if (f.firstAidKit == true) {
            Input(stringResource(R.string.safety_first_aid_where), f.firstAidLocation, edit, error = e("firstAidLocation"), placeholder = stringResource(R.string.safety_first_aid_hint)) { set(f.copy(firstAidLocation = it)) }
        }

        // Security
        Section(stringResource(R.string.safety_security))
        Input(stringResource(R.string.safety_security_name), f.securityName, edit, error = e("securityName")) { set(f.copy(securityName = it)) }
        PhoneRow(stringResource(R.string.safety_security_phone), f.securityPhone, edit, e("securityPhone"), { set(f.copy(securityPhone = it)) }) { pick("security") }
        YesNo(stringResource(R.string.safety_24x7), f.security24x7, edit) { set(f.copy(security24x7 = it)) }

        // Building emergency numbers
        Section(stringResource(R.string.safety_numbers))
        Text(stringResource(R.string.safety_numbers_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        f.emergencyContacts.forEachIndexed { i, c ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(10.sdp),
                verticalArrangement = Arrangement.spacedBy(6.sdp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Input(stringResource(R.string.safety_number_label), c.label, edit, error = e("emergencyContacts.$i.label"), placeholder = stringResource(R.string.safety_number_label_hint)) {
                            onIntent(SafetyIntent.EditContact(i, c.copy(label = it)))
                        }
                    }
                    IconCircle(R.drawable.ic_delete, stringResource(R.string.reg_delete), AuthColors.Error, edit) { onIntent(SafetyIntent.RemoveContact(i)) }
                }
                PhoneRow(stringResource(R.string.safety_number_phone), c.phone, edit, e("emergencyContacts.$i.phone"), { onIntent(SafetyIntent.EditContact(i, c.copy(phone = it))) }) { pick("contact.$i") }
            }
        }
        if (f.emergencyContacts.size < SafetyRules.MAX_CONTACTS) {
            OutlineAction(stringResource(R.string.safety_add_number), R.drawable.ic_add, enabled = edit, modifier = Modifier.fillMaxWidth(), compact = true) { onIntent(SafetyIntent.AddContact) }
        }
        e("emergencyContacts")?.let { ErrorLine(it) }

        // Nearest hospital / police
        PlaceForm(stringResource(R.string.safety_hospital), "nearestHospital", f.nearestHospital, edit, ::e, stringResource(R.string.safety_find_hospital), state.near) { set(f.copy(nearestHospital = it)) }
        PlaceForm(stringResource(R.string.safety_police), "nearestPoliceStation", f.nearestPoliceStation, edit, ::e, stringResource(R.string.safety_find_police), state.near) { set(f.copy(nearestPoliceStation = it)) }

        // Instructions
        Section(stringResource(R.string.safety_instructions))
        Input(stringResource(R.string.safety_instructions_label), f.instructions, edit, singleLine = false, error = e("instructions"), placeholder = stringResource(R.string.safety_instructions_hint)) { set(f.copy(instructions = it)) }

        // Certificates
        Section(stringResource(R.string.safety_certificates))
        Text(stringResource(R.string.safety_certificates_note), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        data.certificates.forEach { c -> CertificateRow(c, state.uploading, edit) { uri -> onIntent(SafetyIntent.Upload(c.docType, uri)) } }
        Spacer(Modifier.height(8.sdp))
    }
    if (datePicker) {
        ExpiryDatePicker(f.fireExtinguisherExpiry, onDismiss = { datePicker = false }) { d -> datePicker = false; set(f.copy(fireExtinguisherExpiry = d)) }
    }
}

@Composable
private fun PlaceForm(
    title: String,
    path: String,
    p: SafetyPlace,
    enabled: Boolean,
    e: (String) -> String?,
    findWhat: String,
    near: String,
    onChange: (SafetyPlace) -> Unit,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.ssp, modifier = Modifier.weight(1f).padding(top = 8.sdp))
        // Free Maps search near the property; the owner copies the name / phone back.
        Text(
            stringResource(R.string.safety_find_nearby), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp,
            modifier = Modifier.padding(top = 8.sdp).clip(RoundedCornerShape(8.sdp)).clickable(role = Role.Button) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SafetyRules.nearbySearchUrl(findWhat, near)))) }
            }.padding(6.sdp),
        )
    }
    Input(stringResource(R.string.safety_place_name), p.name, enabled, error = e("$path.name")) { onChange(p.copy(name = it)) }
    Input(stringResource(R.string.safety_place_phone), p.phone, enabled, error = e("$path.phone"), keyboard = KeyboardType.Phone) { onChange(p.copy(phone = it)) }
    Input(stringResource(R.string.safety_place_address), p.address, enabled, singleLine = false, error = e("$path.address")) { onChange(p.copy(address = it)) }
    Input(
        stringResource(R.string.safety_place_distance), p.distanceKm?.let { km -> if (km % 1.0 == 0.0) km.toInt().toString() else km.toString() }.orEmpty(), enabled,
        error = e("$path.distanceKm"), keyboard = KeyboardType.Decimal,
    ) { t ->
        val clean = t.filter { it.isDigit() || it == '.' }.let { s -> val dot = s.indexOf('.'); if (dot < 0) s.take(3) else s.substring(0, dot).take(3) + "." + s.substring(dot + 1).replace(".", "").take(1) }
        onChange(p.copy(distanceKm = clean.trimEnd('.').toDoubleOrNull()))
    }
}

@Composable
private fun CertificateRow(c: com.saferenttechnologies.android.domain.model.SafetyCertificate, uploading: String?, enabled: Boolean, onPicked: (String) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onPicked(it.toString()) } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(certificateLabel(c.docType)), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            Text(stringResource(R.string.safety_where_applicable), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            if (c.uploaded) {
                Spacer(Modifier.height(4.sdp))
                val (label, fg, bg) = when (c.status) {
                    "APPROVED" -> Triple(R.string.safety_cert_verified, AuthColors.GreenText, AuthColors.GreenBg)
                    "REJECTED" -> Triple(R.string.safety_cert_rejected, AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
                    else -> Triple(R.string.safety_cert_uploaded, Amber, Amber.copy(alpha = 0.12f))
                }
                Pill(stringResource(label), fg, bg)
            }
        }
        Spacer(Modifier.width(8.sdp))
        OutlineAction(
            stringResource(
                when {
                    uploading == c.docType -> R.string.safety_uploading
                    c.uploaded -> R.string.safety_replace
                    else -> R.string.safety_upload
                },
            ),
            R.drawable.ic_document, enabled = enabled && uploading == null, compact = true,
        ) { runCatching { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePicker(current: String?, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val utc = TimeZone.getTimeZone("UTC")
    fun ms(d: SimpleDate) = Calendar.getInstance(utc).apply { clear(); set(d.year, d.month - 1, d.day) }.timeInMillis
    val state = rememberDatePickerState(initialSelectedDateMillis = ms(current?.let { SimpleDate.parse(it) } ?: SimpleDate.today()))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPicked(SimpleDate.fromMillis(it).iso()) } ?: onDismiss() }) { Text(stringResource(R.string.insp_done), color = AuthColors.OrangeText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body) } },
    ) { DatePicker(state = state) }
}

// ======================= tenant: listing "Safety" =======================

/** Listing detail: features + verified certificates only (no phones / instructions). Hidden when null. */
@Composable
fun ListingSafetySection(s: ListingSafety?) {
    if (s == null || s.isEmpty) return
    val rows = buildList {
        when (s.fireExtinguisher) {
            true -> add(stringResource(if (s.fireExtinguisherState == "EXPIRED") R.string.safety_l_extinguisher_refill else R.string.safety_l_extinguisher) to (s.fireExtinguisherState != "EXPIRED"))
            false -> add(stringResource(R.string.safety_l_no_extinguisher) to false)
            null -> Unit
        }
        s.smokeDetectors?.let { add(stringResource(if (it) R.string.safety_l_smoke else R.string.safety_l_no_smoke) to it) }
        s.emergencyExits?.let { n -> add(androidx.compose.ui.res.pluralStringResource(R.plurals.safety_l_exits, n, n) to (n > 0)) }
        s.emergencyStaircase?.let { add(stringResource(if (it) R.string.safety_l_staircase else R.string.safety_l_no_staircase) to it) }
        s.firstAidKit?.let { add(stringResource(if (it) R.string.safety_l_first_aid else R.string.safety_l_no_first_aid) to it) }
        when (s.security) {
            "24X7" -> add(stringResource(R.string.safety_l_security_247) to true)
            "AVAILABLE" -> add(stringResource(R.string.safety_l_security) to true)
            "NONE" -> add(stringResource(R.string.safety_l_no_security) to false)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_shield_check), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
            Spacer(Modifier.width(8.sdp))
            Text(stringResource(R.string.safety_l_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
        }
        rows.forEach { (text, good) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (good) R.drawable.ic_check_circle else R.drawable.ic_block), null,
                    tint = if (good) AuthColors.GreenText else AuthColors.Muted, modifier = Modifier.size(18.sdp),
                )
                Spacer(Modifier.width(10.sdp))
                Text(text, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp)
            }
        }
        if (s.verifiedCertificates.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.sdp), modifier = Modifier.padding(top = 2.sdp)) {
                s.verifiedCertificates.take(3).forEach { d ->
                    Pill(stringResource(R.string.safety_l_cert_verified, stringResource(certificateShort(d))), AuthColors.GreenText, AuthColors.GreenBg)
                }
            }
        }
    }
}

// ======================= tenant: Emergency =======================

/** Red "Emergency" button (tenant home with an active lease, lease screen). */
@Composable
fun EmergencyButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Error).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.sdp, vertical = 14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_call), null, tint = Color.White, modifier = Modifier.size(20.sdp))
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.emergency_title), color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
            Text(stringResource(R.string.emergency_button_note), color = Color.White.copy(alpha = 0.9f), fontFamily = Inter, fontSize = 12.ssp)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = Color.White, modifier = Modifier.size(18.sdp))
    }
}

@Composable
fun EmergencyScreen(state: EmergencyState, onIntent: (EmergencyIntent) -> Unit) {
    BackHandler { onIntent(EmergencyIntent.Close) }
    val info = state.info
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.emergency_title), info?.propertyTitle.orEmpty()) { onIntent(EmergencyIntent.Close) }
        when {
            state.ended != null -> Box(Modifier.fillMaxSize().padding(24.sdp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.ended.ifBlank { stringResource(R.string.emergency_ended) }, color = AuthColors.Ink, fontFamily = Inter, fontSize = 15.ssp)
                    Spacer(Modifier.height(16.sdp))
                    // 112 still works without a lease.
                    DialButton(stringResource(R.string.emergency_call, "112"), "112")
                }
            }
            info == null && state.error == null -> Column(Modifier.padding(16.sdp)) {
                DialButton(stringResource(R.string.emergency_call, "112"), "112")
                ListSkeleton(rows = 5, thumbnail = null, lines = 2)
            }
            info == null -> Column(Modifier.fillMaxSize().padding(16.sdp)) {
                DialButton(stringResource(R.string.emergency_call, "112"), "112")
                Box(Modifier.weight(1f)) { ErrorBox(state.error.orEmpty()) { onIntent(EmergencyIntent.Refresh) } }
            }
            else -> EmergencyContent(info, state.offline, state.loading, onIntent)
        }
    }
}

@Composable
private fun EmergencyContent(info: EmergencyInfo, offline: Boolean, loading: Boolean, onIntent: (EmergencyIntent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp, vertical = 8.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        if (offline) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(Amber.copy(alpha = 0.12f)).padding(10.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.emergency_offline), color = Amber, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (loading) R.string.emergency_refreshing else R.string.add_property_retry), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.ssp,
                    modifier = Modifier.clip(RoundedCornerShape(6.sdp)).clickable(enabled = !loading, role = Role.Button) { onIntent(EmergencyIntent.Refresh) }.padding(4.sdp),
                )
            }
        }
        val main = info.helplines.firstOrNull { it.phone == "112" }?.phone ?: "112"
        DialButton(stringResource(R.string.emergency_call, main), main)
        if (info.address.isNotBlank()) {
            Text(stringResource(R.string.emergency_your_address, info.address), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }

        Section(stringResource(R.string.emergency_helplines))
        info.helplines.filter { it.phone != main }.forEach { h -> CallRow(h.label, h.phone, null) }

        if (info.securityPhone.isNotBlank() || info.securityName.isNotBlank()) {
            Section(stringResource(R.string.safety_security))
            CallRow(info.securityName.ifBlank { stringResource(R.string.safety_security) }, info.securityPhone, if (info.security24x7) stringResource(R.string.emergency_247) else null)
        }
        if (info.landlordPhone.isNotBlank() || info.landlordName.isNotBlank()) {
            Section(stringResource(R.string.emergency_landlord))
            CallRow(info.landlordName.ifBlank { stringResource(R.string.emergency_landlord) }, info.landlordPhone, null)
        }
        if (info.contacts.isNotEmpty()) {
            Section(stringResource(R.string.safety_numbers))
            info.contacts.forEach { c -> CallRow(c.label.ifBlank { c.phone }, c.phone, null) }
        }
        info.hospital?.let { PlaceCard(stringResource(R.string.safety_hospital), it) }
        info.police?.let { PlaceCard(stringResource(R.string.safety_police), it) }

        val facts = buildList {
            info.firstAidLocation?.let { add(stringResource(R.string.emergency_first_aid, it)) }
            if (info.extinguisherCount != null || info.extinguisherExpiry != null || info.extinguisherState != null) {
                add(
                    listOfNotNull(
                        info.extinguisherCount?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.emergency_extinguishers, it, it) } ?: stringResource(R.string.safety_l_extinguisher),
                        info.extinguisherExpiry?.let { stringResource(R.string.emergency_refill_by, calendarDate(it)) },
                    ).joinToString(" · "),
                )
            }
            info.smokeDetectorLocations?.let { add(stringResource(R.string.emergency_smoke, it)) }
            info.emergencyExits?.let { add(androidx.compose.ui.res.pluralStringResource(R.plurals.safety_l_exits, it, it)) }
            if (info.emergencyStaircase == true) add(stringResource(R.string.safety_l_staircase))
        }
        if (facts.isNotEmpty()) {
            Section(stringResource(R.string.emergency_in_building))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp),
                verticalArrangement = Arrangement.spacedBy(6.sdp),
            ) {
                facts.forEach { Text(it, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp) }
                info.extinguisherState?.let { StateChip(it) }
            }
        }
        if (info.instructions.isNotBlank()) {
            Section(stringResource(R.string.safety_instructions))
            Text(
                info.instructions, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp),
            )
        }
        Spacer(Modifier.height(16.sdp))
    }
}

/** Opens the dialer (ACTION_DIAL: no call permission). */
private fun dial(context: android.content.Context, phone: String) {
    val number = phone.filter { it.isDigit() || it == '+' }
    if (number.isNotEmpty()) runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
}

@Composable
private fun DialButton(text: String, phone: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp)).background(AuthColors.Error).clickable(role = Role.Button) { dial(context, phone) }.padding(vertical = 18.sdp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_call), null, tint = Color.White, modifier = Modifier.size(24.sdp))
        Spacer(Modifier.width(10.sdp))
        Text(text, color = Color.White, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
    }
}

@Composable
private fun CallRow(title: String, phone: String, note: String?) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
            .clickable(enabled = phone.isNotBlank(), role = Role.Button) { dial(context, phone) }.padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(phone.ifBlank { null }, note).joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        if (phone.isNotBlank()) {
            Box(Modifier.size(38.sdp).clip(CircleShape).background(AuthColors.Error.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_call), stringResource(R.string.inv_call), tint = AuthColors.Error, modifier = Modifier.size(18.sdp))
            }
        }
    }
}

@Composable
private fun PlaceCard(title: String, p: SafetyPlace) {
    val context = LocalContext.current
    Section(title)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp),
        verticalArrangement = Arrangement.spacedBy(4.sdp),
    ) {
        Text(
            listOfNotNull(p.name.ifBlank { null }, p.distanceKm?.let { stringResource(R.string.emergency_km, kmText(it)) }).joinToString(" · "),
            color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp,
        )
        if (p.address.isNotBlank()) Text(p.address, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(top = 4.sdp)) {
            if (p.phone.isNotBlank()) OutlineAction(stringResource(R.string.inv_call), R.drawable.ic_call, modifier = Modifier.weight(1f), compact = true) { dial(context, p.phone) }
            SafetyRules.directionsUrl(p)?.let { url ->
                OutlineAction(stringResource(R.string.visit_directions), R.drawable.ic_my_location, modifier = Modifier.weight(1f), compact = true) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }
        }
    }
}

private fun kmText(km: Double) = if (km % 1.0 == 0.0) km.toInt().toString() else String.format(Locale.US, "%.1f", km)

// ======================= small parts =======================

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
private fun ErrorLine(text: String) = Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)

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

/** Valid (green) / Expiring (amber) / Expired (red). */
@Composable
private fun StateChip(state: String) = when (state) {
    "EXPIRED" -> Pill(stringResource(R.string.safety_state_expired), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
    "EXPIRING" -> Pill(stringResource(R.string.safety_state_expiring), Amber, Amber.copy(alpha = 0.12f))
    else -> Pill(stringResource(R.string.safety_state_valid), AuthColors.GreenText, AuthColors.GreenBg)
}

/** Yes / No / Skip (Skip = not answered). */
@Composable
private fun YesNo(label: String, value: Boolean?, enabled: Boolean, onChange: (Boolean?) -> Unit) {
    Column {
        Text(label, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.padding(bottom = 6.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            Chip(stringResource(R.string.inv_yes), selected = value == true, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 1) { onChange(true) }
            Chip(stringResource(R.string.inv_no), selected = value == false, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 1) { onChange(false) }
            Chip(stringResource(R.string.safety_skip), selected = value == null, enabled = enabled, modifier = Modifier.weight(1f), maxLines = 1) { onChange(null) }
        }
    }
}

/** Number with + / −; "Not set" until the first tap. */
@Composable
private fun StepRow(label: String, value: Int?, min: Int, max: Int, enabled: Boolean, onChange: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.weight(1f))
        Row(Modifier.clip(RoundedCornerShape(10.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(10.sdp)), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled && value != null) { onChange(if (value == null || value <= min) null else value - 1) }
            Text(value?.toString() ?: stringResource(R.string.safety_not_set), color = if (value == null) AuthColors.Muted else AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp, modifier = Modifier.padding(horizontal = 10.sdp))
            StepButton("+", enabled && (value ?: -1) < max) { onChange(((value ?: (min - 1)) + 1).coerceIn(min, max)) }
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.sdp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.ssp)
    }
}

@Composable
private fun IconCircle(icon: Int, description: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.sdp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), description, tint = if (enabled) tint else AuthColors.Muted, modifier = Modifier.size(18.sdp))
    }
}

/** Phone field + "pick from contacts" (system picker, no contacts permission). */
@Composable
private fun PhoneRow(label: String, value: String, enabled: Boolean, error: String?, onChange: (String) -> Unit, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Input(label, value, enabled, error = error, keyboard = KeyboardType.Phone) { onChange(it.take(16)) } }
        Spacer(Modifier.width(8.sdp))
        Box(
            Modifier.size(44.sdp).clip(CircleShape).background(AuthColors.OrangeSoft).clickable(enabled = enabled, role = Role.Button, onClick = onPick),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_person), stringResource(R.string.inv_pick_contact), tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp)) }
    }
}

@Composable
private fun Input(label: String, value: String, enabled: Boolean, singleLine: Boolean = true, error: String? = null, placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        placeholder = placeholder?.let { p -> { Text(p, fontFamily = Inter) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 3, enabled = enabled, isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = propertyFieldColors(), shape = RoundedCornerShape(12.sdp), modifier = Modifier.fillMaxWidth(),
    )
}

private fun certificateLabel(docType: String) = when (docType) {
    "STRUCTURAL_SAFETY_CERTIFICATE" -> R.string.safety_cert_structural
    "FIRE_NOC" -> R.string.safety_cert_fire_noc
    else -> R.string.safety_cert_occupancy
}

private fun certificateShort(docType: String) = when (docType) {
    "STRUCTURAL_SAFETY_CERTIFICATE" -> R.string.safety_cert_structural_short
    "FIRE_NOC" -> R.string.safety_cert_fire_noc
    else -> R.string.safety_cert_occupancy_short
}

@Composable
private fun problemText(p: SafetyRules.Problem) = stringResource(
    when (p) {
        SafetyRules.Problem.EXPIRY_REQUIRED -> R.string.safety_err_expiry
        SafetyRules.Problem.COUNT -> R.string.safety_err_count
        SafetyRules.Problem.PHONE -> R.string.safety_err_phone
        SafetyRules.Problem.SECURITY_NAME -> R.string.safety_err_security_name
        SafetyRules.Problem.TOO_LONG -> R.string.safety_err_too_long
        SafetyRules.Problem.CONTACT_LABEL -> R.string.safety_err_label
        SafetyRules.Problem.DISTANCE -> R.string.safety_err_distance
    },
)
