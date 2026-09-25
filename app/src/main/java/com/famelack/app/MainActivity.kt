package com.famelack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.famelack.app.ui.FamelackApp
import com.famelack.app.ui.LocalIsTv
import com.famelack.app.ui.TvMode
import com.famelack.app.ui.theme.FamelackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isTv = TvMode.isTvMode(this)
        setContent {
            FamelackTheme {
                CompositionLocalProvider(LocalIsTv provides isTv) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        FamelackApp()
                    }
                }
            }
        }
    }
}
