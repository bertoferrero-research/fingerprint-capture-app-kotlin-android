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
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class CalibratingScreen : Screen {

    private lateinit var calibrationCameraController: CalibrationCameraController
    private var screenSetterCalibrating: (Boolean) -> Unit = {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val (calibratingContent, setCalibratingContent) = remember { mutableStateOf(false) }
        screenSetterCalibrating = setCalibratingContent
        val context = LocalContext.current
        calibrationCameraController = remember { CalibrationCameraController(context, onCalibrationFinished = {
            setCalibratingContent(false)
        }) }

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