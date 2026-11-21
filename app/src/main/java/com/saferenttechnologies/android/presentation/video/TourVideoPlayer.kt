package com.saferenttechnologies.android.presentation.video

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Full-screen video tour player (ExoPlayer, with controls). Server videos need the signed-in
 * user's token (same as photos); local files (the one just recorded) play as they are.
 */
@OptIn(UnstableApi::class)
@Composable
fun TourVideoPlayer(url: String, withAuth: Boolean = true, onClose: () -> Unit) {
    // Whole screen (under the status / navigation bars too) so the full video shows, not a window.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            TourVideo(url, Modifier.fillMaxSize().navigationBarsPadding(), withAuth)
            Box(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.sdp).size(40.sdp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.video_close), tint = Color.White, modifier = Modifier.size(20.sdp)) }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TourVideo(url: String, modifier: Modifier = Modifier, withAuth: Boolean = true) {
    val context = LocalContext.current
    // Local file or a public file (KYC guides): no sign in token is sent.
    val local = url.startsWith("/") || url.startsWith("file:") || !withAuth
    var headers by remember(url) { mutableStateOf<Map<String, String>?>(if (local) emptyMap() else null) }
    LaunchedEffect(url) {
        if (!local) headers = withContext(Dispatchers.IO) {
            runCatching { FirebaseAuth.getInstance().currentUser?.let { Tasks.await(it.getIdToken(false), 10, TimeUnit.SECONDS).token } }
                .getOrNull()?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
        }
    }
    val h = headers ?: return Box(modifier.background(Color.Black))
    val player = remember(url, h) {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(h)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, http)))
            .build().apply {
                setMediaItem(MediaItem.fromUri(if (url.startsWith("/")) Uri.fromFile(java.io.File(url)) else Uri.parse(url)))
                playWhenReady = true
                prepare()
            }
    }
    // App in the background → pause; screen closed → release.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = true
                // Whole frame visible (portrait or landscape), nothing cropped.
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = true
                this.player = player
            }
        },
        update = { it.player = player },
    )
}
