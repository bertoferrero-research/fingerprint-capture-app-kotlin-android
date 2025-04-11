package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestPositioningRotationController
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager
import com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens.TestPositioningRotationViewModel
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.OpenCvCamera
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestPositioningRotationScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = viewModel<TestPositioningRotationViewModel>()
        val isRunning = viewModel.isRunning

        BackHandler {
            if (isRunning) {
                viewModel.stopTest()
            } else {
                navigator.pop()
            }
        }

        if (!viewModel.cameraController.isCalibrationParametersLoaded) {
            Toast.makeText(context, "No camera calibration parameters found", Toast.LENGTH_SHORT)
                .show()
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
                    Log.d("TestPositioningRotationScreen - markerSettings Loaded", jsonString)
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
                    ArucoTypeDropdownMenu(
                        selectedArucoType = ArucoDictionaryType.fromInt(cameraController.arucoDictionaryType)!!,
                        onArucoTypeSelected = {
                            viewModel.updateArucoType(it.value)
                        }
                    )

                    Button(
                        onClick = {
                            markerSettingsFileChooser.launch(arrayOf("application/json"))
                        }) {
                        Text("Choose marker settings file")
                    }

                    // Aquí sigues con los campos de marker1, marker2 como antes
                    // usando directamente cameraController.marker1.id, etc.

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

class GetCustomContents(
    private val isMultiple: Boolean = false, //This input check if the select file option is multiple or not
) : ActivityResultContract<String, List<@JvmSuppressWildcards Uri>>() {

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