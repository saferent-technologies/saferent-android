package com.saferenttechnologies.android.presentation.documents

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.saferenttechnologies.android.presentation.common.SkeletonTrailing
import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.TenantDoc
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.manage.StatusPill
import com.saferenttechnologies.android.presentation.manage.calendarDate
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.presentation.property.SelectField
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.presentation.requests.AddPhotoButtons
import com.saferenttechnologies.android.presentation.requests.PhotoStrip
import com.saferenttechnologies.android.presentation.requests.rememberPhotoPicker
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

/** No screenshots / screen recording / recent-apps preview while ID documents are on screen. */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** ID photos: memory only — never written to Coil's disk cache. */
@Composable
private fun privateImage(url: String) = ImageRequest.Builder(LocalContext.current)
    .data(absoluteUrl(url))
    .diskCachePolicy(CachePolicy.DISABLED)
    .build()

/** Tenant: my documents (upload, share with my landlord, delete). Landlord: what a tenant shared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(state: DocumentsState, effects: Flow<DocumentsEffect>, onIntent: (DocumentsIntent) -> Unit) {
    SecureWindow()
    BackHandler { onIntent(DocumentsIntent.Close) }
    val context = LocalContext.current
    val unreadable = stringResource(R.string.pay_proof_unreadable)
    val pdfBad = stringResource(R.string.docs_pdf_unreadable)
    val pdfBig = stringResource(R.string.docs_pdf_too_big)
    val uploaded = stringResource(R.string.docs_uploaded)
    val deleted = stringResource(R.string.docs_deleted)
    LaunchedEffect(Unit) {
        effects.collect { e ->
            val text = when (e) {
                is DocumentsEffect.Message -> when (e.text) {
                    DocumentsViewModel.PHOTO_UNREADABLE -> unreadable
                    DocumentsViewModel.PDF_UNREADABLE -> pdfBad
                    DocumentsViewModel.PDF_TOO_BIG -> pdfBig
                    else -> e.text
                }
                is DocumentsEffect.Done -> if (e.what == DocumentsViewModel.DONE_UPLOADED) uploaded else deleted
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button) { onIntent(DocumentsIntent.Close) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            Spacer(Modifier.width(12.sdp))
            Text(
                stringResource(if (state.landlord) R.string.docs_shared_title else R.string.docs_title),
                color = AuthColors.Ink,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.ssp,
            )
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(DocumentsIntent.Refresh) }, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.sdp)) {
                PrivacyNote(if (state.landlord) R.string.docs_note_landlord else R.string.docs_note_tenant)
                Spacer(Modifier.height(12.sdp))
                when {
                    state.loading -> ListSkeleton(rows = 4, thumbnail = 42.sdp, lines = 2, trailing = SkeletonTrailing.PILL, outerPadding = 0.sdp, actions = 2)
                    state.landlord -> LandlordList(state, onIntent)
                    else -> TenantList(state, onIntent)
                }
                Spacer(Modifier.height(16.sdp))
            }
        }
        if (!state.landlord && !state.loading) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.sdp, vertical = 10.sdp)) {
                AuthPrimaryButton(
                    text = stringResource(R.string.docs_add),
                    enabled = state.addable.isNotEmpty() && !state.busy,
                    loading = false,
                    onClick = { onIntent(DocumentsIntent.StartUpload) },
                    fontSize = 15.ssp,
                )
            }
        }
    }
    state.upload?.let { UploadSheet(state, onIntent) }
    state.doc(state.shareFor)?.let { ShareSheet(state, it, onIntent) }
    state.doc(state.confirmDelete)?.let { d ->
        AlertDialog(
            onDismissRequest = { onIntent(DocumentsIntent.AskDelete(null)) },
            title = { Text(stringResource(R.string.docs_delete_title, d.typeLabel), fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.docs_delete_body), fontFamily = Inter) },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = { onIntent(DocumentsIntent.ConfirmDelete) }) { Text(stringResource(R.string.docs_delete), color = AuthColors.Error) }
            },
            dismissButton = { TextButton(onClick = { onIntent(DocumentsIntent.AskDelete(null)) }) { Text(stringResource(android.R.string.cancel), color = AuthColors.Body) } },
            containerColor = AuthColors.Card,
        )
    }
    state.viewer?.let { v ->
        when {
            v.pdfLoading || v.pdf != null -> PdfViewer(v.pdf) { onIntent(DocumentsIntent.CloseViewer) }
            else -> PageViewer(v.images, v.start) { onIntent(DocumentsIntent.CloseViewer) }
        }
    }
}

@Composable
private fun PrivacyNote(text: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(R.drawable.ic_shield_check), null, tint = AuthColors.Green, modifier = Modifier.size(20.sdp))
        Spacer(Modifier.width(10.sdp))
        Text(stringResource(text), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
    }
}

@Composable
private fun ColumnScope.TenantList(state: DocumentsState, onIntent: (DocumentsIntent) -> Unit) {
    val docs = state.vault.documents
    if (docs.isEmpty()) {
        Empty(state.error ?: stringResource(R.string.docs_empty))
        return
    }
    docs.forEach { d ->
        DocCard(d, state, landlord = false, onIntent)
        Spacer(Modifier.height(10.sdp))
    }
}

@Composable
private fun ColumnScope.LandlordList(state: DocumentsState, onIntent: (DocumentsIntent) -> Unit) {
    when {
        state.accessEnded -> Empty(stringResource(R.string.docs_access_ended))
        state.shared?.documents.isNullOrEmpty() -> Empty(state.error ?: stringResource(R.string.docs_none_shared))
        else -> {
            state.shared?.tenantName?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.docs_shared_by, it), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                Spacer(Modifier.height(8.sdp))
            }
            state.shared?.documents.orEmpty().forEach { d ->
                DocCard(d, state, landlord = true, onIntent)
                Spacer(Modifier.height(10.sdp))
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = AuthColors.Muted, fontFamily = Inter, fontSize = 14.ssp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 40.sdp, horizontal = 16.sdp))
}

@Composable
private fun DocCard(d: TenantDoc, state: DocumentsState, landlord: Boolean, onIntent: (DocumentsIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(12.sdp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.sdp).clip(RoundedCornerShape(10.sdp)).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_lock), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
            }
            Spacer(Modifier.width(12.sdp))
            Column(Modifier.weight(1f)) {
                Text(d.typeLabel, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
                Text(
                    listOfNotNull(
                        if (d.isPdf) stringResource(R.string.docs_pdf) else pluralStringResource(R.plurals.docs_pages, d.pages.size, d.pages.size),
                        d.uploadedAt.takeIf { it.isNotBlank() }?.let { stringResource(R.string.docs_added_on, calendarDate(it)) },
                    ).joinToString(" · "),
                    color = AuthColors.Body,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                )
            }
            if (!landlord) {
                val share = d.shares.firstOrNull()
                if (share != null) StatusPill(stringResource(R.string.docs_shared_chip), AuthColors.Green) else StatusPill(stringResource(R.string.docs_private_chip), AuthColors.Muted)
            }
        }
        if (!landlord && d.shares.isNotEmpty()) {
            Spacer(Modifier.height(6.sdp))
            Text(
                stringResource(R.string.docs_shared_with, d.shares.joinToString(", ") { it.landlordName.ifBlank { it.propertyTitle } }),
                color = AuthColors.Body,
                fontFamily = Inter,
                fontSize = 12.ssp,
            )
        }
        Spacer(Modifier.height(10.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            OutlineAction(stringResource(R.string.docs_view), R.drawable.ic_document, enabled = d.pages.isNotEmpty(), modifier = Modifier.weight(1f)) { onIntent(DocumentsIntent.View(d)) }
            if (!landlord) {
                OutlineAction(stringResource(R.string.docs_share), R.drawable.ic_share, enabled = !state.busy, modifier = Modifier.weight(1f)) { onIntent(DocumentsIntent.ShowShare(d.id)) }
                Box(
                    Modifier.size(44.sdp).clip(RoundedCornerShape(12.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
                        .clickable(enabled = !state.busy, role = Role.Button) { onIntent(DocumentsIntent.AskDelete(d.id)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.docs_delete), tint = AuthColors.Error, modifier = Modifier.size(20.sdp)) }
            }
        }
    }
}

/** Type (dropdown) + 1–2 photos (front / back) → Upload. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSheet(state: DocumentsState, onIntent: (DocumentsIntent) -> Unit) {
    val f = state.upload ?: return
    val max = (f.type?.pages ?: 2).coerceIn(1, 2)
    val picker = rememberPhotoPicker(max - f.pages.size) { onIntent(DocumentsIntent.AddPages(it)) }
    val pickPdf = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onIntent(DocumentsIntent.PickPdf(it.toString())) }
    }
    ModalBottomSheet(
        onDismissRequest = { onIntent(DocumentsIntent.DismissUpload) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Text(stringResource(R.string.docs_add), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            Spacer(Modifier.height(12.sdp))
            val types = state.addable
            SelectField(
                label = stringResource(R.string.docs_type) + " *",
                options = types.map { it.key },
                value = f.type?.key.orEmpty(),
                error = null,
                enabled = !state.busy,
                labels = types.associate { it.key to it.label },
                placeholder = stringResource(R.string.ann_pick_audience),
            ) { key -> types.firstOrNull { it.key == key }?.let { onIntent(DocumentsIntent.PickType(it)) } }
            if (f.type != null) {
                Spacer(Modifier.height(14.sdp))
                // A PDF instead of photos.
                if (f.pdf != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Surface).padding(12.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_document), null, tint = AuthColors.OrangeText, modifier = Modifier.size(22.sdp))
                        Spacer(Modifier.width(10.sdp))
                        Text(f.pdfName.ifBlank { stringResource(R.string.docs_pdf) }, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(32.sdp).clip(CircleShape).clickable(enabled = !state.busy, role = Role.Button) { onIntent(DocumentsIntent.RemovePdf) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.explore_clear), tint = AuthColors.Body, modifier = Modifier.size(18.sdp)) }
                    }
                } else {
                Text(
                    stringResource(if (max > 1) R.string.docs_photos_front_back else R.string.docs_photos_one),
                    color = AuthColors.Ink,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.ssp,
                )
                Spacer(Modifier.height(8.sdp))
                PhotoStrip(f.pages, onRemove = { onIntent(DocumentsIntent.RemovePage(it)) })
                if (f.pages.isNotEmpty()) Spacer(Modifier.height(8.sdp))
                AddPhotoButtons(picker, enabled = !state.busy && f.pages.size < max)
                }
                Spacer(Modifier.height(10.sdp))
                Text(stringResource(R.string.docs_or_pdf), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.sdp))
                OutlineAction(
                    stringResource(if (f.pdf != null) R.string.docs_change_pdf else R.string.docs_pick_pdf),
                    R.drawable.ic_document,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { pickPdf.launch(arrayOf("application/pdf")) }
            }
            Spacer(Modifier.height(16.sdp))
            AuthPrimaryButton(text = stringResource(R.string.docs_upload), enabled = state.canUpload, loading = state.busy, onClick = { onIntent(DocumentsIntent.SendUpload) }, fontSize = 15.ssp)
        }
    }
}

/** One switch per current landlord (active lease). Access ends by itself when that lease ends. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(state: DocumentsState, d: TenantDoc, onIntent: (DocumentsIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(DocumentsIntent.ShowShare(null)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AuthColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.sdp).padding(bottom = 24.sdp).navigationBarsPadding()) {
            Text(stringResource(R.string.docs_share_title, d.typeLabel), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.ssp)
            Spacer(Modifier.height(6.sdp))
            Text(stringResource(R.string.docs_share_body), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            Spacer(Modifier.height(12.sdp))
            if (state.vault.leases.isEmpty()) {
                Empty(stringResource(R.string.docs_no_lease))
            }
            state.vault.leases.forEach { l ->
                val on = d.sharedWith(l.leaseId)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.sdp)).background(AuthColors.Field).border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
                        .clickable(enabled = !state.busy, role = Role.Switch) { onIntent(DocumentsIntent.ToggleShare(d.id, l.leaseId)) }
                        .padding(horizontal = 14.sdp, vertical = 10.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l.title, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp)
                        if (l.subtitle.isNotBlank()) Text(l.subtitle, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
                    }
                    Switch(
                        checked = on,
                        onCheckedChange = { onIntent(DocumentsIntent.ToggleShare(d.id, l.leaseId)) },
                        enabled = !state.busy,
                        colors = SwitchDefaults.colors(checkedTrackColor = AuthColors.Orange, checkedThumbColor = Color.White),
                    )
                }
                Spacer(Modifier.height(8.sdp))
            }
        }
    }
}

@Composable
private fun PageViewer(urls: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Dialogs have their own window → secure it too.
        SecureDialogWindow()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = start) { urls.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                AsyncImage(model = privateImage(urls[i]), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.sdp).size(TOP_ICON).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.explore_clear), tint = Color.White, modifier = Modifier.size(TOP_ICON_GLYPH)) }
            if (urls.size > 1) {
                Text("${pager.currentPage + 1} / ${urls.size}", color = Color.White, fontFamily = Inter, fontSize = 13.ssp, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp))
            }
        }
    }
}

@Composable
private fun SecureDialogWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }
}

/**
 * PDF shown on the phone with Android's own PdfRenderer (no library). The bytes go to a temp file only
 * long enough to open it (deleted straight away), pages are drawn to memory, nothing is kept.
 */
@Composable
private fun PdfViewer(bytes: ByteArray?, onClose: () -> Unit) {
    val context = LocalContext.current
    var pages by androidx.compose.runtime.remember(bytes) { androidx.compose.runtime.mutableStateOf<List<android.graphics.Bitmap>?>(null) }
    var failed by androidx.compose.runtime.remember(bytes) { androidx.compose.runtime.mutableStateOf(false) }
    val width = (context.resources.displayMetrics.widthPixels * 1.5f).toInt().coerceAtMost(2000)
    LaunchedEffect(bytes) {
        if (bytes == null) return@LaunchedEffect
        val out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { renderPdf(context, bytes, width) }
        if (out == null) failed = true else pages = out
    }
    androidx.compose.runtime.DisposableEffect(bytes) { onDispose { pages?.forEach { it.recycle() } } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SecureDialogWindow()
        Box(Modifier.fillMaxSize().background(Color(0xFF1F2937))) {
            val list = pages
            when {
                failed -> Text(stringResource(R.string.docs_pdf_unreadable), color = Color.White, fontFamily = Inter, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(24.sdp))
                list == null -> androidx.compose.material3.CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.align(Alignment.Center))
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 64.sdp, bottom = 24.sdp, start = 8.sdp, end = 8.sdp),
                    verticalArrangement = Arrangement.spacedBy(8.sdp),
                ) {
                    items(list.size) { i ->
                        androidx.compose.foundation.Image(
                            bitmap = list[i].asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().background(Color.White),
                        )
                    }
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.sdp).size(TOP_ICON).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.explore_clear), tint = Color.White, modifier = Modifier.size(TOP_ICON_GLYPH)) }
        }
    }
}

/** Max pages drawn (ID documents are short). */
private const val PDF_MAX_PAGES = 20

private fun renderPdf(context: android.content.Context, bytes: ByteArray, width: Int): List<android.graphics.Bitmap>? {
    val dir = java.io.File(context.cacheDir, "doc-view").apply { mkdirs() }
    val file = java.io.File(dir, java.util.UUID.randomUUID().toString())
    return runCatching {
        file.writeBytes(bytes)
        val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        file.delete() // the open descriptor keeps working; nothing stays on disk
        fd.use { pfd ->
            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                (0 until minOf(renderer.pageCount, PDF_MAX_PAGES)).map { i ->
                    renderer.openPage(i).use { page ->
                        val h = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bmp = android.graphics.Bitmap.createBitmap(width, h, android.graphics.Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }
    }.also { file.delete() }.getOrNull()
}
