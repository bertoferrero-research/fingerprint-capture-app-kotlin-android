package com.bertoferrero.fingerprintcaptureapp.views.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.OpenCvCamera
import com.bertoferrero.fingerprintcaptureapp.views.components.resolvePermissions
import com.bertoferrero.fingerprintcaptureapp.viewmodels.capture.OnlineCaptureViewModel
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.camera.CAMERA
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.BackHandler
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner

/**
 * Pantalla de captura online que combina:
 * - Captura de señales RSSI BLE mediante RssiCaptureService
 * - Captura de imágenes mediante OnlineCaptureController con OpenCV
 * 
 * La pantalla tiene dos modos:
 * 1. Configuración: Permite establecer parámetros antes de iniciar
 * 2. Ejecución: Muestra la cámara y estadísticas de captura en tiempo real
 */
class OnlineCaptureScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        
        // ViewModel
        val viewModel = viewModel<OnlineCaptureViewModel>(
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application)
        )
        
        // Estados locales de configuración
        val isRunning = viewModel.isRunning

        // Registrar broadcast receiver
        // Limpiar al salir
        DisposableEffect(Unit) {
            viewModel.registerBroadcastReceiver()
            onDispose {
                viewModel.unregisterBroadcastReceiver()
                if (viewModel.isRunning) {
                    viewModel.stopCapture(context)
                }
            }
        }

        // Manejo del botón de retroceso
        BackHandler {
            if (isRunning) {
                viewModel.stopCapture(context)
            } else {
                navigator.pop()
            }
        }

        // Mostrar contenido solo si los permisos están concedidos
        if (BleScanner.checkPermissions()) {
            if (!isRunning) {
                ConfigurationContent(
                    viewModel = viewModel,
                    context = context,
                    onStartCapture = { 
                        viewModel.startCapture(context)
                    },
                    onNavigateBack = { navigator.pop() }
                )
            } else {
                ExecutionContent(
                    viewModel = viewModel,
                    context = context,
                    onStopCapture = { 
                        viewModel.stopCapture(context)
                    },
                    onNavigateBack = { navigator.pop() }
                )
            }
        }
    }
}

/**
 * Contenido de configuración inicial antes de comenzar la captura.
 */
@Composable
private fun ConfigurationContent(
    viewModel: OnlineCaptureViewModel,
    context: Context,
    onStartCapture: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Selector de archivos JSON para filtro MAC
        val macFilterListFileChooser = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { fileUri: android.net.Uri? ->
            fileUri?.let {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    try {
                        viewModel.loadMacFilterListFromJson(jsonString)
                        Toast.makeText(
                            context,
                            "MAC filter list updated from JSON: ${viewModel.macFilterList.size} MAC addresses",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (ex: Exception) {
                        android.util.Log.e(
                            "OnlineCaptureScreen",
                            "Error loading MAC filter list from JSON",
                            ex
                        )
                        Toast.makeText(
                            context,
                            "Error loading MAC filter list from JSON",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        //Selector de carpeta de salida
        val folderPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: android.net.Uri? ->
            uri?.let {
                // Guardar permiso persistente
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Guardar el Uri en ViewModel o DataStore para más adelante
                viewModel.updateOutputFolderUri(it)
            }
        }

        // Título
        Text(
            text = "Captura Online (BLE + Imágenes)",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Parámetros de posición
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Posición",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberField(
                        value = viewModel.x,
                        onValueChange = { viewModel.x = it },
                        label = { Text("X (m)") },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = viewModel.y,
                        onValueChange = { viewModel.y = it },
                        label = { Text("Y (m)") },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = viewModel.z,
                        onValueChange = { viewModel.z = it },
                        label = { Text("Z (m)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Parámetros de captura BLE
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configuración BLE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                NumberField(
                    value = viewModel.initDelaySeconds,
                    onValueChange = { viewModel.initDelaySeconds = it },
                    label = { Text("Retraso inicial (segundos)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                NumberField(
                    value = viewModel.minutesLimit,
                    onValueChange = { viewModel.minutesLimit = it },
                    label = { Text("Límite de tiempo (minutos)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Filtro de MACs - carga desde archivo JSON
                Button(
                    onClick = {
                        macFilterListFileChooser.launch(arrayOf("application/json"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load MAC filter list file")
                }
                
                // Mostrar información sobre las MACs cargadas
                if (viewModel.macFilterList.isNotEmpty()) {
                    Text(
                        text = "MAC addresses loaded: ${viewModel.macFilterList.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Mostrar las primeras 3 MACs como ejemplo
                    val displayMacs = viewModel.macFilterList.take(3).joinToString(", ")
                    val suffix = if (viewModel.macFilterList.size > 3) "..." else ""
                    Text(
                        text = "$displayMacs$suffix",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Parámetros de captura de imágenes
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configuración de Imágenes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                NumberField(
                    value = viewModel.samplingFrequency,
                    onValueChange = { viewModel.samplingFrequency = it },
                    label = { Text("Frecuencia de muestreo (milisegundos)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                NumberField(
                    value = viewModel.imageLimit,
                    onValueChange = { viewModel.imageLimit = it },
                    label = { Text("Límite de imágenes (0 = sin límite)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Carpeta de salida
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Carpeta de Salida",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Seleccionar Carpeta")
                }
                
                viewModel.outputFolderUri?.let { uri ->
                    Text(
                        text = "Carpeta: ${uri.path}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Volver")
            }
            
            Button(
                onClick = {
                    if (viewModel.startCapture(context)) {
                        onStartCapture()
                    } else {
                        Toast.makeText(context, "Error al iniciar la captura", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = viewModel.outputFolderUri != null && !viewModel.isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Iniciar Captura")
            }
        }
    }
}

/**
 * Contenido de ejecución durante la captura activa.
 */
@Composable
private fun ExecutionContent(
    viewModel: OnlineCaptureViewModel,
    context: Context,
    onStopCapture: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Cámara OpenCV que ocupa la mayor parte de la pantalla
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Cámara OpenCV integrada con OnlineCaptureController
            OpenCvCamera(
                object : CameraBridgeViewBase.CvCameraViewListener2 {
                    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                        return viewModel.getCameraController().processFrame(inputFrame)
                    }

                    override fun onCameraViewStarted(width: Int, height: Int) {}

                    override fun onCameraViewStopped() {}
                }
            ).Render(modifier = Modifier.fillMaxSize())
            
            // Overlay con estadísticas
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Captura Online",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text("Muestras BLE: ${viewModel.capturedSamplesCounter}")
                    Text("Imágenes: ${viewModel.capturedImagesCounter}")
                    
                    val status = if (viewModel.isRunning) "🟢 Activo" else "🔴 Preparado"
                    Text(
                        text = status,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Panel inferior con controles
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Volver")
                }
                
                Button(
                    onClick = onStopCapture,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Detener")
                }
            }
        }
    }
}