package com.saferenttechnologies.android.presentation.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.domain.model.SplashAppearance
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Colours from the splash design (light = orange, dark = near-black). */
private data class SplashPalette(
    val background: Color,
    val title: Color,
    val titleAccent: Color,
    val description: Color,
    val footer: Color,
) {
    companion object {
        val Light = SplashPalette(
            background = Color(0xFFE97B36),
            title = Color.White,
            titleAccent = Color.White,
            description = Color.White.copy(alpha = 0.92f),
            footer = Color.White.copy(alpha = 0.78f),
        )
        val Dark = SplashPalette(
            background = Color(0xFF111317),
            title = Color.White,
            titleAccent = Color(0xFFEE9150),
            description = Color(0xFFB4B8BF),
            footer = Color(0xFF8A8F98),
        )
    }
}

@Composable
fun SplashScreen(
    state: SplashState,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val palette = if (darkTheme) SplashPalette.Dark else SplashPalette.Light
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        if (!state.isReady) return@Box
        val appearance = state.appearance

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SplashLogo(logoPath = appearance.logoPath(darkTheme))
            Spacer(Modifier.height(28.dp))
            Text(
                text = brandTitle(appearance.title, palette),
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = appearance.description,
                color = palette.description,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "SAFERENT TECHNOLOGIES",
                color = palette.footer,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp,
            )
            SplashLoading(lottiePath = appearance.lottiePath, darkTheme = darkTheme)
        }
    }
}

private val BRAND_LOGO_SIZE = 148.dp

/** "SafeRent" — dark theme paints the trailing "Rent" in the accent colour (as designed). */
private fun brandTitle(title: String, palette: SplashPalette) = buildAnnotatedString {
    val accent = "Rent"
    if (title.endsWith(accent) && title.length > accent.length) {
        withStyle(SpanStyle(color = palette.title)) { append(title.removeSuffix(accent)) }
        withStyle(SpanStyle(color = palette.titleAccent)) { append(accent) }
    } else {
        withStyle(SpanStyle(color = palette.title)) { append(title) }
    }
}

/** Downloaded server logo when present and readable, otherwise the app's brand logo. */
@Composable
private fun SplashLogo(logoPath: String?) {
    val logoSize = 112.dp
    val sizePx = with(LocalDensity.current) { logoSize.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, logoPath, sizePx) {
        value = logoPath?.let { path ->
            withContext(Dispatchers.IO) { loadLogoBitmap(path, sizePx) }
        }
    }
    val downloaded = bitmap
    if (downloaded != null) {
        Image(
            bitmap = downloaded,
            contentDescription = null,
            modifier = Modifier.size(logoSize),
            contentScale = ContentScale.Fit,
        )
    } else {
        // Brand logo recoloured for the splash: drawable/splash_logo (light, white) and
        // drawable-night/splash_logo (dark, orange + white). The artwork has ~16% padding
        // inside its 1024 canvas, so it is drawn a bit larger to match other logos.
        Image(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = null,
            modifier = Modifier.size(BRAND_LOGO_SIZE),
        )
    }
}

/** Downloaded loading Lottie when present and valid, otherwise the bundled one for the theme. */
@Composable
private fun SplashLoading(lottiePath: String?, darkTheme: Boolean) {
    val bundledSpec = LottieCompositionSpec.RawRes(
        if (darkTheme) R.raw.splash_loading_dark else R.raw.splash_loading_light,
    )
    val remoteSpec = lottiePath?.takeIf { File(it).isFile }?.let { LottieCompositionSpec.File(it) }

    val bundled by rememberLottieComposition(bundledSpec)
    val remoteResult = rememberLottieComposition(remoteSpec ?: bundledSpec)
    val composition = if (remoteSpec != null && !remoteResult.isFailure) remoteResult.value else bundled

    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = Modifier
            .width(132.dp)
            .height(24.dp),
    )
}

@Preview(name = "Light", showSystemUi = true)
@Composable
private fun SplashLightPreview() {
    SplashScreen(state = SplashState(SplashAppearance(), isReady = true), darkTheme = false)
}

@Preview(name = "Dark", showSystemUi = true)
@Composable
private fun SplashDarkPreview() {
    SplashScreen(state = SplashState(SplashAppearance(), isReady = true), darkTheme = true)
}
