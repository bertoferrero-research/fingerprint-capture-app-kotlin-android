package com.bertoferrero.fingerprintcaptureapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.FadeTransition
import com.bertoferrero.fingerprintcaptureapp.models.SharedPreferencesManager
import com.bertoferrero.fingerprintcaptureapp.ui.theme.FingerPrintCaptureAppAndroidTheme
import com.bertoferrero.fingerprintcaptureapp.views.MainScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SharedPreferencesManager.init(this)
        enableEdgeToEdge()
        OpenCVLoader.initLocal()
        setContent {
            FingerPrintCaptureAppAndroidTheme {
                /*Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }*/
                Navigator(screen = MainScreen()){ navigator ->
                    FadeTransition(navigator)
                }
            }
        }
    }
}