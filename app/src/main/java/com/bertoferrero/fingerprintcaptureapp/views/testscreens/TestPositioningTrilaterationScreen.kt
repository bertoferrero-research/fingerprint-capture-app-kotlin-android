package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.unit.dp
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestPositioningTrilaterationController
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestPositioningTrilaterationScreen : Screen {

    private lateinit var cameraController: TestPositioningTrilaterationController
    private lateinit var settingsParametersManager: SettingsParametersManager
    private var setterRunningContent: (Boolean) -> Unit = {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val (runningContent, setRunningContent) = remember { mutableStateOf(false) }
        setterRunningContent = setRunningContent

        val context = LocalContext.current
        settingsParametersManager = remember { SettingsParametersManager() }
        cameraController = remember { TestPositioningTrilaterationController(
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
                        onValueChange = {
                            cameraController.markerSize = it
                            settingsParametersManager.markerSize = it
                        },
                        label = { Text("Marker size (m)") }
                    )
                    ArucoTypeDropdownMenu(
                        selectedArucoType = ArucoDictionaryType.fromInt(cameraController.arucoDictionaryType)!!,
                        onArucoTypeSelected = {
                            cameraController.arucoDictionaryType = it.value
                            settingsParametersManager.arucoDictionaryType = it.value
                        }
                    )

                    NumberField<Int>(
                        value = cameraController.marker1.id,
                        onValueChange = {
                            cameraController.marker1.id = it
                        },
                        label = { Text("Marker 1 - id") }
                    )
                    Row(
                        modifier = Modifier.fillParentMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField<Float>(
                            value = cameraController.marker1.x,
                            onValueChange = {
                                cameraController.marker1.x = it
                            },
                            label = { Text("Marker 1 - x") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker1.y,
                            onValueChange = {
                                cameraController.marker1.y = it
                            },
                            label = { Text("Marker 1 - y") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker1.z,
                            onValueChange = {
                                cameraController.marker1.z = it
                            },
                            label = { Text("Marker 1 - z") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                    }


                    NumberField<Int>(
                        value = cameraController.marker2.id,
                        onValueChange = {
                            cameraController.marker2.id = it
                        },
                        label = { Text("Marker 2 - id") }
                    )
                    Row(
                        modifier = Modifier.fillParentMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField<Float>(
                            value = cameraController.marker2.x,
                            onValueChange = {
                                cameraController.marker2.x = it
                            },
                            label = { Text("Marker 2 - x") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker2.y,
                            onValueChange = {
                                cameraController.marker2.y = it
                            },
                            label = { Text("Marker 2 - y") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker2.z,
                            onValueChange = {
                                cameraController.marker2.z = it
                            },
                            label = { Text("Marker 2 - z") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                    }


                    NumberField<Int>(
                        value = cameraController.marker3.id,
                        onValueChange = {
                            cameraController.marker3.id = it
                        },
                        label = { Text("Marker 3 - id") }
                    )
                    Row(
                        modifier = Modifier.fillParentMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField<Float>(
                            value = cameraController.marker3.x,
                            onValueChange = {
                                cameraController.marker3.x = it
                            },
                            label = { Text("Marker 3 - x") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker3.y,
                            onValueChange = {
                                cameraController.marker3.y = it
                            },
                            label = { Text("Marker 3 - y") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker3.z,
                            onValueChange = {
                                cameraController.marker3.z = it
                            },
                            label = { Text("Marker 3 - z") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                    }

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