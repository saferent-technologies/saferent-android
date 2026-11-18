package com.saferenttechnologies.android.presentation.manage

import com.saferenttechnologies.android.presentation.support.ReportProblemLink
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import com.saferenttechnologies.android.domain.model.BuildingPhotos
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.PropertyAction
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.property.CameraCaptureScreen
import com.saferenttechnologies.android.presentation.property.Chip
import com.saferenttechnologies.android.presentation.property.absoluteUrl
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow

/** Property detail: header, status banners, tabs, actions (only those PropertyActions allows). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PropertyDetailScreen(
    state: PropertyDetailState,
    effects: Flow<PropertyDetailEffect>,
    onIntent: (PropertyDetailIntent) -> Unit,
    onOpenEditor: (propertyId: String, step: Int, newUnit: Boolean) -> Unit,
    onChanged: () -> Unit,
    onNotFound: () -> Unit = {},
    onOpenLease: (leaseId: String, preview: com.saferenttechnologies.android.domain.model.LeaseSummary) -> Unit = { _, _ -> },
    onReviewLease: (leaseId: String) -> Unit = {},
    /** "Report a problem" → support ticket (PROPERTY, this property). */
    onReportProblem: ((propertyId: String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val uploading = stringResource(R.string.manage_photo_uploading)
    val copyLabel = stringResource(R.string.manage_copy_link)
    val linkReady = stringResource(R.string.manage_link_ready)
    val reviewFirst = stringResource(R.string.manage_review_first)
    val copied = stringResource(R.string.manage_link_copied)
    val noApp = stringResource(R.string.account_blocked_no_app)

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is PropertyDetailEffect.Message ->
                    snackbar.showSnackbar(if (effect.text == PropertyDetailViewModel.UPLOADING_MESSAGE) uploading else effect.text)
                is PropertyDetailEffect.Share -> {
                    // Share sheet with the server's shareText, then offer "Copy link".
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, effect.text)
                    if (!context.tryStart(Intent.createChooser(send, null))) snackbar.showSnackbar(noApp)
                    if (snackbar.showSnackbar(linkReady, actionLabel = copyLabel) == SnackbarResult.ActionPerformed) {
                        context.copyToClipboard(effect.url)
                        snackbar.showSnackbar(copied)
                    }
                }
                is PropertyDetailEffect.OpenEditor -> onOpenEditor(effect.propertyId, effect.step, effect.newUnit)
                is PropertyDetailEffect.Dial -> if (!context.tryStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${effect.phone}")))) snackbar.showSnackbar(noApp)
                is PropertyDetailEffect.WhatsApp -> {
                    val digits = effect.phone.filter(Char::isDigit)
                    if (!context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))) snackbar.showSnackbar(noApp)
                }
                is PropertyDetailEffect.ContactSupport ->
                    if (!context.tryStart(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${effect.email}")))) snackbar.showSnackbar(noApp)
                PropertyDetailEffect.Changed -> onChanged()
                PropertyDetailEffect.NotFound -> onNotFound()
                is PropertyDetailEffect.OpenLease -> onOpenLease(effect.leaseId, effect.preview)
                is PropertyDetailEffect.ReviewLease -> onReviewLease(effect.leaseId)
                is PropertyDetailEffect.ReviewFirst -> snackbar.showSnackbar(reviewFirst.format(effect.name.ifBlank { "your last tenant" }))
            }
        }
    }

    val camera = state.camera
    if (camera != null) {
        CameraCaptureScreen(mode = camera, onCaptured = { onIntent(PropertyDetailIntent.Captured(it)) }, onClose = { onIntent(PropertyDetailIntent.CloseCamera) })
        return
    }
    BackHandler { onIntent(PropertyDetailIntent.Close) }

    Box(Modifier.fillMaxSize().background(AuthColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(state, onIntent)
            val detail = state.detail
            when {
                detail == null && state.loading -> Unit // spinner is drawn over the whole screen below
                detail == null -> Box(Modifier.fillMaxSize().padding(24.sdp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: stringResource(R.string.add_property_error_generic), color = AuthColors.Body, fontFamily = Inter, fontSize = 15.ssp)
                        Spacer(Modifier.height(16.sdp))
                        AuthPrimaryButton(text = stringResource(R.string.add_property_retry), enabled = true, loading = false, onClick = { onIntent(PropertyDetailIntent.Refresh) })
                    }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    // Building photos sit above the scrolling page: scroll up → they slide away,
                    // scroll down (anywhere) → they come back. Pager kept here so the page survives hiding.
                    val images = galleryImages(state)
                    val pager = rememberPagerState { images.size }
                    var galleryOpen by rememberSaveable { mutableStateOf(true) }
                    val hideOnScroll = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (available.y < -GALLERY_SCROLL_SLOP) galleryOpen = false
                                else if (available.y > GALLERY_SCROLL_SLOP) galleryOpen = true
                                return Offset.Zero
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = galleryOpen && images.isNotEmpty(),
                        enter = expandVertically(tween(260), expandFrom = Alignment.Top) + fadeIn(tween(260)),
                        exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top) + fadeOut(tween(180)),
                    ) {
                        Box {
                            HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(AuthColors.Surface)) { page ->
                                AsyncImage(model = absoluteUrl(images[page].url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            // 360° views of this property → the 360 viewer.
                            val panos = state.detail?.property?.images.orEmpty().filter { com.saferenttechnologies.android.domain.model.PanoramaRules.isPanoramaCategory(it.category) }
                            var view360 by remember { mutableStateOf(false) }
                            if (panos.isNotEmpty()) {
                                Row(
                                    Modifier.align(Alignment.BottomEnd).padding(12.sdp).clip(RoundedCornerShape(50)).background(AuthColors.Orange)
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { view360 = true }.padding(horizontal = 12.sdp, vertical = 7.sdp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(painterResource(R.drawable.ic_my_location), null, tint = Color.White, modifier = Modifier.size(16.sdp))
                                    Spacer(Modifier.width(6.sdp))
                                    Text(stringResource(R.string.pano_button), color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.ssp)
                                }
                            }
                            if (view360) {
                                com.saferenttechnologies.android.presentation.panorama.PanoramaViewer(panos.map { absoluteUrl(it.url) }) { view360 = false }
                            }
                        }
                    }
                    Column(Modifier.fillMaxSize().nestedScroll(hideOnScroll).verticalScroll(rememberScrollState())) {
                    Header(state)
                    Banners(state, onIntent)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.sdp, vertical = 8.sdp), horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                        items(DetailTab.VISIBLE) { tab ->
                            Chip(tabLabel(tab, detail.property.structure.usesRooms, state.unitWord.takeIf { state.isUnitPriced }), selected = tab == state.tab) { onIntent(PropertyDetailIntent.SelectTab(tab)) }
                        }
                    }
                    Column(Modifier.padding(horizontal = 16.sdp, vertical = 8.sdp)) {
                        when (state.tab) {
                            DetailTab.OVERVIEW -> OverviewTab(state, onIntent)
                            DetailTab.UNITS -> UnitsTab(state, onIntent)
                            DetailTab.TENANTS -> TenantsTab(state, onIntent)
                            DetailTab.INVITATIONS -> InvitationsTab(state, onIntent)
                            DetailTab.APPLICATIONS -> ApplicationsTab(state, onIntent)
                            DetailTab.PHOTOS -> PhotosTab(state, onIntent)
                            DetailTab.DOCUMENTS -> DocumentsTab(state, onIntent)
                            DetailTab.HISTORY -> HistoryTab(state, onIntent)
                        }
                    }
                    onReportProblem?.let { report ->
                        Spacer(Modifier.height(16.sdp))
                        ReportProblemLink({ report(state.propertyId) }, Modifier.align(Alignment.CenterHorizontally))
                    }
                    Spacer(Modifier.height(32.sdp))
                    }
                }
            }
        }
        // First load: the page's shape shimmers (covers the empty header / tabs).
        if (state.detail == null && state.loading) {
            com.saferenttechnologies.android.presentation.common.PropertyDetailSkeleton(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().padding(top = 56.sdp))
        }
        if (state.busy) LinearProgressIndicator(color = AuthColors.Orange, trackColor = Color.Transparent, modifier = Modifier.fillMaxWidth().statusBarsPadding())
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp))
    }

    state.confirm?.let { ConfirmDialog(it, onIntent) }
    if (state.confirmDiscard) DiscardChangesDialog(onIntent)
    state.changeProblems?.let { ChangeProblemsSheet(it, onIntent) }
    state.inviteForm?.let { InviteDialog(state, it, onIntent) }
    state.reviewPrompt?.let { ReviewFirstDialog(it, onIntent) }
    state.openUnitId?.let { id -> state.detail?.property?.units?.firstOrNull { it.id == id }?.let { FlatSheet(state, it, onIntent) } }
    state.decision?.let { DecisionDialog(state, it, onIntent) }
}

@Composable
private fun TopBar(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(R.drawable.ic_chevron_left, stringResource(R.string.add_property_back)) { onIntent(PropertyDetailIntent.Close) }
        Spacer(Modifier.width(12.sdp))
        Text(
            state.detail?.property?.code.orEmpty(),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 13.ssp,
            modifier = Modifier.weight(1f),
        )
        val actions = state.actions.filter { it != PropertyAction.CONTACT_SUPPORT || state.detail?.property?.activeStatus == "ARCHIVED" }
        if (actions.isNotEmpty()) {
            Box {
                RoundButton(R.drawable.ic_chevron_down, stringResource(R.string.manage_actions)) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(if (action == PropertyAction.EDIT && state.continuesChanges) R.string.changes_continue else actionLabel(action)),
                                    fontFamily = Inter,
                                    color = if (action in DANGER) AuthColors.Error else AuthColors.Ink,
                                )
                            },
                            onClick = {
                                menu = false
                                onIntent(PropertyDetailIntent.Action(action))
                            },
                        )
                    }
                }
            }
        }
    }
}

private val DANGER = setOf(PropertyAction.ARCHIVE, PropertyAction.DELETE_DRAFT, PropertyAction.DEACTIVATE, PropertyAction.UNPUBLISH)

/** Finger movement (px) before the gallery hides / shows — ignores tiny jitters. */
private const val GALLERY_SCROLL_SLOP = 6f

/** Apartment / PG: the top gallery is the building only — flat / room photos stay in their own sheet. */
private fun galleryImages(state: PropertyDetailState): List<com.saferenttechnologies.android.domain.model.PropertyImage> {
    val property = state.detail?.property ?: return emptyList()
    return (if (state.isUnitPriced || property.structure.usesRooms) BuildingPhotos.of(property) else property.images)
        .filterNot { com.saferenttechnologies.android.domain.model.PanoramaRules.isPanoramaCategory(it.category) }
        .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(state: PropertyDetailState) {
    val property = state.detail?.property ?: return
    val context = LocalContext.current
    val copied = stringResource(R.string.manage_code_copied)
    Column(Modifier.padding(16.sdp)) {
        PropertyStatusChip(property.publicationStatus, property.activeStatus)
        Spacer(Modifier.height(8.sdp))
        Text(property.title.ifBlank { property.code }, color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 22.ssp)
        Text(
            property.code,
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 12.ssp,
            // Long-press copies the code.
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = {
                context.copyToClipboard(property.code)
                android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_SHORT).show()
            }),
        )
        Spacer(Modifier.height(6.sdp))
        Text(
            listOf(property.addressLine, property.locality, property.city, property.pincode).filter { it.isNotBlank() }.joinToString(", "),
            color = AuthColors.Body,
            fontFamily = Inter,
            fontSize = 13.ssp,
        )
        Spacer(Modifier.height(8.sdp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rentRange(property.rentFrom, property.rentTo), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.ssp, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.my_properties_available_occupied, property.availableCount, property.occupiedCount), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        }
        Spacer(Modifier.height(10.sdp))
        Text(stringResource(R.string.add_property_completeness, property.completeness.score), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Spacer(Modifier.height(4.sdp))
        LinearProgressIndicator(
            progress = { property.completeness.score.coerceIn(0, 100) / 100f },
            color = if (property.completeness.score >= 80) AuthColors.Green else AuthColors.Orange,
            trackColor = AuthColors.Surface,
            modifier = Modifier.fillMaxWidth().height(6.sdp).clip(RoundedCornerShape(3.sdp)),
        )
    }
}

@Composable
private fun Banners(state: PropertyDetailState, onIntent: (PropertyDetailIntent) -> Unit) {
    val property = state.detail?.property ?: return
    val active = property.activeStatus.uppercase()
    val pub = property.publicationStatus.uppercase()
    Column(Modifier.padding(horizontal = 16.sdp)) {
        // Change request of a live listing (unsent / being verified / rejected) or "last changes approved".
        state.detail?.let { ChangeRequestCard(it, state.busy, onIntent) }
        // A past tenant to review first (blocks new invites for this property).
        state.reviewsDue.firstOrNull()?.let { due ->
            Banner(
                stringResource(R.string.manage_review_tenant_title, due.otherName.ifBlank { stringResource(R.string.lease_the_tenant) }),
                if (due.place.isNotBlank()) stringResource(R.string.manage_review_tenant_note_place, due.place) else stringResource(R.string.manage_review_tenant_note),
                com.saferenttechnologies.android.presentation.explore.Amber,
                stringResource(R.string.lease_write_review),
            ) { onIntent(PropertyDetailIntent.ReviewTenant(due.leaseId)) }
            Spacer(Modifier.height(8.sdp))
        }
        when {
            active == "SUSPENDED" -> Banner(stringResource(R.string.manage_banner_suspended), property.moderationNote, AuthColors.Error, stringResource(R.string.account_blocked_contact_support)) {
                onIntent(PropertyDetailIntent.Action(PropertyAction.CONTACT_SUPPORT))
            }
            active == "ARCHIVED" -> Banner(stringResource(R.string.manage_banner_archived), null, AuthColors.Muted, stringResource(R.string.account_blocked_contact_support)) {
                onIntent(PropertyDetailIntent.Action(PropertyAction.CONTACT_SUPPORT))
            }
            active == "INACTIVE" -> Banner(stringResource(R.string.manage_banner_inactive), null, AuthColors.Muted, stringResource(R.string.manage_action_activate).takeIf { state.canManage }) {
                onIntent(PropertyDetailIntent.Action(PropertyAction.ACTIVATE))
            }
            pub == "REJECTED" -> Banner(stringResource(R.string.manage_banner_rejected), property.moderationNote, AuthColors.OrangeText, stringResource(R.string.manage_fix_resubmit).takeIf { state.canManage }) {
                onIntent(PropertyDetailIntent.Edit(com.saferenttechnologies.android.domain.model.WizardStep.REVIEW))
            }
            pub == "PENDING_VERIFICATION" -> Banner(stringResource(R.string.manage_banner_pending), null, Color(0xFF2563EB), null) {}
            pub == "APPROVED" -> Banner(stringResource(R.string.manage_banner_approved), null, Color(0xFF0D9488), stringResource(R.string.manage_action_publish).takeIf { state.canManage }) {
                onIntent(PropertyDetailIntent.Action(PropertyAction.PUBLISH))
            }
        }
        if (state.problems.isNotEmpty()) {
            Spacer(Modifier.height(8.sdp))
            Text(stringResource(R.string.add_property_fix_before_submit), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
            state.problems.forEach { (problem, step) ->
                Text(
                    "• $problem",
                    color = AuthColors.Error,
                    fontFamily = Inter,
                    fontSize = 13.ssp,
                    modifier = Modifier.fillMaxWidth().clickable { onIntent(PropertyDetailIntent.Edit(step)) }.padding(vertical = 6.sdp),
                )
            }
        }
    }
}

@Composable
private fun Banner(title: String, note: String?, color: Color, action: String?, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.sdp)
            .clip(RoundedCornerShape(12.sdp))
            .background(color.copy(alpha = 0.10f))
            .padding(14.sdp),
    ) {
        Text(title, color = color, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
        note?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.sdp))
            Text(it, color = AuthColors.Ink, fontFamily = Inter, fontSize = 13.ssp)
        }
        action?.let {
            Spacer(Modifier.height(8.sdp))
            Text(
                it,
                color = color,
                fontFamily = Inter,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.ssp,
                modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(role = Role.Button, onClick = onAction).padding(vertical = 6.sdp),
            )
        }
    }
}

/** Invite tapped on a flat / bed whose last tenant isn't reviewed yet. Only that flat; others invite normally. */
@Composable
private fun ReviewFirstDialog(due: com.saferenttechnologies.android.domain.model.PendingReview, onIntent: (PropertyDetailIntent) -> Unit) {
    val name = due.otherName.ifBlank { stringResource(R.string.lease_the_tenant) }
    AlertDialog(
        onDismissRequest = { onIntent(PropertyDetailIntent.DismissReviewPrompt) },
        title = { Text(stringResource(R.string.manage_review_tenant_title, name), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
        text = {
            Text(
                if (due.place.isNotBlank()) stringResource(R.string.manage_review_first_place, name, due.place) else stringResource(R.string.manage_review_first, name),
                fontFamily = Inter,
            )
        },
        confirmButton = {
            TextButton(onClick = { onIntent(PropertyDetailIntent.ReviewTenant(due.leaseId)) }) {
                Text(stringResource(R.string.lease_write_review), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(PropertyDetailIntent.DismissReviewPrompt) }) { Text(stringResource(R.string.manage_later), color = AuthColors.Body, fontFamily = Inter) } },
        containerColor = AuthColors.Card,
    )
}

@Composable
private fun ConfirmDialog(action: PropertyAction, onIntent: (PropertyDetailIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(PropertyDetailIntent.DismissConfirm) },
        title = { Text(stringResource(actionLabel(action)), fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
        text = { Text(stringResource(confirmText(action)), fontFamily = Inter) },
        confirmButton = {
            TextButton(onClick = { onIntent(PropertyDetailIntent.Confirm) }) {
                Text(stringResource(R.string.manage_confirm), color = if (action in DANGER) AuthColors.Error else AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(PropertyDetailIntent.DismissConfirm) }) { Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter) } },
        containerColor = AuthColors.Card,
    )
}

fun actionLabel(action: PropertyAction): Int = when (action) {
    PropertyAction.EDIT, PropertyAction.EDIT_WITH_WARNING -> R.string.manage_action_edit
    PropertyAction.SUBMIT -> R.string.manage_action_submit
    PropertyAction.RESUBMIT -> R.string.manage_action_resubmit
    PropertyAction.PUBLISH -> R.string.manage_action_publish
    PropertyAction.UNPUBLISH -> R.string.manage_action_unpublish
    PropertyAction.DEACTIVATE -> R.string.manage_action_deactivate
    PropertyAction.ACTIVATE -> R.string.manage_action_activate
    PropertyAction.ARCHIVE -> R.string.manage_action_archive
    PropertyAction.DELETE_DRAFT -> R.string.manage_action_delete_draft
    PropertyAction.CONTACT_SUPPORT -> R.string.manage_action_contact_restore
}

private fun confirmText(action: PropertyAction): Int = when (action) {
    PropertyAction.EDIT_WITH_WARNING -> R.string.manage_confirm_edit_reverify
    PropertyAction.PUBLISH -> R.string.manage_confirm_publish
    PropertyAction.UNPUBLISH -> R.string.manage_confirm_unpublish
    PropertyAction.DEACTIVATE -> R.string.manage_confirm_deactivate
    PropertyAction.ARCHIVE -> R.string.manage_confirm_archive
    PropertyAction.DELETE_DRAFT -> R.string.manage_confirm_delete_draft
    else -> R.string.manage_confirm_generic
}

@Composable
fun tabLabel(tab: DetailTab, rooms: Boolean, unitWord: String? = null): String =
    if (tab == DetailTab.UNITS && unitWord != null) stringResource(R.string.manage_tab_unit_plural, unitWord) else stringResource(
    when (tab) {
        DetailTab.OVERVIEW -> R.string.manage_tab_overview
        DetailTab.UNITS -> if (rooms) R.string.manage_tab_rooms else R.string.manage_tab_units
        DetailTab.TENANTS -> R.string.manage_tab_tenants
        DetailTab.INVITATIONS -> R.string.manage_tab_invitations
        DetailTab.APPLICATIONS -> R.string.manage_tab_applications
        DetailTab.PHOTOS -> R.string.manage_tab_photos
        DetailTab.DOCUMENTS -> R.string.manage_tab_documents
        DetailTab.HISTORY -> R.string.manage_tab_history
    },
)

@Composable
fun RoundButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(TOP_ICON).clip(CircleShape).background(AuthColors.Surface).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), description, tint = AuthColors.Ink, modifier = Modifier.size(TOP_ICON_GLYPH)) }
}

fun Context.tryStart(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

fun Context.copyToClipboard(text: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("SafeRent", text))
}
