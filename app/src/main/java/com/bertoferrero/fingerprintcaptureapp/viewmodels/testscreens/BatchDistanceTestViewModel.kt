package com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestDistanceCameraController
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.CvCameraViewFrameMockFromImage
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatFromFile
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

data class BatchProcessResult(
    val fileName: String,
    val distance: Double?,
    val error: String? = null
)

class BatchDistanceTestViewModel : ViewModel() {

    val settingsManager = SettingsParametersManager()
    private var _cameraController: TestDistanceCameraController? = null
    
    val cameraController: TestDistanceCameraController
        get() = _cameraController ?: throw IllegalStateException("Camera controller not initialized. Call initializeController first.")

    var inputFolderUri: Uri? by mutableStateOf(null)
        private set

    var outputFolderUri: Uri? by mutableStateOf(null)
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

    var canStartProcessing by mutableStateOf(false)
        private set

    var results by mutableStateOf<List<BatchProcessResult>>(emptyList())
        private set

    fun initializeController(context: Context) {
        if (_cameraController == null) {
            _cameraController = TestDistanceCameraController(
                context = context,
                markerSize = settingsManager.markerSize,
                arucoDictionaryType = settingsManager.arucoDictionaryType,
                method = 1, // Default method
                testingImageFrame = null
            )
            _cameraController?.initProcess()
        }
    }

    fun updateInputFolderUri(uri: Uri) {
        inputFolderUri = uri
        evaluateCanStartProcessing()
    }

    fun updateOutputFolderUri(uri: Uri) {
        outputFolderUri = uri
        evaluateCanStartProcessing()
    }

    fun updateMarkerSize(size: Float) {
        cameraController.markerSize = size
        settingsManager.markerSize = size
    }

    fun updateMethod(method: Int) {
        cameraController.method = method
    }

    fun updateArucoType(type: Int) {
        settingsManager.arucoDictionaryType = type
        cameraController.arucoDictionaryType = type
    }

    private fun evaluateCanStartProcessing() {
        canStartProcessing = inputFolderUri != null && outputFolderUri != null && !isProcessing
    }

    fun startBatchProcessing(context: Context) {
        if (!canStartProcessing) return

        viewModelScope.launch {
            try {
                isProcessing = true
                processingComplete = false
                processedFiles = 0
                results = emptyList()
                
                val inputFolder = DocumentFile.fromTreeUri(context, inputFolderUri!!)
                val matFiles = inputFolder?.listFiles()?.filter { 
                    it.name?.endsWith(".matphoto", ignoreCase = true) == true 
                } ?: emptyList()

                totalFiles = matFiles.size
                val batchResults = mutableListOf<BatchProcessResult>()

                for ((index, file) in matFiles.withIndex()) {
                    currentFileName = file.name ?: "Unknown"
                    
                    try {
                        val result = withContext(Dispatchers.IO) {
                            processMatFile(context, file)
                        }
                        batchResults.add(result)
                    } catch (e: Exception) {
                        batchResults.add(
                            BatchProcessResult(
                                fileName = file.name ?: "Unknown",
                                distance = null,
                                error = e.message ?: "Unknown error"
                            )
                        )
                    }
                    
                    processedFiles = index + 1
                    results = batchResults.toList()
                }

                // Save results to CSV
                saveResultsToCSV(context, batchResults)
                processingComplete = true

            } catch (e: Exception) {
                // Handle general error
                results = results + BatchProcessResult(
                    fileName = "BATCH_ERROR",
                    distance = null,
                    error = e.message ?: "Batch processing failed"
                )
            } finally {
                isProcessing = false
                currentFileName = ""
                evaluateCanStartProcessing()
            }
        }
    }

    private suspend fun processMatFile(context: Context, file: DocumentFile): BatchProcessResult {
        return withContext(Dispatchers.IO) {
            try {
                // Load MAT file
                val inputStream = context.contentResolver.openInputStream(file.uri)
                    ?: throw Exception("Cannot open file: ${file.name}")
                
                val mat = MatFromFile(inputStream)
                inputStream.close()

                // Create mock frame from Mat
                val mockFrame = CvCameraViewFrameMockFromImage(mat)
                cameraController.testingImageFrame = mockFrame

                // Calculate distance directly using the new method
                val distance = cameraController.calculateDistance(mockFrame)

                BatchProcessResult(
                    fileName = file.name ?: "Unknown",
                    distance = distance
                )
            } catch (e: Exception) {
                BatchProcessResult(
                    fileName = file.name ?: "Unknown",
                    distance = null,
                    error = e.message ?: "Processing error"
                )
            }
        }
    }

    private fun extractDistanceFromProcessedFrame(processedMat: Mat): Double? {
        // This method is no longer needed as we use calculateDistance directly
        return null
    }

    private suspend fun saveResultsToCSV(context: Context, results: List<BatchProcessResult>) {
        withContext(Dispatchers.IO) {
            val outputFolder = DocumentFile.fromTreeUri(context, outputFolderUri!!)
            val timestamp = System.currentTimeMillis()
            val fileName = "batch_distance_results_$timestamp.csv"

            val header = "filename,distance_meters,error_message"
            val csvRows = results.map { result ->
                "${result.fileName},${result.distance ?: ""},${result.error ?: ""}"
            }
            val csvContent = (listOf(header) + csvRows).joinToString("\n")

            val newFile = outputFolder?.createFile("text/csv", fileName)
            newFile?.uri?.let { fileUri ->
                context.contentResolver.openOutputStream(fileUri)?.use { out ->
                    out.write(csvContent.toByteArray())
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _cameraController?.finishProcess()
    }
}
