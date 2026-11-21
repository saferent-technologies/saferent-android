package com.saferenttechnologies.android.presentation.kyc

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.KycIdentity
import com.saferenttechnologies.android.domain.model.KycProperty
import com.saferenttechnologies.android.domain.model.KycStatus
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

private val Blue = Color(0xFF2563EB)

/** Owner verification: ① DigiLocker ② live selfie ③ submit → "Under review" → "Owner verified". */
@Composable
fun KycScreen(state: KycState, effects: Flow<KycEffect>, onIntent: (KycIntent) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        effects.collect { e ->
            when (e) {
                is KycEffect.Message -> Toast.makeText(context, e.text, Toast.LENGTH_LONG).show()
                is KycEffect.OpenUrl -> runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url)).addCategory(Intent.CATEGORY_BROWSABLE))
                }.onFailure { Toast.makeText(context, R.string.kyc_no_browser, Toast.LENGTH_LONG).show() }
            }
        }
    }
    // Back from the browser without the App Link → finish the DigiLocker session.
    LifecycleResumeEffect(Unit) {
        onIntent(KycIntent.Resumed)
        onPauseOrDispose { }
    }
    if (state.selfieCamera) {
        SelfieCaptureScreen(onTaken = { onIntent(KycIntent.SelfieTaken(it)) }, onClose = { onIntent(KycIntent.CloseSelfie) })
        return
    }
    // PAN / passport photo: the app's document camera (rectangle guide, no gallery).
    if (state.idCamera != null) {
        com.saferenttechnologies.android.presentation.property.CameraCaptureScreen(
            mode = com.saferenttechnologies.android.presentation.property.CameraMode.DOCUMENT,
            onCaptured = { onIntent(KycIntent.IdPhotoTaken(it)) },
            onClose = { onIntent(KycIntent.CloseIdCamera) },
        )
        return
    }
    // "How to verify": list of guides (from the server) → video / PDF / picture on top.
    if (state.tutorialsOpen) {
        TutorialsScreen(state.tutorials, state.tutorialsError, onIntent)
        state.tutorial?.let { TutorialViewer(it, state.tutorialPdf, state.tutorialLoading) { onIntent(KycIntent.CloseTutorial) } }
        return
    }
    val camera = rememberPermissionRequester(AppPermission.CAMERA)
    state.idForm?.let { form ->
        IdFormScreen(form, state.busy, onIntent, onCamera = { side -> camera.request { granted -> if (granted) onIntent(KycIntent.IdCamera(side)) } })
        return
    }
    BackHandler { onIntent(KycIntent.Close) }
    if (state.idPicker) IdPickerSheet(state.status?.digiLockerOff != false, { state.status?.allows(it) != false }, hasTutorial = true, onIntent)
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(KycIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Text(stringResource(R.string.kyc_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // Right after sign up: can be done later from the dashboard.
            if (state.afterProfile && state.status?.isSubmitted != true) {
                Text(
                    stringResource(R.string.kyc_skip),
                    color = AuthColors.Body,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.ssp,
                    modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(enabled = !state.busy, role = Role.Button) { onIntent(KycIntent.Close) }
                        .padding(horizontal = 10.sdp, vertical = 8.sdp),
                )
            }
        }
        val s = state.status
        when {
            s == null && state.loading -> ListSkeleton(rows = 3, thumbnail = 40.sdp, roundThumb = true, lines = 2)
            s == null -> Text(state.error ?: stringResource(R.string.kyc_load_failed), color = AuthColors.Body, fontFamily = Inter, modifier = Modifier.padding(24.sdp))
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.sdp)) {
                StageBanner(s)
                s.identity?.let { Spacer(Modifier.height(12.sdp)); IdentityCard(it) }
                if (s.showSteps) {
                    val locked = !s.canEdit || state.busy
                    Spacer(Modifier.height(16.sdp))
                    Step(
                        number = 1,
                        title = stringResource(R.string.kyc_step_id),
                        body = stringResource(if (state.waitingDigiLocker) R.string.kyc_digilocker_waiting else R.string.kyc_id_body),
                        done = s.identityDone,
                        action = stringResource(if (s.identityDone) R.string.kyc_change_id else R.string.kyc_choose_id),
                        enabled = !locked,
                        onClick = { onIntent(KycIntent.OpenIdPicker) },
                    )
                    Step(
                        number = 2,
                        title = stringResource(R.string.kyc_step_selfie),
                        body = stringResource(R.string.kyc_selfie_body),
                        done = s.selfie,
                        action = stringResource(if (s.selfie) R.string.kyc_retake_selfie else R.string.kyc_take_selfie),
                        enabled = !locked,
                        onClick = { camera.request { granted -> if (granted) onIntent(KycIntent.OpenSelfie) } },
                    )
                    Step(
                        number = 3,
                        title = stringResource(R.string.kyc_step_submit),
                        body = stringResource(R.string.kyc_submit_body),
                        done = s.isSubmitted,
                        action = null,
                        enabled = false,
                        onClick = {},
                    )
                    Spacer(Modifier.height(8.sdp))
                    if (state.afterProfile && s.isSubmitted) {
                        // Sent for review right after sign up → on to the dashboard.
                        AuthPrimaryButton(text = stringResource(R.string.kyc_go_dashboard), enabled = true, loading = false, onClick = { onIntent(KycIntent.Close) }, fontSize = 15.ssp)
                    } else {
                        AuthPrimaryButton(
                            text = stringResource(R.string.kyc_submit),
                            enabled = s.canSubmit && !state.busy,
                            loading = state.busy,
                            onClick = { onIntent(KycIntent.Submit) },
                            fontSize = 15.ssp,
                        )
                    }
                }
                if (s.properties.isNotEmpty()) {
                    Spacer(Modifier.height(22.sdp))
                    Text(stringResource(R.string.kyc_properties), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
                    Spacer(Modifier.height(8.sdp))
                    s.properties.forEach { PropertyRow(it) }
                }
                Spacer(Modifier.height(24.sdp))
            }
        }
    }
}

@Composable
private fun StageBanner(s: KycStatus) {
    val (title, body, color) = when {
        s.isApproved -> Triple(stringResource(R.string.kyc_verified), null, AuthColors.GreenText)
        s.isSubmitted -> Triple(
            stringResource(R.string.kyc_under_review),
            listOfNotNull(s.submittedAt?.let { stringResource(R.string.kyc_submitted_on, prettyDate(it)) }, stringResource(R.string.kyc_review_time)).joinToString(". "),
            Blue,
        )
        s.isRejected -> Triple(stringResource(R.string.kyc_rejected), s.note ?: stringResource(R.string.kyc_rejected_body), AuthColors.Error)
        else -> Triple(stringResource(R.string.kyc_intro_title), stringResource(R.string.kyc_intro_body), AuthColors.OrangeText)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(color.copy(alpha = 0.08f))
            .border(1.sdp, color.copy(alpha = 0.35f), RoundedCornerShape(14.sdp)).padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.isApproved) {
                Icon(painterResource(R.drawable.ic_shield_check), null, tint = color, modifier = Modifier.size(20.sdp))
                Spacer(Modifier.width(6.sdp))
            }
            Text(title, color = color, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.ssp)
        }
        body?.let { Text(it, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.padding(top = 4.sdp)) }
    }
}

@Composable
private fun IdentityCard(id: KycIdentity) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Surface).padding(14.sdp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(id.name, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.ssp, modifier = Modifier.weight(1f))
            if (id.test) StatusPill(stringResource(R.string.kyc_test_mode), AuthColors.OrangeText)
        }
        val line1 = listOf(
            id.dob.takeIf { it.isNotBlank() }?.let { stringResource(R.string.kyc_dob, prettyDate(it)) }.orEmpty(),
            when (id.gender.uppercase()) { "M" -> stringResource(R.string.kyc_male); "F" -> stringResource(R.string.kyc_female); "" -> ""; else -> stringResource(R.string.kyc_other) },
        ).filter { it.isNotBlank() }.joinToString(" · ")
        if (line1.isNotBlank()) Text(line1, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        if (id.aadhaarLast4.isNotBlank()) Text(stringResource(R.string.kyc_aadhaar_last4, id.aadhaarLast4), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        val place = listOf(id.city, id.state).filter { it.isNotBlank() }.joinToString(", ")
        if (place.isNotBlank()) Text(place, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        when (id.nameMatch) {
            "MISMATCH" -> Note(stringResource(R.string.kyc_name_mismatch), AuthColors.Error)
            "PARTIAL" -> Note(stringResource(R.string.kyc_name_partial), AuthColors.OrangeText)
        }
    }
}

@Composable
private fun ColumnScope.Note(text: String, color: Color) {
    Spacer(Modifier.height(8.sdp))
    Text(text, color = color, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.sdp)).background(color.copy(alpha = 0.08f)).padding(10.sdp))
}

@Composable
private fun Step(number: Int, title: String, body: String, done: Boolean, action: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.sdp).clip(RoundedCornerShape(14.sdp))
            .border(1.sdp, if (done) AuthColors.Green.copy(alpha = 0.5f) else AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
    ) {
        Box(
            Modifier.size(30.sdp).clip(CircleShape).background(if (done) AuthColors.Green else AuthColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(16.sdp))
            else Text(number.toString(), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.ssp)
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            Text(body, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            if (action != null) {
                Spacer(Modifier.height(8.sdp))
                Text(
                    action,
                    color = if (enabled) AuthColors.OrangeText else AuthColors.Muted,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.ssp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.sdp, if (enabled) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(50))
                        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.sdp, vertical = 7.sdp),
                )
            }
        }
    }
}

@Composable
private fun PropertyRow(p: KycProperty) {
    val (label, color) = when (p.ownership) {
        "VERIFIED" -> R.string.kyc_own_verified to AuthColors.Green
        "PENDING" -> R.string.kyc_own_pending to Blue
        "REJECTED" -> R.string.kyc_own_rejected to AuthColors.Error
        else -> R.string.kyc_own_missing to AuthColors.OrangeText
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.sdp).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp)).padding(12.sdp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.title.ifBlank { p.code }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                Text(listOf(p.code, p.city).filter { it.isNotBlank() }.joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
            StatusPill(stringResource(label), color)
        }
        p.documents.filter { it.status == "REJECTED" && !it.rejectionReason.isNullOrBlank() }.forEach { d ->
            Text(optionLabel(d.docType) + ": " + d.rejectionReason, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 4.sdp))
        }
    }
}

/** Landlord dashboard: green "Owner verified" when approved; otherwise status + a small "Verify now". */
@Composable
fun KycDashboardCard(status: KycStatus?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val s = status ?: return
    if (s.isApproved) {
        Row(
            modifier.clip(RoundedCornerShape(50)).background(AuthColors.GreenBg).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 12.sdp, vertical = 6.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_shield_check), null, tint = AuthColors.GreenText, modifier = Modifier.size(16.sdp))
            Spacer(Modifier.width(6.sdp))
            Text(stringResource(R.string.kyc_verified), color = AuthColors.GreenText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.ssp)
        }
        return
    }
    val (title, color) = when {
        s.isSubmitted -> stringResource(R.string.kyc_under_review) to Blue
        s.isRejected -> stringResource(R.string.kyc_rejected) to AuthColors.Error
        else -> stringResource(R.string.kyc_not_verified) to AuthColors.OrangeText
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(color.copy(alpha = 0.08f))
            .border(1.sdp, color.copy(alpha = 0.35f), RoundedCornerShape(14.sdp)).padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_shield_check), null, tint = color, modifier = Modifier.size(20.sdp))
        Spacer(Modifier.width(10.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
            Text(stringResource(if (s.isSubmitted) R.string.kyc_review_time else R.string.kyc_card_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        Spacer(Modifier.width(8.sdp))
        Text(
            stringResource(if (s.isSubmitted) R.string.kyc_view else R.string.kyc_verify_now),
            color = Color.White,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize = 12.ssp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(AuthColors.Orange).clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 12.sdp, vertical = 7.sdp),
        )
    }
}
