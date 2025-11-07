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
     * Procesa una imagen desde DocumentFile - función directora principal.
     */
    fun processImageFile(context: Context, imageFile: DocumentFile): BatchProcessingResult {
        val fileName = imageFile.name ?: "unknown"
        
        return try {
            // Solo soportar archivos MatPhoto
            if (!fileName.endsWith(".matphoto", ignoreCase = true)) {
                return BatchProcessingResult(
                    fileName = fileName,
                    success = false,
                    error = "Only MatPhoto files (.matphoto) are supported",
                    detectedPositions = emptyList()
                )
            }
            
            context.contentResolver.openInputStream(imageFile.uri)?.use { inputStream ->
                // 1. Cargar el Mat desde el archivo
                val mat = MatFromFile(inputStream)
                
                // 2. Obtener las posiciones de los marcadores ArUco
                val positionResult = getPositionsFromAruco(mat)
                
                // 3. Componer el resultado final
                val detectedPositions = composeDetectedPositions(positionResult, mat)
                
                BatchProcessingResult(
                    fileName = fileName,
                    success = true,
                    error = null,
                    detectedPositions = detectedPositions
                )
                
            } ?: BatchProcessingResult(
                fileName = fileName,
                success = false,
                error = "Cannot open file stream",
                detectedPositions = emptyList()
            )
        } catch (e: Exception) {
            BatchProcessingResult(
                fileName = fileName,
                success = false,
                error = e.message ?: "Unknown error",
                detectedPositions = emptyList()
            )
        }
    }
    
    /**
     * Obtiene las posiciones de marcadores ArUco desde una imagen Mat.
     * Retorna el resultado del GlobalPositioner.getPositionFromArucoMarkers.
     */
    private fun getPositionsFromAruco(mat: Mat): Pair<com.bertoferrero.fingerprintcaptureapp.lib.positioning.Position, List<com.bertoferrero.fingerprintcaptureapp.lib.positioning.Position>>? {
        if (mat.empty()) {
            return null
        }
        
        // Crear mock frame para el detector
        val mockFrame = CvCameraViewFrameMockFromImage(mat)
        
        // Detectar marcadores en la imagen
        val detectedMarkers = markersDetector?.detectMarkers(mockFrame) ?: emptyList()
        
        if (detectedMarkers.isEmpty()) {
            return null
        }
        
        // Calcular posición global usando los marcadores detectados
        return globalPositioner?.getPositionFromArucoMarkers(
            detectedMarkers = detectedMarkers,
            multipleMarkersBehaviour = multipleMarkersBehaviour,
            closestMarkersUsed = 0, // Usar todos los marcadores detectados
            ransacThreshold = ransacMinThreshold,
            ransacThresholdMax = if (ransacMaxThreshold > ransacMinThreshold) ransacMaxThreshold else null,
            ransacThresholdStep = ransacStep
        )
    }
    
    /**
     * Compone la lista final de DetectedMarkerPosition a partir del resultado de getPositionsFromAruco.
     */
    private fun composeDetectedPositions(
        positionResult: Pair<com.bertoferrero.fingerprintcaptureapp.lib.positioning.Position, List<com.bertoferrero.fingerprintcaptureapp.lib.positioning.Position>>?,
        mat: Mat
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
                        markerCount = markerCount
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
                            markerCount = 1
                        )
                    )
                }
            }
        }
        
        // Liberar memoria del Mat
        mat.release()
        
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
     * Clase de datos para el resultado del procesamiento de un archivo.
     */
    data class BatchProcessingResult(
        val fileName: String,
        val success: Boolean,
        val error: String? = null,
        val detectedPositions: List<DetectedMarkerPosition>
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
        val markerCount: Int = 1
    )
}