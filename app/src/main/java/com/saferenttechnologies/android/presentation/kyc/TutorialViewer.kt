package com.saferenttechnologies.android.presentation.kyc

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.KycTutorial
import com.saferenttechnologies.android.domain.model.TutorialType
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.video.TourVideoPlayer
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** "How to verify": one card per way to verify (title + description from the server), shimmer while loading. */
@Composable
internal fun TutorialsScreen(tutorials: List<KycTutorial>?, error: String?, onIntent: (KycIntent) -> Unit) {
    androidx.activity.compose.BackHandler { onIntent(KycIntent.CloseTutorials) }
    Column(Modifier.fillMaxSize().background(AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(com.saferenttechnologies.android.presentation.common.TOP_ICON).clip(CircleShape).background(AuthColors.Surface)
                    .clickable(role = Role.Button) { onIntent(KycIntent.CloseTutorials) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_left), stringResource(R.string.add_property_back), tint = AuthColors.Ink,
                    modifier = Modifier.size(com.saferenttechnologies.android.presentation.common.TOP_ICON_GLYPH),
                )
            }
            Spacer(Modifier.width(12.sdp))
            Text(stringResource(R.string.kyc_tutorial), color = AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp)
        }
        when {
            // First load: shimmer cards of the same size as the real ones.
            tutorials == null && error == null -> TutorialsSkeleton()
            tutorials == null -> Column(
                Modifier.fillMaxSize().padding(24.sdp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.orEmpty(), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.size(12.sdp))
                Text(
                    stringResource(R.string.kyc_tutorial_retry), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp,
                    modifier = Modifier.clip(RoundedCornerShape(8.sdp)).clickable(role = Role.Button) { onIntent(KycIntent.RetryTutorials) }.padding(horizontal = 12.sdp, vertical = 8.sdp),
                )
            }
            tutorials.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.sdp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.kyc_tutorial_empty), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.sdp, end = 16.sdp, top = 4.sdp, bottom = 16.sdp),
                verticalArrangement = Arrangement.spacedBy(10.sdp),
            ) {
                items(tutorials.size, key = { tutorials[it].id }) { i -> TutorialCard(tutorials[i]) { onIntent(KycIntent.OpenTutorial(tutorials[i])) } }
            }
        }
    }
}

@Composable
private fun TutorialCard(t: KycTutorial, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
            .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp))
            .clickable(role = Role.Button, onClick = onClick).padding(14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.sdp).clip(CircleShape).background(AuthColors.OrangeSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(t.type.icon()), null, tint = AuthColors.OrangeText, modifier = Modifier.size(20.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(t.title.ifBlank { stringResource(t.type.label()) }, color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp)
            if (t.description.isNotBlank()) {
                Text(t.description, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp, lineHeight = 17.ssp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(6.sdp))
            Text(
                stringResource(t.type.label()), color = AuthColors.OrangeText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.ssp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(AuthColors.OrangeSoft).padding(horizontal = 8.sdp, vertical = 2.sdp),
            )
        }
        Spacer(Modifier.width(8.sdp))
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = AuthColors.Body, modifier = Modifier.size(18.sdp))
    }
}

/** Same layout as [TutorialCard]: icon circle, title, 2 description lines, type pill. */
@Composable
private fun TutorialsSkeleton() {
    com.saferenttechnologies.android.presentation.common.ShimmerHost {
        Column(Modifier.fillMaxSize().padding(start = 16.sdp, end = 16.sdp, top = 4.sdp), verticalArrangement = Arrangement.spacedBy(10.sdp)) {
            repeat(4) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(AuthColors.Card)
                        .border(1.sdp, AuthColors.Border, RoundedCornerShape(14.sdp)).padding(14.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.saferenttechnologies.android.presentation.common.Bone(Modifier.size(40.sdp), CircleShape)
                    Spacer(Modifier.width(12.sdp))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(20.sdp), contentAlignment = Alignment.CenterStart) { com.saferenttechnologies.android.presentation.common.BoneLine(0.55f, 13.sdp) }
                        Box(Modifier.fillMaxWidth().height(17.sdp), contentAlignment = Alignment.CenterStart) { com.saferenttechnologies.android.presentation.common.BoneLine(0.95f, 10.sdp) }
                        Box(Modifier.fillMaxWidth().height(17.sdp), contentAlignment = Alignment.CenterStart) { com.saferenttechnologies.android.presentation.common.BoneLine(0.7f, 10.sdp) }
                        Spacer(Modifier.size(6.sdp))
                        com.saferenttechnologies.android.presentation.common.Bone(Modifier.width(56.sdp).height(16.sdp), RoundedCornerShape(50))
                    }
                    Spacer(Modifier.width(8.sdp))
                    com.saferenttechnologies.android.presentation.common.Bone(Modifier.size(18.sdp), CircleShape)
                }
            }
        }
    }
}

/** Full screen guide: video (ExoPlayer), PDF (Android's own PDF renderer) or picture (pinch to zoom). */
@Composable
internal fun TutorialViewer(tutorial: KycTutorial, pdfPath: String?, loading: Boolean, onClose: () -> Unit) {
    if (tutorial.type == TutorialType.VIDEO) {
        // Public guide file: no sign in token sent with it.
        TourVideoPlayer(tutorial.url, withAuth = false, onClose = onClose)
        return
    }
    val dark = tutorial.type == TutorialType.IMAGE
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(if (dark) Color.Black else AuthColors.Background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.sdp, vertical = 6.sdp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.sdp).clip(CircleShape).background(if (dark) Color.White.copy(alpha = 0.15f) else AuthColors.Surface)
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.video_close), tint = if (dark) Color.White else AuthColors.Ink, modifier = Modifier.size(20.sdp)) }
                Spacer(Modifier.width(12.sdp))
                Text(
                    tutorial.title.ifBlank { stringResource(R.string.kyc_tutorial) },
                    color = if (dark) Color.White else AuthColors.Ink, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.ssp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when {
                    tutorial.type == TutorialType.IMAGE -> ZoomImage(tutorial.url, tutorial.title)
                    loading || pdfPath == null -> CircularProgressIndicator(color = AuthColors.Orange)
                    else -> PdfPages(pdfPath)
                }
            }
        }
    }
}

@Composable
private fun ZoomImage(url: String, title: String) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { z, pan, _ ->
        scale = (scale * z).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = url,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().transformable(zoom).graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        },
    )
}

/** Every page drawn at screen width as you scroll (pages off screen are dropped by the list). */
@Composable
private fun PdfPages(path: String) {
    val renderer = remember(path) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    val lock = remember(path) { Mutex() }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) {
        Text(stringResource(R.string.kyc_tutorial_failed), color = AuthColors.Body, fontFamily = Inter, fontSize = 14.ssp, modifier = Modifier.padding(24.sdp))
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.sdp),
            verticalArrangement = Arrangement.spacedBy(10.sdp),
        ) {
            items(renderer.pageCount) { index -> PdfPage(renderer, lock, index, widthPx) }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, lock: Mutex, index: Int, widthPx: Int) {
    var page by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, widthPx) {
        page = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    renderer.openPage(index).use { p ->
                        val w = widthPx.coerceIn(1, 1600)
                        val h = (w * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                            b.eraseColor(android.graphics.Color.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.getOrNull()
            }
        }
    }
    val b = page
    val shape = RoundedCornerShape(8.sdp)
    if (b == null) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.707f).clip(shape).background(Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AuthColors.Orange, modifier = Modifier.size(24.sdp))
        }
    } else {
        Image(
            b.asImageBitmap(), null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(b.width / b.height.toFloat()).clip(shape).border(1.sdp, AuthColors.Border, shape),
        )
    }
}

private fun TutorialType.icon(): Int = when (this) {
    TutorialType.VIDEO -> R.drawable.ic_play
    TutorialType.PDF -> R.drawable.ic_document
    TutorialType.IMAGE -> R.drawable.ic_document
}

private fun TutorialType.label(): Int = when (this) {
    TutorialType.VIDEO -> R.string.kyc_tutorial_video
    TutorialType.PDF -> R.string.kyc_tutorial_pdf
    TutorialType.IMAGE -> R.string.kyc_tutorial_image
}
