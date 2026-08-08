package com.relayo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.relayo.app.ui.theme.RelayoTheme
import com.relayo.feature.meshstatus.MeshStatusScreen

@AndroidEntryPoint
class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RelayoTheme {
                MeshStatusScreen()
            }
        }
    }
}