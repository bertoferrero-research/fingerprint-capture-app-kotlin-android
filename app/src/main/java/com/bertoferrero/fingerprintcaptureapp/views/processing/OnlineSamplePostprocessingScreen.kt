package com.bertoferrero.fingerprintcaptureapp.views.processing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.viewmodels.processing.OnlineSamplePostprocessingViewModel

/**
 * Pantalla para postprocesamiento de muestras online.
 * Procesa un directorio con __all.csv e imágenes para generar CSV con lecturas y posiciones.
 */
class OnlineSamplePostprocessingScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = viewModel<OnlineSamplePostprocessingViewModel>(
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
                context.applicationContext as android.app.Application
            )
        )
        
        // Launchers para seleccionar directorios
        val inputDirectoryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.updateInputDirectory(it, context)
            }
        }
        
        val outputDirectoryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.updateOutputDirectory(it, context)
            }
        }

        val markersFileChooser =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                        fileUri: Uri? ->
                    fileUri?.let { 
                        viewModel.updateMarkersFileUri(it, context)
                    }
                }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Online Sample Postprocessing") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Text("←")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sección 1: Directorios
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Input/Output Directories",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // Directorio de entrada
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Input Directory (must contain __all.csv and images)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { inputDirectoryLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Select Input Directory")
                            }
                            Text(
                                text = "Selected: ${viewModel.inputDirectoryName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Divider()
                        
                        // Directorio de salida
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Output Directory",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { outputDirectoryLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Select Output Directory")
                            }
                            Text(
                                text = "Selected: ${viewModel.outputDirectoryName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Sección 2: Configuración de tiempo
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Time Configuration",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // Sampling Time (ST)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Sampling Time (ST) - ms between frames",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Avoids processing every single frame",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            NumberField(
                                value = viewModel.samplingTime,
                                onValueChange = { viewModel.updateSamplingTime(it) },
                                label = { Text("Sampling Time (ms)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Divider()
                        
                        // Time Window
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Time Window",
                                style = MaterialTheme.typography.bodySmall
                            )
                            NumberField(
                                value = viewModel.timeWindow,
                                onValueChange = { viewModel.updateTimeWindow(it) },
                                label = { Text("Time Window (ms)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // Sección 3: Configuración de ArUco
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "ArUco Configuration",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            ArucoTypeDropdownMenu(
                                    selectedArucoType = viewModel.selectedArucoType,
                                    onArucoTypeSelected = { viewModel.updateArucoType(it) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                    onClick = {
                                        markersFileChooser.launch(arrayOf("application/json"))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                        if (viewModel.markersFileUri != null) "Markers File: Selected"
                                        else "Select Markers Definition File (JSON)"
                                )
                            }
                        }
                }
                
                // Sección 4: Configuración RANSAC
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "RANSAC Configuration",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // Mínimo
                        NumberField(
                            value = viewModel.ransacMinThreshold,
                            onValueChange = { viewModel.updateRansacMinThreshold(it) },
                            label = { Text("RANSAC Minimum") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Máximo
                        NumberField(
                            value = viewModel.ransacMaxThreshold,
                            onValueChange = { viewModel.updateRansacMaxThreshold(it) },
                            label = { Text("RANSAC Maximum") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Step
                        NumberField(
                            value = viewModel.ransacStep,
                            onValueChange = { viewModel.updateRansacStep(it) },
                            label = { Text("RANSAC Step") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Sección 5: Filtro aritmético
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Arithmetic Filter",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        SimpleDropdownMenu(
                            label = "Filter Type",
                            options = MultipleMarkersBehaviour.entries.map { it.name }.toTypedArray(),
                            values = MultipleMarkersBehaviour.entries.toTypedArray(),
                            onOptionSelected = { viewModel.updateArithmeticFilterType(it) },
                            selectedValue = viewModel.arithmeticFilterType,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Sección 6: Estado del procesamiento
                if (viewModel.isProcessing || viewModel.processingComplete) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Processing Status",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Text(
                                text = viewModel.currentStatus,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            if (viewModel.isProcessing) {
                                LinearProgressIndicator(
                                    progress = { viewModel.processingProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            if (viewModel.processingComplete) {
                                Text(
                                    text = "✓ Processing completed successfully!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                // Mensaje de error
                viewModel.errorMessage?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                // Sección 7: Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewModel.isProcessing) {
                        Button(
                            onClick = { viewModel.cancelProcessing() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel Processing")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startProcessing(context) },
                            modifier = Modifier.weight(1f),
                            enabled = viewModel.canStartProcessing
                        ) {
                            Text("Start Processing")
                        }
                        
                        if (viewModel.processingComplete) {
                            Button(
                                onClick = { viewModel.resetProcessing() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
                
                // Mensaje de ayuda
                if (!viewModel.canStartProcessing && !viewModel.isProcessing) {
                    Text(
                        text = "Please select both input and output directories to start processing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}
