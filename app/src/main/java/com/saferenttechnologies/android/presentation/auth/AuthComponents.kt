package com.saferenttechnologies.android.presentation.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferenttechnologies.android.ui.theme.Inter

/** Login / OTP / demographics colours. Light = the design; dark = same layout on a dark canvas. */
internal data class AuthPalette(
    val Background: Color,
    /** Raised surfaces: selected role card, phone input, active OTP box. */
    val Card: Color,
    val Ink: Color,
    val Body: Color,
    val Muted: Color,
    val Orange: Color,
    val OrangeText: Color,
    val OrangeSoft: Color,
    val Surface: Color,
    val Field: Color,
    val Border: Color,
    val Divider: Color,
    val Green: Color,
    val GreenText: Color,
    val GreenBg: Color,
    val GreenBorder: Color,
    val GreenDot: Color,
    val Cream: Color,
    val CreamBorder: Color,
    val OtpFilled: Color,
    val OtpFilledBorder: Color,
    val Error: Color,
)

private val LightAuthPalette = AuthPalette(
    Background = Color.White,
    Card = Color.White,
    Ink = Color(0xFF0F172A),
    Body = Color(0xFF64748B),
    Muted = Color(0xFF94A3B8),
    Orange = Color(0xFFE2622B),
    OrangeText = Color(0xFFE8622A),
    OrangeSoft = Color(0xFFFDEBDD),
    Surface = Color(0xFFF1F3F6),
    Field = Color(0xFFF8FAFC),
    Border = Color(0xFFE2E8F0),
    Divider = Color(0xFFEEF0F3),
    Green = Color(0xFF15803D),
    GreenText = Color(0xFF166534),
    GreenBg = Color(0xFFF0FDF4),
    GreenBorder = Color(0xFFBBF7D0),
    GreenDot = Color(0xFF22C55E),
    Cream = Color(0xFFFFFBF5),
    CreamBorder = Color(0xFFF5E6D3),
    OtpFilled = Color(0xFFFFF7ED),
    OtpFilledBorder = Color(0xFFF9D5B4),
    Error = Color(0xFFDC2626),
)

private val DarkAuthPalette = AuthPalette(
    Background = Color(0xFF0F1115),
    Card = Color(0xFF1C2028),
    Ink = Color(0xFFF1F5F9),
    Body = Color(0xFF94A3B8),
    Muted = Color(0xFF64748B),
    Orange = Color(0xFFE2622B),
    OrangeText = Color(0xFFF0874F),
    OrangeSoft = Color(0xFF3A2216),
    Surface = Color(0xFF171A20),
    Field = Color(0xFF161920),
    Border = Color(0xFF2A2F38),
    Divider = Color(0xFF1F232A),
    Green = Color(0xFF4ADE80),
    GreenText = Color(0xFF86EFAC),
    GreenBg = Color(0xFF0F2A1C),
    GreenBorder = Color(0xFF1E4D33),
    GreenDot = Color(0xFF22C55E),
    Cream = Color(0xFF161920),
    CreamBorder = Color(0xFF2A2F38),
    OtpFilled = Color(0xFF2A1A10),
    OtpFilledBorder = Color(0xFF5C3418),
    Error = Color(0xFFF87171),
)

/** Follows the system light / dark setting. */
internal val AuthColors: AuthPalette
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DarkAuthPalette else LightAuthPalette

@Composable
internal fun AuthPrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    val shape = RoundedCornerShape(14.sdp)
    val active = enabled && !loading
    // Conditions not met → proper disabled state: grey, no glow, not clickable (no faded orange).
    // Loading keeps the orange with a spinner.
    val disabled = !enabled && !loading
    val content = if (disabled) AuthColors.Muted else Color.White
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.sdp)
            .then(
                if (disabled) Modifier
                else Modifier.shadow(12.sdp, shape, ambientColor = AuthColors.Orange, spotColor = AuthColors.Orange),
            )
            .clip(shape)
            .background(if (disabled) AuthColors.Border else AuthColors.Orange)
            .clickable(enabled = active, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.sdp, modifier = Modifier.size(24.sdp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.let {
                    Icon(painterResource(it), null, tint = content, modifier = Modifier.size(20.sdp))
                    Spacer(Modifier.width(10.sdp))
                }
                Text(text, color = content, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.ssp)
                trailingIcon?.let {
                    Spacer(Modifier.width(10.sdp))
                    Icon(painterResource(it), null, tint = content, modifier = Modifier.size(20.sdp))
                }
            }
        }
    }
}


/**
 * Login / OTP layout: [top] content + [bottom] block (terms, button) pinned to the bottom.
 * Keyboard open → the bottom block stays where it is (behind the keyboard) so it never covers
 * the text field; the field itself moves up just enough via [keepAboveKeyboard]. Anything that
 * scrolls up is clipped at the status bar (goes behind it, never draws over it).
 */
@Composable
internal fun KeyboardAwareScreen(
    top: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(AuthColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val fullHeight = maxHeight // height WITHOUT the keyboard
        val scroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = fullHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(content = top)
                Column(content = bottom)
            }
        }
    }
}

/**
 * Put on the text field (or a Box around it): when the keyboard opens while it's focused, the
 * screen scrolls only as much as needed to show the field plus [margin] above the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.keepAboveKeyboard(margin: Dp = 20.sdp): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val marginPx = with(density) { margin.toPx() }
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Re-runs while the keyboard animates in, so the field rides up with it.
    LaunchedEffect(imeBottom, focused) {
        if (focused && imeBottom > 0 && size != IntSize.Zero) {
            requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + marginPx))
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onSizeChanged { size = it }
        .onFocusEvent { focused = it.hasFocus }
}
