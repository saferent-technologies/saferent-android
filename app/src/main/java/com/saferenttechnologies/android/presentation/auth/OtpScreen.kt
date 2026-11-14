package com.saferenttechnologies.android.presentation.auth

import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.domain.usecase.VerifyOtpUseCase
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

@Composable
internal fun OtpScreen(state: AuthState, onIntent: (AuthIntent) -> Unit) {
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
            Spacer(Modifier.height(12.sdp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(44.sdp)
                        .clip(CircleShape)
                        .background(AuthColors.Surface)
                        .clickable(role = Role.Button) { onIntent(AuthIntent.EditNumber) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_left), "Back", tint = AuthColors.Ink, modifier = Modifier.size(22.sdp))
                }
                Text(
                    "VERIFICATION",
                    color = AuthColors.Body,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.ssp,
                    letterSpacing = 1.4.ssp,
                )
            }

            Spacer(Modifier.height(28.sdp))
            Box(
                Modifier
                    .size(58.sdp)
                    .clip(RoundedCornerShape(16.sdp))
                    .background(AuthColors.OrangeSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_smartphone), null, tint = AuthColors.OrangeText, modifier = Modifier.size(26.sdp))
            }
            Spacer(Modifier.height(20.sdp))
            Text(
                "Verify your mobile",
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.ssp,
            )
            Spacer(Modifier.height(8.sdp))
            Text(
                "We've sent a 6-digit verification code via SMS to",
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 15.ssp,
            )
            Spacer(Modifier.height(4.sdp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.displayPhone, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp)
                Spacer(Modifier.width(10.sdp))
                Text(
                    "Edit",
                    color = AuthColors.OrangeText,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.ssp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(role = Role.Button) { onIntent(AuthIntent.EditNumber) },
                )
            }

            Spacer(Modifier.height(24.sdp))
            OtpInput(otp = state.otp, onChange = { onIntent(AuthIntent.OtpChanged(it)) })
            Spacer(Modifier.height(16.sdp))

            when {
                state.error != null -> Text(state.error, color = AuthColors.Error, fontFamily = Inter, fontSize = 14.ssp)
                state.isDetectingSms -> DetectingSmsChip()
            }

            Spacer(Modifier.height(14.sdp))
            ResendRow(state = state, onResend = { onIntent(AuthIntent.ResendClicked) })
            Spacer(Modifier.height(24.sdp))
        }

        // Same size / position as "Continue with OTP" on the login screen.
        Column(Modifier.padding(horizontal = 24.sdp)) {
            AuthPrimaryButton(
                text = "Verify & Continue",
                enabled = !state.isVerifying, // always same colour; short code → error text
                loading = state.isVerifying,
                leadingIcon = R.drawable.ic_shield_check,
                onClick = { onIntent(AuthIntent.VerifyClicked) },
            )
            Spacer(Modifier.height(16.sdp))
        }
    }
}

/**
 * System number keyboard behind the 6 boxes. Opens only when the user taps the boxes; hides as soon as
 * 6 digits are in (typed, pasted, or auto-filled from the SMS).
 */
@Composable
private fun OtpInput(otp: String, onChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(otp.length) {
        if (otp.length == VerifyOtpUseCase.OTP_LENGTH) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    BasicTextField(
        value = TextFieldValue(otp, selection = TextRange(otp.length)), // caret always at the end
        onValueChange = { onChange(it.text.filter(Char::isDigit).take(VerifyOtpUseCase.OTP_LENGTH)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.SmsOtpCode },
        decorationBox = { inner ->
            Box {
                OtpBoxes(otp = otp)
                // Keeps the real (invisible) text field in the layout so taps open the keyboard.
                Box(Modifier.matchParentSize().alpha(0f)) { inner() }
            }
        },
    )
}

@Composable
private fun OtpBoxes(otp: String) {
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
        repeat(VerifyOtpUseCase.OTP_LENGTH) { index ->
            val shape = RoundedCornerShape(12.sdp)
            val filled = index < otp.length
            val active = index == otp.length
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.sdp)
                    .then(
                        if (active) Modifier.shadow(6.sdp, shape, ambientColor = AuthColors.Orange, spotColor = AuthColors.Orange)
                        else Modifier,
                    )
                    .clip(shape)
                    .background(
                        when {
                            filled -> AuthColors.OtpFilled
                            active -> AuthColors.Card
                            else -> AuthColors.Cream
                        },
                    )
                    .border(
                        width = if (active) 2.sdp else 1.sdp,
                        color = when {
                            active -> AuthColors.Orange
                            filled -> AuthColors.OtpFilledBorder
                            else -> AuthColors.CreamBorder
                        },
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    filled -> Text(
                        otp[index].toString(),
                        color = AuthColors.Ink,
                        fontFamily = Inter,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.ssp,
                    )
                    active -> Box(
                        Modifier
                            .width(2.sdp)
                            .height(26.sdp)
                            .alpha(blink)
                            .background(AuthColors.Ink),
                    )
                    else -> Text("·", color = AuthColors.Muted, fontSize = 22.ssp)
                }
            }
        }
    }
}

@Composable
private fun DetectingSmsChip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(AuthColors.GreenBg)
            .border(1.sdp, AuthColors.GreenBorder, RoundedCornerShape(12.sdp))
            .padding(horizontal = 16.sdp, vertical = 12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.sdp)
                .clip(CircleShape)
                .background(AuthColors.GreenDot),
        )
        Spacer(Modifier.width(10.sdp))
        Text(
            "Detecting OTP from SMS…",
            color = AuthColors.GreenText,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.ssp,
            modifier = Modifier.weight(1f),
        )
        Text("Auto-fill", color = AuthColors.Green, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
    }
}

@Composable
private fun ResendRow(state: AuthState, onResend: () -> Unit) {
    if (state.resendSecondsLeft > 0) {
        val seconds = state.resendSecondsLeft
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = AuthColors.Muted)) { append("Resend code in ") }
                withStyle(SpanStyle(color = AuthColors.Ink, fontWeight = FontWeight.Medium)) {
                    append("%02d:%02ds".format(seconds / 60, seconds % 60))
                }
            },
            fontFamily = Inter,
            fontSize = 14.ssp,
        )
    } else {
        Text(
            "Resend code",
            color = if (state.canResend) AuthColors.OrangeText else AuthColors.Muted,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.ssp,
            modifier = Modifier.clickable(enabled = state.canResend, role = Role.Button, onClick = onResend),
        )
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OtpPreview() {
    OtpScreen(
        AuthState(
            isLoaded = true,
            step = AuthStep.OTP,
            phoneDigits = "9849012345",
            verificationId = "x",
            otp = "5829",
            isDetectingSms = true,
            resendSecondsLeft = 24,
        ),
        onIntent = {},
    )
}
