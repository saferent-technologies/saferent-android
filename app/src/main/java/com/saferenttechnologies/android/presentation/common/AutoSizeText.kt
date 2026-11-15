package com.saferenttechnologies.android.presentation.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * One line that never wraps: when it doesn't fit, the font shrinks step by step (down to [minFontSize])
 * instead of going to the next line. Hidden until the size settles, so there's no visible jump.
 */
@Composable
fun AutoSizeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    minFontSize: TextUnit = 9.sp,
) {
    var size by remember(text, fontSize) { mutableStateOf(fontSize) }
    var ready by remember(text, fontSize) { mutableStateOf(false) }
    Text(
        text,
        color = color,
        fontSize = size,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && size.value > minFontSize.value) {
                size = (size.value * 0.92f).coerceAtLeast(minFontSize.value).sp
            } else {
                ready = true
            }
        },
        modifier = modifier.drawWithContent { if (ready) drawContent() },
    )
}
