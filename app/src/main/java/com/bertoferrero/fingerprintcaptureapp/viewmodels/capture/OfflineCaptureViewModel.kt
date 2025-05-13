package com.bertoferrero.fingerprintcaptureapp.viewmodels.capture

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class OfflineCaptureViewModel(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var minutesLimit: Int = 0
): ViewModel() {

    //UI

    var isRunning by mutableStateOf(false)
        private set

    var pendingSamplesToSave by mutableStateOf(false)
        private set

    var initButtonEnabled by mutableStateOf(false)
        private set
    
    var outputFolderUri: Uri? = null
        private set

    //TODO better in controller
    var macFilterList: List<String> = listOf()
        private set

    fun evaluateEnableButtonTest() {
        initButtonEnabled = outputFolderUri !== null;
    }

    fun loadMacFilterListFromJson(jsonString: String) {
        val type = object : TypeToken<List<String>>() {}.type
        macFilterList = Gson().fromJson(jsonString, type)
    }

    fun updateOutputFolderUri(uri: Uri) {
        outputFolderUri = uri
        evaluateEnableButtonTest()
    }

    // END - UI

    // PROCESS

    fun startCapture() {

    }

    fun stopCapture() {

    }

    fun saveCaptureFile(context: Context) {
        pendingSamplesToSave = false
        //TODO
        /*var samples = cameraController.samples
        if (outputFolderUri == null || samples.isEmpty()) {
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
        }*/

    }

    // END - PROCESS
}