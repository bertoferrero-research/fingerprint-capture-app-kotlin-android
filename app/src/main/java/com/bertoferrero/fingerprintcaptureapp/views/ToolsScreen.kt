package com.bertoferrero.fingerprintcaptureapp.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.settings.CameraSamplerScreen
import com.bertoferrero.fingerprintcaptureapp.views.testscreens.TestRssiMonitorScreen
import com.bertoferrero.fingerprintcaptureapp.views.tools.MatPhotoCompressionToolScreen

/**
 * Pantalla de herramientas utilitarias para el usuario final.
 * Incluye herramientas de muestreo, monitoreo y compresión de datos.
 */
class ToolsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Sección de herramientas de captura y muestreo
                    Text(
                        text = "Capture & Sampling Tools",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Button(
                        onClick = {
                            navigator.push(CameraSamplerScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Camera Sampler")
                    }

                    Button(
                        onClick = {
                            navigator.push(TestRssiMonitorScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RSSI Monitor")
                    }

                    // Sección de herramientas de procesamiento
                    Text(
                        text = "Processing Tools",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    Button(
                        onClick = {
                            navigator.push(MatPhotoCompressionToolScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("MatPhoto Compression Tool")
                    }
                }
            }
        }
    }
}