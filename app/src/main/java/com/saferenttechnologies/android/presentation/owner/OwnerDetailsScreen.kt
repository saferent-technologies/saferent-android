package com.saferenttechnologies.android.presentation.owner

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.saferenttechnologies.android.domain.model.OwnerRules
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.FormSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.SectionTitle
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

/** Profile → "Owner details" (landlords only). Name / mobile / email read only; the rest is saved field by field. */
@Composable
fun OwnerDetailsScreen(state: OwnerDetailsState, effects: Flow<OwnerDetailsEffect>, onIntent: (OwnerDetailsIntent) -> Unit) {
    BackHandler { onIntent(OwnerDetailsIntent.Close) }
    val snackbar = remember { SnackbarHostState() }
    val codes = mapOf(
        OwnerDetailsViewModel.FILLED to stringResource(R.string.owner_location_filled),
        OwnerDetailsViewModel.NO_ADDRESS to stringResource(R.string.owner_location_no_address),
        OwnerDetailsViewModel.NO_FIX to stringResource(R.string.owner_location_no_fix),
    )
    LaunchedEffect(effects) { effects.collect { e -> if (e is OwnerDetailsEffect.Message) snackbar.showSnackbar(codes[e.text] ?: e.text) } }
    // Address empty → fill it from the current location (location permission through our popup first).
    val location = com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester(com.saferenttechnologies.android.domain.model.AppPermission.LOCATION)
    LaunchedEffect(state.original != null, state.autoLocated) {
        if (state.original != null && !state.autoLocated && com.saferenttechnologies.android.domain.model.OwnerRules.addressEmpty(state.form)) {
            location.request { granted -> onIntent(if (granted) OwnerDetailsIntent.UseLocation(auto = true) else OwnerDetailsIntent.SkipAutoLocation) }
        }
    }
    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(enabled = !state.saving, role = Role.Button) { onIntent(OwnerDetailsIntent.Close) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
                Spacer(Modifier.width(12.sdp))
                Text(stringResource(R.string.owner_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp)
            }
            when {
                state.original == null && state.loading -> FormSkeleton(Modifier.padding(horizontal = 20.sdp), fields = 7)
                state.original == null -> Column(Modifier.fillMaxSize().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.loadError ?: stringResource(R.string.add_property_error_generic), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
                    Spacer(Modifier.height(12.sdp))
                    OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh) { onIntent(OwnerDetailsIntent.Retry) }
                }
                else -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.sdp, vertical = 8.sdp)) {
                        Body(state, onIntent) { location.request { granted -> if (granted) onIntent(OwnerDetailsIntent.UseLocation()) } }
                        Spacer(Modifier.height(24.sdp))
                    }
                    Column(Modifier.padding(horizontal = 20.sdp, vertical = 12.sdp)) {
                        AuthPrimaryButton(
                            text = stringResource(R.string.owner_save),
                            enabled = state.canSave,
                            loading = state.saving,
                            onClick = { onIntent(OwnerDetailsIntent.Save) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.sdp))
    }
}

@Composable
private fun Body(state: OwnerDetailsState, onIntent: (OwnerDetailsIntent) -> Unit, onUseLocation: () -> Unit) {
    val f = state.form
    val edit = !state.saving
    fun set(field: String) = { v: String -> onIntent(OwnerDetailsIntent.Edit(field, v)) }

    // a) Read only header
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(14.sdp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.fullName, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp, modifier = Modifier.weight(1f, fill = false))
            if (f.nameLocked) {
                Spacer(Modifier.width(8.sdp))
                Icon(painterResource(R.drawable.ic_lock), null, tint = AuthColors.GreenText, modifier = Modifier.size(14.sdp))
                Spacer(Modifier.width(4.sdp))
                Text(stringResource(R.string.owner_verified_name), color = AuthColors.GreenText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp)
            }
        }
        if (f.phoneNumber.isNotBlank()) Text(f.phoneNumber, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        if (f.email.isNotBlank()) Text(f.email, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
    }

    // b) Alternate number
    Spacer(Modifier.height(18.sdp))
    SectionTitle(stringResource(R.string.owner_alt_title))
    Spacer(Modifier.height(8.sdp))
    Input(stringResource(R.string.owner_alt_phone), f.alternatePhone, err(state, "alternatePhone"), edit, keyboard = KeyboardType.Phone, onChange = set("alternatePhone"))

    // c) Communication address
    Spacer(Modifier.height(18.sdp))
    SectionTitle(stringResource(R.string.owner_address_title))
    Spacer(Modifier.height(8.sdp))
    // Fills street, city, state and pincode from the phone's location (device Geocoder); still editable.
    OutlineAction(
        stringResource(if (state.locating) R.string.owner_locating else R.string.owner_use_location), R.drawable.ic_my_location,
        enabled = edit && !state.locating, modifier = Modifier.fillMaxWidth(), compact = true, onClick = onUseLocation,
    )
    Spacer(Modifier.height(8.sdp))
    Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        Input(stringResource(R.string.owner_address), f.address, err(state, "address"), edit, capitalization = KeyboardCapitalization.Sentences, singleLine = false, onChange = set("address"))
        Input(stringResource(R.string.owner_city), f.city, err(state, "city"), edit, capitalization = KeyboardCapitalization.Words, onChange = set("city"))
        Dropdown(stringResource(R.string.owner_state), f.state, OwnerRules.STATES.map { it to it }, err(state, "state"), edit, onPick = set("state"))
        Input(stringResource(R.string.owner_pincode), f.pincode, err(state, "pincode"), edit, keyboard = KeyboardType.Number, onChange = set("pincode"))
    }

    // d) Preferences
    Spacer(Modifier.height(18.sdp))
    SectionTitle(stringResource(R.string.owner_prefs_title))
    Spacer(Modifier.height(8.sdp))
    Dropdown(stringResource(R.string.owner_language), f.preferredLanguage, OwnerRules.LANGUAGES, err(state, "preferredLanguage"), edit, onPick = set("preferredLanguage"))
    Spacer(Modifier.height(12.sdp))
    Text(stringResource(R.string.owner_contact_method), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
    Spacer(Modifier.height(6.sdp))
    val methods = listOf("CALL" to R.string.owner_cm_call, "SMS" to R.string.owner_cm_sms, "WHATSAPP" to R.string.owner_cm_whatsapp, "EMAIL" to R.string.owner_cm_email, "IN_APP" to R.string.owner_cm_in_app)
    methods.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(bottom = 8.sdp)) {
            row.forEach { (key, label) ->
                Chip(stringResource(label), selected = f.preferredContactMethod == key, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) {
                    onIntent(OwnerDetailsIntent.Edit("preferredContactMethod", if (f.preferredContactMethod == key) "" else key))
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    err(state, "preferredContactMethod")?.let { ErrorLine(it) }

    // e) Emergency contact
    Spacer(Modifier.height(18.sdp))
    SectionTitle(stringResource(R.string.owner_emergency_title))
    Spacer(Modifier.height(8.sdp))
    Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
        Input(stringResource(R.string.owner_ec_name), f.emergencyContactName, err(state, "emergencyContactName"), edit, capitalization = KeyboardCapitalization.Words, onChange = set("emergencyContactName"))
        Input(stringResource(R.string.owner_ec_phone), f.emergencyContactPhone, err(state, "emergencyContactPhone"), edit, keyboard = KeyboardType.Phone, onChange = set("emergencyContactPhone"))
    }
    Spacer(Modifier.height(10.sdp))
    Text(stringResource(R.string.owner_ec_relation), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
    Spacer(Modifier.height(6.sdp))
    val relations = OwnerRules.RELATIONS
    val custom = f.emergencyContactRelation.isNotBlank() && relations.none { it.equals(f.emergencyContactRelation, true) }
    var other by remember { mutableStateOf(custom) }
    (relations.map { it to it } + (OTHER to stringResource(R.string.owner_rel_other))).chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(bottom = 8.sdp)) {
            row.forEach { (key, label) ->
                val selected = if (key == OTHER) other || custom else !other && f.emergencyContactRelation.equals(key, true)
                Chip(label, selected = selected, enabled = edit, modifier = Modifier.weight(1f), maxLines = 1) {
                    if (key == OTHER) {
                        other = true
                        if (!custom) onIntent(OwnerDetailsIntent.Edit("emergencyContactRelation", ""))
                    } else {
                        other = false
                        onIntent(OwnerDetailsIntent.Edit("emergencyContactRelation", key))
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (other || custom) {
        Input(stringResource(R.string.owner_ec_relation_other), f.emergencyContactRelation, err(state, "emergencyContactRelation"), edit, capitalization = KeyboardCapitalization.Words, onChange = set("emergencyContactRelation"))
    } else {
        err(state, "emergencyContactRelation")?.let { ErrorLine(it) }
    }
}

private const val OTHER = "__other"

/** Server message under the field first; local checks only after Save was tapped. */
@Composable
private fun err(state: OwnerDetailsState, field: String): String? =
    state.serverErrors[field] ?: if (state.showErrors) state.problems[field]?.let { p ->
        stringResource(
            when (p) {
                OwnerRules.Problem.PHONE -> R.string.owner_err_phone
                OwnerRules.Problem.SAME_AS_MOBILE -> R.string.owner_err_same
                OwnerRules.Problem.TOO_LONG -> R.string.owner_err_too_long
                OwnerRules.Problem.PINCODE -> R.string.owner_err_pincode
                OwnerRules.Problem.NAME_LENGTH -> R.string.owner_err_name
                OwnerRules.Problem.RELATION_LENGTH -> R.string.own_err_relation
            },
        )
    } else null

@Composable
private fun Input(
    label: String,
    value: String,
    error: String?,
    enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Inter, maxLines = 1) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { e -> { Text(e, fontFamily = Inter) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
        colors = propertyFieldColors(),
        shape = RoundedCornerShape(12.sdp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Read only field that opens a list (state, language). [options] = value to label. */
@Composable
private fun Dropdown(label: String, value: String, options: List<Pair<String, String>>, error: String?, enabled: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label, fontFamily = Inter, maxLines = 1) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_chevron_down), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) },
            isError = error != null,
            supportingText = error?.let { e -> { Text(e, fontFamily = Inter, color = AuthColors.Error) } },
            colors = propertyFieldColors(),
            shape = RoundedCornerShape(12.sdp),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.sdp)).clickable(enabled = enabled, role = Role.Button) { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(AuthColors.Card)) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text, fontFamily = Inter, color = if (key == value) AuthColors.OrangeText else AuthColors.Ink) },
                    onClick = {
                        open = false
                        onPick(key)
                    },
                )
            }
        }
    }
}

/** Landlord home: shown while language, contact method or emergency phone is missing. */
@Composable
fun CompleteOwnerDetailsCard(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.OrangeSoft)
            .border(1.sdp, AuthColors.Orange.copy(alpha = 0.4f), RoundedCornerShape(14.sdp))
            .clickable(role = Role.Button, onClick = onOpen).padding(14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.sdp).clip(CircleShape).background(AuthColors.Orange), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_person), null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.owner_card_title), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            Text(stringResource(R.string.owner_card_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = AuthColors.Body, modifier = Modifier.size(18.sdp))
    }
}
