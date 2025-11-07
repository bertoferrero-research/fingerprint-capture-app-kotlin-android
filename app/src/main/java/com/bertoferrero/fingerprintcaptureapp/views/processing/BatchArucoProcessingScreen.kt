package com.bertoferrero.fingerprintcaptureapp.views.processing

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.viewmodels.processing.BatchArucoProcessingViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Pantalla para procesamiento por lotes de imágenes. Permite procesar una carpeta completa de
 * imágenes y exportar las posiciones detectadas de códigos ArUco a un archivo CSV.
 */
class BatchArucoProcessingScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = viewModel<BatchArucoProcessingViewModel>(
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application)
        )

        // Launchers para selección de carpetas y archivos
        val inputFolderLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
                        uri: Uri? ->
                    uri?.let {
                        context.contentResolver.takePersistableUriPermission(
                                it,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        viewModel.updateInputFolderUri(it)
                    }
                }

        val outputFolderLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
                        uri: Uri? ->
                    uri?.let {
                        context.contentResolver.takePersistableUriPermission(
                                it,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        viewModel.updateOutputFolderUri(it)
                    }
                }

        val markersFileChooser =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                        fileUri: Uri? ->
                    fileUri?.let { 
                        viewModel.updateMarkersFileUri(it, context)
                    }
                }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                            text = "Batch ArUco Processing",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Sección de carpetas
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "Input/Output Folders",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Button(
                                    onClick = { inputFolderLauncher.launch(null) },
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                        if (viewModel.inputFolderUri != null) "Input Folder: Selected"
                                        else "Select Input Folder (Images)"
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                    onClick = { outputFolderLauncher.launch(null) },
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                        if (viewModel.outputFolderUri != null) "Output Folder: Selected"
                                        else "Select Output Folder (CSV)"
                                )
                            }
                        }
                    }
                }

                // Sección de configuración ArUco
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
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
                }

                // Sección de parámetros RANSAC
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "RANSAC Parameters",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            NumberField<Double>(
                                    value = viewModel.ransacMinThreshold,
                                    onValueChange = { 
                                        viewModel.updateRansacParameters(
                                            it, 
                                            viewModel.ransacMaxThreshold, 
                                            viewModel.ransacStep
                                        ) 
                                    },
                                    label = { Text("Minimum Threshold") }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            NumberField<Double>(
                                    value = viewModel.ransacMaxThreshold,
                                    onValueChange = { 
                                        viewModel.updateRansacParameters(
                                            viewModel.ransacMinThreshold, 
                                            it, 
                                            viewModel.ransacStep
                                        ) 
                                    },
                                    label = { Text("Maximum Threshold") }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            NumberField<Double>(
                                    value = viewModel.ransacStep,
                                    onValueChange = { 
                                        viewModel.updateRansacParameters(
                                            viewModel.ransacMinThreshold, 
                                            viewModel.ransacMaxThreshold, 
                                            it
                                        ) 
                                    },
                                    label = { Text("Step Size") }
                            )
                        }
                    }
                }

                // Sección de filtro aritmético
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "Arithmetic Filter",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            SimpleDropdownMenu(
                                    label = "Filter Type",
                                    values = MultipleMarkersBehaviour.entries.toTypedArray(),
                                    options =
                                            arrayOf(
                                                    "Closest",
                                                    "Weighted average",
                                                    "Average",
                                                    "Weighted median",
                                                    "Median"
                                            ),
                                    onOptionSelected = { viewModel.updateArithmeticFilterType(it) },
                                    selectedValue = viewModel.arithmeticFilterType
                            )
                        }
                    }
                }

                // Sección de progreso y control
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "Processing Control",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Mostrar errores si existen
                            viewModel.errorMessage?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (viewModel.isProcessing) {
                                LinearProgressIndicator(
                                        progress =
                                                if (viewModel.totalImages > 0)
                                                        viewModel.processedImages.toFloat() /
                                                                viewModel.totalImages.toFloat()
                                                else 0f,
                                        modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Processing: ${viewModel.processedImages} / ${viewModel.totalImages} images")
                                
                                if (viewModel.currentImageName.isNotEmpty()) {
                                    Text(
                                        text = "Current: ${viewModel.currentImageName}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            if (viewModel.processingComplete) {
                                Text(
                                    text = "✅ Processing completed successfully!",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                    onClick = {
                                        if (!viewModel.isProcessing) {
                                            viewModel.startProcessing(context)
                                        } else {
                                            viewModel.stopProcessing()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = viewModel.canStartProcessing || viewModel.isProcessing
                            ) { 
                                Text(
                                    if (viewModel.isProcessing) "Stop Processing" 
                                    else "Start Processing"
                                ) 
                            }

                            if (!viewModel.isProcessing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                        onClick = { 
                                            viewModel.resetProcessing()
                                            navigator.pop() 
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                ) { Text("Back") }
                            }
                        }
                    }
                }
            }
        }
    }
}
