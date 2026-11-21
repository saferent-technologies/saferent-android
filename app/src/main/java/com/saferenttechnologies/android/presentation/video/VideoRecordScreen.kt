package com.saferenttechnologies.android.presentation.video

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.saferenttechnologies.android.domain.model.VideoRules
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter
import java.io.File
import java.util.UUID

/**
 * Records a flat / room walkthrough in the app (no gallery): 1080p (720p fallback) at
 * [VideoRules.BITRATE], no sound, max [VideoRules.MAX_SECONDS] s. The SafeRent watermark is drawn
 * into the middle of every frame (CameraX OverlayEffect), so it's in the saved file itself.
 * After recording: watch it, Retake or Use video. Camera permission is asked before opening this.
 */
@Composable
fun VideoRecordScreen(onRecorded: (filePath: String, durationSec: Int) -> Unit, onClose: () -> Unit, hd720: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // [hd720]: request videos (720p, smaller upload); tours stay 1080p.
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(
                if (hd720) QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                else QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)),
            )
            .setTargetVideoEncodingBitRate(if (hd720) VideoRules.BITRATE_720 else VideoRules.BITRATE)
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var recorded by remember { mutableStateOf<Pair<File, Int>?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun discard() { recorded?.first?.delete(); recorded = null }

    BackHandler {
        when {
            recording != null -> { recording?.stop(); recording = null }
            recorded != null -> discard()
            else -> onClose()
        }
    }

    val shot = recorded
    if (shot != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            TourVideo(shot.first.absolutePath, Modifier.fillMaxSize())
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.sdp),
                horizontalArrangement = Arrangement.spacedBy(12.sdp),
            ) {
                Pill(stringResource(R.string.video_retake), filled = false, modifier = Modifier.weight(1f)) { discard() }
                Pill(stringResource(R.string.video_use), filled = true, modifier = Modifier.weight(1f)) {
                    recorded = null
                    onRecorded(shot.first.absolutePath, shot.second)
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        DisposableEffect(Unit) {
            val effect = watermarkEffect(context)
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(videoCapture).addEffect(effect).build()
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                }.onFailure { failed = true }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                recording?.stop()
                runCatching { future.get().unbindAll() }
                effect.close()
            }
        }

        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    failed -> stringResource(R.string.video_failed)
                    recording != null -> VideoRules.clock(seconds) + " / " + VideoRules.clock(VideoRules.MAX_SECONDS)
                    else -> stringResource(R.string.video_hint, VideoRules.MAX_SECONDS)
                },
                color = Color.White,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.ssp,
                textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(12.sdp))
                    .background(if (recording != null) AuthColors.Error.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.sdp, vertical = 8.sdp),
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.sdp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(stringResource(R.string.add_property_cancel), filled = false, enabled = recording == null, modifier = Modifier.weight(1f)) { onClose() }
            // Record / stop: red ring; stop only after the minimum length (grey until then).
            val canStop = recording == null || VideoRules.canStop(seconds)
            Box(
                Modifier.padding(horizontal = 20.sdp).size(72.sdp).clip(CircleShape).border(4.sdp, Color.White, CircleShape)
                    .clickable(enabled = !failed && canStop, role = Role.Button) {
                        val active = recording
                        if (active != null) {
                            active.stop(); recording = null
                        } else {
                            seconds = 0
                            val file = File(File(context.cacheDir, "videos").apply { mkdirs() }, "${UUID.randomUUID()}.mp4")
                            val options = FileOutputOptions.Builder(file).setDurationLimitMillis(VideoRules.MAX_SECONDS * 1000L).build()
                            recording = recorder.prepareRecording(context, options) // no audio: smaller, no mic permission
                                .start(ContextCompat.getMainExecutor(context)) { event ->
                                    when (event) {
                                        is VideoRecordEvent.Status -> seconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                                        is VideoRecordEvent.Finalize -> {
                                            recording = null
                                            val ok = !event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                                            if (ok && file.length() > 0) recorded = file to seconds.coerceAtLeast(1) else { file.delete(); failed = !ok }
                                        }
                                        else -> Unit
                                    }
                                }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(if (recording != null) 28.sdp else 56.sdp)
                        .clip(if (recording != null) RoundedCornerShape(6.sdp) else CircleShape)
                        .background(if (canStop) AuthColors.Error else AuthColors.Muted),
                )
            }
            Box(Modifier.weight(1f))
        }
    }
}

/** SafeRent logo + name, faint, in the middle of every preview and video frame. */
private fun watermarkEffect(context: android.content.Context): OverlayEffect {
    val effect = OverlayEffect(CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE, 0, Handler(Looper.getMainLooper())) { }
    var mark: Bitmap? = null
    effect.setOnDrawListener { frame ->
        val canvas = frame.overlayCanvas
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val rotation = frame.rotationDegrees
        val w = frame.size.width
        val h = frame.size.height
        // Upright size of the frame as people will see it.
        val (uw, uh) = if (rotation % 180 == 0) w to h else h to w
        val bmp = mark?.takeIf { it.width == uw && it.height == uh }
            ?: CapturedPhotoWriter.drawWatermark(context, Bitmap.createBitmap(uw, uh, Bitmap.Config.ARGB_8888), CapturedPhotoWriter.WATERMARK).also { mark = it }
        canvas.save()
        // The buffer is shown turned by [rotation] → draw the upright mark turned back, centred.
        canvas.rotate(-rotation.toFloat(), w / 2f, h / 2f)
        canvas.drawBitmap(bmp, (w - uw) / 2f, (h - uh) / 2f, null)
        canvas.restore()
        true
    }
    return effect
}

@Composable
private fun Pill(text: String, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(48.sdp).clip(RoundedCornerShape(50))
            .background(if (!enabled) AuthColors.Muted else if (filled) AuthColors.Orange else Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.ssp) }
}
