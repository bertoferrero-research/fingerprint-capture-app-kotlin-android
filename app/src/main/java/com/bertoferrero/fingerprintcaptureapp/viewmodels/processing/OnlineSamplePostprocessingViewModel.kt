package com.bertoferrero.fingerprintcaptureapp.viewmodels.processing

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bertoferrero.fingerprintcaptureapp.lib.CSV_FIELD_SEPARATOR
import com.bertoferrero.fingerprintcaptureapp.lib.toCsvDecimal
import com.bertoferrero.fingerprintcaptureapp.lib.markers.DetectionProfile
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.withContext
import org.opencv.objdetect.Dictionary
import com.bertoferrero.fingerprintcaptureapp.controllers.processing.ArucoProcessingController
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson

/**
 * ViewModel para el postprocesamiento de muestras online.
 * Gestiona la configuración y el estado del procesamiento de un directorio
 * con __all.csv e imágenes para generar CSV con lecturas y posiciones.
 */
class OnlineSamplePostprocessingViewModel(
    application: Application
) : AndroidViewModel(application) {

    // Estados de directorios
    var inputDirectoryUri: Uri? by mutableStateOf(null)
        private set

    var outputDirectoryUri: Uri? by mutableStateOf(null)
        private set

    var inputDirectoryName: String by mutableStateOf("No directory selected")
        private set

    var outputDirectoryName: String by mutableStateOf("No directory selected")
        private set

    // Configuración de tiempo
    var timeWindow: Int by mutableStateOf(1000)
        private set

    var markersFileUri: Uri? by mutableStateOf(null)
        private set

    // Configuración de ArUco
    var selectedArucoType: ArucoDictionaryType by mutableStateOf(ArucoDictionaryType.DICT_6X6_250)
        private set

    // Parámetros RANSAC
    var ransacMinThreshold: Double by mutableStateOf(0.2)
        private set

    var ransacMaxThreshold: Double by mutableStateOf(0.4)
        private set

    var ransacStep: Double by mutableStateOf(0.01)
        private set

    // Filtro aritmético
    var arithmeticFilterType: MultipleMarkersBehaviour by mutableStateOf(MultipleMarkersBehaviour.WEIGHTED_MEDIAN)
        private set

    // Perfil de detección (BASE = comportamiento pre-optimizaciones, OPTIMIZED = actual)
    var detectionProfile: DetectionProfile by mutableStateOf(DetectionProfile.OPTIMIZED)
        private set

    // Estado del procesamiento
    var isProcessing: Boolean by mutableStateOf(false)
        private set

    var processingProgress: Float by mutableStateOf(0f)
        private set

    var currentStatus: String by mutableStateOf("")
        private set

    var processingComplete: Boolean by mutableStateOf(false)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set
        
    // Datos cargados
    private var markersDefinition: List<MarkerDefinition> = emptyList()
    
    // Controlador de procesamiento
    private var processingController: ArucoProcessingController? = null

    // Job para control del procesamiento
    private var processingJob: Job? = null

    /**
     * Indica si se puede iniciar el procesamiento.
     */
    val canStartProcessing: Boolean
        get() = inputDirectoryUri != null && 
                outputDirectoryUri != null &&  
                markersFileUri != null && 
                !isProcessing &&
                markersDefinition.isNotEmpty() &&
                processingController != null

    /**
     * Actualiza el URI del directorio de entrada.
     */
    fun updateInputDirectory(uri: Uri, context: Context) {
        inputDirectoryUri = uri
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        inputDirectoryName = documentFile?.name ?: "Unknown"
        clearError()
    }

    /**
     * Actualiza el URI del directorio de salida.
     */
    fun updateOutputDirectory(uri: Uri, context: Context) {
        outputDirectoryUri = uri
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        outputDirectoryName = documentFile?.name ?: "Unknown"
        clearError()
    }


    /**
     * Actualiza el Time Window.
     */
    fun updateTimeWindow(value: Int) {
        timeWindow = value
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
        clearError()
    }

    /**
     * Actualiza el umbral mínimo de RANSAC.
     */
    fun updateRansacMinThreshold(value: Double) {
        ransacMinThreshold = value
        processingController?.updateRansacParameters(ransacMinThreshold, ransacMaxThreshold, ransacStep)
        clearError()
    }

    /**
     * Actualiza el umbral máximo de RANSAC.
     */
    fun updateRansacMaxThreshold(value: Double) {
        ransacMaxThreshold = value
        processingController?.updateRansacParameters(ransacMinThreshold, ransacMaxThreshold, ransacStep)
        clearError()
    }

    /**
     * Actualiza el step de RANSAC.
     */
    fun updateRansacStep(value: Double) {
        ransacStep = value
        processingController?.updateRansacParameters(ransacMinThreshold, ransacMaxThreshold, ransacStep)
        clearError()
    }

    /**
     * Actualiza el tipo de filtro aritmético.
     */
    fun updateArithmeticFilterType(type: MultipleMarkersBehaviour) {
        arithmeticFilterType = type
        initializeProcessingController() // Reinicializar con nueva configuración
        clearError()
    }

    /**
     * Actualiza el perfil de detección.
     */
    fun updateDetectionProfile(profile: DetectionProfile) {
        detectionProfile = profile
        initializeProcessingController() // Reinicializar con nueva configuración
        clearError()
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
            processingController = ArucoProcessingController(
                arucoDictionaryType = selectedArucoType,
                markersDefinition = markersDefinition,
                multipleMarkersBehaviour = arithmeticFilterType,
                detectionProfile = detectionProfile
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
            errorMessage = "Please select both input and output directories"
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

        processingJob = viewModelScope.launch {
            try {
                isProcessing = true
                processingComplete = false
                processingProgress = 0f
                currentStatus = "Starting processing..."
                errorMessage = null


                // Cargamos directorios
                val inputDirectory = DocumentFile.fromTreeUri(context, inputDirectoryUri!!)!!
                val outputDirectory = DocumentFile.fromTreeUri(context, outputDirectoryUri!!)!!

                //Preparamos el listado de imágenes
                val imageFileList = loadMatPhotoImages(inputDirectory)
                if (imageFileList.isEmpty()){
                    throw Exception("There was no .matphoto at the input directory")
                }

                //Cargamos el all.csv y procesamos cada linea
                val allCsvFile = inputDirectory.listFiles().find {
                    it.name?.endsWith("all.csv", ignoreCase = true) == true
                }
                if (allCsvFile == null) {
                    throw Exception("File __all.csv not found in input directory")
                }

                //Creamos el fichero de salida
                val outputFile = outputDirectory.createFile("text/csv", "all.csv")
                    ?: throw IllegalStateException("Cannot create output CSV file")

                //Creamos el fichero de estadísticas de procesamiento
                val statsFile = outputDirectory.createFile("text/csv", "all_processing_stats.csv")
                    ?: throw IllegalStateException("Cannot create stats CSV file")

                //Ejecutamos toda la lógica
                processData(context, imageFileList, allCsvFile, outputFile, statsFile)

                withContext(Dispatchers.Main) {
                    processingComplete = true
                    currentStatus = "Processing completed successfully!"
                }

            } catch (e: Exception) {
                Log.e("OnlineSamplePostprocessingVM", "Error during processing", e)
                errorMessage = "Processing error: ${e.message}"
            } finally {
                isProcessing = false
            }
        }
    }

    /**
     * Procesa los datos del directorio de entrada.
     */
    private suspend fun processData(
        context: Context,
        imageFileList: Map<Double, DocumentFile>,
        allCsvFile: DocumentFile,
        outputFile: DocumentFile,
        statsFile: DocumentFile
    ) = withContext(Dispatchers.IO) {


        // Preparamos el stream del fichero de salida
        val outputStream = context.contentResolver.openOutputStream(outputFile.uri)
            ?: throw IllegalStateException("Cannot open output stream")
        
        // Preparamos el stream del fichero de estadísticas
        val statsStream = context.contentResolver.openOutputStream(statsFile.uri)
            ?: throw IllegalStateException("Cannot open stats stream")
        
        //Indicamos que se escriban las cabeceras
        var writeHeaders = true
        
        // Escribir cabecera del CSV de estadísticas
        writeStatsHeader(statsStream)
        


        // Para casos en los que no se pueda obtener la posición, usamos la ultima posición calculada
        var lastPosition = mutableListOf(0.0, 0.0, 0.0) //x, y, z
        var lastPositionTimestamp = ""
        var lastPositionCacheImageMin : Double = 0.0
        var lastPositionCacheImageMax : Double = 0.0

        val inputStreamAllCsv = context.contentResolver.openInputStream(allCsvFile.uri)
            ?: throw Exception("Cannot open file: ${allCsvFile.name}")

        currentStatus = "Reading all.csv..."
        // all.csv se escribe con ";" como separador (ver RssiCaptureService) para poder usar
        // coma como separador decimal, así que hay que leerlo con el mismo delimitador.
        val rows: List<Map<String,String>> = csvReader { delimiter = ';' }.readAllWithHeader(inputStreamAllCsv)
        val rowsTotal = rows.size
        var currentRow = 0
        rows.forEach { row ->
            currentRow++
            processingProgress = (currentRow.toFloat() / rowsTotal.toFloat())

            // Escribimos cabeceras de salida si hace falta
            if (writeHeaders){
                var headers = row.keys.toMutableList()
                headers.add("position_calculation_timestamp")
                headers.add("position_calculation_status")
                outputStream.write((headers.joinToString(separator=CSV_FIELD_SEPARATOR)+"\n").toByteArray())
                writeHeaders = false
            }

            // Obtenemos el timestamp de la muestra y calculamos el valor mínimo de la ventana
            val sampleTimestamp = row["timestamp"]!!
            val sampleTimestampDouble = sampleTimestamp.toDouble()
            val minWindow = sampleTimestampDouble - timeWindow

            // Sacamos el listado de imágenes a utilizar para esta posición y, si no está vacía, solicitamos el cálculo de posición
            val selectedArucoImages = imageFileList.filter {
                it.key <= sampleTimestampDouble && it.key >= minWindow
            }
            
            val lastPositionStatus: String = if(selectedArucoImages.isNotEmpty()){
                //Comprobamos caché
                val selectedMin = selectedArucoImages.keys.min()
                val selectedMax = selectedArucoImages.keys.max()

                if(lastPositionCacheImageMin != selectedMin || lastPositionCacheImageMax != selectedMax) {
                    // Cache miss: recalcular posición
                    lastPositionCacheImageMin = selectedMin
                    lastPositionCacheImageMax = selectedMax
                    val positioningResult = processingController!!.processImageFiles(
                        context,
                        selectedArucoImages.values.toList()
                    )
                    
                    // Escribir estadísticas del procesamiento
                    writeProcessingStats(
                        statsStream = statsStream,
                        positioningResult = positioningResult,
                        rssiTimestamp = sampleTimestamp,
                        selectedImages = selectedArucoImages
                    )
                    
                    // Comprobamos si se ha podido obtener la posición
                    val globalPosition =
                        positioningResult.detectedPositions.find { it.isGlobalPosition }
                    if (globalPosition != null) {
                        lastPosition[0] = globalPosition.x
                        lastPosition[1] = globalPosition.y
                        lastPosition[2] = globalPosition.z
                        lastPositionTimestamp = sampleTimestamp
                        "calculated"
                    } else {
                        "no_calculated"
                    }

                } else {
                    // Cache hit: reutilizar última posición calculada
                    "cached"
                }
            } else {
                "no_images"
            }

            // Actualizamos la posición de la fila
            var outputRow = row.toMutableMap()
            outputRow["pos_x"] = lastPosition[0].toCsvDecimal()
            outputRow["pos_y"] = lastPosition[1].toCsvDecimal()
            outputRow["pos_z"] = lastPosition[2].toCsvDecimal()
            //Añadimos el nuevo dato usando una lista para segurar que se quede al final
            var listOutputRow = outputRow.values.toMutableList()
            listOutputRow.add(lastPositionTimestamp)
            listOutputRow.add(lastPositionStatus)

            //Escribimos la salida
            outputStream.write((listOutputRow.joinToString(separator=CSV_FIELD_SEPARATOR)+"\n").toByteArray())

        }
        inputStreamAllCsv.close()
        outputStream.close()
        statsStream.close()
    }

    private fun loadMatPhotoImages(
        directory: DocumentFile
    ): MutableMap<Double, DocumentFile> {
        val imageFileList = mutableMapOf<Double, DocumentFile>()
        directory.listFiles().forEach {
            if(it.name?.endsWith(".matphoto", ignoreCase = true) == true){
                //Obtenemos el nombre para sacar el timestamp y lo indexamos
                val match = Regex(".*([0-9]{13}).*").find(it.name!!)
                var imageTimestamp = match?.groups?.last()?.value
                val imageTimestampDouble = imageTimestamp?.toDoubleOrNull()
                if(imageTimestampDouble == null){
                    throw Exception("Error transforming timestamp to int")
                }
                imageFileList.put(imageTimestampDouble, it)
            }
        }
        return imageFileList
    }

    /**
     * Escribe la cabecera del CSV de estadísticas de procesamiento.
     */
    private fun writeStatsHeader(statsStream: java.io.OutputStream) {
        val headers = listOf(
            "rssi_timestamp",
            "entry_type",
            "image_filename",
            "marker_id",
            "x",
            "y",
            "z",
            "ransac_threshold",
            "filter_type",
            "is_global_position",
            "marker_count",
            "images_processed",
            "successful_images",
            "ransac_population",
            "is_ransac_excluded",
            "image_timestamp"
        )
        statsStream.write((headers.joinToString(CSV_FIELD_SEPARATOR) + "\n").toByteArray())
    }

    /**
     * Escribe las estadísticas de procesamiento en el CSV.
     * Escribe una línea para la posición global y una línea por cada imagen procesada.
     */
    private fun writeProcessingStats(
        statsStream: java.io.OutputStream,
        positioningResult: ArucoProcessingController.BatchProcessingResult,
        rssiTimestamp: String,
        selectedImages: Map<Double, DocumentFile>
    ) {
        val totalImagesProcessed = positioningResult.processedImages.size
        val successfulImages = positioningResult.processedImages.count { it.success }
        
        // 1. Escribir línea de posición global (si existe)
        val globalPosition = positioningResult.detectedPositions.find { it.isGlobalPosition }
        if (globalPosition != null) {
            val ransacPopulation = globalPosition.ransacResult?.size ?: 0
            val globalLine = listOf(
                rssiTimestamp,
                "GLOBAL",
                "",
                globalPosition.markerId.toString(),
                globalPosition.x.toCsvDecimal(),
                globalPosition.y.toCsvDecimal(),
                globalPosition.z.toCsvDecimal(),
                globalPosition.ransacThreshold.toCsvDecimal(),
                arithmeticFilterType.name,
                globalPosition.isGlobalPosition.toString(),
                globalPosition.markerCount.toString(),
                totalImagesProcessed.toString(),
                successfulImages.toString(),
                ransacPopulation.toString(),
                (globalPosition.ransacExcluded ?: false).toString(),
                ""
            )
            statsStream.write((globalLine.joinToString(CSV_FIELD_SEPARATOR) + "\n").toByteArray())
        } else {
            // No se calculó posición global
            val noPositionLine = listOf(
                rssiTimestamp,
                "GLOBAL",
                "",
                "NO_POSITION",
                "",
                "",
                "",
                "",
                arithmeticFilterType.name,
                "false",
                "0",
                totalImagesProcessed.toString(),
                successfulImages.toString(),
                "0",
                "false",
                ""
            )
            statsStream.write((noPositionLine.joinToString(CSV_FIELD_SEPARATOR) + "\n").toByteArray())
        }
        
        // 2. Escribir línea por cada imagen procesada
        for (imageInfo in positioningResult.processedImages) {
            // Buscar el timestamp de esta imagen
            val imageTimestamp = selectedImages.entries.find { 
                it.value.name == imageInfo.fileName 
            }?.key?.toString() ?: ""
            
            if (imageInfo.success) {
                // Buscar posiciones detectadas para esta imagen
                val imagePositions = positioningResult.detectedPositions.filter { 
                    !it.isGlobalPosition && it.sourceIdentifier == imageInfo.fileName 
                }
                
                if (imagePositions.isNotEmpty()) {
                    // Escribir una línea por cada marcador detectado en la imagen
                    for (position in imagePositions) {
                        val imageLine = listOf(
                            rssiTimestamp,
                            "IMAGE",
                            imageInfo.fileName,
                            position.markerId.toString(),
                            position.x.toCsvDecimal(),
                            position.y.toCsvDecimal(),
                            position.z.toCsvDecimal(),
                            "",
                            "",
                            "false",
                            "1",
                            totalImagesProcessed.toString(),
                            successfulImages.toString(),
                            "",
                            (position.ransacExcluded ?: false).toString(),
                            imageTimestamp
                        )
                        statsStream.write((imageLine.joinToString(CSV_FIELD_SEPARATOR) + "\n").toByteArray())
                    }
                } else {
                    // Imagen procesada pero sin detecciones
                    val noDetectionLine = listOf(
                        rssiTimestamp,
                        "IMAGE",
                        imageInfo.fileName,
                        "NO_DETECTION",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "false",
                        "0",
                        totalImagesProcessed.toString(),
                        successfulImages.toString(),
                        "",
                        "false",
                        imageTimestamp
                    )
                    statsStream.write((noDetectionLine.joinToString(CSV_FIELD_SEPARATOR) + "\n").toByteArray())
                }
            } else {
                // Error al procesar la imagen
                val errorLine = listOf(
                    rssiTimestamp,
                    "IMAGE_ERROR",
                    imageInfo.fileName,
                    "ERROR",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "false",
                    imageInfo.markerCount.toString(),
                    totalImagesProcessed.toString(),
                    successfulImages.toString(),
                    "",
                    "false",
                    imageTimestamp
                )
                statsStream.write((errorLine.joinToString(CSV_FIELD_SEPARATOR) + "\n").toByteArray())
            }
        }
    }

    /**
     * Cancela el procesamiento en curso.
     */
    fun cancelProcessing() {
        processingJob?.cancel()
        isProcessing = false
        currentStatus = "Processing cancelled"
    }

    /**
     * Limpia el mensaje de error.
     */
    fun clearError() {
        errorMessage = null
    }

    /**
     * Resetea el estado del procesamiento.
     */
    fun resetProcessing() {
        processingProgress = 0f
        currentStatus = ""
        processingComplete = false
        clearError()
    }

    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
    }
}
