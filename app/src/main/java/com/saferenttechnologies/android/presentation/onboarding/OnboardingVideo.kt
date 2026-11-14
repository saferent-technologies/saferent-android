package com.saferenttechnologies.android.presentation.onboarding

import android.view.ViewGroup
import androidx.annotation.OptIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Looping, centre-cropped onboarding video with ExoPlayer (Media3). Starts muted.
 * Plays only while [playing] and the screen is resumed; [onError] → caller falls back.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun OnboardingVideo(
    url: String,
    playing: Boolean,
    muted: Boolean,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) = key(url) {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)
    var resumed by remember { mutableStateOf(true) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> resumed = false
                Lifecycle.Event.ON_RESUME -> resumed = true
                else -> Unit
            }
        }
        val errorListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = currentOnError()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        player.addListener(errorListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(errorListener)
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // fill edge to edge, keep aspect
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
    )

    LaunchedEffect(playing, resumed) { player.playWhenReady = playing && resumed }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
}
