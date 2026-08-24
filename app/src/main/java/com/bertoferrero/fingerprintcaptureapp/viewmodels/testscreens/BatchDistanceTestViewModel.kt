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
import com.bertoferrero.fingerprintcaptureapp.lib.CSV_FIELD_SEPARATOR
import com.bertoferrero.fingerprintcaptureapp.lib.toCsvDecimal
import com.bertoferrero.fingerprintcaptureapp.lib.markers.DetectionProfile
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
    val error: String? = null,
    val detectionProfile: String = ""
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

    // Se mantiene aparte de cameraController (y no dentro de settingsManager, ya que no debe
    // persistir entre sesiones): la Configuration Section se renderiza antes de que
    // initializeController() complete (se llama desde un LaunchedEffect), así que leer
    // directamente de cameraController.detectionProfile ahí lanzaría IllegalStateException.
    var detectionProfile by mutableStateOf(DetectionProfile.OPTIMIZED)
        private set

    fun initializeController(context: Context) {
        if (_cameraController == null) {
            _cameraController = TestDistanceCameraController(
                context = context,
                markerSize = settingsManager.markerSize,
                arucoDictionaryType = settingsManager.arucoDictionaryType,
                method = 1, // Default method
                testingImageFrame = null,
                detectionProfile = detectionProfile
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

    fun updateDetectionProfile(profile: DetectionProfile) {
        detectionProfile = profile
        _cameraController?.detectionProfile = profile
    }

    private fun evaluateCanStartProcessing() {
        // La carpeta de salida es opcional: sin ella, los resultados solo se muestran en
        // pantalla (útil para depurar/previsualizar sin generar CSVs de sobra).
        canStartProcessing = inputFolderUri != null && !isProcessing
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
                                error = e.message ?: "Unknown error",
                                detectionProfile = cameraController.detectionProfile.name
                            )
                        )
                    }
                    
                    processedFiles = index + 1
                    results = batchResults.toList()
                }

                // Guardar CSV solo si hay carpeta de salida seleccionada; si no, modo
                // previsualización: los resultados ya están en pantalla vía `results`.
                if (outputFolderUri != null) {
                    saveResultsToCSV(context, batchResults)
                }
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
                    distance = distance,
                    detectionProfile = cameraController.detectionProfile.name
                )
            } catch (e: Exception) {
                BatchProcessResult(
                    fileName = file.name ?: "Unknown",
                    distance = null,
                    error = e.message ?: "Processing error",
                    detectionProfile = cameraController.detectionProfile.name
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
            val profileName = cameraController.detectionProfile.name
            val fileName = "batch_distance_results_${profileName}_$timestamp.csv"

            val header = listOf("filename", "distance_meters", "error_message", "detection_profile")
                .joinToString(CSV_FIELD_SEPARATOR)
            val csvRows = results.map { result ->
                listOf(
                    result.fileName,
                    result.distance?.toCsvDecimal() ?: "",
                    result.error ?: "",
                    result.detectionProfile
                ).joinToString(CSV_FIELD_SEPARATOR)
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
