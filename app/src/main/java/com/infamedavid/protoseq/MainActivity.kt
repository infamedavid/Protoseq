package com.infamedavid.protoseq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.infamedavid.protoseq.ui.AppScreen
import com.infamedavid.protoseq.ui.theme.ProtoseqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtoseqTheme {
                AppScreen()
            }
        }
    }
}
