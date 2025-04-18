package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens.TestPositioningRotationViewModel
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.OpenCvCamera
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestPositioningRotationScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = viewModel<TestPositioningRotationViewModel>()
        val isRunning = viewModel.isRunning
        val pendingSave = viewModel.pendingSamplesSave

        LaunchedEffect(pendingSave) {
            if (!viewModel.cameraController.isCalibrationParametersLoaded) {
                Toast.makeText(
                    context,
                    "No camera calibration parameters found",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
            if (pendingSave) {
                viewModel.saveSampleFile(context)
                Toast.makeText(context, "Sample saved", Toast.LENGTH_SHORT).show()
            }
        }

        BackHandler {
            if (isRunning) {
                viewModel.stopTest()
            } else {
                navigator.pop()
            }
        }

        if (!isRunning) {
            RenderSettingsScreen(viewModel)
        } else {
            RenderRunningContent(viewModel)
        }
    }


    @Composable
    fun RenderSettingsScreen(viewModel: TestPositioningRotationViewModel) {
        val context = LocalContext.current
        val cameraController = viewModel.cameraController

        val markerSettingsFileChooser = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { fileUri: Uri? ->
            fileUri?.let {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    try {
                        viewModel.loadMarkersFromJson(jsonString)
                        Toast.makeText(
                            context,
                            "Markers loaded from JSON: ${viewModel.cameraController.markersDefinition.size} markers",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (ex: Exception) {
                        Log.e(
                            "TestPositioningRotationScreen",
                            "Error loading markers from JSON",
                            ex
                        )
                        Toast.makeText(
                            context,
                            "Error loading markers from JSON",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val folderPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let {
                // Guardar permiso persistente
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Guardar el Uri en ViewModel o DataStore para más adelante
                viewModel.updateOutputFolderUri(it)
            }
        }


        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    NumberField<Float>(
                        value = cameraController.markerSize,
                        onValueChange = viewModel::updateMarkerSize,
                        label = { Text("Marker size (m)") }
                    )
                    ArucoTypeDropdownMenu(
                        selectedArucoType = ArucoDictionaryType.fromInt(cameraController.arucoDictionaryType)!!,
                        onArucoTypeSelected = {
                            viewModel.updateArucoType(it.value)
                        }
                    )
                    NumberField<Int>(
                        value = cameraController.samplesLimit,
                        onValueChange = { cameraController.samplesLimit = it },
                        label = { Text("Samples to take (0 = unlimited)") }
                    )

                    SimpleDropdownMenu(
                        label = "When multiple markers",
                        values = MultipleMarkersBehaviour.entries.toTypedArray(),
                        options = arrayOf("Closest", "Weighted average", "Average"),
                        onOptionSelected = {
                            viewModel.cameraController.multipleMarkersBehaviour = it
                        },
                        selectedValue = viewModel.cameraController.multipleMarkersBehaviour
                    )

                    NumberField<Int>(
                        value = cameraController.closestMarkersUsed,
                        onValueChange = { cameraController.closestMarkersUsed = it },
                        label = { Text("C - Number of closest markers used") }
                    )

                    NumberField<Double>(
                        value = viewModel.cameraController.kalmanFilter.covQ,
                        onValueChange = { viewModel.cameraController.kalmanFilter.covQ = it },
                        label = { Text("Kalman filter - Covariance Q") }
                    )

                    NumberField<Double>(
                        value = viewModel.cameraController.kalmanFilter.covR,
                        onValueChange = { viewModel.cameraController.kalmanFilter.covR = it },
                        label = { Text("Kalman filter - Covariance R") }
                    )

                    Button(
                        onClick = {
                            markerSettingsFileChooser.launch(arrayOf("application/json"))
                        }) {
                        Text("Choose marker settings file")
                    }

                    Button(onClick = { folderPickerLauncher.launch(null) }) {
                        Text("Pickup output folder")
                    }

                    Button(
                        onClick = viewModel::startTest,
                        enabled = (viewModel.initButtonEnabled)
                    ) {
                        Text("Start test")
                    }
                }
            }
        }
    }

    @Composable
    fun RenderRunningContent(viewModel: TestPositioningRotationViewModel) {
        val cameraController = viewModel.cameraController

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = viewModel::stopTest
                ) {
                    Text(modifier = Modifier.padding(10.dp), text = "Finish test")
                }
            }
        ) { innerPadding ->
            OpenCvCamera(
                object : CameraBridgeViewBase.CvCameraViewListener2 {
                    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                        return cameraController.processFrame(inputFrame)
                    }

                    override fun onCameraViewStarted(width: Int, height: Int) {}
                    override fun onCameraViewStopped() {}
                }
            ).Render()
        }
    }

}
