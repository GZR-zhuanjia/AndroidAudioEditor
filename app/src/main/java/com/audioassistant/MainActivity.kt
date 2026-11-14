package com.audioassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.audioassistant.ui.theme.AudioAssistantTheme

/** 阶段 1 仅建立启动宿主；功能页面与导航在阶段 2 实现。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) { }
            }
        }
    }
}
