package com.bertoferrero.fingerprintcaptureapp.views.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner
import com.bertoferrero.fingerprintcaptureapp.viewmodels.capture.OfflineCaptureViewModel

class OfflineCaptureScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        // No consigo que el hilt funcione con esta version de kotlin
        //val viewModel = hiltViewModel<OfflineCaptureViewModel>()
        val viewModel = viewModel<OfflineCaptureViewModel>(
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application)
        )

        val isRunning = viewModel.isRunning

        // Register/unregister broadcast receiver through ViewModel
        DisposableEffect(Unit) {
            viewModel.registerBroadcastReceiver()
            onDispose {
                viewModel.unregisterBroadcastReceiver()
            }
        }

        BackHandler {
            if (isRunning) {
                viewModel.stopCapture(context)
            } else {
                navigator.pop()
            }
        }
        if(BleScanner.checkPermissions()) {
            if (!isRunning) {
                settingsScreen(viewModel)
            } else {
                runningContent(viewModel, context)
            }
        }
    }


    @Composable
    private fun settingsScreen(viewModel: OfflineCaptureViewModel) {
        val context = LocalContext.current

        val macFilterListFileChooser = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { fileUri: Uri? ->
            fileUri?.let {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    try {
                        viewModel.loadMacFilterListFromJson(jsonString)
                        Toast.makeText(
                            context,
                            "MAC filter list updated from JSON: ${viewModel.macFilterList.size} MAC addresses",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (ex: Exception) {
                        Log.e(
                            "OfflineCaptureScreen",
                            "Error loading MAC filter list from JSON",
                            ex
                        )
                        Toast.makeText(
                            context,
                            "Error loading MAC filter list from JSON",
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
                        value = viewModel.x,
                        onValueChange = {
                            viewModel.x = it
                        },
                        label = { Text("X") }
                    )
                    NumberField<Float>(
                        value = viewModel.y,
                        onValueChange = {
                            viewModel.y = it
                        },
                        label = { Text("Y") }
                    )
                    NumberField<Float>(
                        value = viewModel.z,
                        onValueChange = {
                            viewModel.z = it
                        },
                        label = { Text("Z") }
                    )
                    NumberField<Int>(
                        value = viewModel.minutesLimit,
                        onValueChange = {
                            viewModel.minutesLimit = it
                        },
                        label = { Text("Sampling minutes (0 for infinite)") }
                    )
                    NumberField<Int>(
                        value = viewModel.initDelaySeconds,
                        onValueChange = {
                            viewModel.initDelaySeconds = it
                        },
                        label = { Text("Init delay in seconds (0 for disable)") }
                    )

                    Button(
                        onClick = {
                            macFilterListFileChooser.launch(arrayOf("application/json"))
                        }) {
                        Text("Load MAC filter list file")
                    }

                    Button(onClick = { folderPickerLauncher.launch(null) }) {
                        Text("Pickup output folder")
                    }

                    Button(
                        onClick = {
                            val success = viewModel.startCapture(context)
                            if (!success) {
                                Toast.makeText(
                                    context,
                                    "Error starting capture. Check configuration.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = (viewModel.initButtonEnabled)
                    ) {
                        Text("Start")
                    }
                }
            }
        }
    }

    @Composable
    private fun runningContent(viewModel: OfflineCaptureViewModel, context: Context) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.stopCapture(context) }
                ) {
                    Text(modifier = Modifier.padding(10.dp), text = "Finish test")
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("${viewModel.capturedSamplesCounter} sample(s) captured")
            }
        }
    }

}
