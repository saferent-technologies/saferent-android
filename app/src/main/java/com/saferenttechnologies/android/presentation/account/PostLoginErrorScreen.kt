package com.saferenttechnologies.android.presentation.account

import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** phone/complete or auth/me failed (offline, LOGIN_METHOD_DISABLED, …): retry or sign out. */
@Composable
fun PostLoginErrorScreen(message: String, onIntent: (PostLoginIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.sdp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "We couldn't sign you in",
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.ssp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.sdp))
        Text(message, color = AuthColors.Body, fontFamily = Inter, fontSize = 15.ssp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.sdp))
        AuthPrimaryButton(text = "Try again", enabled = true, loading = false, onClick = { onIntent(PostLoginIntent.Retry) })
        Spacer(Modifier.height(8.sdp))
        Text(
            "Sign out",
            color = AuthColors.Error,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 15.ssp,
            modifier = Modifier
                .heightIn(min = 48.sdp)
                .clickable(role = Role.Button) { onIntent(PostLoginIntent.SignOut) }
                .padding(14.sdp),
        )
    }
}
