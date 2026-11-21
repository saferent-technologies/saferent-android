package com.saferenttechnologies.android.presentation.smart

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.ui.theme.Inter
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** ZXing: QR bitmaps (error correction M, 4-module margin) and a card with the label under it. Nothing is logged. */
object QrImages {
    /** Black-on-white QR of [text], [size] px square. */
    fun qr(text: String, size: Int): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8")
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(m.width * m.height)
        for (y in 0 until m.height) {
            val row = y * m.width
            for (x in 0 until m.width) pixels[row + x] = if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    /** QR + title (and "Scan with your phone camera") under it: the PNG that's shared / printed. */
    fun card(url: String, title: String, footer: String, size: Int = 1024): Bitmap? {
        val code = qr(url, size) ?: return null
        val pad = size / 16
        val titleSize = size / 14f
        val footerSize = size / 26f
        val height = size + pad * 2 + (titleSize * 1.6f).toInt() + (footerSize * 1.8f).toInt()
        val out = Bitmap.createBitmap(size, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.WHITE)
        c.drawBitmap(code, 0f, 0f, null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = titleSize }
        val text = ellipsize(title, p, size - pad * 2f)
        c.drawText(text, size / 2f, size + pad + titleSize, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = footerSize
        p.color = android.graphics.Color.DKGRAY
        c.drawText(footer, size / 2f, size + pad + titleSize * 1.6f + footerSize, p)
        return out
    }

    private fun ellipsize(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    /** PNG in cache/qr → share sheet (FileProvider, read-only grant). */
    fun share(context: Context, bitmap: Bitmap, name: String, chooserTitle: String) {
        runCatching {
            val dir = File(context.cacheDir, "qr").apply { mkdirs() }
            val file = File(dir, "${name.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "saferent-qr" }}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, chooserTitle))
        }
    }

    /** One page, the card centred (Android's print framework, no extra library). */
    fun print(context: Context, bitmap: Bitmap, jobName: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            private var attributes: PrintAttributes? = null
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
                attributes = new
                if (cancel?.isCanceled == true) return callback.onLayoutCancelled()
                callback.onLayoutFinished(PrintDocumentInfo.Builder("$jobName.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).setPageCount(1).build(), true)
            }

            override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
                val pdf = PrintedPdfDocument(context, attributes ?: PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
                try {
                    val page = pdf.startPage(0)
                    val canvas = page.canvas
                    val margin = 36f
                    val w = canvas.width - margin * 2
                    val h = canvas.height - margin * 2
                    val scale = minOf(w / bitmap.width, h / bitmap.height)
                    val dw = bitmap.width * scale
                    val dh = bitmap.height * scale
                    val left = (canvas.width - dw) / 2
                    val top = (canvas.height - dh) / 3
                    canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + dw, top + dh), null)
                    pdf.finishPage(page)
                    FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(null)
                } finally {
                    pdf.close()
                }
            }
        }
        runCatching { manager.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build()) }
    }
}

/**
 * Full screen camera that reads QR codes (CameraX frames → ZXing). [onText] gets each different text
 * it reads (the caller decides if it's a SafeRent code). Camera permission is asked before opening.
 */
@Composable
fun QrScannerScreen(onText: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onText)
    var failed by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        DisposableEffect(Unit) {
            val executor = Executors.newSingleThreadExecutor()
            val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
            val busy = AtomicBoolean(false)
            var last: String? = null
            val main = ContextCompat.getMainExecutor(context)
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { image ->
                        val text = if (busy.compareAndSet(false, true)) decode(reader, image).also { busy.set(false) } else null
                        image.close()
                        // Same code read again → once only (the caller shows a sheet / message).
                        if (text != null && text != last) {
                            last = text
                            main.execute { latest(text) }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onFailure { failed = true }
            }, main)
            onDispose {
                runCatching { future.get().unbindAll() }
                executor.shutdown()
            }
        }
        // Viewfinder
        Box(Modifier.align(Alignment.Center).size(240.sdp).border(3.sdp, Color.White, RoundedCornerShape(20.sdp)))
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.sdp, start = 24.sdp, end = 24.sdp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(if (failed) R.string.qr_camera_failed else R.string.qr_scan_hint),
                color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.ssp, textAlign = TextAlign.Center,
            )
        }
        Box(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.sdp).size(44.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.add_property_cancel), tint = Color.White, modifier = Modifier.size(20.sdp)) }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.sdp))
    }
}

/** Y plane → ZXing; null when there's no QR in the frame. */
private fun decode(reader: MultiFormatReader, image: ImageProxy): String? = runCatching {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height
    val data = ByteArray(width * height)
    buffer.rewind()
    if (rowStride == width) {
        buffer.get(data, 0, minOf(data.size, buffer.remaining()))
    } else {
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            buffer.get(row, 0, minOf(rowStride, buffer.remaining()))
            System.arraycopy(row, 0, data, y * width, width)
        }
    }
    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } finally {
        reader.reset()
    }
}.getOrNull()
