package com.saferenttechnologies.android.presentation.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.ApprovalBlocker
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.explore.Amber
import com.saferenttechnologies.android.presentation.property.propertyFieldColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/**
 * Property waiting for SafeRent verification.
 * Moderator: blockers (server messages) + Approve / Approve without ownership document / Reject.
 * Owner: "Verify your identity" / "Upload an ownership proof" / "Waiting for SafeRent review".
 */
@Composable
fun ApprovalSection(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    val approval = state.detail?.approval ?: return
    when {
        state.isModerating -> Column(Modifier.fillMaxWidth().padding(vertical = 6.sdp)) {
            approval.blockers.forEach { b ->
                Card(b.message, Amber) {
                    if (b.code == ApprovalBlocker.OWNER_NOT_VERIFIED && (b.ownerKycSubmitted || approval.ownerKycSubmitted)) {
                        Note(stringResource(R.string.approval_owner_submitted))
                    }
                    if (b.code == ApprovalBlocker.OWNER_NOT_VERIFIED) Note(stringResource(R.string.approval_verify_owner_staff))
                    if (b.code == ApprovalBlocker.OWNERSHIP_PROOF_REQUIRED) {
                        SmallAction(stringResource(R.string.approval_check_documents)) { onIntent(PropertyDetailIntent.CheckDocuments) }
                    }
                }
            }
            Spacer(Modifier.height(4.sdp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.sdp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(52.sdp).clip(RoundedCornerShape(14.sdp))
                        .border(1.sdp, if (state.busy) AuthColors.Border else AuthColors.Error, RoundedCornerShape(14.sdp))
                        .clickable(enabled = !state.busy, role = Role.Button) { onIntent(PropertyDetailIntent.AskReject) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.approval_reject), color = if (state.busy) AuthColors.Muted else AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp) }
                AuthPrimaryButton(
                    text = stringResource(R.string.approval_approve),
                    enabled = approval.canApprove && !state.busy,
                    loading = state.busy,
                    onClick = { onIntent(PropertyDetailIntent.AskApprove(withoutProof = false)) },
                    modifier = Modifier.weight(1.3f),
                    fontSize = 15.ssp,
                    shadow = false,
                )
            }
            if (approval.canApproveWithoutProof) {
                Spacer(Modifier.height(8.sdp))
                SmallAction(stringResource(R.string.approval_without_proof), enabled = !state.busy) { onIntent(PropertyDetailIntent.AskApprove(withoutProof = true)) }
            }
        }
        state.ownerWaiting -> Column(Modifier.fillMaxWidth().padding(vertical = 6.sdp)) {
            if (approval.blockers.isEmpty()) {
                Card(stringResource(R.string.approval_waiting), AuthColors.GreenText) { Note(stringResource(R.string.kyc_review_time)) }
            }
            if (!approval.ownerVerified) {
                if (approval.ownerKycSubmitted) {
                    Card(stringResource(R.string.approval_owner_review), AuthColors.OrangeText) { Note(stringResource(R.string.kyc_review_time)) }
                } else {
                    Card(stringResource(R.string.approval_verify_identity_title), AuthColors.Error) {
                        SmallAction(stringResource(R.string.kyc_verify_now)) { onIntent(PropertyDetailIntent.OpenVerifySheet) }
                    }
                }
            }
            if (!approval.ownershipProofVerified) {
                Card(stringResource(R.string.approval_upload_proof), Amber) {
                    Note(stringResource(R.string.approval_upload_proof_note))
                    SmallAction(stringResource(R.string.approval_check_documents)) { onIntent(PropertyDetailIntent.CheckDocuments) }
                }
            }
        }
    }
}

/** Confirm / reject / blocked / verify sheets (drawn once, over the property screen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalDialogs(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    val approval = state.detail?.approval
    state.approveConfirm?.let { without ->
        AlertDialog(
            onDismissRequest = { onIntent(PropertyDetailIntent.DismissApproval) },
            containerColor = AuthColors.Card,
            title = { Text(approval?.confirmTitle?.ifBlank { null } ?: stringResource(R.string.approval_confirm_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text(approval?.confirmMessage?.ifBlank { null } ?: stringResource(R.string.approval_confirm_body), fontFamily = Inter, color = AuthColors.Body)
                    if (without) Text(stringResource(R.string.approval_without_proof_note), fontFamily = Inter, color = AuthColors.Error, modifier = Modifier.padding(top = 8.sdp))
                }
            },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = { onIntent(PropertyDetailIntent.ConfirmApprove) }) {
                    Text(stringResource(R.string.approval_approve), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { onIntent(PropertyDetailIntent.DismissApproval) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
        )
    }
    state.rejectReason?.let { reason ->
        val ok = reason.trim().length >= 5
        AlertDialog(
            onDismissRequest = { onIntent(PropertyDetailIntent.DismissApproval) },
            containerColor = AuthColors.Card,
            title = { Text(stringResource(R.string.approval_reject_title), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { onIntent(PropertyDetailIntent.RejectReason(it)) },
                    label = { Text(stringResource(R.string.approval_reject_reason), fontFamily = Inter) },
                    minLines = 3,
                    maxLines = 6,
                    colors = propertyFieldColors(),
                    shape = RoundedCornerShape(12.sdp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = ok && !state.busy, onClick = { onIntent(PropertyDetailIntent.ConfirmReject) }) {
                    Text(stringResource(R.string.approval_reject), color = if (ok) AuthColors.Error else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { onIntent(PropertyDetailIntent.DismissApproval) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
        )
    }
    // 412 from approve / publish, or the owner's "Verify now": bottom sheet with the next step.
    val block = state.approvalBlock
    if (block != null || state.verifySheet) {
        val ownerSide = !state.isModerating
        val ownerBlock = block == null || block.code == ApprovalBlocker.OWNER_NOT_VERIFIED
        ModalBottomSheet(
            onDismissRequest = { onIntent(PropertyDetailIntent.DismissApproval) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AuthColors.Card,
            dragHandle = { BottomSheetDefaults.DragHandle(color = AuthColors.Border) },
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.sdp, end = 24.sdp, bottom = 16.sdp, top = 4.sdp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(64.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (ownerBlock) R.drawable.ic_shield_check else R.drawable.ic_document), null, tint = AuthColors.OrangeText, modifier = Modifier.size(30.sdp))
                }
                Spacer(Modifier.height(16.sdp))
                Text(
                    stringResource(if (ownerBlock) R.string.approval_verify_identity_title else R.string.approval_upload_proof),
                    color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 19.ssp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.sdp))
                Text(
                    block?.message?.ifBlank { null } ?: stringResource(R.string.approval_verify_sheet_body),
                    color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.sdp))
                when {
                    // Owner: verify identity (Owner verification) or ask support.
                    ownerBlock && ownerSide -> {
                        AuthPrimaryButton(text = stringResource(R.string.approval_verify_identity), enabled = true, loading = false, onClick = { onIntent(PropertyDetailIntent.VerifyIdentity) }, fontSize = 15.ssp)
                        Spacer(Modifier.height(4.sdp))
                        SheetLink(stringResource(R.string.approval_contact_support)) { onIntent(PropertyDetailIntent.ContactSupportVerify) }
                    }
                    !ownerBlock -> {
                        AuthPrimaryButton(text = stringResource(R.string.approval_check_documents), enabled = true, loading = false, onClick = { onIntent(PropertyDetailIntent.CheckDocuments) }, fontSize = 15.ssp)
                        Spacer(Modifier.height(4.sdp))
                        if (ownerSide) SheetLink(stringResource(R.string.approval_contact_support)) { onIntent(PropertyDetailIntent.ContactSupportVerify) }
                        else SheetLink(stringResource(R.string.add_property_cancel)) { onIntent(PropertyDetailIntent.DismissApproval) }
                    }
                    // Moderator, owner not verified: done in the staff portal.
                    else -> {
                        Text(stringResource(R.string.approval_verify_owner_staff), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.sdp))
                        SheetLink(stringResource(R.string.add_property_cancel)) { onIntent(PropertyDetailIntent.DismissApproval) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(title: String, color: Color, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.sdp).clip(RoundedCornerShape(14.sdp)).background(color.copy(alpha = 0.08f))
            .border(1.sdp, color.copy(alpha = 0.35f), RoundedCornerShape(14.sdp)).padding(14.sdp),
    ) {
        Text(title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.padding(top = 4.sdp))
}

@Composable
private fun SmallAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (enabled) AuthColors.OrangeText else AuthColors.Muted,
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 13.ssp,
        modifier = Modifier.padding(top = 8.sdp).clip(RoundedCornerShape(50))
            .border(1.sdp, if (enabled) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.sdp, vertical = 7.sdp),
    )
}

@Composable
private fun SheetLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = AuthColors.Body,
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.ssp,
        modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.sdp, vertical = 14.sdp),
    )
}

