package com.saferenttechnologies.android.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R

/**
 * Bundled brand fonts (res/font). Bundled — not downloadable — so the splash
 * renders with the right font on the very first frame, even offline.
 */

/** Brand / headings — "SafeRent" title. */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/** Body text — descriptions, labels. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)
