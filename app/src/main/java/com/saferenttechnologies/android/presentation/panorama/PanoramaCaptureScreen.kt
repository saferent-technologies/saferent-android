package com.saferenttechnologies.android.presentation.panorama

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import androidx.activity.compose.BackHandler
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.PanoramaCapture
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.asin
import kotlin.math.atan2

/**
 * SafeRent 360 camera: opens straight into panorama capture. Hold the phone upright in the middle
 * of the room, tap Start, turn slowly to the right; a photo is taken by itself at every dot
 * (every 30°). After a full turn (or "Done" after 120°+) the photos are joined into one 360 photo.
 * [onCaptured] gets the JPEG path (app cache). Camera permission is asked before opening this.
 */
@Composable
fun PanoramaCaptureScreen(onCaptured: (filePath: String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    var hfov by remember { mutableFloatStateOf(PanoramaCapture.DEFAULT_HFOV_DEG) }
    var started by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) } // degrees turned since Start
    var level by remember { mutableStateOf(false) }
    var shooting by remember { mutableStateOf(false) }
    var stitching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val frames = remember { mutableStateListOf<PanoramaFrame>() }
    var shots by remember { mutableIntStateOf(0) }

    fun discard() { frames.forEach { it.bitmap.recycle() }; frames.clear(); shots = 0 }

    BackHandler(enabled = !stitching) { discard(); onClose() }

    // Phone turn (yaw) + upright check from the rotation sensor.
    DisposableEffect(Unit) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = PanoramaSupport.rotationSensor(sensors)
        var lastYaw: Float? = null
        val matrix = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                // Camera looks along the phone's -Z; its direction in the world:
                val dx = -matrix[2]; val dy = -matrix[5]; val dz = -matrix[8]
                val yaw = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
                val pitch = Math.toDegrees(asin(dz.coerceIn(-1f, 1f).toDouble())).toFloat()
                // Phone's "up" (+Y) should point up: sideways tilt = how far its up-vector leans.
                val roll = Math.toDegrees(atan2(matrix[6].toDouble(), matrix[7].toDouble())).toFloat()
                level = PanoramaCapture.isLevel(pitch, roll)
                val prev = lastYaw
                if (started && prev != null) progress = PanoramaCapture.advance(progress, prev, yaw)
                lastYaw = yaw
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensor?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensors.unregisterListener(listener) }
    }

    fun finish() {
        if (frames.isEmpty()) return
        stitching = true
        scope.launch {
            val path = withContext(Dispatchers.Default) {
                runCatching {
                    val pano = PanoramaStitcher.stitch(frames.toList(), hfov)
                    val dir = File(context.cacheDir, "panorama").apply { mkdirs() }
                    val file = File(dir, "pano_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { pano.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    pano.recycle()
                    file.absolutePath
                }.getOrNull()
            }
            discard()
            stitching = false
            if (path != null) onCaptured(path) else failed = true
        }
    }

    fun shoot() {
        if (shooting) return
        shooting = true
        val yawAtShot = if (shots == 0) 0f else progress
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        val frame = withContext(Dispatchers.Default) {
                            runCatching {
                                val rotation = image.imageInfo.rotationDegrees
                                val raw = image.toBitmap()
                                val upright = if (rotation == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                                Bitmap.createScaledBitmap(upright, PanoramaCapture.FRAME_WIDTH, PanoramaCapture.FRAME_HEIGHT, true).also {
                                    if (upright !== raw) upright.recycle()
                                    raw.recycle()
                                }
                            }.getOrNull()
                        }
                        image.close()
                        if (frame != null) {
                            frames += PanoramaFrame(frame, yawAtShot)
                            shots = frames.size
                        }
                        shooting = false
                        if (PanoramaCapture.isFullTurn(shots)) finish()
                    }
                }
                override fun onError(exception: ImageCaptureException) { shooting = false }
            },
        )
    }

    // Auto shot when the phone reaches the next dot (and is upright).
    LaunchedEffect(started, progress, level, shooting, shots) {
        if (started && !stitching && !shooting && level && shots > 0 && !PanoramaCapture.isFullTurn(shots) && PanoramaCapture.isAligned(progress, shots)) shoot()
    }

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
                    val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    portraitFov(camera.cameraInfo)?.let { hfov = it }
                }.onFailure { failed = true }
            }, ContextCompat.getMainExecutor(context))
            onDispose { runCatching { future.get().unbindAll() } }
        }

        // Guide: the next dot slides towards the centre ring as the phone turns.
        if (started && !PanoramaCapture.isFullTurn(shots) && !stitching) {
            val offset = ((PanoramaCapture.target(shots) - progress) * 6f).coerceIn(-160f, 160f)
            Box(Modifier.align(Alignment.Center).size(64.sdp).border(3.sdp, Color.White, CircleShape))
            Box(
                Modifier.align(Alignment.Center).offset(x = offset.dp).size(22.sdp).clip(CircleShape)
                    .background(if (level) AuthColors.Orange else Color.White.copy(alpha = 0.5f)),
            )
        }

        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(
                    when {
                        failed -> R.string.pano_cam_failed
                        stitching -> R.string.pano_cam_joining
                        !level -> R.string.pano_cam_level
                        !started -> R.string.pano_cam_start_hint
                        shooting -> R.string.pano_cam_hold
                        progress < PanoramaCapture.target(shots) - 20f -> R.string.pano_cam_turn_right
                        else -> R.string.pano_cam_turn
                    },
                ),
                color = Color.White,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(12.sdp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.sdp, vertical = 8.sdp),
            )
            if (started) {
                Spacer(Modifier.height(10.sdp))
                LinearProgressIndicator(
                    progress = { (PanoramaCapture.span(shots, hfov) / PanoramaCapture.FULL_TURN_DEG).coerceIn(0f, 1f) },
                    color = AuthColors.Orange,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.width(200.sdp),
                )
                Spacer(Modifier.height(4.sdp))
                Text(stringResource(R.string.pano_cam_count, shots, 12), color = Color.White, fontFamily = Inter, fontSize = 12.ssp)
            }
        }

        if (stitching) CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.align(Alignment.Center))

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.sdp),
            horizontalArrangement = Arrangement.spacedBy(12.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(stringResource(R.string.add_property_cancel), filled = false, enabled = !stitching, modifier = Modifier.weight(1f)) { discard(); onClose() }
            if (!started) {
                Pill(stringResource(R.string.pano_cam_start), filled = true, enabled = level && !failed, modifier = Modifier.weight(1f)) {
                    started = true; progress = 0f; shoot()
                }
            } else {
                Pill(stringResource(R.string.pano_cam_done), filled = true, enabled = !stitching && !shooting && PanoramaCapture.canFinish(shots, hfov), modifier = Modifier.weight(1f)) { finish() }
            }
        }
    }
}

/** Horizontal field of view with the phone upright = the sensor's short side. */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun portraitFov(info: androidx.camera.core.CameraInfo): Float? = runCatching {
    val c2 = Camera2CameraInfo.from(info)
    val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
    val size = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
    val shortSide = minOf(size.width, size.height)
    Math.toDegrees(2 * kotlin.math.atan((shortSide / 2.0) / focal)).toFloat().takeIf { it in 25f..90f }
}.getOrNull()

/** No fade: unmet conditions use the grey disabled style. */
@Composable
private fun Pill(text: String, filled: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.sdp).clip(RoundedCornerShape(50))
            .background(if (!enabled) AuthColors.Muted else if (filled) AuthColors.Orange else Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.ssp)
    }
}
