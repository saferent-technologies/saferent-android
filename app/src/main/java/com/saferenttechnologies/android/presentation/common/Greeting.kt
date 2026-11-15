package com.saferenttechnologies.android.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter
import com.saferenttechnologies.android.ui.theme.PlusJakartaSans
import java.util.Calendar

/** Hour of day → greeting: 5–11 morning, 12–16 afternoon, else evening. */
fun greetingFor(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..16 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

/** Small "Good morning" + the full name under it (blank name → "Welcome to SafeRent"). */
@Composable
fun Greeting(fullName: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val hello = remember { greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    Column(modifier) {
        Text(stringResource(hello), color = AuthColors.Body, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.ssp)
        Text(
            fullName.trim().ifBlank { stringResource(R.string.greeting_welcome) },
            color = AuthColors.Ink,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 19.ssp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let { Text(it, color = AuthColors.Body, fontFamily = Inter, fontSize = 12.ssp) }
    }
}
