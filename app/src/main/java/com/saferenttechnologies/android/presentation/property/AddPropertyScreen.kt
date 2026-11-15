package com.saferenttechnologies.android.presentation.property

import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.FormSkeleton
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.WizardStep
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Add-property wizard: progress on top, Back / Next at the bottom, "Save & exit" keeps the draft. */
@Composable
fun AddPropertyScreen(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val camera = state.camera
    if (camera != null) {
        CameraCaptureScreen(
            mode = camera,
            onCaptured = { onIntent(AddPropertyIntent.Captured(it)) },
            onClose = { onIntent(AddPropertyIntent.CloseCamera) },
        )
        return
    }
    if (state.submitted) {
        SubmittedContent(liveChanges = state.isLiveEdit, onDone = { onIntent(AddPropertyIntent.Done) })
        return
    }
    BackHandler { onIntent(AddPropertyIntent.Back) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AuthColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        TopBar(state, onIntent)
        LinearProgressIndicator(
            progress = { state.step / WizardStep.COUNT.toFloat() },
            color = AuthColors.Orange,
            trackColor = AuthColors.Surface,
            modifier = Modifier.fillMaxWidth().height(4.sdp),
        )
        Banner(state, onIntent)

        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Types loading, or an existing property (continue draft / edit) still loading.
            if (state.types == null || (state.loading && state.property == null)) {
                if (state.loading) {
                    FormSkeleton()
                } else {
                    RetryBox(onRetry = { onIntent(AddPropertyIntent.Retry) })
                }
            } else {
                val scroll = rememberScrollState()
                LaunchedEffect(state.step) { scroll.scrollTo(0) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = 20.sdp, vertical = 16.sdp),
                ) {
                    StepHeader(state)
                    Spacer(Modifier.height(16.sdp))
                    ChangesBanner(state)
                    when (state.step) {
                        WizardStep.CATEGORY -> CategoryStep(state, onIntent)
                        WizardStep.BASIC -> BasicStep(state, onIntent)
                        WizardStep.LOCATION -> LocationStep(state, onIntent)
                        WizardStep.BUILDING -> BuildingStep(state, onIntent)
                        WizardStep.UNITS -> UnitsStep(state, onIntent)
                        WizardStep.PRICING -> PricingStep(state, onIntent)
                        WizardStep.OPTIONAL -> OptionalStep(state, onIntent)
                        WizardStep.PHOTOS -> PhotosStep(state, onIntent)
                        else -> DocumentsStep(state, onIntent) // last step: documents + submit
                    }
                    Spacer(Modifier.height(24.sdp))
                }
            }
        }
        BottomBar(state, onIntent)
    }

    if (state.drafts != null) DraftsSheet(state, onIntent)
    if (state.confirmSubmit) {
        AlertDialog(
            onDismissRequest = { onIntent(AddPropertyIntent.DismissSubmit) },
            title = { Text(stringResource(R.string.add_property_confirm_submit_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    stringResource(
                        when {
                            state.isLiveEdit -> R.string.changes_confirm_body
                            state.isEditingSubmitted -> R.string.add_property_confirm_resubmit_body
                            else -> R.string.add_property_confirm_submit_body
                        },
                    ),
                    fontFamily = Inter,
                    color = AuthColors.Body,
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(AddPropertyIntent.ConfirmSubmit) }) {
                    Text(stringResource(R.string.add_property_confirm_submit_yes), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(AddPropertyIntent.DismissSubmit) }) {
                    Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter)
                }
            },
            containerColor = AuthColors.Card,
        )
    }
}

@Composable
private fun TopBar(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(TOP_ICON)
                .clip(CircleShape)
                .background(AuthColors.Surface)
                .clickable(role = Role.Button) { onIntent(AddPropertyIntent.Close) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.add_property_close), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.add_property_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.ssp)
            Text(
                stringResource(R.string.add_property_step_of, state.step, WizardStep.COUNT),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
            )
        }
        if (state.property != null) {
            Text(
                stringResource(R.string.add_property_save_exit),
                color = AuthColors.OrangeText,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.ssp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.sdp))
                    .clickable(enabled = !state.busy, role = Role.Button) { onIntent(AddPropertyIntent.SaveAndExit) }
                    .padding(10.sdp),
            )
        }
    }
}

/**
 * Live listing edits: "stays live" info on every step, the admin's note for a rejected change,
 * or "being verified" (read-only) while SafeRent checks it.
 */
@Composable
private fun ChangesBanner(state: AddPropertyState) {
    if (!state.isLiveEdit) return
    val rejected = state.revision?.status == com.saferenttechnologies.android.domain.model.RevisionStatus.REJECTED
    val (text, color) = when {
        state.locked -> stringResource(R.string.changes_wizard_locked) to Color(0xFF2563EB)
        rejected -> stringResource(R.string.changes_wizard_rejected, state.revision?.moderationNote.orEmpty()).trim() to AuthColors.Error
        else -> stringResource(R.string.changes_wizard_info) to Color(0xFF0D9488)
    }
    Text(
        text,
        color = color,
        fontFamily = Inter,
        fontSize = 13.ssp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.sdp),
    )
    Spacer(Modifier.height(14.sdp))
}

@Composable
private fun StepHeader(state: AddPropertyState) {
    val type = state.type
    val title = when {
        state.step >= WizardStep.LAST && state.isLiveEdit -> R.string.changes_review_title
        (state.isUnitPriced || state.isRoomPriced) && state.step == WizardStep.PRICING -> R.string.apartment_common_charges
        (state.isUnitPriced || state.isRoomPriced) && state.step == WizardStep.PHOTOS -> R.string.apartment_building_photos
        state.step != WizardStep.UNITS -> stepTitle(state.step)
        type?.structure?.usesRooms == true -> R.string.step_rooms_beds
        type?.isFlat == true -> R.string.step_flats
        else -> R.string.step_units_only
    }
    Text(
        stringResource(title),
        color = AuthColors.Ink,
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.ssp,
    )
}

fun stepTitle(step: Int): Int = when (step) {
    WizardStep.CATEGORY -> R.string.step_category
    WizardStep.BASIC -> R.string.step_basic
    WizardStep.LOCATION -> R.string.step_location
    WizardStep.BUILDING -> R.string.step_building
    WizardStep.UNITS -> R.string.step_units
    WizardStep.PRICING -> R.string.step_pricing
    WizardStep.OPTIONAL -> R.string.step_optional
    WizardStep.PHOTOS -> R.string.step_photos
    WizardStep.DOCUMENTS -> R.string.step_documents
    else -> R.string.step_review
}

@Composable
private fun Banner(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val text = state.bannerText ?: state.bannerMessage?.let { stringResource(it.res()) } ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.sdp, vertical = 8.sdp)
            .clip(RoundedCornerShape(12.sdp))
            .background(AuthColors.Error.copy(alpha = 0.10f))
            .clickable { onIntent(AddPropertyIntent.DismissBanner) }
            .padding(12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = AuthColors.Error, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_close), null, tint = AuthColors.Error, modifier = Modifier.size(16.sdp))
    }
}

private fun AddPropertyMessage.res(): Int = when (this) {
    AddPropertyMessage.NETWORK_OR_SERVER -> R.string.add_property_error_generic
    AddPropertyMessage.VERSION_CONFLICT -> R.string.add_property_error_conflict
    AddPropertyMessage.MAX_PHOTOS -> R.string.add_property_error_max_photos
    AddPropertyMessage.CHANGES_LOCKED -> R.string.changes_wizard_locked
    AddPropertyMessage.UNITS_LIMIT -> R.string.add_property_error_units_limit
    AddPropertyMessage.UNIT_OCCUPIED -> R.string.add_property_error_occupied
    AddPropertyMessage.PHOTO_REQUIRED -> R.string.add_property_error_photo_required
    AddPropertyMessage.UNIT_REQUIRED -> R.string.add_property_error_unit_required
    AddPropertyMessage.PIN_INVALID -> R.string.add_property_error_pin
    AddPropertyMessage.REQUIRED_PHOTOS_MISSING -> R.string.add_property_error_bedroom_photos
    AddPropertyMessage.WAIT_FOR_UPLOADS -> R.string.add_property_error_wait_uploads
}

@Composable
private fun BottomBar(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val editorOpen = state.unitEditor != null || state.roomEditor != null || state.floorsUnitId != null
    if (editorOpen || state.types == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.sdp, vertical = 12.sdp)) {
        if (state.step == WizardStep.OPTIONAL) {
            Text(
                stringResource(R.string.add_property_skip_step),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.busy, role = Role.Button) { onIntent(AddPropertyIntent.Skip) }
                    .padding(bottom = 12.sdp),
            )
        }
        // Documents is the last step → "Submit for verification" instead of Next.
        val isReview = state.step >= WizardStep.LAST
        Row(horizontalArrangement = Arrangement.spacedBy(12.sdp), verticalAlignment = Alignment.CenterVertically) {
            if (state.step > WizardStep.CATEGORY) {
                val shape = RoundedCornerShape(14.sdp)
                Box(
                    Modifier
                        .height(56.sdp)
                        .weight(if (isReview) 0.32f else 0.4f) // longer "Submit for verification" gets more room
                        .clip(shape)
                        .border(1.5.sdp, AuthColors.Border, shape)
                        .clickable(enabled = !state.busy, role = Role.Button) { onIntent(AddPropertyIntent.Back) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.add_property_back), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.ssp)
                }
            }
            AuthPrimaryButton(
                text = stringResource(
                    when {
                        !isReview -> R.string.add_property_next
                        state.isLiveEdit -> R.string.changes_submit_for_verification
                        state.needsSubmit -> R.string.add_property_submit
                        else -> R.string.add_property_save_changes
                    },
                ),
                // Change under verification → read-only: Next only browses, submit is off.
                enabled = !state.busy && (state.step != WizardStep.CATEGORY || state.selectedTypeKey != null) && !(isReview && state.locked),
                loading = state.saving,
                onClick = { onIntent(if (isReview) AddPropertyIntent.Submit else AddPropertyIntent.Next) },
                modifier = Modifier.weight(if (isReview) 0.68f else 0.6f),
                // "Submit for verification" is long → smaller text so it fits in full.
                fontSize = if (isReview) 14.ssp else 17.ssp,
            )
        }
    }
}

@Composable
private fun RetryBox(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.sdp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.add_property_error_generic), color = AuthColors.Body, fontFamily = Inter, fontSize = 15.ssp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.sdp))
        AuthPrimaryButton(text = stringResource(R.string.account_blocked_try_again), enabled = true, loading = false, onClick = onRetry)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftsSheet(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(AddPropertyIntent.DismissDrafts) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Card,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.sdp).padding(bottom = 20.sdp)) {
            Text(stringResource(R.string.add_property_drafts_title), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Spacer(Modifier.height(12.sdp))
            state.drafts.orEmpty().forEach { draft ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.sdp)
                        .clip(RoundedCornerShape(12.sdp))
                        .clickable(role = Role.Button) { onIntent(AddPropertyIntent.ContinueDraft(draft.id)) }
                        .padding(vertical = 10.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_home_outline), null, tint = AuthColors.OrangeText, modifier = Modifier.size(22.sdp))
                    Spacer(Modifier.width(12.sdp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.add_property_continue_draft, draft.title.ifBlank { draft.propertyTypeLabel.ifBlank { draft.code } }),
                            color = AuthColors.Ink,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.ssp,
                        )
                        Text(stringResource(R.string.add_property_step_of, draft.wizardStep, WizardStep.COUNT), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    }
                }
            }
            Spacer(Modifier.height(12.sdp))
            AuthPrimaryButton(
                text = stringResource(R.string.add_property_start_new),
                enabled = true,
                loading = false,
                onClick = { onIntent(AddPropertyIntent.StartNew) },
            )
        }
    }
}

@Composable
private fun SubmittedContent(liveChanges: Boolean, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Column(
        Modifier
            .fillMaxSize()
            .background(AuthColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.sdp).clip(CircleShape).background(AuthColors.GreenBg), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_check_circle), null, tint = AuthColors.Green, modifier = Modifier.size(44.sdp))
        }
        Spacer(Modifier.height(24.sdp))
        Text(
            stringResource(if (liveChanges) R.string.changes_sent_title else R.string.add_property_submitted_title),
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.ssp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.sdp))
        Text(
            stringResource(if (liveChanges) R.string.changes_sent_body else R.string.add_property_submitted_body),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 15.ssp,
            lineHeight = 22.ssp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.sdp))
        AuthPrimaryButton(
            text = stringResource(if (liveChanges) R.string.changes_back_to_property else R.string.add_property_back_to_dashboard),
            enabled = true,
            loading = false,
            onClick = onDone,
        )
    }
}
