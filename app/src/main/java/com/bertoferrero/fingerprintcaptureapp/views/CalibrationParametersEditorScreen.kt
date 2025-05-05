package com.bertoferrero.fingerprintcaptureapp.views

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CalibrationParametersEditorScreen : Screen {


    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val calibrationParameters = remember {
            mutableStateOf(
                CameraCalibrationParameters.loadParameters(
                    throwExceptionIfEmpty = false
                )
            )
        }

        val saveParametersFolderChooser = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let {
                try {

                    // Get the preferences
                    val cameraPreferences = CameraCalibrationParameters.export()
                    // Serialize to json
                    val gson: Gson = Gson()
                    val exportCameraPreferences = gson.toJson(cameraPreferences)
                    //Save into a random named file
                    val folder = DocumentFile.fromTreeUri(context, it)
                    val timestamp = System.currentTimeMillis()
                    val fileName = "camera_calibration_$timestamp.json"
                    val newFile = folder?.createFile("application/json", fileName)
                    newFile?.uri?.let { fileUri ->
                        context.contentResolver.openOutputStream(fileUri)?.use { out ->
                            out.write(exportCameraPreferences.toByteArray())
                        }
                    }

                    Toast.makeText(
                        context,
                        "Calibration exported to $fileName file",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (ex: Exception) {
                    Toast.makeText(
                        context,
                        "Error exporting camera parameters",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val loadParametersFileChooser = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { fileUri: Uri? ->
            fileUri?.let {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    try {
                        //Decode json
                        val type = object : TypeToken<Map<String, String?>>() {}.type
                        var loadedMarkers: Map<String, String?> = Gson().fromJson(jsonString, type)
                        CameraCalibrationParameters.import(loadedMarkers)
                        calibrationParameters.value = CameraCalibrationParameters.loadParameters(
                            throwExceptionIfEmpty = false
                        )
                        Toast.makeText(
                            context,
                            "Camera parameters imported from JSON",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (ex: Exception) {
                        Toast.makeText(
                            context,
                            "Error importing camera parameters from JSON",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        BackHandler {
            calibrationParameters.value.saveParameters()
            navigator.pop()
        }

        Scaffold(
            modifier = Modifier.padding(horizontal = 10.dp)
        ) { innerPadding ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {


                    Text(
                        text = "Camera Matrix",
                        modifier = Modifier.padding(16.dp)
                    )

                    for (i in 0 until calibrationParameters.value.cameraMatrix.rows()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (j in 0 until calibrationParameters.value.cameraMatrix.cols()) {

                                NumberField<Double>(
                                    value = calibrationParameters.value.cameraMatrix.get(i, j)[0],
                                    onValueChange = {
                                        calibrationParameters.value.cameraMatrix.put(i, j, it)
                                    },
                                    modifier = Modifier
                                        .fillParentMaxWidth(0.33f)
                                        .padding(2.dp)
                                )

                            }
                        }
                    }

                    Text(
                        text = "Distortion coefficients",
                        modifier = Modifier.padding(16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField<Double>(
                            value = calibrationParameters.value.distCoeffs.get(0, 0)[0],
                            onValueChange = {
                                calibrationParameters.value.distCoeffs.put(0, 0, it)
                            },
                            modifier = Modifier
                                .fillParentMaxWidth(0.50f)
                                .padding(2.dp),
                            label = { Text("k1") }
                        )
                        NumberField<Double>(
                            value = calibrationParameters.value.distCoeffs.get(0, 1)[0],
                            onValueChange = {
                                calibrationParameters.value.distCoeffs.put(0, 1, it)
                            },
                            modifier = Modifier
                                .fillParentMaxWidth(0.50f)
                                .padding(2.dp),
                            label = { Text("k2") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField<Double>(
                            value = calibrationParameters.value.distCoeffs.get(0, 2)[0],
                            onValueChange = {
                                calibrationParameters.value.distCoeffs.put(0, 2, it)
                            },
                            modifier = Modifier
                                .fillParentMaxWidth(0.50f)
                                .padding(2.dp),
                            label = { Text("p1") }
                        )
                        NumberField<Double>(
                            value = calibrationParameters.value.distCoeffs.get(0, 3)[0],
                            onValueChange = {
                                calibrationParameters.value.distCoeffs.put(0, 3, it)
                            },
                            modifier = Modifier
                                .fillParentMaxWidth(0.50f)
                                .padding(2.dp),
                            label = { Text("p2") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField<Double>(
                            value = calibrationParameters.value.distCoeffs.get(0, 4)[0],
                            onValueChange = {
                                calibrationParameters.value.distCoeffs.put(0, 4, it)
                            },
                            modifier = Modifier
                                .fillParentMaxWidth(0.50f)
                                .padding(2.dp),
                            label = { Text("k3") }
                        )
                    }

                    Button(onClick = { saveParametersFolderChooser.launch(null) }) {
                        Text("Export parameters")
                    }

                    Button(
                        onClick = {
                            loadParametersFileChooser.launch(arrayOf("application/json"))
                        }) {
                        Text("Import parameters")
                    }


                }

            }
        }
    }
}