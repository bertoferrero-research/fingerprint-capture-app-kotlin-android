package com.bertoferrero.fingerprintcaptureapp.viewmodels.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.BatchCalibrationController
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.CalibrationBatchResult
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager
import kotlinx.coroutines.launch

class BatchCalibrationViewModel : ViewModel() {

    val settingsManager = SettingsParametersManager()
    private var _batchController: BatchCalibrationController? = null
    
    val batchController: BatchCalibrationController
        get() = _batchController ?: throw IllegalStateException("Batch controller not initialized. Call initializeController first.")

    var inputFolderUri: Uri? by mutableStateOf(null)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    var processedFiles by mutableStateOf(0)
        private set

    var totalFiles by mutableStateOf(0)
        private set

    var currentFileName by mutableStateOf("")
        private set

    var processingComplete by mutableStateOf(false)
        private set

    var calibrationSuccessful by mutableStateOf(false)
        private set

    var calibrationMessage by mutableStateOf("")
        private set

    var canStartProcessing by mutableStateOf(false)
        private set

    var results by mutableStateOf<List<CalibrationBatchResult>>(emptyList())
        private set

    var successfulSamples by mutableStateOf(0)
        private set

    fun initializeController(context: Context) {
        if (_batchController == null) {
            _batchController = BatchCalibrationController(
                context = context,
                onCalibrationFinished = { success, message ->
                    calibrationSuccessful = success
                    calibrationMessage = message
                    processingComplete = true
                    successfulSamples = batchController.getSamplesCount()
                },
                arucoDictionaryType = settingsManager.arucoDictionaryType,
                charucoXSquares = settingsManager.charucoXSquares,
                charucoYSquares = settingsManager.charucoYSquares,
                charucoSquareLength = settingsManager.charucoSquareLength,
                charucoMarkerLength = settingsManager.charucoMarkerLength
            )
        }
    }

    fun updateInputFolderUri(uri: Uri) {
        inputFolderUri = uri
        evaluateCanStartProcessing()
    }

    fun updateCharucoXSquares(squares: Int) {
        batchController.charucoXSquares = squares
        settingsManager.charucoXSquares = squares
    }

    fun updateCharucoYSquares(squares: Int) {
        batchController.charucoYSquares = squares
        settingsManager.charucoYSquares = squares
    }

    fun updateCharucoSquareLength(length: Float) {
        batchController.charucoSquareLength = length
        settingsManager.charucoSquareLength = length
    }

    fun updateCharucoMarkerLength(length: Float) {
        batchController.charucoMarkerLength = length
        settingsManager.charucoMarkerLength = length
    }

    fun updateArucoType(type: Int) {
        settingsManager.arucoDictionaryType = type
        batchController.arucoDictionaryType = type
    }

    private fun evaluateCanStartProcessing() {
        canStartProcessing = inputFolderUri != null && !isProcessing
    }

    fun startBatchCalibration() {
        if (!canStartProcessing || inputFolderUri == null) return

        viewModelScope.launch {
            try {
                isProcessing = true
                processingComplete = false
                calibrationSuccessful = false
                calibrationMessage = ""
                processedFiles = 0
                results = emptyList()
                successfulSamples = 0
                
                // Process all files
                val batchResults = batchController.processBatchCalibration(inputFolderUri!!)
                
                results = batchResults
                totalFiles = batchResults.size
                processedFiles = batchResults.size

            } catch (e: Exception) {
                calibrationSuccessful = false
                calibrationMessage = "Batch calibration failed: ${e.message}"
                processingComplete = true
            } finally {
                isProcessing = false
                currentFileName = ""
                evaluateCanStartProcessing()
            }
        }
    }

    fun resetCalibration() {
        processingComplete = false
        calibrationSuccessful = false
        calibrationMessage = ""
        results = emptyList()
        processedFiles = 0
        totalFiles = 0
        successfulSamples = 0
        currentFileName = ""
    }

    override fun onCleared() {
        super.onCleared()
        // The controller cleanup is handled internally
    }
}
