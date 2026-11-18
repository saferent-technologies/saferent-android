package com.saferenttechnologies.android.presentation.account

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.BuildConfig
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.manage.tryStart
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** One row in a menu card. */
data class AccountLink(val icon: Int, val title: String, val onClick: () -> Unit, val danger: Boolean = false)

/** Profile tab (landlord + tenant). Shortcuts are passed in by the host so each role gets its own. */
@Composable
fun AccountScreen(
    state: AccountState,
    user: AccountUser?,
    activeRole: UserRole?,
    signingOut: Boolean,
    onIntent: (AccountIntent) -> Unit,
    onSwitchRole: (UserRole) -> Unit,
    onSignOut: () -> Unit,
    shortcuts: List<AccountLink>,
) {
    BackHandler { onIntent(AccountIntent.Close) }
    val context = LocalContext.current
    fun open(uri: String) = context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Text(
            stringResource(R.string.tab_profile),
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.ssp,
            modifier = Modifier.padding(horizontal = 20.sdp, vertical = 12.sdp),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
            // Who I am
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp)).background(AuthColors.Surface).padding(16.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                    Text(initials(user?.fullName.orEmpty()), color = AuthColors.OrangeText, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
                }
                Spacer(Modifier.width(14.sdp))
                Column(Modifier.weight(1f)) {
                    Text(user?.fullName?.ifBlank { null } ?: stringResource(R.string.greeting_welcome), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
                    user?.phone?.takeIf { it.isNotBlank() }?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp) }
                    user?.email?.takeIf { it.isNotBlank() }?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp) }
                    val where = listOf(user?.city.orEmpty(), user?.state.orEmpty()).filter { it.isNotBlank() }.joinToString(", ")
                    if (where.isNotBlank()) Text(where, color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
                }
            }

            // Role (only for accounts with both)
            val roles = user?.allRoles.orEmpty()
            if (roles.size > 1) {
                Heading(stringResource(R.string.account_using_as))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.sdp)) {
                    roles.forEach { r ->
                        Chip(roleLabel(r), selected = r == activeRole, enabled = !signingOut, modifier = Modifier.weight(1f), maxLines = 1) { if (r != activeRole) onSwitchRole(r) }
                    }
                }
            }

            if (shortcuts.isNotEmpty()) {
                Heading(stringResource(R.string.account_shortcuts))
                Menu(shortcuts)
            }

            // Help
            val s = state.support
            val help = buildList {
                if (s.phone.isNotBlank()) add(AccountLink(R.drawable.ic_call, stringResource(R.string.account_call_support), { open("tel:${s.phone}") }))
                if (s.whatsapp.isNotBlank()) add(AccountLink(R.drawable.ic_chat, stringResource(R.string.account_whatsapp_support), { open("https://wa.me/${s.whatsapp.filter { it.isDigit() }}") }))
                if (s.email.isNotBlank()) add(AccountLink(R.drawable.ic_mail, stringResource(R.string.account_email_support), { open("mailto:${s.email}") }))
                if (s.helpCenterUrl.isNotBlank()) add(AccountLink(R.drawable.ic_help, stringResource(R.string.account_help_center), { open(s.helpCenterUrl) }))
            }
            if (help.isNotEmpty()) {
                Heading(stringResource(R.string.account_help))
                Menu(help)
            }

            val l = state.legal
            val legal = buildList {
                if (l.privacyPolicyUrl.isNotBlank()) add(AccountLink(R.drawable.ic_shield_check, stringResource(R.string.account_privacy), { open(l.privacyPolicyUrl) }))
                if (l.termsUrl.isNotBlank()) add(AccountLink(R.drawable.ic_document, stringResource(R.string.account_terms), { open(l.termsUrl) }))
                if (l.refundPolicyUrl.isNotBlank()) add(AccountLink(R.drawable.ic_document, stringResource(R.string.account_refund), { open(l.refundPolicyUrl) }))
            }
            if (legal.isNotEmpty()) {
                Heading(stringResource(R.string.account_legal))
                Menu(legal)
            }

            Spacer(Modifier.height(16.sdp))
            Menu(listOf(AccountLink(R.drawable.ic_close, stringResource(if (signingOut) R.string.account_signing_out else R.string.account_sign_out), { if (!signingOut) onIntent(AccountIntent.AskSignOut(true)) }, danger = true)))
            Text(
                stringResource(R.string.account_version, BuildConfig.VERSION_NAME),
                color = AuthColors.Muted,
                fontFamily = Inter,
                fontSize = 11.ssp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.sdp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    if (state.confirmSignOut) {
        AlertDialog(
            onDismissRequest = { onIntent(AccountIntent.AskSignOut(false)) },
            title = { Text(stringResource(R.string.account_sign_out_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(AccountIntent.AskSignOut(false))
                    onSignOut()
                }) { Text(stringResource(R.string.account_sign_out), color = AuthColors.Error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { onIntent(AccountIntent.AskSignOut(false)) }) { Text(stringResource(android.R.string.cancel), color = AuthColors.Body) } },
            containerColor = AuthColors.Card,
        )
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

@Composable
private fun roleLabel(r: UserRole) = stringResource(if (r == UserRole.TENANT) R.string.account_role_tenant else R.string.account_role_landlord)

@Composable
private fun Heading(text: String) {
    Text(text, color = AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.ssp, modifier = Modifier.padding(start = 4.sdp, top = 18.sdp, bottom = 8.sdp))
}

@Composable
private fun Menu(items: List<AccountLink>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))) {
        items.forEachIndexed { i, item ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = item.onClick).padding(horizontal = 14.sdp, vertical = 14.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(item.icon), null, tint = if (item.danger) AuthColors.Error else AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
                Spacer(Modifier.width(12.sdp))
                Text(item.title, color = if (item.danger) AuthColors.Error else AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                if (!item.danger) Icon(painterResource(R.drawable.ic_chevron_left), null, tint = AuthColors.Muted, modifier = Modifier.size(16.sdp).rotate(180f))
            }
            if (i < items.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 46.sdp).height(1.sdp).background(AuthColors.Border))
        }
    }
}
