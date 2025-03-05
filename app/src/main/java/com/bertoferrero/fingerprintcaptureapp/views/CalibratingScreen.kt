package com.bertoferrero.fingerprintcaptureapp.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.components.OpenCvCamera
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.CalibrationCameraController
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TextField
import com.bertoferrero.fingerprintcaptureapp.models.ViewParametersManager
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class CalibratingScreen : Screen {

    private lateinit var calibrationCameraController: CalibrationCameraController
    private lateinit var viewParametersManager: ViewParametersManager
    private var screenSetterCalibrating: (Boolean) -> Unit = {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val (calibratingContent, setCalibratingContent) = remember { mutableStateOf(false) }
        screenSetterCalibrating = setCalibratingContent

        val context = LocalContext.current
        viewParametersManager = remember { ViewParametersManager(context) }
        calibrationCameraController = remember {
            CalibrationCameraController(
                context,
                onCalibrationFinished = {
                    setCalibratingContent(false)
                },
                viewParametersManager.arucoDictionaryType,
                viewParametersManager.charucoVerticalSquares,
                viewParametersManager.charucoHorizontalSquares,
                viewParametersManager.charucoSquareLength,
                viewParametersManager.charucoMarkerLength
            )
        }

        BackHandler {
            if (calibratingContent) {
                calibrationCameraController.finishProcess()
            } else {
                navigator.pop()
            }
        }

        if (!calibratingContent) {
            RenderSettingsScreen()
        } else {
            RenderCalibratingScreen()
        }
    }

    @Composable
    fun RenderSettingsScreen() {

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                NumberField<Int>(
                    value = calibrationCameraController.charucoVerticalSquares,
                    onValueChange = {
                        calibrationCameraController.charucoVerticalSquares = it
                        viewParametersManager.charucoVerticalSquares = it
                    },
                    label = { Text("Charuco vertical squares") }
                )
                NumberField<Int>(
                    value = calibrationCameraController.charucoHorizontalSquares,
                    onValueChange = {
                        calibrationCameraController.charucoHorizontalSquares = it
                        viewParametersManager.charucoHorizontalSquares = it
                    },
                    label = { Text("Charuco horizontal squares") }
                )
                NumberField<Float>(
                    value = calibrationCameraController.charucoSquareLength,
                    onValueChange = {
                        calibrationCameraController.charucoSquareLength = it
                        viewParametersManager.charucoSquareLength = it
                    },
                    label = { Text("Charuco square length (m)") }
                )
                NumberField<Float>(
                    value = calibrationCameraController.charucoMarkerLength,
                    onValueChange = {
                        calibrationCameraController.charucoMarkerLength = it
                        viewParametersManager.charucoMarkerLength = it
                    },
                    label = { Text("Charuco marker length (m)") }
                )

                ArucoTypeDropdownMenu(
                    selectedArucoType = ArucoDictionaryType.fromInt(calibrationCameraController.arucoDictionaryType)!!,
                    onArucoTypeSelected = {
                        calibrationCameraController.arucoDictionaryType = it.value
                        viewParametersManager.arucoDictionaryType = it.value
                    }
                )

                Button(
                    onClick = {
                        calibrationCameraController.initProcess()
                        screenSetterCalibrating(true)
                    }) {
                    Text("Start calibration")
                }
            }
        }
    }

    @Composable
    fun RenderCalibratingScreen() {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        calibrationCameraController.captureFrame()
                    }) {
                    Text(
                        modifier = Modifier.padding(10.dp, 10.dp),
                        text = "Capture sample"
                    )
                }
            }) { innerPadding ->

            OpenCvCamera(
                object :
                    CameraBridgeViewBase.CvCameraViewListener2 {
                    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                        return calibrationCameraController.processFrame(inputFrame)
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