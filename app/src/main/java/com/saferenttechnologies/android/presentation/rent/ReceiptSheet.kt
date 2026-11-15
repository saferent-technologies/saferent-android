package com.saferenttechnologies.android.presentation.rent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.RentReceipt
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.money
import com.saferenttechnologies.android.presentation.manage.prettyDate
import com.saferenttechnologies.android.presentation.property.optionLabel
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** Opens a cached receipt PDF in a viewer; false → no PDF app. */
fun Context.viewPdf(uri: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    true
} catch (_: ActivityNotFoundException) {
    false
}

fun Context.sharePdf(uri: String) = startActivity(
    Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        null,
    ),
)

private fun day(iso: String): String = if (iso.isBlank()) "" else prettyDate(iso.take(10))

/** Receipt details + View / Download / Share (the PDF from the server). Rent and Transactions use it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptSheet(
    receipt: RentReceipt,
    tenantName: String,
    home: String,
    fallbackAmount: String,
    busy: ReceiptAction?,
    onAction: (ReceiptAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val r = receipt
    ModalBottomSheet(
        onDismissRequest = { if (busy == null) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.sdp).clip(RoundedCornerShape(14.sdp)).background(AuthColors.GreenBg), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_document), null, tint = AuthColors.Green, modifier = Modifier.size(24.sdp))
                }
                Spacer(Modifier.width(12.sdp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rent_receipt), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
                    if (r.number.isNotBlank()) Text(r.number, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                }
                StatusPill(stringResource(R.string.rent_paid), AuthColors.Green)
            }
            Spacer(Modifier.height(16.sdp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Cream).border(1.sdp, AuthColors.CreamBorder, RoundedCornerShape(14.sdp)).padding(16.sdp),
            ) {
                Text(stringResource(R.string.rent_amount_paid), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
                Text(money(r.amount.ifBlank { fallbackAmount }), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 26.ssp)
                Spacer(Modifier.height(10.sdp))
                listOfNotNull(
                    stringResource(R.string.rent_r_tenant) to tenantName,
                    stringResource(R.string.tenants_home) to home,
                    r.period.takeIf { it.isNotBlank() }?.let { stringResource(R.string.rent_r_for) to it },
                    r.paidAt.takeIf { it.isNotBlank() }?.let { stringResource(R.string.rent_r_paid_on) to day(it) },
                    r.mode.takeIf { it.isNotBlank() }?.let { stringResource(R.string.rent_r_mode) to optionLabel(it) },
                ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.sdp)) {
                        Text(k, color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.weight(1f))
                        Text(v, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
                    }
                }
            }
            Spacer(Modifier.height(16.sdp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                FileButton(stringResource(R.string.rent_view), R.drawable.ic_search, busy == ReceiptAction.VIEW, busy == null, Modifier.weight(1f)) { onAction(ReceiptAction.VIEW) }
                FileButton(stringResource(R.string.rent_download), R.drawable.ic_arrow_upward, busy == ReceiptAction.DOWNLOAD, busy == null, Modifier.weight(1f), flip = true) {
                    onAction(ReceiptAction.DOWNLOAD)
                }
                FileButton(stringResource(R.string.rent_share), R.drawable.ic_share, busy == ReceiptAction.SHARE, busy == null, Modifier.weight(1f)) { onAction(ReceiptAction.SHARE) }
            }
        }
    }
}

@Composable
private fun FileButton(text: String, icon: Int, busy: Boolean, enabled: Boolean, modifier: Modifier, flip: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.sdp))
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(22.sdp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(color = AuthColors.Orange, strokeWidth = 2.sdp, modifier = Modifier.size(18.sdp))
            else Icon(
                painterResource(icon),
                null,
                tint = if (enabled) AuthColors.OrangeText else AuthColors.Muted,
                // Download = the "up" arrow turned down (no extra icon file).
                modifier = Modifier.size(20.sdp).rotate(if (flip) 180f else 0f),
            )
        }
        Spacer(Modifier.height(4.sdp))
        Text(text, color = if (enabled) AuthColors.Ink else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
    }
}
