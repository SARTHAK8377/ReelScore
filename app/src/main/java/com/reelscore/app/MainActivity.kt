package com.reelscore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reelscore.app.sync.ScoreUpload
import com.reelscore.app.ui.AppNav
import com.reelscore.app.ui.theme.ReelScoreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Uploads totals for a signed-in account; does nothing while signed out.
        ScoreUpload.schedule(this)
        setContent {
            ReelScoreTheme {
                AppNav()
            }
        }
    }
}
