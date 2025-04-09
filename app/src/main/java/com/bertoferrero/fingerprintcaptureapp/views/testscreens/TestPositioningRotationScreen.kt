package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import android.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestPositioningRotationController
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestPositioningTrilaterationController
import com.bertoferrero.fingerprintcaptureapp.models.ViewParametersManager
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver
import com.lemmingapex.trilateration.TrilaterationFunction
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import org.apache.commons.math3.linear.RealVector
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestPositioningRotationScreen : Screen {
    private lateinit var cameraController: TestPositioningRotationController
    private lateinit var viewParametersManager: ViewParametersManager
    private var setterRunningContent: (Boolean) -> Unit = {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val (runningContent, setRunningContent) = remember { mutableStateOf(false) }
        setterRunningContent = setRunningContent

        val context = LocalContext.current
        viewParametersManager = remember { ViewParametersManager(context) }
        cameraController = remember { TestPositioningRotationController(
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

        //https://medium.com/@cepv2010/how-to-easily-choose-files-in-android-compose-28f4637d1c21
        //https://stackoverflow.com/questions/68768236/how-do-i-start-file-chooser-using-intent-in-compose
         val markerSettingsFileChooser = rememberLauncherForActivityResult(
            //GetCustomContents(isMultiple = false)
             ActivityResultContracts.GetContent()
        ) { fileUri ->
                // Update the state with the Uri
                Log.d("TestPositioningRotationScreen", "Selected file URI: $fileUri")
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
                            markerSettingsFileChooser.launch("application/json")
                        }) {
                        Text("Choose marker settings file")
                    }

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
                    Row(
                        modifier = Modifier.fillParentMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField<Float>(
                            value = cameraController.marker1.rotation.x,
                            onValueChange = {
                                cameraController.marker1.rotation.x = it
                            },
                            label = { Text("Marker 1 - rot x") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker1.rotation.y,
                            onValueChange = {
                                cameraController.marker1.rotation.y = it
                            },
                            label = { Text("Marker 1 - rot y") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker1.rotation.z,
                            onValueChange = {
                                cameraController.marker1.rotation.z = it
                            },
                            label = { Text("Marker 1 - rot z") },
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
                    Row(
                        modifier = Modifier.fillParentMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField<Float>(
                            value = cameraController.marker2.rotation.x,
                            onValueChange = {
                                cameraController.marker2.rotation.x = it
                            },
                            label = { Text("Marker 2 - rot x") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker2.rotation.y,
                            onValueChange = {
                                cameraController.marker2.rotation.y = it
                            },
                            label = { Text("Marker 2 - rot y") },
                            modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                        )
                        NumberField<Float>(
                            value = cameraController.marker2.rotation.z,
                            onValueChange = {
                                cameraController.marker2.rotation.z = it
                            },
                            label = { Text("Marker 2 - rot z") },
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

class GetCustomContents(
    private val isMultiple: Boolean = false, //This input check if the select file option is multiple or not
): ActivityResultContract<String, List<@JvmSuppressWildcards Uri>>() {

    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input //The input option es the MIME Type that you need to use
            putExtra(Intent.EXTRA_LOCAL_ONLY, false) //Return data on the local device
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple) //If select one or more files
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        return intent.takeIf {
            resultCode == Activity.RESULT_OK
        }?.getClipDataUris() ?: emptyList()
    }

    internal companion object {

        //Collect all Uris from files selected
        internal fun Intent.getClipDataUris(): List<Uri> {
            // Use a LinkedHashSet to maintain any ordering that may be
            // present in the ClipData
            val resultSet = LinkedHashSet<Uri>()
            data?.let { data ->
                resultSet.add(data)
            }
            val clipData = clipData
            if (clipData == null && resultSet.isEmpty()) {
                return emptyList()
            } else if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    if (uri != null) {
                        resultSet.add(uri)
                    }
                }
            }
            return ArrayList(resultSet)
        }
    }
}