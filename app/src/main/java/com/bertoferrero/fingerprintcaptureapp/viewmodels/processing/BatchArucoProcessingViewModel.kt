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

    // Opción de unificar poses
    var unifyPoses: Boolean by mutableStateOf(false)
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
     * Actualiza la opción de unificar poses.
     */
    fun updateUnifyPoses(enabled: Boolean) {
        unifyPoses = enabled
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

        // Escribir encabezado CSV según el modo
        context.contentResolver.openOutputStream(csvFile.uri)?.use { outputStream ->
            if (unifyPoses) {
                // Modo unificado: procesar todas las imágenes juntas
                processImagesUnified(context, imageFiles, outputStream)
            } else {
                // Modo individual: procesar cada imagen por separado
                processImagesIndividually(context, imageFiles, outputStream)
            }

            outputStream.flush()
        }

        Log.i("BatchArucoProcessingViewModel", 
              "Processing complete. Results saved to $csvFileName")
    }



    /**
     * Procesa imágenes individualmente (modo actual).
     */
    private suspend fun processImagesIndividually(
        context: Context, 
        imageFiles: List<DocumentFile>, 
        outputStream: java.io.OutputStream
    ) {

        
        val csvHeader = "filename,marker_id,x,y,z,ransac_threshold,filter_type,is_global_position,marker_count,image_markers_detected\n"
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
                
                // Escribir información detallada de procesamiento por imagen
                for (imageInfo in result.processedImages) {
                    if (imageInfo.success) {
                        // Escribir resultados al CSV por cada posición detectada
                        if (result.detectedPositions.isNotEmpty()) {
                            for (position in result.detectedPositions) {
                                val csvLine = "${imageInfo.fileName},${position.markerId},${position.x},${position.y},${position.z},${position.ransacThreshold},${arithmeticFilterType.name},${position.isGlobalPosition},${position.markerCount},${imageInfo.markerCount}\n"
                                outputStream.write(csvLine.toByteArray())
                            }
                        } else {
                            // Imagen procesada correctamente pero sin detecciones
                            val noDetectionLine = "${imageInfo.fileName},NO_DETECTION,,,,,,,${imageInfo.markerCount}\n"
                            outputStream.write(noDetectionLine.toByteArray())
                        }
                    } else {
                        // Error específico de esta imagen
                        val errorLine = "${imageInfo.fileName},ERROR,,,,,${imageInfo.error ?: "Image processing error"},,,${imageInfo.markerCount}\n"
                        outputStream.write(errorLine.toByteArray())
                    }
                }
                
                // Si hubo error global (no por imagen individual)
                if (!result.success && result.error != null) {
                    val globalErrorLine = "${imageFile.name},GLOBAL_ERROR,,,,,${result.error},,,0\n"
                    outputStream.write(globalErrorLine.toByteArray())
                }

            } catch (e: Exception) {
                Log.e("BatchArucoProcessingViewModel", 
                      "Error processing image ${imageFile.name}", e)
                
                // Escribir línea de error al CSV
                val errorLine = "${imageFile.name},EXCEPTION,,,,,${e.message},,,0\n"
                outputStream.write(errorLine.toByteArray())
            }
        }
    }

    /**
     * Procesa todas las imágenes unificadas (modo nuevo).
     */
    private suspend fun processImagesUnified(
        context: Context, 
        imageFiles: List<DocumentFile>, 
        outputStream: java.io.OutputStream
    ) {
        
        withContext(Dispatchers.Main) {
            currentImageName = "Processing unified batch..."
            processedImages = 0
        }
        
        val csvHeader = "image_name,marker_id,x,y,z,ransac_threshold,filter_type,is_global_position,marker_count,total_images_processed,successful_images,total_markers_detected\n"
        outputStream.write(csvHeader.toByteArray())

        try {
            // Procesar todas las imágenes juntas
            val result = processingController!!.processImageFiles(context, imageFiles)
            
            // Estadísticas del lote
            val totalImagesProcessed = result.processedImages.size
            val successfulImages = result.processedImages.count { it.success }
            val totalMarkersDetected = result.processedImages.sumOf { it.markerCount }
            
            // Escribir posición global (si existe)
            val globalPosition = result.detectedPositions.find { it.isGlobalPosition }
            if (globalPosition != null) {
                val csvLine = ",${globalPosition.markerId},${globalPosition.x},${globalPosition.y},${globalPosition.z},${globalPosition.ransacThreshold},${arithmeticFilterType.name},${globalPosition.isGlobalPosition},${globalPosition.markerCount},$totalImagesProcessed,$successfulImages,$totalMarkersDetected\n"
                outputStream.write(csvLine.toByteArray())
            }
            
            // Escribir posiciones individuales de marcadores
            val individualPositions = result.detectedPositions.filter { !it.isGlobalPosition }
            for (position in individualPositions) {
                val imageName = position.sourceIdentifier ?: ""
                val csvLine = "$imageName,${position.markerId},${position.x},${position.y},${position.z},${position.ransacThreshold},${arithmeticFilterType.name},${position.isGlobalPosition},${position.markerCount},$totalImagesProcessed,$successfulImages,$totalMarkersDetected\n"
                outputStream.write(csvLine.toByteArray())
            }
            
            // Escribir información de imágenes con errores
            for (imageInfo in result.processedImages.filter { !it.success }) {
                val errorLine = "${imageInfo.fileName},ERROR,,,,,${imageInfo.error ?: "Image processing error"},,,${imageInfo.markerCount},$totalImagesProcessed,$successfulImages,$totalMarkersDetected\n"
                outputStream.write(errorLine.toByteArray())
            }
            
            // Si hubo error global
            if (!result.success && result.error != null) {
                val globalErrorLine = ",GLOBAL_ERROR,,,,,${result.error},,,0,$totalImagesProcessed,$successfulImages,$totalMarkersDetected\n"
                outputStream.write(globalErrorLine.toByteArray())
            }
            
            // Si no se detectaron posiciones
            if (result.detectedPositions.isEmpty() && result.success) {
                val noDetectionLine = ",NO_DETECTION,,,,,,,0,$totalImagesProcessed,$successfulImages,$totalMarkersDetected\n"
                outputStream.write(noDetectionLine.toByteArray())
            }

        } catch (e: Exception) {
            Log.e("BatchArucoProcessingViewModel", 
                  "Error processing unified batch", e)
            
            val errorLine = ",EXCEPTION,,,,,${e.message},,,0,${imageFiles.size},0,0\n"
            outputStream.write(errorLine.toByteArray())
        }
        
        withContext(Dispatchers.Main) {
            processedImages = imageFiles.size
        }
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