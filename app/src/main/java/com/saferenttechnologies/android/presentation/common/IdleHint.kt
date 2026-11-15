package com.saferenttechnologies.android.presentation.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.saferenttechnologies.android.ui.theme.Inter

/**
 * "e.g. …" example inside an empty text box that disappears as soon as the box is tapped (focused).
 * Pass [source] as the field's interactionSource and [placeholder] as its placeholder.
 */
class IdleHint internal constructor(val source: MutableInteractionSource, private val focused: Boolean) {
    fun placeholder(text: String): (@Composable () -> Unit)? = if (focused) null else { { Text(text, fontFamily = Inter) } }
}

@Composable
fun rememberIdleHint(): IdleHint {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    return IdleHint(source, focused)
}
