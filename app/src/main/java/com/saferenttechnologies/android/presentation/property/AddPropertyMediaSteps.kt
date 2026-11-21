package com.saferenttechnologies.android.presentation.property

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.saferenttechnologies.android.presentation.common.ListSkeleton
import com.saferenttechnologies.android.domain.model.WizardStep
import com.saferenttechnologies.android.domain.model.RequiredPhoto
import com.saferenttechnologies.android.domain.model.PhotoRequirements
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.BuildConfig
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.PropertyForms
import com.saferenttechnologies.android.domain.model.PropertyImage
import com.saferenttechnologies.android.domain.model.SubmitProblems
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.auth.AuthPrimaryButton
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans

/** "/api/v1/files/…" → absolute URL (Coil uses the app OkHttp client, so the token is added). */
fun absoluteUrl(url: String): String =
    if (url.startsWith("http")) url else BuildConfig.API_BASE_URL.trimEnd('/') + "/" + url.trimStart('/')

// ---------------- 8. Photos (camera only) ----------------

@Composable
fun PhotosStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val property = state.property ?: return
    val camera = rememberPermissionRequester(AppPermission.CAMERA)
    Hint(stringResource(R.string.add_property_photos_hint))
    Spacer(Modifier.height(14.sdp))

    // Checklist: Front view (apartments) → then each flat (302, 303, …) with its bedrooms /
    // bathrooms / balconies (0 → not asked). Tap a row → camera for exactly that photo.
    val required = state.requiredPhotos
    val missing = state.missingRequiredPhotos
    val canShoot = state.imageCount < state.maxImages
    fun shoot(need: RequiredPhoto) {
        camera.request { granted ->
            if (granted) {
                onIntent(AddPropertyIntent.SelectRequiredPhoto(need.unitId, need.slot, need.category))
                onIntent(AddPropertyIntent.OpenCamera(CameraMode.PHOTO))
            }
        }
    }
    val flatLabel = state.type?.unitLabel?.ifBlank { null }
        ?: stringResource(if (state.type?.isFlat == true) R.string.add_property_flat else R.string.add_property_unit)
    // Apartment: this step is "Building photos"; each flat's room photos are taken on that flat.
    val perUnit = state.isUnitPriced || state.isRoomPriced
    val shown = if (perUnit) required.filter { it.unitId == null && it.roomId == null } else required
    shown.groupBy { (it.category == PhotoRequirements.FRONT_VIEW) to it.unitNumber }.forEach { (key, needs) ->
        val (isBuilding, unitNumber) = key
        SectionTitle(
            when {
                isBuilding -> stringResource(R.string.add_property_photos_building)
                unitNumber.isNotBlank() -> "$flatLabel $unitNumber"
                else -> stringResource(R.string.add_property_required_photos)
            },
        )
        Spacer(Modifier.height(6.sdp))
        needs.forEach { need ->
            val done = need !in missing
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.sdp))
                    .border(1.sdp, if (done) AuthColors.Border else AuthColors.Orange, RoundedCornerShape(12.sdp))
                    .clickable(enabled = !done && canShoot, role = Role.Button) { shoot(need) }
                    .padding(horizontal = 12.sdp, vertical = 12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) {
                    Icon(painterResource(R.drawable.ic_check_circle), null, tint = AuthColors.Green, modifier = Modifier.size(20.sdp))
                } else {
                    Icon(painterResource(R.drawable.ic_camera), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
                }
                Spacer(Modifier.width(10.sdp))
                Text(
                    countedPhotoName(need.category, need.slot),
                    color = if (done) AuthColors.Body else AuthColors.Ink,
                    fontFamily = Inter,
                    fontWeight = if (done) FontWeight.Normal else FontWeight.Medium,
                    fontSize = 14.ssp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(if (done) R.string.add_property_photo_done else R.string.add_property_required),
                    color = if (done) AuthColors.Green else AuthColors.OrangeText,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                )
            }
            Spacer(Modifier.height(8.sdp))
        }
        Spacer(Modifier.height(10.sdp))
    }

    if (perUnit) {
        // Progress per unit / room ("Flat 101: 3 / 5 rooms") → opens that unit's / room's photos.
        val word = state.unitWord
        val targets = if (state.isRoomPriced) {
            property.rooms.map { r -> Triple(r.roomNumber, required.filter { it.roomId == r.id }, AddPropertyIntent.OpenRoomPanel(r.id)) }
        } else {
            property.units.map { u -> Triple(u.unitNumber, required.filter { it.unitId == u.id }, AddPropertyIntent.OpenFlatPanel(u.id)) }
        }
        targets.forEach { (number, needs, open) ->
            val done = needs.count { it !in missing }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.sdp)
                    .clip(RoundedCornerShape(12.sdp))
                    .border(1.sdp, if (done == needs.size) AuthColors.Border else AuthColors.Orange, RoundedCornerShape(12.sdp))
                    .clickable(role = Role.Button) {
                        onIntent(AddPropertyIntent.GoToStep(WizardStep.UNITS))
                        onIntent(open)
                    }
                    .padding(12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$word $number", color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.apartment_flat_progress, done, needs.size),
                    color = if (done == needs.size) AuthColors.Green else AuthColors.OrangeText,
                    fontFamily = Inter,
                    fontSize = 12.ssp,
                )
            }
        }
        // More building photos (no flat): pick what it is, then shoot.
        Spacer(Modifier.height(14.sdp))
        SectionTitle(stringResource(R.string.apartment_more_building_photos))
        Spacer(Modifier.height(8.sdp))
        ChipGrid(com.saferenttechnologies.android.domain.model.ApartmentRules.BUILDING_CATEGORIES, columns = 2) { c, mod ->
            Chip(optionLabel(c), selected = false, modifier = mod, maxLines = 1) {
                camera.request { granted ->
                    if (granted) {
                        onIntent(AddPropertyIntent.SelectRequiredPhoto(null, 1, c))
                        onIntent(AddPropertyIntent.OpenCamera(CameraMode.PHOTO))
                    }
                }
            }
        }
    }

    // PG / hostel (old flow, only when rooms aren't priced one by one): pick what the photo is.
    if (state.structure.usesRooms && !state.isRoomPriced) {
        SectionTitle(stringResource(R.string.add_property_photo_category))
        Spacer(Modifier.height(10.sdp))
        ChipRows(PropertyForms.imageCategories(state.types), perRow = 2) { category ->
            Chip(optionLabel(category), selected = category == state.imageCategory) { onIntent(AddPropertyIntent.SelectImageCategory(category)) }
        }
        if (property.rooms.size > 1) {
            Spacer(Modifier.height(14.sdp))
            Text(stringResource(R.string.add_property_photo_for), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
            Spacer(Modifier.height(8.sdp))
            val all = listOf(null to stringResource(R.string.add_property_whole_property)) +
                property.rooms.map { it.id to stringResource(R.string.add_property_room_n, it.roomNumber) }
            ChipRows(all.map { it.second }) { label ->
                val roomId = all.first { it.second == label }.first
                Chip(label, selected = state.imageRoomId == roomId && state.imageUnitId == null) {
                    onIntent(AddPropertyIntent.SelectImageTarget(null, roomId))
                }
            }
        }
        Spacer(Modifier.height(16.sdp))
        AuthPrimaryButton(
            text = stringResource(R.string.add_property_take_photo),
            enabled = canShoot,
            loading = false,
            leadingIcon = R.drawable.ic_camera,
            onClick = { camera.request { granted -> if (granted) onIntent(AddPropertyIntent.OpenCamera(CameraMode.PHOTO)) } },
        )
    }
    Spacer(Modifier.height(8.sdp))
    Text(stringResource(R.string.add_property_photo_count, state.imageCount, state.maxImages), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)

    if (state.uploads.running > 0) {
        Spacer(Modifier.height(12.sdp))
        Text(stringResource(R.string.add_property_uploading, state.uploads.running), color = AuthColors.Body, fontFamily = Inter, fontSize = 13.ssp)
        Spacer(Modifier.height(6.sdp))
        LinearProgressIndicator(color = AuthColors.Orange, trackColor = AuthColors.Surface, modifier = Modifier.fillMaxWidth())
    }
    state.failedUploads.forEach { upload ->
        Spacer(Modifier.height(8.sdp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.sdp))
                .background(AuthColors.Error.copy(alpha = 0.08f))
                .padding(12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.add_property_upload_failed, optionLabel(upload.category)) + (upload.message?.let { "\n$it" } ?: ""),
                color = AuthColors.Error,
                fontFamily = Inter,
                fontSize = 13.ssp,
                modifier = Modifier.weight(1f),
            )
            OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh) { onIntent(AddPropertyIntent.RetryUpload(upload)) }
        }
    }

    // Videos: building & common areas (optional) first, then every flat (required), then PG rooms (optional).
    Spacer(Modifier.height(18.sdp))
    VideosBlock(state, onIntent)
    // 360° view: built but switched off for now (AppFeatureFlags.PANORAMA_360).
    if (com.saferenttechnologies.android.domain.model.AppFeatureFlags.PANORAMA_360) {
        Spacer(Modifier.height(18.sdp))
        PanoramaSection(state, onIntent)
    }

    // Uploaded photos grouped by category (PENDING until SafeRent verifies the property).
    val sorted = property.images.sortedBy { it.displayOrder ?: Int.MAX_VALUE }
    val coverId = sorted.firstOrNull()?.id
    sorted.groupBy { it.category }.forEach { (category, images) ->
        Spacer(Modifier.height(18.sdp))
        Text(optionLabel(category), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
        Spacer(Modifier.height(8.sdp))
        images.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
                row.forEach { image -> PhotoTile(image, isCover = image.id == coverId, modifier = Modifier.weight(1f), onIntent = onIntent) }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.sdp))
        }
    }
}

/**
 * "Video tour" of one flat / PG room (or the whole building): record in the app (watermarked,
 * small file), uploads in the background. Shows where it is: uploading / uploaded / failed (Retry).
 * Flats must have one ([required]); the building's own video is optional.
 */
@Composable
internal fun VideoSection(
    state: AddPropertyState,
    onIntent: (AddPropertyIntent) -> Unit,
    target: PanoramaTarget = PanoramaTarget(),
    required: Boolean = false,
    /** Slot name ("Building & common areas", "Flat 302", "Room 2"); null = "Video tour". */
    title: String? = null,
) {
    val camera = com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester(com.saferenttechnologies.android.domain.model.AppPermission.CAMERA)
    val video = state.property?.videos.orEmpty().lastOrNull { it.unitId == target.unitId && it.roomId == target.roomId }
    // Newest recording for this flat / room that's still on its way (or failed).
    val upload = state.videoUploads.lastOrNull { it.unitId == target.unitId && it.roomId == target.roomId && !(it.state == com.saferenttechnologies.android.domain.repository.VideoUploadState.UPLOADED && video != null) }
    var playing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val missing = (required && video == null && upload == null) || (target.unitId != null && target.unitId in state.missingVideoUnitIds)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp))
            .border(1.sdp, if (missing) AuthColors.Orange else AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title ?: stringResource(R.string.video_title), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (required) R.string.add_property_required else R.string.pano_optional),
                color = if (required) AuthColors.OrangeText else AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp,
            )
        }
        Spacer(Modifier.height(4.sdp))
        Text(stringResource(R.string.video_section_hint, com.saferenttechnologies.android.domain.model.VideoRules.MAX_SECONDS), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        // Where the video is: uploading (with %), failed, or the server's status chip (In review / Live / Rejected).
        val uploading = upload?.state == com.saferenttechnologies.android.domain.repository.VideoUploadState.UPLOADING
        when {
            uploading -> {
                val pct = upload?.progress ?: -1
                Spacer(Modifier.height(6.sdp))
                Text(
                    if (pct in 0..100) stringResource(R.string.video_uploading_pct, pct) else stringResource(R.string.video_uploading),
                    color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp,
                )
                if (pct in 0..100) {
                    Spacer(Modifier.height(4.sdp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { pct / 100f }, color = AuthColors.Orange, trackColor = AuthColors.Surface,
                        modifier = Modifier.fillMaxWidth().height(4.sdp).clip(RoundedCornerShape(2.sdp)),
                    )
                }
            }
            upload?.state == com.saferenttechnologies.android.domain.repository.VideoUploadState.FAILED -> {
                Spacer(Modifier.height(6.sdp))
                Text(stringResource(R.string.video_failed_upload) + (upload.message?.let { "\n$it" } ?: ""), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.ssp)
            }
            video != null -> {
                Spacer(Modifier.height(6.sdp))
                val (label, fg, bg) = when {
                    video.isLive -> Triple(stringResource(R.string.video_chip_live), AuthColors.GreenText, AuthColors.GreenBg)
                    video.isRejected -> Triple(stringResource(R.string.video_chip_rejected), AuthColors.Error, AuthColors.Error.copy(alpha = 0.1f))
                    else -> Triple(stringResource(R.string.video_chip_review), AuthColors.OrangeText, AuthColors.OrangeSoft)
                }
                Text(
                    label, color = fg, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.sdp, vertical = 3.sdp),
                )
                if (video.isRejected && video.rejectionReason.isNotBlank()) {
                    Spacer(Modifier.height(4.sdp))
                    Text(video.rejectionReason, color = AuthColors.Error, fontFamily = Inter, fontSize = 12.ssp)
                }
            }
        }
        Spacer(Modifier.height(10.sdp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            if (upload?.state == com.saferenttechnologies.android.domain.repository.VideoUploadState.FAILED && java.io.File(upload.filePath).exists()) {
                OutlineAction(stringResource(R.string.add_property_retry), R.drawable.ic_refresh, modifier = Modifier.weight(1f), compact = true) { onIntent(AddPropertyIntent.RetryVideo(upload)) }
            } else if (video != null) {
                OutlineAction(
                    stringResource(R.string.video_play, com.saferenttechnologies.android.domain.model.VideoRules.clock(video.durationSec)),
                    R.drawable.ic_play,
                    modifier = Modifier.weight(1f),
                    compact = true,
                ) { playing = true }
            }
            OutlineAction(
                stringResource(if (video != null || upload != null) R.string.video_record_again else R.string.video_record),
                R.drawable.ic_camera,
                enabled = !state.busy && !uploading, // one upload at a time per place
                modifier = Modifier.weight(1f),
                compact = true,
            ) { camera.request { granted -> if (granted) onIntent(AddPropertyIntent.OpenVideoCamera(target)) } }
            // Delete (confirm first). Not while a new one is uploading.
            if (video != null && !uploading) {
                Box(
                    Modifier.size(38.sdp).clip(RoundedCornerShape(10.sdp))
                        .border(1.sdp, if (state.busy) AuthColors.Border else AuthColors.Error.copy(alpha = 0.6f), RoundedCornerShape(10.sdp))
                        .clickable(enabled = !state.busy, role = Role.Button) { onIntent(AddPropertyIntent.AskDeleteVideo(video.id)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.video_delete), tint = if (state.busy) AuthColors.Muted else AuthColors.Error, modifier = Modifier.size(18.sdp)) }
            }
        }
    }
    if (playing && video != null) {
        com.saferenttechnologies.android.presentation.video.TourVideoPlayer(absoluteUrl(video.url)) { playing = false }
    }
}

/** Media step "Videos": one slot per place. Building first (optional), each flat (required), each PG room (optional). */
@Composable
private fun VideosBlock(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val p = state.property ?: return
    Text(stringResource(R.string.video_block_title), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.ssp)
    Spacer(Modifier.height(8.sdp))
    Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) {
        VideoSection(state, onIntent, PanoramaTarget(), required = false, title = stringResource(R.string.listing_building_video))
        p.units.forEach { u ->
            VideoSection(state, onIntent, PanoramaTarget(unitId = u.id), required = true, title = "${state.unitWord} ${u.unitNumber}".trim())
        }
        p.rooms.forEach { r ->
            VideoSection(state, onIntent, PanoramaTarget(roomId = r.id), required = false, title = stringResource(R.string.add_property_room_n, r.roomNumber))
        }
    }
}

/** "Delete this video tour?" */
@Composable
internal fun DeleteVideoDialog(onIntent: (AddPropertyIntent) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onIntent(AddPropertyIntent.DismissDeleteVideo) },
        title = { Text(stringResource(R.string.video_delete_title), fontFamily = com.saferenttechnologies.android.ui.theme.PlusJakartaSans, fontWeight = FontWeight.ExtraBold) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onIntent(AddPropertyIntent.ConfirmDeleteVideo) }) {
                Text(stringResource(R.string.video_delete), color = AuthColors.Error, fontFamily = Inter, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { onIntent(AddPropertyIntent.DismissDeleteVideo) }) {
                Text(stringResource(R.string.add_property_cancel), color = AuthColors.Body, fontFamily = Inter)
            }
        },
        containerColor = AuthColors.Card,
    )
}

/**
 * "360° view" of one flat / PG room (or, optionally, the whole building): "Open camera" goes straight
 * into SafeRent's 360 camera. Phones without a gyroscope can't take one → the card is disabled.
 */
@Composable
internal fun PanoramaSection(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit, target: PanoramaTarget = PanoramaTarget()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val supported = androidx.compose.runtime.remember { com.saferenttechnologies.android.presentation.panorama.PanoramaSupport.isSupported(context) }
    val camera = com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester(com.saferenttechnologies.android.domain.model.AppPermission.CAMERA)
    val panos = state.property?.images.orEmpty().filter {
        com.saferenttechnologies.android.domain.model.PanoramaRules.isPanoramaCategory(it.category) && it.unitId == target.unitId && it.roomId == target.roomId
    }
    var viewing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pano_title), color = if (supported) AuthColors.Ink else AuthColors.Muted, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.pano_optional), color = AuthColors.Muted, fontFamily = Inter, fontSize = 12.ssp)
        }
        Spacer(Modifier.height(4.sdp))
        Text(stringResource(if (supported) R.string.pano_hint else R.string.pano_unsupported), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
        Spacer(Modifier.height(10.sdp))
        OutlineAction(
            stringResource(R.string.pano_open_camera),
            R.drawable.ic_camera,
            enabled = supported && !state.busy && state.imageCount < state.maxImages,
            modifier = Modifier.fillMaxWidth(),
        ) {
            camera.request { granted -> if (granted) onIntent(AddPropertyIntent.OpenPanoramaCamera(target)) }
        }
        if (panos.isNotEmpty()) {
            Spacer(Modifier.height(10.sdp))
            OutlineAction(
                androidx.compose.ui.res.pluralStringResource(R.plurals.pano_view_n, panos.size, panos.size),
                R.drawable.ic_my_location,
                modifier = Modifier.fillMaxWidth(),
            ) { viewing = true }
        }
    }
    if (viewing) {
        com.saferenttechnologies.android.presentation.panorama.PanoramaViewer(panos.map { absoluteUrl(it.url) }) { viewing = false }
    }
}

@Composable
private fun PhotoTile(image: PropertyImage, isCover: Boolean, modifier: Modifier, onIntent: (AddPropertyIntent) -> Unit) {
    // Photos are 4:3 (landscape) → thumbnails too, so nothing is cut off.
    Box(modifier.aspectRatio(4f / 3f).clip(RoundedCornerShape(10.sdp)).background(AuthColors.Surface)) {
        AsyncImage(model = absoluteUrl(image.url), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Row(Modifier.align(Alignment.TopEnd).padding(4.sdp)) {
            TileIcon(R.drawable.ic_star, stringResource(R.string.add_property_set_cover), tint = if (isCover) AuthColors.Orange else Color.White) {
                onIntent(AddPropertyIntent.SetCover(image.id))
            }
            Spacer(Modifier.width(4.sdp))
            TileIcon(R.drawable.ic_delete, stringResource(R.string.add_property_delete), tint = Color.White) { onIntent(AddPropertyIntent.DeleteImage(image.id)) }
        }
        if (image.status.equals("PENDING", ignoreCase = true)) {
            Text(
                stringResource(R.string.add_property_pending),
                color = Color.White,
                fontFamily = Inter,
                fontSize = 10.ssp,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.sdp).clip(RoundedCornerShape(6.sdp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.sdp, vertical = 2.sdp),
            )
        }
    }
}

@Composable
private fun TileIcon(icon: Int, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(30.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), description, tint = tint, modifier = Modifier.size(16.sdp)) }
}

// ---------------- 9. Ownership documents (camera only) ----------------

@Composable
fun DocumentsStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val camera = rememberPermissionRequester(AppPermission.CAMERA)
    // Submit came back with problems → listed here (tap one to fix it).
    SubmitProblemsList(state, onIntent)
    // Live listing: this is "Review your changes" — completeness of the change first.
    if (state.isLiveEdit && state.completeness != null) ReviewCompleteness(state, onIntent)
    Hint(stringResource(R.string.add_property_documents_hint))
    Spacer(Modifier.height(14.sdp))
    SectionTitle(stringResource(R.string.add_property_document_type))
    Spacer(Modifier.height(10.sdp))
    ChipGrid(PropertyForms.documentTypes(state.types), columns = 2) { type, mod ->
        Chip(optionLabel(type), selected = type == state.docType, modifier = mod) { onIntent(AddPropertyIntent.SelectDocType(type)) }
    }
    Spacer(Modifier.height(16.sdp))
    AuthPrimaryButton(
        text = stringResource(R.string.add_property_scan_document),
        enabled = !state.uploadingDocument,
        loading = state.uploadingDocument,
        leadingIcon = R.drawable.ic_document,
        onClick = { camera.request { granted -> if (granted) onIntent(AddPropertyIntent.OpenCamera(CameraMode.DOCUMENT)) } },
    )
    if (state.documents.none { it.docType in PropertyForms.OWNERSHIP_DOC_TYPES }) {
        Spacer(Modifier.height(10.sdp))
        Text(stringResource(R.string.add_property_ownership_needed), color = AuthColors.OrangeText, fontFamily = Inter, fontSize = 13.ssp)
    }
    Spacer(Modifier.height(16.sdp))
    state.documents.forEach { doc ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.sdp))
                .border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
                .padding(12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_document), null, tint = AuthColors.OrangeText, modifier = Modifier.size(22.sdp))
            Spacer(Modifier.width(10.sdp))
            Column(Modifier.weight(1f)) {
                Text(optionLabel(doc.docType), color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                Text(listOf(doc.code, optionLabel(doc.status)).filter { it.isNotBlank() }.joinToString(" · "), color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
            OutlineAction(stringResource(R.string.add_property_retake)) {
                camera.request { granted -> if (granted) onIntent(AddPropertyIntent.OpenCamera(CameraMode.DOCUMENT, replaceDocId = doc.id)) }
            }
        }
        Spacer(Modifier.height(8.sdp))
    }
}

// ---------------- 10. Review & submit ----------------

@Composable
fun ReviewStep(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    SubmitProblemsList(state, onIntent)
    ReviewCompleteness(state, onIntent)
}

/** What the server said must be fixed before it can be submitted (tap → that step). */
@Composable
fun SubmitProblemsList(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    if (state.problems.isNotEmpty()) {
        SectionTitle(stringResource(R.string.add_property_fix_before_submit))
        Spacer(Modifier.height(10.sdp))
        state.problems.forEach { (problem, step) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.sdp))
                    .background(AuthColors.Error.copy(alpha = 0.08f))
                    .clickable(role = Role.Button) { onIntent(AddPropertyIntent.GoToStep(step)) }
                    .padding(12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.sdp).clip(CircleShape).border(2.sdp, AuthColors.Error, CircleShape))
                Spacer(Modifier.width(10.sdp))
                Text(problem, color = AuthColors.Ink, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (step == WizardStep.OWNERSHIP) R.string.own_go_to else R.string.add_property_fix),
                    color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp,
                )
            }
            Spacer(Modifier.height(8.sdp))
        }
        Spacer(Modifier.height(14.sdp))
    }
}

@Composable
private fun ReviewCompleteness(state: AddPropertyState, onIntent: (AddPropertyIntent) -> Unit) {
    val completeness = state.completeness
    if (state.loading && completeness == null) {
        ListSkeleton(rows = 3, thumbnail = 24.sdp, roundThumb = true)
        return
    }
    completeness?.let { c ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.sdp))
                .background(AuthColors.Surface)
                .padding(16.sdp),
        ) {
            Text(stringResource(R.string.add_property_completeness, c.score), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 18.ssp)
            Spacer(Modifier.height(8.sdp))
            LinearProgressIndicator(
                progress = { (c.score.coerceIn(0, 100)) / 100f },
                color = if (c.score >= 80) AuthColors.Green else AuthColors.Orange,
                trackColor = AuthColors.Border,
                modifier = Modifier.fillMaxWidth().height(8.sdp).clip(RoundedCornerShape(4.sdp)),
            )
            if (c.note.isNotBlank()) {
                Spacer(Modifier.height(8.sdp))
                Text(c.note, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp)
            }
        }
        Spacer(Modifier.height(14.sdp))
        c.sections.forEach { section ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.sdp))
                    .border(1.sdp, AuthColors.Border, RoundedCornerShape(12.sdp))
                    .padding(12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${section.label} · ${section.score}%", color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.ssp)
                    section.missing.take(3).forEach { Text("• $it", color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
                }
                OutlineAction(stringResource(R.string.add_property_edit)) {
                    onIntent(AddPropertyIntent.GoToStep(SubmitProblems.stepFor(section.key + " " + section.label)))
                }
            }
            Spacer(Modifier.height(8.sdp))
        }
    }
    Spacer(Modifier.height(8.sdp))
    Hint(stringResource(R.string.add_property_review_hint))
}

/** "Bedroom 2", "Bathroom 1", "Balcony 1". */
@Composable
fun countedPhotoName(category: String, slot: Int): String = when (category) {
    PhotoRequirements.BATHROOM -> stringResource(R.string.add_property_bathroom_n, slot)
    PhotoRequirements.BALCONY -> stringResource(R.string.add_property_balcony_n, slot)
    PhotoRequirements.BEDROOM -> stringResource(R.string.add_property_bedroom_n, slot)
    PhotoRequirements.KITCHEN -> stringResource(R.string.add_property_kitchen_n, slot)
    PhotoRequirements.LIVING_ROOM -> stringResource(R.string.add_property_living_room_n, slot)
    else -> optionLabel(category) // Front view, Living room
}
