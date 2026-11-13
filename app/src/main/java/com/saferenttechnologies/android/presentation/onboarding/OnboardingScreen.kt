package com.saferenttechnologies.android.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.domain.model.DefaultOnboarding
import com.saferenttechnologies.android.domain.model.OnboardingContent
import com.saferenttechnologies.android.domain.model.OnboardingMedia
import com.saferenttechnologies.android.domain.model.OnboardingPage
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Onboarding design colours (dark, media-first). */
private object OnboardingColors {
    val Background = Color(0xFF09090C)
    val Card = Color(0xFF16171B)
    val Control = Color(0xFF1C1D21)
    val ControlBorder = Color(0xFF2E3035)
    val Accent = Color(0xFFEE8A4A)
    val Text = Color.White
    val TextMuted = Color(0xFFC9CBD0)
    val DotInactive = Color(0xFF5A5C61)
    val ButtonStart = Color(0xFFF07B3B)
    val ButtonEnd = Color(0xFFDB5E2A)
}

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    effects: Flow<OnboardingEffect>,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state.content ?: return
    val pages = content.pages
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = state.currentPage) { pages.size }
    // Which media candidate each page is on (advances when one fails to load / play).
    val mediaIndex = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            if (effect is OnboardingEffect.ScrollToPage) pagerState.animateScrollToPage(effect.page)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onIntent(OnboardingIntent.PageChanged(it)) }
    }

    val current = pages[state.currentPage.coerceIn(pages.indices)]
    val currentMedia = current.media.getOrNull(mediaIndex[current.id] ?: 0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingColors.Background),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            val page = pages[index]
            val candidate = mediaIndex[page.id] ?: 0
            OnboardingPageContent(
                page = page,
                index = index,
                count = pages.size,
                media = page.media.getOrElse(candidate) { OnboardingMedia.Bundled },
                isCurrentPage = index == pagerState.currentPage,
                muted = state.isMuted,
                onMediaFailed = {
                    // Only advance once per failing candidate.
                    if ((mediaIndex[page.id] ?: 0) == candidate) mediaIndex[page.id] = candidate + 1
                },
            )
        }

        TopBar(
            showMute = currentMedia is OnboardingMedia.Video,
            muted = state.isMuted,
            skippable = content.skippable,
            skipText = content.skipButtonText,
            onToggleMute = { onIntent(OnboardingIntent.ToggleMute) },
            onSkip = { onIntent(OnboardingIntent.Skip) },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(count = pages.size, current = state.currentPage)
            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = current.buttonText,
                onClick = {
                    // Ignore taps while a page is still sliding in: a quick double tap on the
                    // second-last page must not complete onboarding before the user sees the
                    // last page and its "Get started" button.
                    if (!pagerState.isScrollInProgress) onIntent(OnboardingIntent.Next)
                },
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    index: Int,
    count: Int,
    media: OnboardingMedia,
    isCurrentPage: Boolean,
    muted: Boolean,
    onMediaFailed: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Media edge to edge (full screen, behind the status bar and top bar).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OnboardingColors.Card),
        ) {
            OnboardingMediaView(
                media = media,
                isCurrentPage = isCurrentPage,
                muted = muted,
                onFailed = onMediaFailed,
            )
            // Top fade keeps the status bar + top bar readable on bright media;
            // bottom fade blends into the background behind the text and button.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to OnboardingColors.Background.copy(alpha = 0.55f),
                            0.18f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.72f to OnboardingColors.Background.copy(alpha = 0.85f),
                            1f to OnboardingColors.Background,
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 140.dp),
        ) {
            StepChip(step = index + 1, total = count)
            Spacer(Modifier.height(16.dp))
            Text(
                text = highlightedTitle(page.title),
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = page.description,
                color = OnboardingColors.TextMuted,
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                lineHeight = 25.sp,
            )
        }
    }
}

/** Last word (or last two for longer titles) on its own line in the accent colour. */
private fun highlightedTitle(title: String) = buildAnnotatedString {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val highlight = when {
        words.size >= 4 -> 2
        words.size >= 2 -> 1
        else -> 0
    }
    val plain = words.dropLast(highlight).joinToString(" ")
    val accent = words.takeLast(highlight).joinToString(" ")
    withStyle(SpanStyle(color = OnboardingColors.Text)) { append(plain) }
    if (accent.isNotEmpty()) {
        append("\n")
        withStyle(SpanStyle(color = OnboardingColors.Accent)) { append(accent) }
    }
}

@Composable
private fun TopBar(
    showMute: Boolean,
    muted: Boolean,
    skippable: Boolean,
    skipText: String,
    onToggleMute: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(OnboardingColors.Control)
                .border(1.dp, OnboardingColors.ControlBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = OnboardingColors.Text)) { append("Safe") }
                withStyle(SpanStyle(color = OnboardingColors.Accent)) { append("Rent") }
            },
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
        )
        Spacer(Modifier.weight(1f))
        if (showMute) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(OnboardingColors.Control)
                    .border(1.dp, OnboardingColors.ControlBorder, CircleShape)
                    .clickable(role = Role.Button, onClick = onToggleMute),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on),
                    contentDescription = if (muted) "Unmute video" else "Mute video",
                    tint = OnboardingColors.Text,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        if (skippable) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(OnboardingColors.Control)
                    .border(1.dp, OnboardingColors.ControlBorder, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onSkip)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = skipText,
                    color = OnboardingColors.Text,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun StepChip(step: Int, total: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(OnboardingColors.Accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "STEP $step OF $total",
            color = OnboardingColors.Text,
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 36.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (active) OnboardingColors.Accent else OnboardingColors.DotInactive,
                label = "dotColor",
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = OnboardingColors.ButtonStart,
                spotColor = OnboardingColors.ButtonStart,
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(OnboardingColors.ButtonStart, OnboardingColors.ButtonEnd)))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                color = Color.White,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun OnboardingPreview() {
    OnboardingScreen(
        state = OnboardingState(
            isLoaded = true,
            content = OnboardingContent(
                version = DefaultOnboarding.VERSION,
                skippable = true,
                skipButtonText = DefaultOnboarding.SKIP,
                pages = DefaultOnboarding.pages,
            ),
            currentPage = 1,
        ),
        effects = emptyFlow(),
        onIntent = {},
    )
}
