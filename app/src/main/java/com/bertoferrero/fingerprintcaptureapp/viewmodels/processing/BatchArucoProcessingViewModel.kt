package com.bertoferrero.fingerprintcaptureapp.viewmodels.processing

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.controllers.processing.BatchArucoProcessingController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * ViewModel para el procesamiento por lotes de imágenes ArUco.
 * Gestiona la configuración, el procesamiento y el estado de la operación.
 */
class BatchArucoProcessingViewModel(
    application: Application
): AndroidViewModel(application) {

    private val settingsManager = SettingsParametersManager()

    // Estados de configuración
    var inputFolderUri: Uri? by mutableStateOf(null)
        private set

    var outputFolderUri: Uri? by mutableStateOf(null)
        private set

    var markersFileUri: Uri? by mutableStateOf(null)
        private set

    var selectedArucoType: ArucoDictionaryType by mutableStateOf(ArucoDictionaryType.DICT_6X6_250)
        private set

    // Parámetros RANSAC
    var ransacMinThreshold: Double by mutableStateOf(0.2)
        private set

    var ransacMaxThreshold: Double by mutableStateOf(0.4)
        private set

    var ransacStep: Double by mutableStateOf(0.1)
        private set

    // Filtro aritmético
    var arithmeticFilterType: MultipleMarkersBehaviour by mutableStateOf(MultipleMarkersBehaviour.WEIGHTED_MEDIAN)
        private set

    // Estado del procesamiento
    var isProcessing: Boolean by mutableStateOf(false)
        private set

    var processedImages: Int by mutableStateOf(0)
        private set

    var totalImages: Int by mutableStateOf(0)
        private set

    var currentImageName: String by mutableStateOf("")
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    var processingComplete: Boolean by mutableStateOf(false)
        private set

    // Datos cargados
    private var markersDefinition: List<MarkerDefinition> = emptyList()
    
    // Controlador de procesamiento
    private var processingController: BatchArucoProcessingController? = null

    // Job del procesamiento para cancelación
    private var processingJob: Job? = null

    // Validación de configuración
    val canStartProcessing: Boolean
        get() = inputFolderUri != null && 
                outputFolderUri != null && 
                markersFileUri != null && 
                !isProcessing &&
                markersDefinition.isNotEmpty() &&
                processingController != null

    /**
     * Actualiza la URI de la carpeta de entrada.
     */
    fun updateInputFolderUri(uri: Uri) {
        inputFolderUri = uri
        clearError()
    }

    /**
     * Actualiza la URI de la carpeta de salida.
     */
    fun updateOutputFolderUri(uri: Uri) {
        outputFolderUri = uri
        clearError()
    }

    /**
     * Actualiza la URI del archivo de marcadores y carga las definiciones.
     */
    fun updateMarkersFileUri(uri: Uri, context: Context) {
        markersFileUri = uri
        loadMarkersFromFile(context)
        clearError()
    }

    /**
     * Actualiza el tipo de diccionario ArUco.
     */
    fun updateArucoType(type: ArucoDictionaryType) {
        selectedArucoType = type
        initializeProcessingController() // Reinicializar con nueva configuración
    }

    /**
     * Actualiza los parámetros RANSAC.
     */
    fun updateRansacParameters(min: Double, max: Double, step: Double) {
        ransacMinThreshold = min
        ransacMaxThreshold = max
        ransacStep = step
        processingController?.updateRansacParameters(min, max, step)
    }

    /**
     * Actualiza el tipo de filtro aritmético.
     */
    fun updateArithmeticFilterType(type: MultipleMarkersBehaviour) {
        arithmeticFilterType = type
        initializeProcessingController() // Reinicializar con nueva configuración
    }

    /**
     * Carga las definiciones de marcadores desde el archivo JSON.
     */
    private fun loadMarkersFromFile(context: Context) {
        markersFileUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val listType = object : TypeToken<List<MarkerDefinition>>() {}.type
                    markersDefinition = Gson().fromJson(jsonString, listType)
                    
                    // Inicializar el controlador de procesamiento con los marcadores cargados
                    initializeProcessingController()
                    
                    Log.i("BatchArucoProcessingViewModel", 
                          "Loaded ${markersDefinition.size} marker definitions")
                }
            } catch (e: Exception) {
                Log.e("BatchArucoProcessingViewModel", "Error loading markers from file", e)
                errorMessage = "Error loading markers file: ${e.message}"
                markersDefinition = emptyList()
                processingController = null
            }
        }
    }
    
    /**
     * Inicializa el controlador de procesamiento con la configuración actual.
     */
    private fun initializeProcessingController() {
        if (markersDefinition.isNotEmpty()) {
            processingController = BatchArucoProcessingController(
                arucoDictionaryType = selectedArucoType,
                markersDefinition = markersDefinition,
                multipleMarkersBehaviour = arithmeticFilterType
            ).apply {
                updateRansacParameters(ransacMinThreshold, ransacMaxThreshold, ransacStep)
            }
        }
    }

    /**
     * Inicia el procesamiento por lotes.
     */
    fun startProcessing(context: Context) {
        if (!canStartProcessing) {
            errorMessage = "Configuration incomplete or processing controller not initialized"
            return
        }
        
        if (processingController == null) {
            errorMessage = "Processing controller not initialized. Please reload markers file."
            return
        }
        
        if (!processingController!!.isCalibrationParametersLoaded()) {
            errorMessage = "Camera calibration parameters not loaded. Please calibrate camera first."
            return
        }

        processingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    isProcessing = true
                    processedImages = 0
                    totalImages = 0
                    processingComplete = false
                    clearError()
                }

                processImagesInFolder(context)

                withContext(Dispatchers.Main) {
                    processingComplete = true
                }

            } catch (e: Exception) {
                Log.e("BatchArucoProcessingViewModel", "Processing error", e)
                withContext(Dispatchers.Main) {
                    errorMessage = "Processing error: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                }
            }
        }
    }

    /**
     * Detiene el procesamiento en curso.
     */
    fun stopProcessing() {
        processingJob?.cancel()
        isProcessing = false
        Log.i("BatchArucoProcessingViewModel", "Processing stopped by user")
    }

    /**
     * Procesa todas las imágenes en la carpeta de entrada.
     */
    private suspend fun processImagesInFolder(context: Context) {
        val inputFolder = DocumentFile.fromTreeUri(context, inputFolderUri!!)
            ?: throw IllegalStateException("Cannot access input folder")

        val outputFolder = DocumentFile.fromTreeUri(context, outputFolderUri!!)
            ?: throw IllegalStateException("Cannot access output folder")

        // Obtener lista de archivos de imagen
        val imageFiles = inputFolder.listFiles().filter { file ->
            file.isFile && isImageFile(file.name ?: "")
        }

        withContext(Dispatchers.Main) {
            totalImages = imageFiles.size
        }

        if (imageFiles.isEmpty()) {
            throw IllegalStateException("No image files found in input folder")
        }

        // Crear archivo CSV de resultados
        val csvFileName = "aruco_positions_${System.currentTimeMillis()}.csv"
        val csvFile = outputFolder.createFile("text/csv", csvFileName)
            ?: throw IllegalStateException("Cannot create CSV output file")

        // Escribir encabezado CSV
        context.contentResolver.openOutputStream(csvFile.uri)?.use { outputStream ->
            val csvHeader = "filename,marker_id,x,y,z,ransac_threshold,filter_type,is_global_position,marker_count,timestamp\n"
            outputStream.write(csvHeader.toByteArray())

            // Procesar cada imagen
            for ((index, imageFile) in imageFiles.withIndex()) {
                if (!isProcessing) break // Verificar cancelación

                withContext(Dispatchers.Main) {
                    currentImageName = imageFile.name ?: "unknown"
                    processedImages = index + 1
                }

                try {
                    // Llamar directamente al controlador
                    val result = processingController!!.processImageFile(context, imageFile)
                    
                    if (result.success) {
                        // Escribir resultados al CSV
                        for (position in result.detectedPositions) {
                            val csvLine = "${imageFile.name},${position.markerId},${position.x},${position.y},${position.z},${position.ransacThreshold},${arithmeticFilterType.name},${position.isGlobalPosition},${position.markerCount},${System.currentTimeMillis()}\n"
                            outputStream.write(csvLine.toByteArray())
                        }

                        // Si no se encontraron posiciones, escribir una línea indicándolo
                        if (result.detectedPositions.isEmpty()) {
                            val noDetectionLine = "${imageFile.name},NO_DETECTION,,,,,,,${System.currentTimeMillis()}\n"
                            outputStream.write(noDetectionLine.toByteArray())
                        }
                    } else {
                        // Escribir línea de error al CSV si el procesamiento falló
                        val errorLine = "${imageFile.name},ERROR,,,,,${result.error ?: "Unknown error"},,,${System.currentTimeMillis()}\n"
                        outputStream.write(errorLine.toByteArray())
                    }

                } catch (e: Exception) {
                    Log.e("BatchArucoProcessingViewModel", 
                          "Error processing image ${imageFile.name}", e)
                    
                    // Escribir línea de error al CSV
                    val errorLine = "${imageFile.name},ERROR,,,,,${e.message},,,${System.currentTimeMillis()}\n"
                    outputStream.write(errorLine.toByteArray())
                }
            }

            outputStream.flush()
        }

        Log.i("BatchArucoProcessingViewModel", 
              "Processing complete. Results saved to $csvFileName")
    }



    /**
     * Verifica si un archivo es una imagen soportada.
     */
    private fun isImageFile(fileName: String): Boolean {
        return fileName.endsWith(".matphoto", ignoreCase = true)
    }

    /**
     * Limpia el mensaje de error.
     */
    private fun clearError() {
        errorMessage = null
    }

    /**
     * Reinicia el estado del procesamiento.
     */
    fun resetProcessing() {
        processedImages = 0
        totalImages = 0
        currentImageName = ""
        processingComplete = false
        clearError()
    }

    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
    }


}