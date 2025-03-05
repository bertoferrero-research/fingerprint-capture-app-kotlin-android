package com.bertoferrero.fingerprintcaptureapp.views

import android.widget.Toast
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
import com.bertoferrero.fingerprintcaptureapp.components.OpenCvCamera
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestDistanceCameraController
import com.bertoferrero.fingerprintcaptureapp.models.ViewParametersManager
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestDistanceScreen : Screen {

    private lateinit var cameraController: TestDistanceCameraController
    private lateinit var viewParametersManager: ViewParametersManager
    private var setterRunningContent: (Boolean) -> Unit = {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val (runningContent, setRunningContent) = remember { mutableStateOf(false) }
        setterRunningContent = setRunningContent

        val context = LocalContext.current
        viewParametersManager = remember { ViewParametersManager(context) }
        cameraController = remember { TestDistanceCameraController(
            context,
            viewParametersManager.markerSize,
            viewParametersManager.arucoDictionaryType
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
                        viewParametersManager.markerSize = it
                                    },
                    label = { Text("Marker size (m)") }
                )

                ArucoTypeDropdownMenu(
                    selectedArucoType = ArucoDictionaryType.fromInt(cameraController.arucoDictionaryType)!!,
                    onArucoTypeSelected = {
                        cameraController.arucoDictionaryType = it.value
                        viewParametersManager.arucoDictionaryType = it.value
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