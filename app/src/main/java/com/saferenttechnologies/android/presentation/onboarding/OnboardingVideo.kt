package com.saferenttechnologies.android.presentation.onboarding

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Looping, centre-cropped video with the platform MediaPlayer (no third-party player).
 * Plays only while [playing] and the screen is resumed; [onError] → caller falls back.
 */
@Composable
internal fun OnboardingVideo(
    url: String,
    playing: Boolean,
    muted: Boolean,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) = key(url) {
    val currentOnError by rememberUpdatedState(onError)
    val player = remember { MediaPlayer() }
    var prepared by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> resumed = false
                Lifecycle.Event.ON_RESUME -> resumed = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { player.release() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).also { view ->
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        try {
                            player.setSurface(Surface(texture))
                            player.setDataSource(context, Uri.parse(url))
                            player.isLooping = true
                            player.setOnPreparedListener { mp ->
                                view.centerCrop(mp.videoWidth, mp.videoHeight)
                                prepared = true
                            }
                            player.setOnVideoSizeChangedListener { _, w, h -> view.centerCrop(w, h) }
                            player.setOnErrorListener { _, _, _ ->
                                currentOnError()
                                true
                            }
                            player.prepareAsync()
                        } catch (e: Exception) {
                            currentOnError()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        runCatching { view.centerCrop(player.videoWidth, player.videoHeight) }
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )

    LaunchedEffect(prepared, playing, resumed) {
        if (!prepared) return@LaunchedEffect
        runCatching { if (playing && resumed) player.start() else player.pause() }
    }
    LaunchedEffect(prepared, muted) {
        if (!prepared) return@LaunchedEffect
        val volume = if (muted) 0f else 1f
        runCatching { player.setVolume(volume, volume) }
    }
}

/** Scales the video to fill the view (like ContentScale.Crop), keeping its aspect ratio. */
private fun TextureView.centerCrop(videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0 || width == 0 || height == 0) return
    val viewW = width.toFloat()
    val viewH = height.toFloat()
    val scale = maxOf(viewW / videoWidth, viewH / videoHeight)
    setTransform(
        Matrix().apply {
            setScale(videoWidth * scale / viewW, videoHeight * scale / viewH, viewW / 2f, viewH / 2f)
        },
    )
}
