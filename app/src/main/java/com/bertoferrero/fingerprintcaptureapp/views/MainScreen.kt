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
import com.bertoferrero.fingerprintcaptureapp.views.capture.OfflineCaptureScreen
import com.bertoferrero.fingerprintcaptureapp.views.capture.OnlineCaptureScreen

/**
 * Pantalla principal de navegación de la aplicación.
 * Organizada en 5 secciones principales según funcionalidad:
 * - Capture: Funcionalidades de captura de datos
 * - Processing: Postprocesamiento de datos capturados
 * - Tools: Herramientas utilitarias para el usuario
 * - Configuration: Configuración del sistema
 * - Tests: Pruebas de desarrollo (opcional)
 */
class MainScreen : Screen {
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
                    // Sección 1: Captura de datos
                    Text(
                        text = "Data Capture",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Button(
                        onClick = {
                            navigator.push(OfflineCaptureScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Offline Capture")
                    }
                    Button(
                        onClick = {
                            navigator.push(OnlineCaptureScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Online Capture (BLE + Images)")
                    }

                    // Sección 2: Procesamiento
                    Text(
                        text = "Data Processing",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    Button(
                        onClick = {
                            // TODO: Implementar navegación a BatchImageProcessingScreen
                            // navigator.push(BatchImageProcessingScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Batch Image Processing (Images → CSV)")
                    }
                    
                    Button(
                        onClick = {
                            // TODO: Implementar navegación a OnlinePostprocessingScreen
                            // navigator.push(OnlinePostprocessingScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Online Sample Postprocessing")
                    }

                    // Sección 3: Otros
                    Text(
                        text = "Others",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    
                    Button(
                        onClick = {
                            navigator.push(ToolsScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tools")
                    }
                    
                    Button(
                        onClick = {
                            navigator.push(ConfigurationScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configuration")
                    }
                    
                    Button(
                        onClick = {
                            navigator.push(TestsScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Development Tests")
                    }

                }

            }
        }

    }
}