package com.bertoferrero.fingerprintcaptureapp.views.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.components.OpenCvCamera
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bertoferrero.fingerprintcaptureapp.viewmodels.settings.CameraSamplerViewModel
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class CameraSamplerScreen : Screen {


    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<CameraSamplerViewModel>()
        val context = LocalContext.current


        BackHandler {
            if (viewModel.isRunning) {
                viewModel.stopProcess(context)
            } else {
                navigator.pop()
            }
        }

        if (!viewModel.isRunning) {
            RenderSettingsScreen(viewModel)
        } else {
            RenderCalibratingScreen(viewModel)
        }
    }

    @Composable
    fun RenderSettingsScreen(viewModel: CameraSamplerViewModel) {
        val context = LocalContext.current

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                NumberField<Long>(
                    value = viewModel.initDelay,
                    onValueChange = {
                        viewModel.initDelay = it
                    },
                    label = { Text("Capture delay (ms)") }
                )

                // Selector tipo de captura con SimpleDropdownMenu
                SimpleDropdownMenu(
                    label = "Tipo de captura",
                    values = arrayOf("photo", "video"),
                    options = arrayOf("Photo", "Video"),
                    selectedValue = viewModel.captureType,
                    onOptionSelected = { viewModel.defineCaptureType(it) }
                )

                Button(onClick = { folderPickerLauncher.launch(null) }) {
                    Text("Pickup output folder")
                }

                Button(
                    onClick = {viewModel.startProcess()},
                    enabled = (viewModel.initButtonEnabled)
                ) {
                    Text("Start")
                }
            }
        }
    }

    @Composable
    fun RenderCalibratingScreen(viewModel: CameraSamplerViewModel) {
        val context = LocalContext.current

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                when (viewModel.captureType) {
                    "photo" -> FloatingActionButton(
                        onClick = { viewModel.takeSample() }
                    ) {
                        Text(
                            modifier = Modifier.padding(10.dp, 10.dp),
                            text = "Capture sample"
                        )
                    }
                    "video" -> FloatingActionButton(
                        onClick = { viewModel.stopProcess(context) }
                    ) {
                        Text(
                            modifier = Modifier.padding(10.dp, 10.dp),
                            text = "Detener captura"
                        )
                    }
                    else -> {}
                }
            }
        ) { innerPadding ->

            OpenCvCamera(
                object :
                    CameraBridgeViewBase.CvCameraViewListener2 {
                    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                        return viewModel.processFrame(inputFrame, context)
                    }

                    override fun onCameraViewStarted(width: Int, height: Int) {
                        if (viewModel.captureType == "video" && viewModel.isRunning) {
                            viewModel.startVideoRecording(context, width, height)
                        }
                    }

                    override fun onCameraViewStopped() {
                    }
                }
            ).Render(Modifier.padding(innerPadding))
        }
    }
}