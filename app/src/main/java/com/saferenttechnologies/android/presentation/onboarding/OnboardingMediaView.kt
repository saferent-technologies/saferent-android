package com.saferenttechnologies.android.presentation.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.media.LocalRemoteImageLoader
import com.saferenttechnologies.android.domain.model.OnboardingMedia

/** Shows one media item; calls [onFailed] so the page can fall back to the next candidate. */
@Composable
internal fun OnboardingMediaView(
    media: OnboardingMedia,
    isCurrentPage: Boolean,
    muted: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (media) {
        is OnboardingMedia.Video -> OnboardingVideo(
            url = media.url,
            playing = isCurrentPage,
            muted = muted,
            onError = onFailed,
            modifier = modifier.fillMaxSize(),
        )
        is OnboardingMedia.Image -> RemoteImage(media.url, onFailed, modifier)
        is OnboardingMedia.Lottie -> RemoteLottie(media.url, isCurrentPage, onFailed, modifier)
        OnboardingMedia.Bundled -> Image(
            painter = painterResource(R.drawable.onboarding_page),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RemoteImage(url: String, onFailed: () -> Unit, modifier: Modifier) {
    val loader = LocalRemoteImageLoader.current
    // Full-screen image: decode at the screen's size so it stays sharp edge to edge.
    val window = LocalWindowInfo.current.containerSize
    val maxPx = maxOf(window.width, window.height).takeIf { it > 0 }
        ?: with(LocalDensity.current) { 900.dp.roundToPx() }
    val currentOnFailed by rememberUpdatedState(onFailed)
    // null = loading; Result.failure = give up and fall back
    val result by produceState<Result<ImageBitmap>?>(initialValue = null, url, loader) {
        val bitmap = loader?.load(url, maxPx)
        value = if (bitmap != null) Result.success(bitmap.asImageBitmap()) else Result.failure(IllegalStateException())
    }
    val loaded = result
    when {
        loaded == null -> Box(modifier.fillMaxSize())
        loaded.isSuccess -> Image(
            bitmap = loaded.getOrThrow(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
        else -> LaunchedEffect(url) { currentOnFailed() }
    }
}

@Composable
private fun RemoteLottie(url: String, isCurrentPage: Boolean, onFailed: () -> Unit, modifier: Modifier) {
    val compositionResult = rememberLottieComposition(LottieCompositionSpec.Url(url))
    val currentOnFailed by rememberUpdatedState(onFailed)
    if (compositionResult.isFailure) {
        LaunchedEffect(url) { currentOnFailed() }
        return
    }
    LottieAnimation(
        composition = compositionResult.value,
        iterations = LottieConstants.IterateForever,
        isPlaying = isCurrentPage,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize(),
    )
}
