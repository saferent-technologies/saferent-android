package com.saferenttechnologies.android.presentation.requests

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.presentation.property.OutlineAction
import java.io.File
import java.util.UUID

/** Camera (our permission popup first) + gallery (system photo picker, no permission) for request photos. */
class PhotoPicker internal constructor(val camera: () -> Unit, val gallery: () -> Unit)

@Composable
fun rememberPhotoPicker(remaining: Int, onPicked: (List<String>) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionRequester(AppPermission.CAMERA)
    var pendingShot by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingShot
        pendingShot = null
        if (ok && uri != null) onPicked(listOf(uri))
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, remaining))) { uris ->
        onPicked(uris.take(remaining).map(Uri::toString))
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onPicked(listOf(it.toString())) } }
    return remember(remaining) {
        PhotoPicker(
            camera = {
                if (remaining > 0) cameraPermission.request { granted ->
                    if (granted) {
                        val uri = context.newShotUri()
                        pendingShot = uri.toString()
                        takePicture.launch(uri)
                    }
                }
            },
            gallery = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                when {
                    remaining <= 0 -> Unit
                    remaining == 1 -> pickOne.launch(request)
                    else -> pickMany.launch(request)
                }
            },
        )
    }
}

/** An empty file in cache/request-photos for the camera to write into (shared via FileProvider). */
private fun Context.newShotUri(): Uri {
    val dir = File(cacheDir, "request-photos").apply { mkdirs() }
    val file = File(dir, "shot-${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.files", file)
}

/** "Camera" + "Gallery" buttons (hidden at the limit). */
@Composable
fun AddPhotoButtons(picker: PhotoPicker, enabled: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        OutlineAction(stringResource(R.string.req_camera), R.drawable.ic_camera, enabled = enabled, modifier = Modifier.weight(1f), onClick = picker.camera)
        OutlineAction(stringResource(R.string.req_gallery), R.drawable.ic_add, enabled = enabled, modifier = Modifier.weight(1f), onClick = picker.gallery)
    }
}

/** Thumbnails (local uris or server urls); × removes when [onRemove] is given; tap → [onOpen]. */
@Composable
fun PhotoStrip(models: List<String>, onRemove: ((Int) -> Unit)? = null, onOpen: ((Int) -> Unit)? = null) {
    if (models.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        itemsIndexed(models) { i, m ->
            Box(
                Modifier
                    .size(76.sdp)
                    .clip(RoundedCornerShape(10.sdp))
                    .background(AuthColors.Surface)
                    .then(if (onOpen != null) Modifier.clickable(role = Role.Button) { onOpen(i) } else Modifier),
            ) {
                AsyncImage(model = m, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (onRemove != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.sdp)
                            .size(22.sdp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(role = Role.Button) { onRemove(i) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.add_property_delete), tint = Color.White, modifier = Modifier.size(14.sdp)) }
                }
            }
        }
    }
}
