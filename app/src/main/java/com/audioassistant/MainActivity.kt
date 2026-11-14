package com.audioassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.audioassistant.ui.AudioAssistantApp
import com.audioassistant.ui.theme.AudioAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioAssistantTheme { AudioAssistantApp() }
        }
    }
}
