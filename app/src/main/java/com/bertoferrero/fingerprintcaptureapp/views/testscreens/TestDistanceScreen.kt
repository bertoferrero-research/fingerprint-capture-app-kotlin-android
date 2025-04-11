package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.components.OpenCvCamera
import androidx.activity.compose.BackHandler
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.unit.dp
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestDistanceCameraController
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestDistanceScreen : Screen {

    private lateinit var cameraController: TestDistanceCameraController
    private lateinit var settingsParametersManager: SettingsParametersManager
    private var setterRunningContent: (Boolean) -> Unit = {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val (runningContent, setRunningContent) = remember { mutableStateOf(false) }
        setterRunningContent = setRunningContent

        val context = LocalContext.current
        settingsParametersManager = remember { SettingsParametersManager() }
        cameraController = remember { TestDistanceCameraController(
            context,
            settingsParametersManager.markerSize,
            settingsParametersManager.arucoDictionaryType
        ) }

        BackHandler {
            if (runningContent) {
                cameraController.finishProcess()
            } else {
                navigator.pop()
            }
        }

        if (!runningContent) {
            RenderSettingsScreen()
        } else {
            RenderRunningContent()
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

                NumberField<Float>(
                    value = cameraController.markerSize,
                    onValueChange = {
                        cameraController.markerSize = it
                        settingsParametersManager.markerSize = it
                                    },
                    label = { Text("Marker size (m)") }
                )

                SimpleDropdownMenu(
                    label = "Method",
                    options = arrayOf("M1 - Calibration", "M2 - Javi", "M3 - Pixel ratio", "M4 - horizontal FOV", "M5 - horizontal FOV 2", "M6 - Calculated camera matrix"),
                    values = arrayOf(1, 2, 3, 4, 5, 6),
                    onOptionSelected = {
                        cameraController.method = it
                    },
                    selectedValue = cameraController.method,
                )

                ArucoTypeDropdownMenu(
                    selectedArucoType = ArucoDictionaryType.fromInt(cameraController.arucoDictionaryType)!!,
                    onArucoTypeSelected = {
                        cameraController.arucoDictionaryType = it.value
                        settingsParametersManager.arucoDictionaryType = it.value
                    }
                )

                Button(
                    onClick = {
                        cameraController.initProcess()
                        setterRunningContent(true)
                    }) {
                    Text("Start test")
                }
            }
        }
    }

    @Composable
    fun RenderRunningContent() {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        cameraController.finishProcess()
                        setterRunningContent(false)
                    }) {
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