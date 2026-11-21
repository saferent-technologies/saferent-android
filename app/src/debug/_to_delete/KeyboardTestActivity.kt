package com.saferenttechnologies.android.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * DEBUG ONLY: keyboard flicker test (no app code involved).
 * adb shell am start -n com.saferenttechnologies.android/.debug.KeyboardTestActivity
 * Tap between the boxes in each mode; Logcat tag "SR-Keyboard" shows keyboard shown / hidden.
 */
class KeyboardTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var mode by remember { mutableIntStateOf(0) }
                    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
                    LaunchedEffect(ime > 0, mode) { Log.d("SR-Keyboard", "TEST mode=${mode + 1} keyboard ${if (ime > 0) "shown" else "hidden"}") }
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1 Plain", "2 Scroll", "3 Basic", "4 State").forEachIndexed { i, label ->
                                Button(onClick = { mode = i }) { Text(label) }
                            }
                        }
                        Text("Mode ${mode + 1}: tap box A, then B, then C")
                        when (mode) {
                            0 -> Fields(Modifier)
                            1 -> Fields(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()))
                            2 -> BasicFields()
                            else -> StateFields()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fields(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("A", "B", "C", "D", "E").forEach { name ->
            var v by remember { mutableStateOf("") }
            OutlinedTextField(
                value = v,
                onValueChange = { v = it },
                label = { Text("Box $name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BasicFields() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf("A", "B", "C").forEach { name ->
            var v by remember { mutableStateOf("") }
            Text("Basic $name")
            BasicTextField(
                value = v,
                onValueChange = { v = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
}

/** Mode 4: the newer text field (TextFieldState). Different keyboard connection code inside Compose. */
@Composable
private fun StateFields() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("A", "B", "C", "D", "E").forEach { name ->
            val state = androidx.compose.foundation.text.input.rememberTextFieldState()
            OutlinedTextField(
                state = state,
                label = { Text("Box $name") },
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
