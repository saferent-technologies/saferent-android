package com.saferenttechnologies.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.saferenttechnologies.android.presentation.splash.SplashScreen
import com.saferenttechnologies.android.presentation.splash.SplashViewModel
import com.saferenttechnologies.android.ui.theme.SafeRentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (colour only) stays until the saved splash is read → no flash.
        installSplashScreen().setKeepOnScreenCondition { !splashViewModel.state.value.isReady }
        super.onCreate(savedInstanceState)
        // Splash backgrounds (orange / near-black) both need light status-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            SafeRentTheme {
                val splashState by splashViewModel.state.collectAsState()
                if (!splashState.isFinished) {
                    SplashScreen(state = splashState)
                } else {
                    LaunchedEffect(Unit) { enableEdgeToEdge() } // back to theme-based bar icons
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Greeting(
                            name = "Android",
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SafeRentTheme {
        Greeting("Android")
    }
}
