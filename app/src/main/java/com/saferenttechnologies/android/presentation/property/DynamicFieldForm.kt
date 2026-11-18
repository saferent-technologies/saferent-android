package com.saferenttechnologies.android.presentation.property

import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.FieldDef
import com.saferenttechnologies.android.domain.model.FieldError
import com.saferenttechnologies.android.domain.model.FieldType
import com.saferenttechnologies.android.domain.model.FieldValidator
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.humanize
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter
import java.util.Calendar
import java.util.TimeZone

/**
 * Renders server-driven (or app-side) fields: text / textarea / numbers / boolean / disclosure /
 * select / date. Required fields get a "*". Values are strings; the data layer sends real types.
 */
@Composable
fun DynamicFieldForm(
    fields: List<FieldDef>,
    values: Map<String, String>,
    errors: Map<String, FieldError>,
    onChange: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Follow-up questions appear only when their parent answer needs them (ShownWhen).
    val shown = com.saferenttechnologies.android.domain.model.FormVisibility.visible(fields, values)
    // Keyboard "Next" moves to the next text field; only the last one shows "Done" and closes it.
    val lastTextKey = shown.lastOrNull { it.type.isTyped() }?.key
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.sdp)) {
        shown.forEach { field ->
            val label = fieldLabel(field) + if (FieldValidator.isRequired(field, values)) " *" else ""
            val value = values[field.key].orEmpty()
            val error = errors[field.key]?.let { fieldErrorText(it) }
            when (field.type) {
                FieldType.BOOLEAN -> BooleanField(label, value, enabled) { onChange(field.key, it) }
                FieldType.DISCLOSURE -> ChoiceGrid(label, field.options.ifEmpty { DISCLOSURE }, value, error, enabled) { onChange(field.key, it) }
                FieldType.SELECT -> SelectField(label, field.options, value, error, enabled) { onChange(field.key, it) }
                FieldType.DATE -> DateField(label, value, error, enabled) { onChange(field.key, it) }
                else -> TextInput(field, label, value, error, enabled, isLast = field.key == lastTextKey) { onChange(field.key, it) }
            }
        }
    }
}

private val DISCLOSURE = listOf("YES", "NO", "NOT_APPLICABLE", "NOT_DISCLOSED")

/** Fields drawn as a typed text box (not a toggle / choice / dropdown / date). */
private fun FieldType.isTyped(): Boolean =
    this != FieldType.BOOLEAN && this != FieldType.DISCLOSURE && this != FieldType.SELECT && this != FieldType.DATE

/** [locked] = a field the user can't change (greyed out). Select fields are drawn disabled but stay normal. */
@Composable
fun propertyFieldColors(locked: Boolean = false) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AuthColors.Orange,
    unfocusedBorderColor = AuthColors.Border,
    errorBorderColor = AuthColors.Error,
    focusedLabelColor = AuthColors.OrangeText,
    unfocusedLabelColor = AuthColors.Body,
    errorLabelColor = AuthColors.Error,
    focusedTextColor = AuthColors.Ink,
    unfocusedTextColor = AuthColors.Ink,
    disabledTextColor = if (locked) AuthColors.Body else AuthColors.Ink,
    disabledBorderColor = AuthColors.Border,
    disabledLabelColor = if (locked) AuthColors.Muted else AuthColors.Body,
    cursorColor = AuthColors.Orange,
    focusedContainerColor = AuthColors.Field,
    unfocusedContainerColor = AuthColors.Field,
    disabledContainerColor = if (locked) AuthColors.Surface else AuthColors.Field,
    errorContainerColor = AuthColors.Field,
    errorSupportingTextColor = AuthColors.Error,
)

@Composable
private fun TextInput(field: FieldDef, label: String, value: String, error: String?, enabled: Boolean, isLast: Boolean, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val multiLine = field.type == FieldType.TEXTAREA
    val keyboard = when {
        field.digitsOnly -> KeyboardType.Number // flat number: digits keyboard
        field.type == FieldType.INTEGER -> KeyboardType.Number
        field.type == FieldType.DECIMAL || field.type == FieldType.MONEY -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            val clean = if (field.digitsOnly) v.filter(Char::isDigit) else when (field.type) {
                FieldType.INTEGER -> v.filter { it.isDigit() || it == '-' }
                FieldType.DECIMAL, FieldType.MONEY -> v.filter { it.isDigit() || it == '.' }
                else -> v
            }
            onChange(field.maxLength?.let { clean.take(it) } ?: clean)
        },
        label = { Text(label, fontFamily = Inter) },
        singleLine = field.type != FieldType.TEXTAREA,
        minLines = if (field.type == FieldType.TEXTAREA) 3 else 1,
        enabled = enabled,
        isError = error != null,
        supportingText = if (error != null) { { Text(error, fontFamily = Inter) } } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            // Description keeps Enter for new lines; others: Next → next field, last → Done.
            imeAction = when {
                multiLine -> ImeAction.Default
                isLast -> ImeAction.Done
                else -> ImeAction.Next
            },
            capitalization = if (field.type == FieldType.TEXT || field.type == FieldType.TEXTAREA) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(
            onNext = { if (!focusManager.moveFocus(FocusDirection.Down)) keyboardController?.hide() },
            onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
            },
        ),
        shape = RoundedCornerShape(12.sdp),
        colors = propertyFieldColors(locked = !enabled),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BooleanField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val checked = value.equals("true", ignoreCase = true)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(AuthColors.Field)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
            .padding(horizontal = 14.sdp, vertical = 8.sdp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, color = AuthColors.Ink, fontFamily = Inter, fontSize = 15.ssp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onChange(it.toString()) },
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White),
        )
    }
}

/** Segmented-style grid (2 per row): disclosure YES / NO / Not applicable / Not disclosed, or small selects. */
@Composable
fun ChoiceGrid(label: String, options: List<String>, value: String, error: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    Column {
        Text(label, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
        Spacer(Modifier.height(8.sdp))
        Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
            options.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                    row.forEach { option ->
                        val selected = option == value
                        Text(
                            optionLabel(option),
                            color = if (selected) AuthColors.OrangeText else AuthColors.Ink,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.ssp,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.sdp))
                                .background(if (selected) AuthColors.OtpFilled else AuthColors.Field)
                                .border(
                                    1.sdp,
                                    when {
                                        selected -> AuthColors.Orange
                                        error != null -> AuthColors.Error
                                        else -> AuthColors.Border
                                    },
                                    RoundedCornerShape(10.sdp),
                                )
                                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onSelect(option) }
                                .padding(horizontal = 12.sdp, vertical = 12.sdp),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (error != null) {
            Text(error, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 4.sdp))
        }
    }
}

@Composable
fun SelectField(
    label: String,
    options: List<String>,
    value: String,
    error: String?,
    enabled: Boolean,
    /** Own text per option (else optionLabel). */
    labels: Map<String, String> = emptyMap(),
    placeholder: String? = null,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = if (value.isBlank()) "" else labels[value] ?: optionLabel(value),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label, fontFamily = Inter) },
            placeholder = placeholder?.let { { Text(it, fontFamily = Inter) } },
            trailingIcon = { Icon(painterResource(R.drawable.ic_chevron_down), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp)) },
            isError = error != null,
            supportingText = if (error != null) { { Text(error, fontFamily = Inter, color = AuthColors.Error) } } else null,
            shape = RoundedCornerShape(12.sdp),
            colors = propertyFieldColors(locked = !enabled),
            modifier = Modifier.fillMaxWidth(),
        )
        // Disabled fields don't take clicks → overlay opens the menu.
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.sdp))
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true },
        )
        // Non-focusable popup: picking an option doesn't take focus from the text field, so the
        // keyboard doesn't close and pop up again.
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = false)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labels[option] ?: optionLabel(option), fontFamily = Inter) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    label: String,
    value: String,
    error: String?,
    enabled: Boolean,
    /** Earliest pickable day (null = any). */
    notBefore: SimpleDate? = null,
    /** Shown under the box when there's no error. */
    helper: String? = null,
    /** Box text (null = the raw "YYYY-MM-DD"). */
    display: String? = null,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Box {
        OutlinedTextField(
            value = display ?: value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label, fontFamily = Inter) },
            placeholder = { Text("YYYY-MM-DD", fontFamily = Inter) },
            isError = error != null,
            supportingText = when {
                error != null -> { { Text(error, fontFamily = Inter, color = AuthColors.Error) } }
                helper != null -> { { Text(helper, fontFamily = Inter, color = AuthColors.Body) } }
                else -> null
            },
            shape = RoundedCornerShape(12.sdp),
            colors = propertyFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        // The calendar is a dialog (needs focus) → close the keyboard once, so it doesn't pop back up after.
        Box(
            Modifier.matchParentSize().clickable(enabled = enabled, role = Role.Button) {
                focusManager.clearFocus()
                open = true
            },
        )
    }
    if (open) {
        val utc = TimeZone.getTimeZone("UTC")
        val initial = SimpleDate.parse(value)?.let { d ->
            Calendar.getInstance(utc).run { clear(); set(d.year, d.month - 1, d.day); timeInMillis }
        }
        val minMillis = notBefore?.let { d -> Calendar.getInstance(utc).run { clear(); set(d.year, d.month - 1, d.day); timeInMillis } }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = minMillis == null || utcTimeMillis >= minMillis
                override fun isSelectableYear(year: Int) = notBefore == null || year >= notBefore.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance(utc).apply { timeInMillis = ms }
                        onChange(SimpleDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)).iso())
                    }
                    open = false
                }) { Text(stringResource(android.R.string.ok), color = AuthColors.OrangeText) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel), color = AuthColors.Body) } },
        ) { DatePicker(state = state) }
    }
}

/** Server label, or our string for app-side keys, or the humanized key. */
@Composable
fun fieldLabel(field: FieldDef): String {
    if (field.label != field.key) return field.label
    return localFieldLabel(field.key)?.let { stringResource(it) } ?: humanize(field.key)
}

/** Option keys → readable text (YES → Yes, NOT_APPLICABLE → Not applicable, FOUR_SHARING → Four sharing). */
@Composable
fun optionLabel(option: String): String = when (option) {
    "YES" -> stringResource(R.string.option_yes)
    "NO" -> stringResource(R.string.option_no)
    "NOT_APPLICABLE" -> stringResource(R.string.option_not_applicable)
    "NOT_DISCLOSED" -> stringResource(R.string.option_not_disclosed)
    "BIKE" -> stringResource(R.string.option_parking_bike)
    "CAR" -> stringResource(R.string.option_parking_car)
    "BOTH" -> stringResource(R.string.option_parking_both)
    "NONE" -> stringResource(R.string.option_parking_none)
    else -> if (option.any { it.isLowerCase() }) option else humanize(option)
}

@Composable
fun fieldErrorText(error: FieldError): String = when (error) {
    FieldError.Required -> stringResource(R.string.field_error_required)
    FieldError.NotANumber -> stringResource(R.string.field_error_number)
    is FieldError.TooLong -> stringResource(R.string.field_error_too_long, error.max)
    is FieldError.BelowMin -> stringResource(R.string.field_error_min, error.min.pretty())
    is FieldError.AboveMax -> stringResource(R.string.field_error_max, error.max.pretty())
    is FieldError.Server -> error.message
}

private fun Double.pretty(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun localFieldLabel(key: String): Int? = when (key) {
    "title" -> R.string.prop_field_title
    "description" -> R.string.prop_field_description
    "addressLine" -> R.string.prop_field_address_line
    "locality" -> R.string.prop_field_locality
    "city" -> R.string.prop_field_city
    "state" -> R.string.prop_field_state
    "pincode" -> R.string.prop_field_pincode
    "unitNumber" -> R.string.prop_field_unit_number
    "roomNumber" -> R.string.prop_field_room_number
    "floorNumber" -> R.string.prop_field_floor_number
    "sharingType" -> R.string.prop_field_sharing_type
    "customSharing" -> R.string.prop_field_custom_sharing
    "attachedBathroom" -> R.string.prop_field_attached_bathroom
    "acAvailable" -> R.string.prop_field_ac_available
    "rentPerBed" -> R.string.prop_field_rent_per_bed
    "securityDeposit" -> R.string.prop_field_security_deposit
    "monthlyRent" -> R.string.prop_field_monthly_rent
    "advanceRentMonths" -> R.string.prop_field_advance_rent_months
    "rentDueDay" -> R.string.prop_field_rent_due_day
    "lockInPeriodMonths" -> R.string.prop_field_lock_in_months
    "noticePeriodDays" -> R.string.prop_field_notice_days
    "rentNegotiable" -> R.string.prop_field_rent_negotiable
    "agreementCharges" -> R.string.prop_field_agreement_charges
    "agreementChargesPaidBy" -> R.string.prop_field_agreement_paid_by
    "separateElectricityMeter" -> R.string.prop_field_separate_meter
    "electricityBillingType" -> R.string.prop_field_electricity_billing
    "meterPaymentType" -> R.string.prop_field_meter_payment
    "waterSupplyType" -> R.string.prop_field_water_supply
    "waterChargesType" -> R.string.prop_field_water_charges
    "powerBackup" -> R.string.prop_field_power_backup
    "internetAvailable" -> R.string.prop_field_internet
    "meterNumber" -> R.string.prop_field_meter_number
    "electricityRatePerUnit" -> R.string.prop_field_rate_per_unit
    "separateWaterMeter" -> R.string.prop_field_separate_water_meter
    "waterChargesAmount" -> R.string.prop_field_water_amount
    "waterAvailability" -> R.string.prop_field_water_availability
    "gasConnectionType" -> R.string.prop_field_gas
    "internetCharges" -> R.string.prop_field_internet_charges
    "generatorAvailable" -> R.string.prop_field_generator
    "sewageFacilities" -> R.string.prop_field_sewage
    "wasteCollection" -> R.string.prop_field_waste
    "notes" -> R.string.prop_field_utility_notes
    "maximumGuestStayDays" -> R.string.prop_field_max_guest_days
    "guestRegistrationRequired" -> R.string.prop_field_guest_registration
    "visitorTimingRestrictions" -> R.string.prop_field_visitor_timings
    "landlordApprovalRequired" -> R.string.prop_field_landlord_approval
    "guestParkingAvailable" -> R.string.prop_field_guest_parking
    "guestChargesApplicable" -> R.string.prop_field_guest_charges
    "guestChargeAmount" -> R.string.prop_field_guest_charge_amount
    "partiesAllowed" -> R.string.prop_field_parties
    "cookingAllowed" -> R.string.prop_field_cooking
    "sublettingAllowed" -> R.string.prop_field_subletting
    "workFromHomeAllowed" -> R.string.prop_field_wfh
    "loudMusicPolicy" -> R.string.prop_field_loud_music
    "lateFeePolicy" -> R.string.prop_field_late_fee
    "rentIncreasePolicy" -> R.string.prop_field_rent_increase
    "refundPolicy" -> R.string.prop_field_refund_policy
    "depositUsableAsRent" -> R.string.prop_field_deposit_as_rent
    "otherTerms" -> R.string.prop_field_other_terms
    "guestsAllowed" -> R.string.prop_field_guests
    "overnightGuestsAllowed" -> R.string.prop_field_overnight_guests
    "petsAllowed" -> R.string.prop_field_pets
    "smokingAllowed" -> R.string.prop_field_smoking
    else -> null
}
