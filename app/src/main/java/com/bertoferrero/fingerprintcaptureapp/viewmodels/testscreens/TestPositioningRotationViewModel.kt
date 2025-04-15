package com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestPositioningRotationController
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestPositioningRotationSample
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TestPositioningRotationViewModel : ViewModel() {

    val settingsManager = SettingsParametersManager()
    val cameraController = TestPositioningRotationController(
        settingsManager.markerSize,
        settingsManager.arucoDictionaryType,
        onSamplesLimitReached = ::onSamplesLimitReached
    )

    var outputFolderUri: Uri? = null
        private set

    var isRunning by mutableStateOf(false)
        private set

    var initButtonEnabled by mutableStateOf(false)
        private set

    var pendingSamplesSave by mutableStateOf(false)
        private set

    fun startTest() {
        if(outputFolderUri == null){
            return
        }
        cameraController.initProcess()
        isRunning = true
    }

    fun stopTest() {
        cameraController.finishProcess()
        isRunning = false
    }

    fun updateMarkerSize(size: Float) {
        settingsManager.markerSize = size
        cameraController.markerSize = size
    }

    fun updateArucoType(type: Int) {
        settingsManager.arucoDictionaryType = type
        cameraController.arucoDictionaryType = type
    }

    fun updateOutputFolderUri(uri: Uri){
        outputFolderUri = uri
        evaluateEnableButtonTest()
    }

    fun evaluateEnableButtonTest(){
        initButtonEnabled = cameraController.markersDefinition.isNotEmpty() && outputFolderUri !== null;
    }

    fun loadMarkersFromJson(jsonString: String) {
        val type = object : TypeToken<List<MarkerDefinition>>() {}.type
        var loadedMarkers: List<MarkerDefinition> = Gson().fromJson(jsonString, type)
        cameraController.markersDefinition = loadedMarkers
        evaluateEnableButtonTest()
    }

    //Sample files block

    fun saveSampleFile(context: Context){
        pendingSamplesSave = false
        var samples = cameraController.samples
        if(outputFolderUri == null || samples.isEmpty()){
            return
        }
        //Transform samples into csv
        val header = listOf(
            "timestamp",
            "multipleMarkersBehaviour",
            "amountMarkersEmployed",
            "kalmanQ",
            "kalmanR",
            "rawX", "rawY", "rawZ",
            "kalmanX", "kalmanY", "kalmanZ"
        ).joinToString(",")

        val rows = samples.map { sample ->
            listOf(
                sample.timestamp,
                sample.multipleMarkersBehaviour.name,
                sample.amountMarkersEmployed,
                sample.kalmanQ,
                sample.kalmanR,
                sample.rawX, sample.rawY, sample.rawZ,
                sample.kalmanX, sample.kalmanY, sample.kalmanZ
            ).joinToString(",")
        }

        val csvString = (listOf(header) + rows).joinToString("\n")

        //Save the file
        val folder = DocumentFile.fromTreeUri(context, outputFolderUri!!)
        val timestamp = System.currentTimeMillis()
        val fileName = "test_position_sample_$timestamp.csv"

        val newFile = folder?.createFile("text/csv", fileName)
        newFile?.uri?.let { fileUri ->
            context.contentResolver.openOutputStream(fileUri)?.use { out ->
                out.write(csvString.toByteArray())
            }
        }

    }

    private fun onSamplesLimitReached(samples: List<TestPositioningRotationSample>){
        stopTest()
        pendingSamplesSave = true
        //TODO write the samples
    }

}
