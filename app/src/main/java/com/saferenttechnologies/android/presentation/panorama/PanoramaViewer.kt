package com.saferenttechnologies.android.presentation.panorama

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.PanoramaRules
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.common.TOP_ICON
import com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH
import com.saferenttechnologies.android.ui.theme.Inter

/**
 * Full-screen 360° viewer (OpenGL ES 2.0, no library): drag to look around, pinch to zoom,
 * "Move phone" uses the rotation sensor. [urls] = the property's 360 photos.
 */
@Composable
fun PanoramaViewer(urls: List<String>, onClose: () -> Unit) {
    if (urls.isEmpty()) return
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var gyro by remember { mutableStateOf(false) }
    val renderer = remember { PanoramaRenderer() }
    val hasGyro = remember { (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null }

    LaunchedEffect(index) {
        loading = true
        failed = false
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(urls[index]).size(PanoramaRules.MAX_WIDTH, PanoramaRules.MAX_HEIGHT).allowHardware(false).build(),
        )
        val bmp = (result as? SuccessResult)?.image?.toBitmap()
        if (bmp == null) {
            failed = true
        } else {
            renderer.yaw = 0f
            renderer.pitch = 0f
            renderer.setImage(bmp, PanoramaRules.projection(bmp.width, bmp.height))
        }
        loading = false
    }
    GyroEffect(gyro, renderer)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx -> panoramaView(ctx, renderer) { gyro = false } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.onPause() },
            )
            if (loading) CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.align(Alignment.Center))
            if (failed) Text(stringResource(R.string.pano_failed), color = Color.White, fontFamily = Inter, modifier = Modifier.align(Alignment.Center).padding(24.sdp))

            // Top: close + "360°" + n / total
            Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.sdp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(R.drawable.ic_close, stringResource(R.string.explore_clear), onClose)
                Spacer(Modifier.width(10.sdp))
                Pill(if (urls.size > 1) "360°  ${index + 1} / ${urls.size}" else "360°")
            }
            // Bottom: prev · move phone · next
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (urls.size > 1) {
                    RoundIcon(R.drawable.ic_chevron_left, stringResource(R.string.pano_previous)) { index = (index - 1 + urls.size) % urls.size }
                    Spacer(Modifier.width(10.sdp))
                }
                if (hasGyro) {
                    Text(
                        stringResource(if (gyro) R.string.pano_gyro_on else R.string.pano_gyro_off),
                        color = if (gyro) Color.Black else Color.White,
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.ssp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (gyro) AuthColors.Orange else Color.Black.copy(alpha = 0.55f))
                            .clickable(role = Role.Switch) { gyro = !gyro }.padding(horizontal = 16.sdp, vertical = 10.sdp),
                    )
                } else {
                    Pill(stringResource(R.string.pano_drag_hint))
                }
                if (urls.size > 1) {
                    Spacer(Modifier.width(10.sdp))
                    RoundIcon(R.drawable.ic_arrow_forward, stringResource(R.string.pano_next)) { index = (index + 1) % urls.size }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(TOP_ICON).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(TOP_ICON_GLYPH)) }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        color = Color.White,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.ssp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.sdp, vertical = 6.sdp),
    )
}

/** GLSurfaceView + drag (look around) + pinch (zoom). Touching turns "Move phone" off. */
@SuppressLint("ClickableViewAccessibility")
private fun panoramaView(context: Context, renderer: PanoramaRenderer, onTouched: () -> Unit): GLSurfaceView {
    val view = GLSurfaceView(context)
    view.setEGLContextClientVersion(2)
    view.setRenderer(renderer)
    view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            renderer.fov = (renderer.fov / d.scaleFactor).coerceIn(MIN_FOV, MAX_FOV)
            return true
        }
    })
    var lastX = 0f
    var lastY = 0f
    view.setOnTouchListener { v, e ->
        scale.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; onTouched() }
            MotionEvent.ACTION_MOVE -> if (!scale.isInProgress && e.pointerCount == 1) {
                // Degrees per pixel follow the zoom, so a drag feels the same at any zoom.
                val perPx = renderer.fov / v.height.coerceAtLeast(1)
                renderer.yaw -= (e.x - lastX) * perPx
                renderer.pitch = (renderer.pitch - (e.y - lastY) * perPx).coerceIn(-renderer.maxPitch, renderer.maxPitch)
                lastX = e.x
                lastY = e.y
            }
            MotionEvent.ACTION_POINTER_UP -> { lastX = e.x; lastY = e.y }
        }
        true
    }
    return view
}

/** "Move phone": rotation sensor → where the phone points = where you look. */
@Composable
private fun GyroEffect(on: Boolean, renderer: PanoramaRenderer) {
    val context = LocalContext.current
    DisposableEffect(on) {
        if (!on) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return@DisposableEffect onDispose { }
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val angles = FloatArray(3)
        var startYaw: Float? = null
        val baseYaw = renderer.yaw
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                // Phone held upright (portrait), looking through the screen.
                SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                SensorManager.getOrientation(remapped, angles)
                val azimuth = Math.toDegrees(angles[0].toDouble()).toFloat()
                val first = startYaw ?: azimuth.also { startYaw = it }
                renderer.yaw = baseYaw + (azimuth - first)
                renderer.pitch = (-Math.toDegrees(angles[1].toDouble()).toFloat()).coerceIn(-renderer.maxPitch, renderer.maxPitch)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
}

private const val MIN_FOV = 35f
private const val MAX_FOV = 100f
