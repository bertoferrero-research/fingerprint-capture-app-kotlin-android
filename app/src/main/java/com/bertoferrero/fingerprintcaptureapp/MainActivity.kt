package com.bertoferrero.fingerprintcaptureapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
        
        // Solicitar exclusión de optimización de batería para que el servicio no se corte
        requestBatteryOptimizationExclusion()
        
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
    
    private fun requestBatteryOptimizationExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Si no se puede abrir la configuración específica, abrir la general
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (e2: Exception) {
                        android.util.Log.w("MainActivity", "Cannot open battery optimization settings", e2)
                    }
                }
            }
        }
    }
}