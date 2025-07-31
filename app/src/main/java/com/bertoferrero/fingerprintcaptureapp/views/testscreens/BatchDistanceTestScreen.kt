package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens.BatchDistanceTestViewModel
import org.opencv.objdetect.Objdetect

class BatchDistanceTestScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: BatchDistanceTestViewModel = viewModel()
        val context = LocalContext.current
        
        // Initialize controller when screen starts
        LaunchedEffect(Unit) {
            viewModel.initializeController(context)
        }

        // Folder choosers
        val inputFolderChooser = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let { 
                viewModel.updateInputFolderUri(it)
            }
        }

        val outputFolderChooser = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let { 
                viewModel.updateOutputFolderUri(it)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Batch Distance Test") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Text("←")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Configuration Section
                item {
                    ConfigurationSection(
                        viewModel = viewModel,
                        inputFolderChooser = { inputFolderChooser.launch(null) },
                        outputFolderChooser = { outputFolderChooser.launch(null) }
                    )
                }

                // Processing Section
                item {
                    ProcessingSection(viewModel = viewModel, context = context)
                }

                // Results Section
                if (viewModel.results.isNotEmpty()) {
                    item {
                        Text(
                            text = "Processing Results",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(viewModel.results) { result ->
                        ResultCard(result = result)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSection(
    viewModel: BatchDistanceTestViewModel,
    inputFolderChooser: () -> Unit,
    outputFolderChooser: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Folder Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = inputFolderChooser,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select Input Folder")
                }

                Button(
                    onClick = outputFolderChooser,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select Output Folder")
                }
            }

            // Folder Status
            if (viewModel.inputFolderUri != null) {
                Text(
                    text = "Input: Selected ✓",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (viewModel.outputFolderUri != null) {
                Text(
                    text = "Output: Selected ✓",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Divider()

            // Distance Calculation Parameters
            Text(
                text = "Distance Calculation Parameters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            // Marker Size
            NumberField<Float>(
                value = viewModel.settingsManager.markerSize,
                onValueChange = viewModel::updateMarkerSize,
                label = { Text("Marker size (m)") }
            )

            // Method Selection
            SimpleDropdownMenu(
                label = "Distance Calculation Method",
                options = arrayOf(
                    "M1 - Calibration", 
                    "M2 - Javi", 
                    "M3 - Pixel ratio", 
                    "M4 - horizontal FOV", 
                    "M5 - horizontal FOV 2", 
                    "M6 - Calculated camera matrix"
                ),
                values = arrayOf(1, 2, 3, 4, 5, 6),
                onOptionSelected = viewModel::updateMethod,
                selectedValue = 1 // Default to method 1
            )

            // ArUco Dictionary
            ArucoTypeDropdownMenu(
                selectedArucoType = ArucoDictionaryType.fromInt(viewModel.settingsManager.arucoDictionaryType)!!,
                onArucoTypeSelected = { viewModel.updateArucoType(it.value) }
            )
        }
    }
}

@Composable
private fun ProcessingSection(
    viewModel: BatchDistanceTestViewModel,
    context: android.content.Context
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Batch Processing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (!viewModel.isProcessing) {
                Button(
                    onClick = { viewModel.startBatchProcessing(context) },
                    enabled = viewModel.canStartProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Batch Processing")
                }

                if (!viewModel.canStartProcessing) {
                    Text(
                        text = "Please select both input and output folders to start processing",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                // Processing Progress
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Processing: ${viewModel.processedFiles}/${viewModel.totalFiles}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (viewModel.totalFiles > 0) {
                        LinearProgressIndicator(
                            progress = viewModel.processedFiles.toFloat() / viewModel.totalFiles.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (viewModel.currentFileName.isNotEmpty()) {
                        Text(
                            text = "Current: ${viewModel.currentFileName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (viewModel.processingComplete) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "Processing completed! Results saved to output folder.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens.BatchProcessResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.error != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = result.fileName,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleSmall
            )

            if (result.distance != null) {
                Text(
                    text = "Distance: ${String.format("%.4f", result.distance)} m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (result.error != null) {
                Text(
                    text = "Error: ${result.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
