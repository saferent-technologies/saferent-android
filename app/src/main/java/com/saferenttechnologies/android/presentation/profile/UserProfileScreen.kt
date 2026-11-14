package com.saferenttechnologies.android.presentation.profile

import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import android.content.res.Configuration
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.domain.model.UserProfileField
import com.saferenttechnologies.android.domain.model.UserProfileValidator
import com.saferenttechnologies.android.domain.model.Gender
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.domain.usecase.SaveUserProfileUseCase
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import java.util.Calendar
import java.util.TimeZone

/** Profile details after sign-in (spec: name, role, gender, DOB 18+, city, state, optional email…). */
@Composable
fun UserProfileScreen(state: UserProfileState, onIntent: (UserProfileIntent) -> Unit) {
    val form = state.form
    val errors = state.fieldErrors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.sdp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.sdp)) {
                if (state.skippable && !state.registrationRequired) {
                    Text(
                        if (state.isSkipping) "Skipping…" else "Skip",
                        color = AuthColors.Body,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.ssp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(8.sdp))
                            .clickable(enabled = !state.busy, role = Role.Button) { onIntent(UserProfileIntent.Skip) }
                            .padding(12.sdp),
                    )
                }
            }
            Spacer(Modifier.height(8.sdp))
            Text(
                "Tell us about yourself",
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.ssp,
                lineHeight = 34.ssp,
            )
            Spacer(Modifier.height(8.sdp))
            Text(
                "A few quick details help us personalise SafeRent for you.",
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 15.ssp,
                lineHeight = 22.ssp,
            )

            Spacer(Modifier.height(24.sdp))
            SectionLabel("I am a")
            Row(horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                RoleCard(
                    title = "Landlord",
                    subtitle = "I own / manage homes",
                    icon = R.drawable.ic_building,
                    selected = form.role == UserRole.LANDLORD,
                    isError = UserProfileField.ROLE in errors,
                    onClick = { onIntent(UserProfileIntent.RoleSelected(UserRole.LANDLORD)) },
                    modifier = Modifier.weight(1f),
                )
                RoleCard(
                    title = "Tenant",
                    subtitle = "I'm looking to rent",
                    icon = R.drawable.ic_home_outline,
                    selected = form.role == UserRole.TENANT,
                    isError = UserProfileField.ROLE in errors,
                    onClick = { onIntent(UserProfileIntent.RoleSelected(UserRole.TENANT)) },
                    modifier = Modifier.weight(1f),
                )
            }
            ErrorText(errors[UserProfileField.ROLE])

            Spacer(Modifier.height(20.sdp))
            Field(
                label = "Full name",
                value = form.fullName,
                error = errors[UserProfileField.FULL_NAME],
                onChange = { onIntent(UserProfileIntent.NameChanged(it)) },
                capitalization = KeyboardCapitalization.Words,
            )

            Spacer(Modifier.height(20.sdp))
            SectionLabel("Gender")
            GenderGrid(
                selected = form.gender,
                isError = UserProfileField.GENDER in errors,
                onSelect = { onIntent(UserProfileIntent.GenderSelected(it)) },
            )
            ErrorText(errors[UserProfileField.GENDER])

            Spacer(Modifier.height(20.sdp))
            DateField(
                value = form.dateOfBirth,
                error = errors[UserProfileField.DATE_OF_BIRTH],
                onClick = { onIntent(UserProfileIntent.OpenDatePicker) },
            )

            Spacer(Modifier.height(16.sdp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                Field(
                    label = "City",
                    value = form.city,
                    error = errors[UserProfileField.CITY],
                    onChange = { onIntent(UserProfileIntent.CityChanged(it)) },
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.weight(1f),
                )
                Field(
                    label = "State",
                    value = form.state,
                    error = errors[UserProfileField.STATE],
                    onChange = { onIntent(UserProfileIntent.StateChanged(it)) },
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.sdp))
            Field(
                label = "Email (optional)",
                value = form.email,
                error = errors[UserProfileField.EMAIL],
                onChange = { onIntent(UserProfileIntent.EmailChanged(it)) },
                keyboardType = KeyboardType.Email,
            )

            if (state.showOccupation) {
                Spacer(Modifier.height(16.sdp))
                Field(
                    label = "Occupation (optional)",
                    value = form.occupation,
                    error = errors[UserProfileField.OCCUPATION],
                    onChange = { onIntent(UserProfileIntent.OccupationChanged(it)) },
                    capitalization = KeyboardCapitalization.Sentences,
                )
            }
            if (state.showReferral) {
                Spacer(Modifier.height(16.sdp))
                Field(
                    label = "Referral code (optional)",
                    value = form.referralCode,
                    error = errors[UserProfileField.REFERRAL_CODE],
                    onChange = { onIntent(UserProfileIntent.ReferralChanged(it)) },
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                )
            }
            Spacer(Modifier.height(24.sdp))
        }

        Column(Modifier.padding(start = 24.sdp, end = 24.sdp, bottom = 16.sdp, top = 8.sdp)) {
            state.banner?.let {
                Text(
                    it,
                    color = AuthColors.Error,
                    fontFamily = Inter,
                    fontSize = 13.ssp,
                    modifier = Modifier.padding(bottom = 10.sdp),
                )
            }
            if (state.consentMissing) {
                // New account without Terms + Privacy → tick them on the login screen again.
                Text(
                    "Back to login",
                    color = AuthColors.OrangeText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.ssp,
                    modifier = Modifier
                        .padding(bottom = 10.sdp)
                        .clip(RoundedCornerShape(8.sdp))
                        .clickable(role = Role.Button) { onIntent(UserProfileIntent.BackToLogin) }
                        .padding(vertical = 8.sdp),
                )
            }
            AuthPrimaryButton(
                text = "Continue",
                enabled = state.canSave, // never sends a new account without consents
                loading = state.isSaving,
                trailingIcon = R.drawable.ic_arrow_forward,
                onClick = { onIntent(UserProfileIntent.Save) },
            )
        }
    }

    if (state.showDatePicker) {
        DobPickerDialog(
            initial = state.form.dateOfBirth,
            onPicked = { onIntent(UserProfileIntent.DateOfBirthPicked(it)) },
            onDismiss = { onIntent(UserProfileIntent.DismissDatePicker) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AuthColors.Ink,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.ssp,
        modifier = Modifier.padding(bottom = 8.sdp),
    )
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 6.sdp))
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.sdp)
    val borderColor = when {
        selected -> AuthColors.Orange
        isError -> AuthColors.Error
        else -> AuthColors.Border
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) AuthColors.OtpFilled else AuthColors.Field)
            .border(if (selected) 2.sdp else 1.sdp, borderColor, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.sdp),
    ) {
        Box(
            modifier = Modifier
                .size(44.sdp)
                .clip(CircleShape)
                .background(if (selected) AuthColors.Orange else AuthColors.OrangeSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (selected) Color.White else AuthColors.OrangeText,
                modifier = Modifier.size(22.sdp),
            )
        }
        Spacer(Modifier.height(14.sdp))
        Text(title, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
        Spacer(Modifier.height(4.sdp))
        Text(subtitle, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, lineHeight = 16.ssp)
    }
}

@Composable
private fun GenderGrid(selected: Gender?, isError: Boolean, onSelect: (Gender) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        Gender.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                row.forEach { gender ->
                    val isSelected = gender == selected
                    val shape = RoundedCornerShape(12.sdp)
                    Text(
                        gender.label,
                        color = if (isSelected) AuthColors.OrangeText else AuthColors.Ink,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.ssp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (isSelected) AuthColors.OtpFilled else AuthColors.Field)
                            .border(
                                1.sdp,
                                when {
                                    isSelected -> AuthColors.Orange
                                    isError -> AuthColors.Error
                                    else -> AuthColors.Border
                                },
                                shape,
                            )
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(gender) })
                            .padding(horizontal = 14.sdp, vertical = 14.sdp),
                    )
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AuthColors.Orange,
    unfocusedBorderColor = AuthColors.Border,
    errorBorderColor = AuthColors.Error,
    focusedLabelColor = AuthColors.OrangeText,
    unfocusedLabelColor = AuthColors.Body,
    errorLabelColor = AuthColors.Error,
    focusedTextColor = AuthColors.Ink,
    unfocusedTextColor = AuthColors.Ink,
    disabledTextColor = AuthColors.Ink,
    disabledBorderColor = AuthColors.Border,
    disabledLabelColor = AuthColors.Body,
    cursorColor = AuthColors.Orange,
    focusedContainerColor = AuthColors.Field,
    unfocusedContainerColor = AuthColors.Field,
    disabledContainerColor = AuthColors.Field,
    errorContainerColor = AuthColors.Field,
    errorSupportingTextColor = AuthColors.Error,
)

@Composable
private fun Field(
    label: String,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = Inter) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) { { Text(error, fontFamily = Inter) } } else null,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
        shape = RoundedCornerShape(12.sdp),
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Read-only field that opens the date picker. */
@Composable
private fun DateField(value: String, error: String?, onClick: () -> Unit) {
    Box {
        OutlinedTextField(
            value = displayDate(value),
            onValueChange = {},
            enabled = false,
            label = { Text("Date of birth", fontFamily = Inter) },
            placeholder = { Text("DD MMM YYYY", fontFamily = Inter) },
            trailingIcon = {
                Icon(painterResource(R.drawable.ic_chevron_down), null, tint = AuthColors.Body, modifier = Modifier.size(20.sdp))
            },
            isError = error != null,
            supportingText = if (error != null) { { Text(error, fontFamily = Inter, color = AuthColors.Error) } } else null,
            shape = RoundedCornerShape(12.sdp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        // Disabled fields swallow no clicks → overlay handles them.
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.sdp))
                .clickable(role = Role.Button, onClickLabel = "Pick date of birth", onClick = onClick),
        )
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "1998-04-12" → "12 Apr 1998" */
private fun displayDate(iso: String): String =
    SimpleDate.parse(iso)?.let { "${it.day} ${MONTHS[it.month - 1]} ${it.year}" } ?: iso

/** DatePicker works in UTC millis. */
private fun SimpleDate.toUtcMillis(): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
    clear()
    set(year, month - 1, day)
    timeInMillis
}

private fun Long.toSimpleDate(): SimpleDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).let {
    it.timeInMillis = this
    SimpleDate(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DobPickerDialog(initial: String, onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    val today = remember { SaveUserProfileUseCase.today() }
    // Latest allowed birthday = exactly 18 years ago (Feb 29 → Feb 28).
    val latest = remember(today) {
        val y = today.year - UserProfileValidator.MIN_AGE
        SimpleDate.parse(today.copy(year = y).iso()) ?: SimpleDate(y, today.month, 28)
    }
    val latestMillis = remember(latest) { latest.toUtcMillis() }
    val initialMillis = remember(initial) { SimpleDate.parse(initial)?.toUtcMillis()?.coerceAtMost(latestMillis) ?: latestMillis }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        yearRange = (today.year - 100)..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestMillis
            override fun isSelectableYear(year: Int): Boolean = year <= latest.year
        },
    )
    val colors = DatePickerDefaults.colors(
        containerColor = AuthColors.Card,
        titleContentColor = AuthColors.Body,
        headlineContentColor = AuthColors.Ink,
        weekdayContentColor = AuthColors.Body,
        subheadContentColor = AuthColors.Body,
        navigationContentColor = AuthColors.Ink,
        yearContentColor = AuthColors.Ink,
        currentYearContentColor = AuthColors.OrangeText,
        selectedYearContainerColor = AuthColors.Orange,
        selectedYearContentColor = Color.White,
        dayContentColor = AuthColors.Ink,
        selectedDayContainerColor = AuthColors.Orange,
        selectedDayContentColor = Color.White,
        todayContentColor = AuthColors.OrangeText,
        todayDateBorderColor = AuthColors.Orange,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedDateMillis != null,
                onClick = { pickerState.selectedDateMillis?.let { onPicked(it.toSimpleDate().iso()) } },
            ) { Text("OK", color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AuthColors.Body, fontFamily = Inter) }
        },
        colors = colors,
    ) {
        DatePicker(state = pickerState, colors = colors)
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UserProfilePreview() {
    UserProfileScreen(
        UserProfileState(
            isStarted = true,
            registrationRequired = true,
            form = com.saferenttechnologies.android.domain.model.UserProfileForm(role = UserRole.TENANT, dateOfBirth = "1998-04-12"),
            fieldErrors = mapOf(UserProfileField.FULL_NAME to "Enter your full name"),
        ),
        onIntent = {},
    )
}
