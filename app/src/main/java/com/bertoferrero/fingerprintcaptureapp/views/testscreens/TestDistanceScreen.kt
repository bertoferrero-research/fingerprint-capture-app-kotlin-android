package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.components.OpenCvCamera
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.CvCameraViewFrameMockFromImage
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatFromFile
import com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens.TestDistanceViewModel
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestDistanceScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = viewModel<TestDistanceViewModel>()
        val isRunning = viewModel.isRunning

        // Initialize controller immediately with context
        viewModel.initializeController(context)

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
    fun RenderSettingsScreen(viewModel: TestDistanceViewModel) {
        val context = LocalContext.current
        val cameraController = viewModel.cameraController

        val imageMatFileChooser = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { fileUri: Uri? ->
            fileUri?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    inputStream?.let{
                        val mat = MatFromFile(it)
                        viewModel.cameraController.testingImageFrame =
                            CvCameraViewFrameMockFromImage(mat)
                    }
                    inputStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

                    SimpleDropdownMenu(
                        label = "Method",
                        options = arrayOf("M1 - Calibration", "M2 - Javi", "M3 - Pixel ratio", "M4 - horizontal FOV", "M5 - horizontal FOV 2", "M6 - Calculated camera matrix"),
                        values = arrayOf(1, 2, 3, 4, 5, 6),
                        onOptionSelected = viewModel::updateMethod,
                        selectedValue = cameraController.method,
                    )

                    ArucoTypeDropdownMenu(
                        selectedArucoType = ArucoDictionaryType.fromInt(cameraController.arucoDictionaryType)!!,
                        onArucoTypeSelected = { viewModel.updateArucoType(it.value) }
                    )

                    Button(
                        onClick = viewModel::startTest
                    ) {
                        Text("Start test")
                    }
                }
            }
        }
    }

    @Composable
    fun RenderRunningContent(viewModel: TestDistanceViewModel) {
        val cameraController = viewModel.cameraController
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = viewModel::stopTest
                ) {
                    Text(
                        modifier = Modifier.padding(10.dp, 10.dp),
                        text = "Finish test"
                    )
                }
            }
        ) { innerPadding ->
            OpenCvCamera(
                object :
                    CameraBridgeViewBase.CvCameraViewListener2 {
                    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                        return cameraController.processFrame(inputFrame)
                    }

                    override fun onCameraViewStarted(width: Int, height: Int) {
                    }

                    override fun onCameraViewStopped() {
                    }
                }
            ).Render()
        }
    }
}