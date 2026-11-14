package com.saferenttechnologies.android.presentation.home

import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Placeholder Home: real Home comes later. Holds the WhatsApp consent setting + sign out. */
@Composable
fun HomeScreen(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.sdp),
    ) {
        val name = state.user?.fullName?.trim()?.substringBefore(' ').orEmpty()
        Text(
            if (name.isEmpty()) "Welcome to SafeRent" else "Hi, $name",
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.ssp,
        )
        if (state.showRoleSwitcher) {
            Spacer(Modifier.height(16.sdp))
            RoleSwitcher(roles = state.roles, active = state.activeRole, onSelect = { onIntent(HomeIntent.SwitchRole(it)) })
        }
        Spacer(Modifier.height(16.sdp))
        // Placeholder until the dashboard API (GET api/v1/dashboard?role=) format + design are final.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.sdp))
                .background(AuthColors.Surface)
                .padding(20.sdp),
        ) {
            Text(
                "${state.activeRole.label()} dashboard",
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.ssp,
            )
            Spacer(Modifier.height(6.sdp))
            Text("Coming soon.", color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (state.isSigningOut) "Signing out…" else "Sign out",
            color = AuthColors.Error,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 15.ssp,
            modifier = Modifier
                .heightIn(min = 48.sdp)
                .clickable(enabled = !state.isSigningOut, role = Role.Button) { onIntent(HomeIntent.SignOut) }
                .padding(vertical = 14.sdp),
        )
    }
}

private fun UserRole?.label(): String = when (this) {
    UserRole.LANDLORD -> "Landlord"
    UserRole.TENANT -> "Tenant"
    null -> "Your"
}

/** Landlord | Tenant pill switcher (only for accounts with both roles). */
@Composable
private fun RoleSwitcher(roles: List<UserRole>, active: UserRole?, onSelect: (UserRole) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(AuthColors.Surface)
            .padding(4.sdp),
    ) {
        roles.forEach { role ->
            val selected = role == active
            Text(
                role.label(),
                color = if (selected) Color.White else AuthColors.Body,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.sdp))
                    .background(if (selected) AuthColors.Orange else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(role) })
                    .padding(vertical = 10.sdp),
            )
        }
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePreview() {
    HomeScreen(
        HomeState(
            user = AccountUser(fullName = "Ravi Kumar", role = UserRole.LANDLORD, roles = listOf(UserRole.LANDLORD, UserRole.TENANT)),
            activeRole = UserRole.LANDLORD,
        ),
        onIntent = {},
    )
}
