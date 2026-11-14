package com.saferenttechnologies.android.presentation.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.saferenttechnologies.android.R
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

/** Coil (with the app's OkHttp client). PNG / JPG / WEBP; a failure falls back to the next media. */
@Composable
private fun RemoteImage(url: String, onFailed: () -> Unit, modifier: Modifier) {
    val currentOnFailed by rememberUpdatedState(onFailed)
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { currentOnFailed() },
        modifier = modifier.fillMaxSize(),
    )
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
