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
    var samplingTime: Int by mutableStateOf(1000)
        private set

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

    var ransacStep: Double by mutableStateOf(0.1)
        private set

    // Filtro aritmético
    var arithmeticFilterType: MultipleMarkersBehaviour by mutableStateOf(MultipleMarkersBehaviour.WEIGHTED_MEDIAN)
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
     * Actualiza el Sampling Time.
     */
    fun updateSamplingTime(value: Int) {
        samplingTime = value
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
        clearError()
    }

    /**
     * Actualiza el umbral máximo de RANSAC.
     */
    fun updateRansacMaxThreshold(value: Double) {
        ransacMaxThreshold = value
        clearError()
    }

    /**
     * Actualiza el step de RANSAC.
     */
    fun updateRansacStep(value: Double) {
        ransacStep = value
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

                // Validar que existe __all.csv en el directorio de entrada


                // Aquí iría la lógica de procesamiento
                processData(context)

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
     * TODO: Implementar la lógica de procesamiento.
     */
    private suspend fun processData(
        context: Context
    ) = withContext(Dispatchers.IO) {

        // Cargamos directorios
        val inputDirectory = DocumentFile.fromTreeUri(context, inputDirectoryUri!!)!!
        val outputDirectory = DocumentFile.fromTreeUri(context, outputDirectoryUri!!)!!

        //Creamos el fichero de salida
        val outputFile = outputDirectory.createFile("text/csv", "all.csv")
            ?: throw IllegalStateException("Cannot create output CSV file")
        val outputStream = context.contentResolver.openOutputStream(outputFile.uri)
            ?: throw IllegalStateException("Cannot open output stream")
        var writeHeaders = true
        

        //Preparamos el listado de imágenes
        val imageFileList = mutableMapOf<Int, DocumentFile>()
        inputDirectory.listFiles().forEach {
            if(it.name?.endsWith(".matphoto", ignoreCase = true) == true){
                //Obtenemos el nombre para sacar el timestamp y lo indexamos
                val imageTimestamp =
                    it.name?.replace(oldValue = ".matphoto",newValue = "", ignoreCase = true)?.toInt()
                if (imageTimestamp == null){
                    throw Exception("Something goes extremly wrong getting the timestamp from the image name")
                }
                imageFileList.put(imageTimestamp, it)
            }
        }

        // Para casos en los que no se pueda obtener la posición, usamos la ultima posición calculada
        var lastPosition = mutableListOf(0.0, 0.0, 0.0) //x, y, z
        var lastPositionTimestamp = 0

        //Cargamos el all.csv y procesamos cada linea
        val allCsvFile = inputDirectory.listFiles().find {
            it.name?.lowercase() == "__all.csv"
        }
        if (allCsvFile == null) {
            throw Exception("File __all.csv not found in input directory")
        }
        val inputStreamAllCsv = context.contentResolver.openInputStream(allCsvFile.uri)
            ?: throw Exception("Cannot open file: ${allCsvFile.name}")
        csvReader().open(inputStreamAllCsv){
            readAllWithHeaderAsSequence().forEach { row ->
                // Escribimos cabeceras de salida si hace falta
                if (writeHeaders){
                    var headers = row.keys.toMutableList()
                    headers.add("position_calculation_timestamp")
                    outputStream.write(headers.joinToString(separator=",").toByteArray())
                    writeHeaders = false
                }

                // Obtenemos el timestamp de la muestra y calculamos el valor mínimo de la ventana
                val sampleTimestamp = row["timestamp"]!!.toInt()
                val minWindow = sampleTimestamp - timeWindow

                // Sacamos el listado de imágenes a utilizar para esta posición y, si no está vacía, solicitamos el cálculo de posición
                val selectedArucoImages = imageFileList.filter { 
                    it.key <= sampleTimestamp && it.key >= minWindow
                }
                if(selectedArucoImages.size > 0){
                    val positioningResult = processingController!!.processImageFiles(context, selectedArucoImages.values.toList())
                    // Comprobamos si se ha podido obtener la posición
                    val globalPosition = positioningResult.detectedPositions.find { it.isGlobalPosition }
                    if (globalPosition != null) {
                        lastPosition.set(0, globalPosition.x)
                        lastPosition.set(1, globalPosition.y)
                        lastPosition.set(2, globalPosition.z)
                        lastPositionTimestamp = sampleTimestamp
                    }

                    //TODO obtener datos para estadísticas y volcarlso en un csv a parte, como en batcharucoprocessingviewmodel
                    //Reciclar esa lógica en un controller o en una librería y mejorar la escritura en csv evitando hacer el string a pelo
                }

                // Actualizamos la posición de la fila
                var outputRow = row.toMutableMap()
                outputRow["pos_x"] = lastPosition[0].toString()
                outputRow["pos_y"] = lastPosition[1].toString()
                outputRow["pos_z"] = lastPosition[2].toString()
                //Añadimos el nuevo dato usando una lista para segurar que se quede al final
                var listOutputRow = outputRow.values.toMutableList()
                listOutputRow.add(lastPositionTimestamp.toString())
                
                //Escribimos la salida
                outputStream.write(listOutputRow.joinToString(separator=",").toByteArray())

            }
        }
        inputStreamAllCsv.close()
        outputStream.close()
        
        currentStatus = "Reading __all.csv..."
        processingProgress = 0.1f
        
        // Simulación temporal para testing
        kotlinx.coroutines.delay(500)
        
        currentStatus = "Processing images..."
        processingProgress = 0.5f
        
        kotlinx.coroutines.delay(500)
        
        currentStatus = "Generating output CSV..."
        processingProgress = 0.9f
        
        kotlinx.coroutines.delay(500)
        
        processingProgress = 1.0f
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
