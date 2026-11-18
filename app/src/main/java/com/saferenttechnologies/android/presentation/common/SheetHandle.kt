package com.saferenttechnologies.android.presentation.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Put on a bottom sheet's scrolling content (before verticalScroll): swipes / flings inside the
 * content stay inside it and never reach the sheet, so the sheet doesn't "dance" while you scroll
 * the middle. The sheet still follows your finger smoothly when you drag its top handle.
 */
private val KeepSwipesInside = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

fun Modifier.keepSwipesInside(): Modifier = nestedScroll(KeepSwipesInside)
