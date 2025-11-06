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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu

/**
 * Pantalla para procesamiento por lotes de imágenes. Permite procesar una carpeta completa de
 * imágenes y exportar las posiciones detectadas de códigos ArUco a un archivo CSV.
 */
class BatchArucoProcessingScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        // Estados de configuración
        var inputFolderUri by remember { mutableStateOf<Uri?>(null) }
        var outputFolderUri by remember { mutableStateOf<Uri?>(null) }
        var markersFileUri by remember { mutableStateOf<Uri?>(null) }
        var selectedArucoType by remember { mutableStateOf(ArucoDictionaryType.DICT_6X6_250) }

        // Parámetros RANSAC
        var ransacMinThreshold by remember { mutableStateOf(0.2) }
        var ransacMaxThreshold by remember { mutableStateOf(0.4) }
        var ransacStep by remember { mutableStateOf(0.1) }

        // Filtro aritmético
        var arithmeticFilterType by remember {
            mutableStateOf(MultipleMarkersBehaviour.WEIGHTED_MEDIAN)
        }

        // Estado de procesamiento
        var isProcessing by remember { mutableStateOf(false) }
        var processedImages by remember { mutableStateOf(0) }
        var totalImages by remember { mutableStateOf(0) }

        // Launchers para selección de carpetas y archivos
        val inputFolderLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
                        uri: Uri? ->
                    uri?.let {
                        context.contentResolver.takePersistableUriPermission(
                                it,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        inputFolderUri = it
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
                        outputFolderUri = it
                    }
                }

        val markersFileChooser =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                        fileUri: Uri? ->
                    fileUri?.let { markersFileUri = it }
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
                                        if (inputFolderUri != null) "Input Folder: Selected"
                                        else "Select Input Folder (Images)"
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                    onClick = { outputFolderLauncher.launch(null) },
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                        if (outputFolderUri != null) "Output Folder: Selected"
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
                                    selectedArucoType = selectedArucoType,
                                    onArucoTypeSelected = { selectedArucoType = it }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                    onClick = {
                                        markersFileChooser.launch(arrayOf("application/json"))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                        if (markersFileUri != null) "Markers File: Selected"
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
                                    value = ransacMinThreshold,
                                    onValueChange = { ransacMinThreshold = it },
                                    label = { Text("Minimum Threshold") }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            NumberField<Double>(
                                    value = ransacMaxThreshold,
                                    onValueChange = { ransacMaxThreshold = it },
                                    label = { Text("Maximum Threshold") }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            NumberField<Double>(
                                    value = ransacStep,
                                    onValueChange = { ransacStep = it },
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
                                    onOptionSelected = { arithmeticFilterType = it },
                                    selectedValue = arithmeticFilterType
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

                            if (isProcessing) {
                                LinearProgressIndicator(
                                        progress =
                                                if (totalImages > 0)
                                                        processedImages.toFloat() /
                                                                totalImages.toFloat()
                                                else 0f,
                                        modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Processing: $processedImages / $totalImages images")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                    onClick = {
                                        if (!isProcessing) {
                                            // TODO: Iniciar procesamiento
                                            isProcessing = true
                                        } else {
                                            // TODO: Detener procesamiento
                                            isProcessing = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled =
                                            inputFolderUri != null &&
                                                    outputFolderUri != null &&
                                                    markersFileUri != null
                            ) { Text(if (isProcessing) "Stop Processing" else "Start Processing") }

                            if (!isProcessing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                        onClick = { navigator.pop() },
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
