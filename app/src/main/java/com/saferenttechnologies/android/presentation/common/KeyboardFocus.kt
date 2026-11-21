package com.saferenttechnologies.android.presentation.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Tap targets laid over a form (dropdown / date boxes): never take keyboard focus. So the
 * keyboard's "Next" jumps straight to the next text box (the keyboard stays open), and tapping
 * one doesn't steal focus and close the keyboard.
 */
fun Modifier.skipKeyboardFocus(): Modifier = focusProperties { canFocus = false }
