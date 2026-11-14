package com.saferenttechnologies.android.presentation.auth

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.saferenttechnologies.android.core.android.openInCustomTab
import kotlinx.coroutines.flow.Flow

/** Mobile number → OTP. Back on the OTP step returns to the number. */
@Composable
fun AuthScreen(state: AuthState, effects: Flow<AuthEffect>, onIntent: (AuthIntent) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            if (effect is AuthEffect.OpenUrl) context.openInCustomTab(effect.url)
        }
    }
    BackHandler(enabled = state.step == AuthStep.OTP) { onIntent(AuthIntent.EditNumber) }
    when (state.step) {
        AuthStep.PHONE -> MobileEntryScreen(state, onIntent)
        AuthStep.OTP -> OtpScreen(state, onIntent)
    }
}
