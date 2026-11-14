package com.saferenttechnologies.android.presentation.auth

import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.domain.model.IndianPhone
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

@Composable
internal fun MobileEntryScreen(state: AuthState, onIntent: (AuthIntent) -> Unit) {
    // Keyboard open → the button stays down (never lands on the field) and only the field
    // moves up a little (keepAboveKeyboard); nothing draws over the status bar.
    KeyboardAwareScreen(
        top = {
            Column(modifier = Modifier.padding(horizontal = 24.sdp)) {
                Spacer(Modifier.height(16.sdp))
                Header()
                Spacer(Modifier.height(32.sdp))
                TrustBadge()
                Spacer(Modifier.height(18.sdp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = AuthColors.Ink)) { append("Get Started with\n") }
                        withStyle(SpanStyle(color = AuthColors.OrangeText)) { append("SafeRent") }
                },
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.ssp,
                    lineHeight = 37.ssp,
                )
                Spacer(Modifier.height(10.sdp))
                Text(
                    text = "Enter your mobile number to sign in or create your verified rental account.",
                    color = AuthColors.Body,
                    fontFamily = Inter,
                    fontSize = 15.ssp,
                    lineHeight = 22.ssp,
                )
                Spacer(Modifier.height(22.sdp))
                Spacer(Modifier.height(6.sdp)) // tenant / landlord is asked on the demographics screen
                Text(
                    text = "MOBILE NUMBER",
                    color = AuthColors.Ink,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.ssp,
                    letterSpacing = 0.6.ssp,
                )
                Spacer(Modifier.height(10.sdp))
                // Keyboard open → only this field moves up (with a small gap), not the whole screen.
                Box(Modifier.keepAboveKeyboard()) {
                    PhoneField(
                        digits = state.phoneDigits,
                        valid = state.isPhoneValid,
                        onChange = { onIntent(AuthIntent.PhoneChanged(it)) },
                        onDone = { onIntent(AuthIntent.ContinueClicked) },
                    )
                }
                Spacer(Modifier.height(10.sdp))
                if (state.error != null) {
                    Text(state.error, color = AuthColors.Error, fontFamily = Inter, fontSize = 13.ssp)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(
                            painterResource(R.drawable.ic_lock),
                            null,
                            tint = AuthColors.Muted,
                            modifier = Modifier
                                .padding(top = 2.sdp) // line up with the first text line
                                .size(13.sdp),
                        )
                        Spacer(Modifier.width(6.sdp))
                        Text(
                            "We never share your number with unauthorized brokers. adasdasd",
                            color = AuthColors.Muted,
                            fontFamily = Inter,
                            fontSize = 12.ssp,
                        )
                    }
                }
                Spacer(Modifier.height(24.sdp))
            }

        },
        bottom = {
            // Bottom: Terms (always unticked by default) right above the button.
            Column(Modifier.padding(horizontal = 24.sdp)) {
                ConsentBlock(state = state, onIntent = onIntent)
                Spacer(Modifier.height(14.sdp))
                AuthPrimaryButton(
                    text = "Continue with OTP",
                    enabled = !state.isSendingOtp, // always same colour; missing input → error text
                    loading = state.isSendingOtp,
                    trailingIcon = R.drawable.ic_arrow_forward,
                    onClick = { onIntent(AuthIntent.ContinueClicked) },
                )
                Spacer(Modifier.height(16.sdp))
            }
        },
    )
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Dark: splash_logo's night variant (orange + white) reads well on dark backgrounds.
        Image(
            painterResource(if (isSystemInDarkTheme()) R.drawable.splash_logo else R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(44.sdp),
        )
        Spacer(Modifier.width(6.sdp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = AuthColors.Ink)) { append("Safe") }
                withStyle(SpanStyle(color = AuthColors.OrangeText)) { append("Rent") }
            },
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.ssp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(AuthColors.Surface)
                .padding(horizontal = 12.sdp, vertical = 6.sdp),
        ) {
            Text("India 🇮🇳", color = AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
        }
    }
}

@Composable
private fun TrustBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.sdp))
            .background(AuthColors.GreenBg)
            .border(1.sdp, AuthColors.GreenBorder, RoundedCornerShape(6.sdp))
            .padding(horizontal = 8.sdp, vertical = 4.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_check_circle), null, tint = AuthColors.Green, modifier = Modifier.size(12.sdp))
        Spacer(Modifier.width(5.sdp))
        Text(
            "100% Direct Landlords & Verified Tenants",
            color = AuthColors.GreenText,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 11.ssp,
        )
    }
}

@Composable
private fun PhoneField(digits: String, valid: Boolean, onChange: (String) -> Unit, onDone: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // All 10 digits typed → close the keyboard so the Continue button is visible.
    LaunchedEffect(digits.length) {
        if (digits.length == IndianPhone.LENGTH) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    Row(Modifier.fillMaxWidth().height(60.sdp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(0.3f)
                .clip(RoundedCornerShape(topStart = 12.sdp, bottomStart = 12.sdp))
                .background(AuthColors.Field)
                .border(1.sdp, AuthColors.Border, RoundedCornerShape(topStart = 12.sdp, bottomStart = 12.sdp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🇮🇳  +91", color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.ssp)
            Spacer(Modifier.width(6.sdp))
            Icon(painterResource(R.drawable.ic_chevron_down), null, tint = AuthColors.Muted, modifier = Modifier.size(18.sdp))
        }
        val shape = RoundedCornerShape(topEnd = 12.sdp, bottomEnd = 12.sdp)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(0.7f)
                .then(
                    if (focused) Modifier.shadow(6.sdp, shape, ambientColor = AuthColors.Orange, spotColor = AuthColors.Orange)
                    else Modifier,
                )
                .clip(shape)
                .background(AuthColors.Card)
                .border(if (focused) 1.5.sdp else 1.sdp, if (focused) AuthColors.Orange else AuthColors.Border, shape)
                .padding(horizontal = 16.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = digits,
                onValueChange = onChange,
                singleLine = true,
                interactionSource = interaction,
                cursorBrush = SolidColor(AuthColors.Orange),
                visualTransformation = IndianPhoneTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                textStyle = TextStyle(
                    color = AuthColors.Ink,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.ssp,
                    letterSpacing = 1.ssp,
                ),
                decorationBox = { inner ->
                    Box {
                        if (digits.isEmpty()) {
                            Text("98490 12345", color = AuthColors.Muted, fontFamily = Inter, fontSize = 20.ssp, letterSpacing = 1.ssp)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            if (valid) {
                Box(
                    Modifier
                        .size(24.sdp)
                        .clip(CircleShape)
                        .background(AuthColors.GreenBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_check), "Valid number", tint = AuthColors.Green, modifier = Modifier.size(15.sdp))
                }
            }
        }
    }
}

/** Shows "98490 12345" while the state keeps plain digits. */
private object IndianPhoneTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = if (raw.length > 5) raw.take(5) + " " + raw.drop(5) else raw
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset > 5) offset + 1 else offset
            override fun transformedToOriginal(offset: Int): Int =
                (if (offset > 5) offset - 1 else offset).coerceAtMost(raw.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/**
 * Consent, recorded on the server after sign-in:
 * - Terms of Service + Privacy Policy (required — "Continue with OTP" stays disabled until ticked)
 * - WhatsApp reminders & updates (optional, unticked by default)
 */
@Composable
private fun ConsentBlock(state: AuthState, onIntent: (AuthIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(AuthColors.Cream)
            .border(1.sdp, AuthColors.CreamBorder, RoundedCornerShape(12.sdp))
            .padding(vertical = 4.sdp),
    ) {
        val linkStyle = TextLinkStyles(
            SpanStyle(textDecoration = TextDecoration.Underline, color = AuthColors.Ink, fontWeight = FontWeight.Medium),
        )
        ConsentCheckbox(
            checked = state.termsAccepted,
            onChange = { onIntent(AuthIntent.ToggleTerms(it)) },
            label = buildAnnotatedString {
                append("I agree to the ")
                withLink(LinkAnnotation.Clickable("terms", linkStyle) { onIntent(AuthIntent.OpenTerms) }) {
                    append("Terms of Service")
                }
                append(" and ")
                withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onIntent(AuthIntent.OpenPrivacy) }) {
                    append("Privacy Policy")
                }
            },
            stateDescription = "Required",
        )
        // WhatsApp opt-in hidden for now — un-comment to show it again (state + API already support it).
        // ConsentCheckbox(
        //     checked = state.whatsappOptIn,
        //     onChange = { onIntent(AuthIntent.ToggleWhatsapp(it)) },
        //     label = buildAnnotatedString {
        //         append("Send me rent reminders and updates on ")
        //         withStyle(SpanStyle(color = AuthColors.Green, fontWeight = FontWeight.Medium)) { append("WhatsApp") }
        //     },
        //     stateDescription = "Optional",
        // )
    }
}

/** Whole row toggles (48dp+ touch target); the label is the checkbox's accessible name. */
@Composable
private fun ConsentCheckbox(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    label: AnnotatedString,
    stateDescription: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.sdp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .semantics(mergeDescendants = true) { this.stateDescription = stateDescription }
            .padding(start = 4.sdp, end = 12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null, // the row handles the toggle
            colors = CheckboxDefaults.colors(checkedColor = AuthColors.Orange, uncheckedColor = AuthColors.Muted),
            modifier = Modifier.padding(12.sdp),
        )
        Text(
            text = label,
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 13.ssp,
            lineHeight = 19.ssp,
        )
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MobileEntryPreview() {
    MobileEntryScreen(AuthState(isLoaded = true, phoneDigits = "9849012345"), onIntent = {})
}
