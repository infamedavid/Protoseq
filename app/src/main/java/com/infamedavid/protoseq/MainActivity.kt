package com.infamedavid.protoseq

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.infamedavid.protoseq.ui.AppScreen
import com.infamedavid.protoseq.ui.theme.ProtoseqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ProtoseqTheme {
                AppScreen()
            }
        }
    }
}
