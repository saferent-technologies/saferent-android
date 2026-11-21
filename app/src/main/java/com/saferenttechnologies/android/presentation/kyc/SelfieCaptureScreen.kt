package com.saferenttechnologies.android.presentation.kyc

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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

/** Live selfie only (front camera, no gallery): face in the oval → shutter → uploaded. */
@Composable
fun SelfieCaptureScreen(onTaken: (filePath: String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var busy by remember { mutableStateOf(false) }
    BackHandler(enabled = !busy) { onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        DisposableEffect(Unit) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose { runCatching { future.get().unbindAll() } }
        }
        // Face guide.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * 0.62f
            val h = w * 1.3f
            drawOval(Color.White, Offset((size.width - w) / 2, size.height * 0.42f - h / 2), Size(w, h), style = Stroke(width = 3.sdp.toPx()))
        }
        Text(
            stringResource(R.string.kyc_selfie_hint),
            color = Color.White,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.ssp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.sdp)
                .clip(RoundedCornerShape(12.sdp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.sdp, vertical = 8.sdp),
        )
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(28.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.sdp).clip(CircleShape).border(4.sdp, Color.White, CircleShape)
                    .clickable(enabled = !busy, role = Role.Button) {
                        busy = true
                        capture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    scope.launch {
                                        val path = withContext(Dispatchers.Default) {
                                            runCatching {
                                                val bmp = image.toBitmap()
                                                CapturedPhotoWriter.write(context, bmp, image.imageInfo.rotationDegrees, 3f / 4f, null).absolutePath
                                            }.getOrNull()
                                        }
                                        image.close()
                                        busy = false
                                        path?.let(onTaken)
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) { busy = false }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.sdp))
                else Box(Modifier.size(58.sdp).clip(CircleShape).background(Color.White))
            }
            Text(
                stringResource(R.string.add_property_cancel),
                color = Color.White,
                fontFamily = Inter,
                fontSize = 14.ssp,
                modifier = Modifier.padding(top = 12.sdp).clickable(enabled = !busy) { onClose() }.padding(8.sdp),
            )
        }
    }
}
