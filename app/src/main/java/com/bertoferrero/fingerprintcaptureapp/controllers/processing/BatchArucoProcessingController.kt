package com.bertoferrero.fingerprintcaptureapp.controllers.processing

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersDetector
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.CvCameraViewFrameMockFromImage
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatFromFile
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.GlobalPositioner
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import org.opencv.core.Mat
import java.io.InputStream
import kotlin.collections.mutableListOf
import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersInFrame

/**
 * Controlador para procesamiento por lotes de imágenes ArUco.
 * Encapsula la lógica de detección de marcadores y cálculo de posiciones
 * para procesamiento de múltiples imágenes.
 */
class BatchArucoProcessingController(
    private val arucoDictionaryType: ArucoDictionaryType,
    private val markersDefinition: List<MarkerDefinition>,
    private val multipleMarkersBehaviour: MultipleMarkersBehaviour = MultipleMarkersBehaviour.WEIGHTED_MEDIAN
) {
    
    // Componentes de detección y posicionamiento
    private var markersDetector: MarkersDetector? = null
    private var globalPositioner: GlobalPositioner? = null
    
    // Parámetros de calibración de cámara
    private var cameraMatrix: Mat = Mat()
    private var distCoeffs: Mat = Mat()
    private var isCalibrationLoaded: Boolean = false
    
    // Parámetros RANSAC
    var ransacMinThreshold: Double = 0.2
    var ransacMaxThreshold: Double = 0.4
    var ransacStep: Double = 0.1
    
    init {
        loadCameraCalibrationParameters()
        initializeComponents()
    }
    
    /**
     * Carga los parámetros de calibración de la cámara.
     */
    private fun loadCameraCalibrationParameters() {
        try {
            val calibrationParameters = CameraCalibrationParameters.loadParameters()
            cameraMatrix = calibrationParameters.cameraMatrix
            distCoeffs = calibrationParameters.distCoeffs
            isCalibrationLoaded = true
        } catch (e: Exception) {
            // Usar matrices vacías si no hay calibración
            cameraMatrix = Mat()
            distCoeffs = Mat()
            isCalibrationLoaded = false
        }
    }
    
    /**
     * Inicializa los componentes de detección y posicionamiento.
     */
    private fun initializeComponents() {
        markersDetector = MarkersDetector(
            markersDefinition,
            arucoDictionaryType.value,
            cameraMatrix,
            distCoeffs
        )
        
        globalPositioner = GlobalPositioner(markersDefinition)
    }
    
    
    /**
     * Procesa una sola imagen desde DocumentFile - método de compatibilidad.
     */
    fun processImageFile(context: Context, imageFile: DocumentFile): BatchProcessingResult {
        return processImageFiles(context, listOf(imageFile))
    }

    
    /**
     * Procesa múltiples imágenes desde DocumentFiles - función directora principal.
     * Unifica todos los marcadores detectados en las imágenes para mejorar la precisión del posicionamiento.
     */
    fun processImageFiles(context: Context, imageFiles: List<DocumentFile>): BatchProcessingResult {
        // 1. Procesar cada imagen y recolectar marcadores
        val (processedImages, markersPool) = processIndividualImages(context, imageFiles)
        
        // 2. Verificar si tenemos marcadores para procesar
        if (markersPool.isEmpty()) {
            return BatchProcessingResult(
                success = false,
                error = "No ArUco markers detected in any of the processed images",
                detectedPositions = emptyList(),
                processedImages = processedImages
            )
        }
        
        // 3. Calcular posiciones globales usando todos los marcadores detectados
        var globalError: String? = null
        val positionResult = try {
            globalPositioner?.getPositionFromArucoMarkers(
                detectedMarkers = markersPool,
                multipleMarkersBehaviour = multipleMarkersBehaviour,
                closestMarkersUsed = 0, // Usar todos los marcadores detectados
                ransacThreshold = ransacMinThreshold,
                ransacThresholdMax = if (ransacMaxThreshold > ransacMinThreshold) ransacMaxThreshold else null,
                ransacThresholdStep = ransacStep
            )
        } catch (e: Exception) {
            globalError = "Error during position calculation: ${e.message}"
            null
        }
        
        // 4. Componer resultado final
        val detectedPositions = if (globalError == null) {
            composeDetectedPositions(positionResult)
        } else {
            mutableListOf()
        }
        
        return BatchProcessingResult(
            success = globalError == null,
            error = globalError,
            detectedPositions = detectedPositions,
            processedImages = processedImages
        )
    }
    
    /**
     * Procesa cada imagen individual y recolecta marcadores detectados.
     * Retorna una pareja con la información de procesamiento y el pool de marcadores.
     */
    private fun processIndividualImages(
        context: Context, 
        imageFiles: List<DocumentFile>
    ): Pair<List<ImageProcessingInfo>, List<MarkersInFrame>> {
        val processedImages = mutableListOf<ImageProcessingInfo>()
        var markersPool: List<MarkersInFrame> = mutableListOf()
        
        for (imageFile in imageFiles) {
            val fileName = imageFile.name ?: "unknown"
            
            try {
                // Solo soportar archivos MatPhoto
                if (!fileName.endsWith(".matphoto", ignoreCase = true)) {
                    processedImages.add(
                        ImageProcessingInfo(
                            fileName = fileName,
                            success = false,
                            error = "Unsupported file format (only .matphoto supported)",
                            markerCount = 0
                        )
                    )
                    continue
                }
                
                context.contentResolver.openInputStream(imageFile.uri)?.use { inputStream ->
                    // Cargar el Mat desde el archivo
                    val mat = MatFromFile(inputStream)
                    
                    if (mat.empty()) {
                        processedImages.add(
                            ImageProcessingInfo(
                                fileName = fileName,
                                success = false,
                                error = "Empty or corrupted image",
                                markerCount = 0
                            )
                        )
                        return@use
                    }
                    
                    // Detectar marcadores ArUco
                    val detectedMarkers = markersDetector!!.detectMarkersFromMat(
                        inputMat = mat,
                        sourceIdentifier = fileName
                    )
                    
                    // Añadir marcadores al pool global
                    markersPool = markersPool.plus(detectedMarkers)
                    
                    // Registrar información de procesamiento de esta imagen
                    processedImages.add(
                        ImageProcessingInfo(
                            fileName = fileName,
                            success = true,
                            error = null,
                            markerCount = detectedMarkers.size
                        )
                    )
                    
                    // Liberar memoria del Mat
                    mat.release()
                    
                } ?: run {
                    processedImages.add(
                        ImageProcessingInfo(
                            fileName = fileName,
                            success = false,
                            error = "Cannot open file stream",
                            markerCount = 0
                        )
                    )
                }
                
            } catch (e: Exception) {
                processedImages.add(
                    ImageProcessingInfo(
                        fileName = fileName,
                        success = false,
                        error = e.message ?: "Unknown error during processing",
                        markerCount = 0
                    )
                )
            }
        }
        
        return Pair(processedImages, markersPool)
    }
    
    
    /**
     * Compone la lista final de DetectedMarkerPosition.
     */
    private fun composeDetectedPositions(
        positionResult: Pair<com.bertoferrero.fingerprintcaptureapp.lib.positioning.Position, List<com.bertoferrero.fingerprintcaptureapp.lib.positioning.Position>>?
    ): MutableList<DetectedMarkerPosition> {
        val positions = mutableListOf<DetectedMarkerPosition>()
        
        positionResult?.let { (globalPosition, markerPositions) ->
            // Calcular número de marcadores detectados
            val markerCount = markerPositions.size
            
            // Agregar la posición global calculada
            if (globalPosition.x.isFinite() && globalPosition.y.isFinite() && globalPosition.z.isFinite()) {
                positions.add(
                    DetectedMarkerPosition(
                        markerId = -1, // ID especial para posición global
                        x = globalPosition.x,
                        y = globalPosition.y,
                        z = globalPosition.z,
                        ransacThreshold = ransacMinThreshold, // Threshold inicial usado
                        isGlobalPosition = true,
                        markerCount = markerCount,
                        sourceIdentifier = null
                    )
                )
            }
            
            // Agregar posiciones individuales de cada marcador
            for (markerPos in markerPositions) {
                if (markerPos.x.isFinite() && markerPos.y.isFinite() && markerPos.z.isFinite()) {
                    // Convertir a PositionFromMarker para acceder al markerId
                    val positionFromMarker = markerPos as? com.bertoferrero.fingerprintcaptureapp.lib.positioning.PositionFromMarker
                    val markerId = positionFromMarker?.markerId ?: 0
                    
                    positions.add(
                        DetectedMarkerPosition(
                            markerId = markerId,
                            x = markerPos.x,
                            y = markerPos.y,
                            z = markerPos.z,
                            ransacThreshold = ransacMinThreshold, // Threshold inicial usado
                            isGlobalPosition = false,
                            markerCount = 1,
                            sourceIdentifier = positionFromMarker?.sourceIdentifier
                        )
                    )
                }
            }
        }
        
        return positions
    }


    

    
    /**
     * Actualiza los parámetros RANSAC.
     */
    fun updateRansacParameters(min: Double, max: Double, step: Double) {
        ransacMinThreshold = min
        ransacMaxThreshold = max  
        ransacStep = step
    }
    
    /**
     * Verifica si la calibración de cámara está cargada.
     */
    fun isCalibrationParametersLoaded(): Boolean = isCalibrationLoaded
    
    /**
     * Clase de datos para el resultado del procesamiento de múltiples archivos.
     */
    data class BatchProcessingResult(
        val success: Boolean,
        val error: String? = null,
        val detectedPositions: List<DetectedMarkerPosition>,
        val processedImages: List<ImageProcessingInfo>
    )
    
    /**
     * Información del procesamiento de una imagen individual.
     */
    data class ImageProcessingInfo(
        val fileName: String,
        val success: Boolean,
        val error: String? = null,
        val markerCount: Int = 0
    )
    
    /**
     * Clase de datos para una posición de marcador detectada.
     */
    data class DetectedMarkerPosition(
        val markerId: Int,
        val x: Double,
        val y: Double,
        val z: Double,
        val ransacThreshold: Double,
        val isGlobalPosition: Boolean = false,
        val markerCount: Int = 1,
        val sourceIdentifier: String? = null
    )
}