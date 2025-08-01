package com.bertoferrero.fingerprintcaptureapp.views.settings

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
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.CalibrationBatchResult
import com.bertoferrero.fingerprintcaptureapp.viewmodels.settings.BatchCalibrationViewModel
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField

class BatchCalibrationScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: BatchCalibrationViewModel = viewModel()
        val context = LocalContext.current
        
        // Initialize controller when screen starts
        LaunchedEffect(Unit) {
            viewModel.initializeController(context)
        }

        // Folder chooser
        val inputFolderChooser = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let { 
                viewModel.updateInputFolderUri(it)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Batch Camera Calibration") },
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
                        inputFolderChooser = { inputFolderChooser.launch(null) }
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
                        
                        Text(
                            text = "Successful samples: ${viewModel.successfulSamples}/${viewModel.totalFiles}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (viewModel.successfulSamples >= 10) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }

                    items(viewModel.results) { result ->
                        ResultCard(result = result)
                    }
                }

                // Calibration Result Section
                if (viewModel.processingComplete) {
                    item {
                        CalibrationResultCard(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSection(
    viewModel: BatchCalibrationViewModel,
    inputFolderChooser: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Calibration Configuration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Folder Selection
            Button(
                onClick = inputFolderChooser,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Folder with MAT Images")
            }

            // Folder Status
            if (viewModel.inputFolderUri != null) {
                Text(
                    text = "Folder selected ✓",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // ChArUco Board Configuration
            Text(
                text = "ChArUco Board Parameters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            NumberField<Int>(
                value = viewModel.settingsManager.charucoXSquares,
                onValueChange = viewModel::updateCharucoXSquares,
                label = { Text("Charuco vertical squares") }
            )

            NumberField<Int>(
                value = viewModel.settingsManager.charucoYSquares,
                onValueChange = viewModel::updateCharucoYSquares,
                label = { Text("Charuco horizontal squares") }
            )

            NumberField<Float>(
                value = viewModel.settingsManager.charucoSquareLength,
                onValueChange = viewModel::updateCharucoSquareLength,
                label = { Text("Charuco square length (m)") }
            )

            NumberField<Float>(
                value = viewModel.settingsManager.charucoMarkerLength,
                onValueChange = viewModel::updateCharucoMarkerLength,
                label = { Text("Charuco marker length (m)") }
            )

            ArucoTypeDropdownMenu(
                selectedArucoType = ArucoDictionaryType.fromInt(viewModel.settingsManager.arucoDictionaryType)!!,
                onArucoTypeSelected = { viewModel.updateArucoType(it.value) }
            )
        }
    }
}

@Composable
private fun ProcessingSection(
    viewModel: BatchCalibrationViewModel,
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
                text = "Batch Calibration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (!viewModel.isProcessing && !viewModel.processingComplete) {
                Button(
                    onClick = { viewModel.startBatchCalibration() },
                    enabled = viewModel.canStartProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Batch Calibration")
                }

                if (!viewModel.canStartProcessing) {
                    Text(
                        text = "Please select a folder with MAT images to start calibration",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (viewModel.isProcessing) {
                // Processing Progress
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Processing images for calibration...",
                        style = MaterialTheme.typography.titleMedium
                    )

                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Extracting ChArUco corners from MAT images",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (viewModel.processingComplete) {
                Button(
                    onClick = { viewModel.resetCalibration() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start New Calibration")
                }
            }
        }
    }
}

@Composable
private fun CalibrationResultCard(viewModel: BatchCalibrationViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (viewModel.calibrationSuccessful) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (viewModel.calibrationSuccessful) "Calibration Successful! ✓" else "Calibration Failed ✗",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (viewModel.calibrationSuccessful) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )

            Text(
                text = viewModel.calibrationMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (viewModel.calibrationSuccessful) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )

            if (viewModel.calibrationSuccessful) {
                Text(
                    text = "Camera calibration parameters have been saved and are now available for distance calculations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: CalibrationBatchResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.processed) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
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

            if (result.processed) {
                Text(
                    text = "✓ Processed - ${result.cornersDetected} corners detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "✗ Failed - ${result.cornersDetected} corners detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                
                result.error?.let { error ->
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
