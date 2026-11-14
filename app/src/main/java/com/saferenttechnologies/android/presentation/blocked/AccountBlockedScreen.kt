package com.saferenttechnologies.android.presentation.blocked

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.android.openInCustomTab
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AccountBlockedInfo
import com.saferenttechnologies.android.domain.model.AccountStatus
import com.saferenttechnologies.android.domain.model.SupportContact
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.Locale

/**
 * Deleted / deactivated / suspended account: icon, title, message, reason.
 * Bottom (same place as the login button): "Try again" + "Contact support".
 * Top-right "Sign out" (and system back) → login.
 */
@Composable
fun AccountBlockedScreen(
    state: AccountBlockedState,
    effects: Flow<AccountBlockedEffect>,
    onIntent: (AccountBlockedIntent) -> Unit,
    onRetry: () -> Unit,
) {
    val info = state.info ?: return
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val noApp = stringResource(R.string.account_blocked_no_app)
    val statusLabel = statusLabel(info.status)
    val emailSubject = stringResource(R.string.account_blocked_email_subject, statusLabel.lowercase(Locale.getDefault()))

    BackHandler {
        onIntent(if (state.showContactSupport) AccountBlockedIntent.CloseContactSupport else AccountBlockedIntent.SignOut)
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            val opened = when (effect) {
                is AccountBlockedEffect.OpenEmail -> context.tryStart(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(effect.email)}?subject=${Uri.encode(emailSubject)}"))
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf(effect.email))
                        .putExtra(Intent.EXTRA_SUBJECT, emailSubject),
                )
                is AccountBlockedEffect.OpenDialer -> context.tryStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${effect.phone}")))
                is AccountBlockedEffect.OpenWhatsApp -> context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)))
                is AccountBlockedEffect.OpenUrl -> runCatching { context.openInCustomTab(effect.url) }.isSuccess
                AccountBlockedEffect.NavigateHome -> true // signed out + store cleared → login screen
            }
            if (!opened) snackbar.showSnackbar(noApp)
        }
    }

    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        if (state.showContactSupport) {
            ContactSupportContent(state = state, onIntent = onIntent)
        } else {
            BlockedContent(
                state = state,
                info = info,
                onIntent = onIntent,
                // Same click: leave this screen + re-check the account (no effect → nothing lost).
                onTryAgain = {
                    onIntent(AccountBlockedIntent.TryAgain)
                    onRetry()
                },
            )
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.sdp),
        )
    }
}

@Composable
private fun BlockedContent(
    state: AccountBlockedState,
    info: AccountBlockedInfo,
    onIntent: (AccountBlockedIntent) -> Unit,
    onTryAgain: () -> Unit,
) {
    val isDeleted = info.status == AccountStatus.DELETED
    val accent = if (isDeleted) AuthColors.Error else AuthColors.OrangeText
    val accentBg = if (isDeleted) AuthColors.Error.copy(alpha = 0.12f) else AuthColors.OrangeSoft
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top-right: Sign out
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 4.sdp)) {
            Text(
                stringResource(R.string.account_blocked_sign_out),
                color = AuthColors.Error,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.ssp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(8.sdp))
                    .clickable(enabled = !state.isSigningOut, role = Role.Button) { onIntent(AccountBlockedIntent.SignOut) }
                    .padding(12.sdp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.sdp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.sdp))
            // Icon by status: deleted / deactivated / suspended
            Box(
                Modifier
                    .size(96.sdp)
                    .clip(CircleShape)
                    .background(accentBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(statusIcon(info.status)), contentDescription = statusLabel(info.status), tint = accent, modifier = Modifier.size(48.sdp))
            }
            Spacer(Modifier.height(24.sdp))
            Text(
                info.title.ifBlank { fallbackTitle(info.status) },
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.ssp,
                lineHeight = 30.ssp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.sdp))
            Text(
                info.message.ifBlank { stringResource(R.string.account_blocked_message_default) },
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 15.ssp,
                lineHeight = 22.ssp,
                textAlign = TextAlign.Center,
            )
            info.reason?.let { reason ->
                Spacer(Modifier.height(24.sdp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.sdp))
                        .background(AuthColors.Surface)
                        .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
                        .padding(16.sdp),
                ) {
                    Text(stringResource(R.string.account_blocked_reason), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                    Spacer(Modifier.height(6.sdp))
                    Text(reason, color = AuthColors.Ink, fontFamily = Inter, fontSize = 15.ssp, lineHeight = 21.ssp)
                }
            }
            Spacer(Modifier.height(24.sdp))
        }
        // Bottom — same size / place as the login button.
        Column(Modifier.padding(horizontal = 24.sdp)) {
            OutlinedButton(
                text = stringResource(R.string.account_blocked_try_again),
                enabled = !state.isSigningOut,
                onClick = onTryAgain,
            )
            Spacer(Modifier.height(12.sdp))
            AuthPrimaryButton(
                text = stringResource(R.string.account_blocked_contact_support),
                enabled = !state.isSigningOut,
                loading = false,
                onClick = { onIntent(AccountBlockedIntent.ContactSupport) },
            )
            Spacer(Modifier.height(16.sdp))
        }
    }
}

/** Placeholder until the ticket-system API arrives: shows the channels the server sent. */
@Composable
private fun ContactSupportContent(state: AccountBlockedState, onIntent: (AccountBlockedIntent) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 8.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.sdp)
                    .clip(CircleShape)
                    .background(AuthColors.Surface)
                    .clickable(role = Role.Button) { onIntent(AccountBlockedIntent.CloseContactSupport) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_chevron_left), "Back", tint = AuthColors.Ink, modifier = Modifier.size(22.sdp))
            }
            Spacer(Modifier.width(12.sdp))
            Text(
                stringResource(R.string.contact_support_title),
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.ssp,
            )
        }
        Spacer(Modifier.height(12.sdp))
        Text(
            stringResource(
                if (state.channels.isEmpty()) R.string.contact_support_ticket_soon_no_channels else R.string.contact_support_ticket_soon,
            ),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 15.ssp,
            lineHeight = 22.ssp,
            modifier = Modifier.padding(horizontal = 24.sdp),
        )
        Spacer(Modifier.height(12.sdp))
        state.channels.forEach { channel ->
            ChannelRow(channel, state.info?.support) { onIntent(AccountBlockedIntent.OpenChannel(channel)) }
        }
    }
}

@Composable
private fun ChannelRow(channel: SupportChannel, support: SupportContact?, onClick: () -> Unit) {
    val (icon, label, value) = when (channel) {
        SupportChannel.EMAIL -> Triple(R.drawable.ic_mail, R.string.account_blocked_channel_email, support?.email)
        SupportChannel.CALL -> Triple(R.drawable.ic_call, R.string.account_blocked_channel_call, support?.phone)
        SupportChannel.WHATSAPP -> Triple(R.drawable.ic_chat, R.string.account_blocked_channel_whatsapp, support?.whatsapp)
        SupportChannel.HELP_CENTER -> Triple(R.drawable.ic_help, R.string.account_blocked_channel_help, support?.helpCenterUrl)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.sdp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.sdp, vertical = 10.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.sdp).clip(CircleShape).background(AuthColors.OrangeSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
        }
        Spacer(Modifier.width(14.sdp))
        Column {
            Text(stringResource(label), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp)
            value?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, maxLines = 1) }
        }
    }
}

@Composable
private fun OutlinedButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.sdp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.sdp)
            .clip(shape)
            .border(1.5.sdp, AuthColors.Orange, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.ssp)
    }
}

@DrawableRes
private fun statusIcon(status: AccountStatus): Int = when (status) {
    AccountStatus.DELETED -> R.drawable.ic_delete_forever
    AccountStatus.SUSPENDED -> R.drawable.ic_pause_circle
    AccountStatus.DEACTIVATED, AccountStatus.ACTIVE -> R.drawable.ic_block
}

@Composable
private fun fallbackTitle(status: AccountStatus): String = stringResource(
    when (status) {
        AccountStatus.DELETED -> R.string.account_blocked_title_deleted
        AccountStatus.SUSPENDED -> R.string.account_blocked_title_suspended
        AccountStatus.DEACTIVATED, AccountStatus.ACTIVE -> R.string.account_blocked_title_deactivated
    },
)

@Composable
private fun statusLabel(status: AccountStatus): String = stringResource(
    when (status) {
        AccountStatus.DELETED -> R.string.account_blocked_status_deleted
        AccountStatus.SUSPENDED -> R.string.account_blocked_status_suspended
        AccountStatus.DEACTIVATED, AccountStatus.ACTIVE -> R.string.account_blocked_status_deactivated
    },
)

/** startActivity that never crashes: false when no app can handle it. */
private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountBlockedPreview() {
    AccountBlockedScreen(
        state = AccountBlockedState(
            AccountBlockedInfo(
                status = AccountStatus.DELETED,
                title = "Account deleted",
                message = "This account has been deleted by SafeRent and can no longer be used.",
                reason = "Fake listings reported by several tenants",
                changedAtMillis = 1_794_652_325_120,
                support = SupportContact(email = "support@saferenttechnologies.com", phone = "+919876543210"),
            ),
        ),
        effects = emptyFlow(),
        onIntent = {},
        onRetry = {},
    )
}
