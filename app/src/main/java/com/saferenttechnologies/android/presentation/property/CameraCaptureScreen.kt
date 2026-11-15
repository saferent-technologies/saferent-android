package com.saferenttechnologies.android.presentation.property

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.aspectRatio
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.data.property.CapturedPhotoWriter
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Everything the camera screen can do. There is deliberately NO gallery / file option. */
enum class CameraAction { CLOSE, CAPTURE, RETAKE, USE_PHOTO }

object CameraActions {
    fun beforeCapture(@Suppress("UNUSED_PARAMETER") mode: CameraMode) = listOf(CameraAction.CLOSE, CameraAction.CAPTURE)
    fun afterCapture(@Suppress("UNUSED_PARAMETER") mode: CameraMode) = listOf(CameraAction.RETAKE, CameraAction.USE_PHOTO)
}

/**
 * Live camera only (CameraX Preview + ImageCapture). PHOTO = property photos, DOCUMENT = a
 * rectangle guide for ownership papers. After capture: Retake / Use photo.
 */
@Composable
fun CameraCaptureScreen(
    mode: CameraMode,
    onCaptured: (filePath: String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var captured by remember { mutableStateOf<Pair<File, Bitmap>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    BackHandler {
        val shot = captured
        if (shot != null) {
            shot.first.delete()
            captured = null
        } else {
            onClose()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val shot = captured
        if (shot == null) {
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    // TextureView: the preview is cut to the 16:9 frame (a SurfaceView would draw past it).
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            }
            // Property photos: 16:9 frame (exactly what will be saved); documents use the full screen.
            AndroidView(
                factory = { previewView },
                modifier = if (mode == CameraMode.PHOTO) {
                    Modifier.fillMaxWidth().aspectRatio(CapturedPhotoWriter.PHOTO_ASPECT).align(Alignment.Center).clipToBounds()
                } else {
                    Modifier.fillMaxSize()
                },
            )
            DisposableEffect(lifecycleOwner) {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    }.onFailure { failed = true }
                }, ContextCompat.getMainExecutor(context))
                onDispose { runCatching { providerFuture.get().unbindAll() } }
            }
            if (mode == CameraMode.DOCUMENT) DocumentGuide()

            // Top: close + hint
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(12.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(R.drawable.ic_close, stringResource(R.string.camera_close)) { onClose() }
                Text(
                    stringResource(if (mode == CameraMode.DOCUMENT) R.string.camera_hint_document else R.string.camera_hint_photo),
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 13.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.sdp),
                )
                Box(Modifier.size(44.sdp))
            }
            if (failed) {
                Text(
                    stringResource(R.string.camera_unavailable),
                    color = Color.White,
                    fontFamily = Inter,
                    fontSize = 15.ssp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.sdp),
                )
            }
            // Bottom: shutter (the ONLY way to get a photo — no gallery / files)
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.sdp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(76.sdp)
                        .clip(CircleShape)
                        .border(4.sdp, Color.White, CircleShape)
                        .padding(7.sdp)
                        .clip(CircleShape)
                        .background(if (busy) AuthColors.Muted else Color.White)
                        .clickable(enabled = !busy && !failed, role = Role.Button, onClickLabel = stringResource(R.string.camera_capture)) {
                            busy = true
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        scope.launch {
                                            val result = withContext(Dispatchers.Default) {
                                                runCatching {
                                                    val rotation = image.imageInfo.rotationDegrees
                                                    val bitmap = image.toBitmap()
                                                    image.close()
                                                    val aspect = if (mode == CameraMode.PHOTO) CapturedPhotoWriter.PHOTO_ASPECT else null
                                                    val mark = if (mode == CameraMode.PHOTO) CapturedPhotoWriter.WATERMARK else null
                                                    val file = CapturedPhotoWriter.write(context, bitmap, rotation, aspect, mark)
                                                    file to android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                                }.onFailure { runCatching { image.close() } }.getOrNull()
                                            }
                                            busy = false
                                            if (result != null) captured = result else failed = true
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        busy = false
                                    }
                                },
                            )
                        },
                )
                if (busy) CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.size(36.sdp))
            }
        } else {
            // Preview: Retake / Use photo
            Image(
                bitmap = shot.second.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // Property photo → shown in the same 16:9 frame it was taken in.
                modifier = if (mode == CameraMode.PHOTO) {
                    Modifier.fillMaxWidth().aspectRatio(CapturedPhotoWriter.PHOTO_ASPECT).align(Alignment.Center)
                } else {
                    Modifier.fillMaxSize()
                },
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.sdp),
                horizontalArrangement = Arrangement.spacedBy(12.sdp),
            ) {
                PillButton(stringResource(R.string.camera_retake), filled = false, modifier = Modifier.weight(1f)) {
                    shot.first.delete()
                    captured = null
                }
                PillButton(stringResource(R.string.camera_use_photo), filled = true, modifier = Modifier.weight(1f)) {
                    captured = null
                    onCaptured(shot.first.absolutePath)
                }
            }
        }
    }
}

@Composable
private fun DocumentGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width * 0.86f
        val h = w * 1.35f // A4-ish
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 5f),
        )
    }
}

@Composable
private fun RoundIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.sdp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(22.sdp))
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.sdp)
    Box(
        modifier
            .clip(shape)
            .background(if (filled) AuthColors.Orange else Color.Black.copy(alpha = 0.55f))
            .border(1.5.sdp, if (filled) AuthColors.Orange else Color.White, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.sdp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.ssp)
    }
}
