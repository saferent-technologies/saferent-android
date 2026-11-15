package com.saferenttechnologies.android.presentation.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.PermissionPrompt
import com.saferenttechnologies.android.domain.model.PermissionStatus
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/**
 * Our own popup, always shown BEFORE the system permission dialog.
 * Permanently denied → the button says "Open settings".
 */
@Composable
fun PermissionRationaleDialog(
    prompt: PermissionPrompt,
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
) {
    val texts = prompt.permission.texts()
    Dialog(onDismissRequest = onNotNow, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.sdp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.sdp))
                .background(AuthColors.Card)
                .padding(24.sdp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.sdp).clip(CircleShape).background(AuthColors.OrangeSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(texts.icon), null, tint = AuthColors.OrangeText, modifier = Modifier.size(30.sdp))
            }
            Spacer(Modifier.height(18.sdp))
            Text(
                stringResource(texts.title),
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.ssp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.sdp))
            Text(
                stringResource(texts.body),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 14.ssp,
                lineHeight = 21.ssp,
                textAlign = TextAlign.Center,
            )
            if (prompt.status == PermissionStatus.PERMANENTLY_DENIED) {
                Spacer(Modifier.height(10.sdp))
                Text(
                    stringResource(R.string.permission_settings_hint),
                    color = AuthColors.Muted,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(22.sdp))
            AuthPrimaryButton(
                text = stringResource(
                    if (prompt.status == PermissionStatus.PERMANENTLY_DENIED) R.string.permission_open_settings
                    else R.string.permission_enable,
                ),
                enabled = true,
                loading = false,
                onClick = onEnable,
            )
            Spacer(Modifier.height(6.sdp))
            Text(
                stringResource(R.string.permission_not_now),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.ssp,
                modifier = Modifier
                    .heightIn(min = 48.sdp)
                    .clip(RoundedCornerShape(8.sdp))
                    .clickable(role = Role.Button, onClick = onNotNow)
                    .padding(horizontal = 16.sdp, vertical = 14.sdp),
            )
        }
    }
}

private data class PermissionTexts(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int)

private fun AppPermission.texts(): PermissionTexts = when (this) {
    AppPermission.LOCATION -> PermissionTexts(R.drawable.ic_location, R.string.permission_location_title, R.string.permission_location_body)
    AppPermission.NOTIFICATIONS ->
        PermissionTexts(R.drawable.ic_notifications, R.string.permission_notifications_title, R.string.permission_notifications_body)
    AppPermission.CAMERA -> PermissionTexts(R.drawable.ic_camera, R.string.permission_camera_title, R.string.permission_camera_body)
}

/** Our app's page in system Settings (the only way back from "permanently denied"). */
fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
