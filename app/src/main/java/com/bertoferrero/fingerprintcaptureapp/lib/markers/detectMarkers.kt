package com.bertoferrero.fingerprintcaptureapp.lib.markers

import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.objdetect.ArucoDetector
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition

// Nota: El cálculo de error de reproyección ahora se maneja en MarkersDetector.kt

/**
 * Detects markers in the input frame and returns the detected markers with their pose and distance.
 * 
 * Esta función ahora utiliza internamente MarkersDetector para evitar duplicación de código.
 * Se mantiene para compatibilidad hacia atrás, pero se recomienda usar MarkersDetector directamente.
 *
 * @deprecated use MarkersDetector - Esta función es un wrapper que delega a MarkersDetector
 * @param inputFrame The input frame.
 * @param markerSize The size of the marker.
 * @param arucoDetector The ArucoDetector object.
 * @param cameraMatrix The camera matrix.
 * @param distCoeffs The distortion coefficients.
 * @param outputCorners Mutable list to store the detected corners (optional).
 * @param outputIds Mat to store the detected IDs (optional).
 * @return A mutable list of MarkersInFrame objects containing the information of the detected markers.
 */
fun detectMarkers(
    inputFrame: CameraBridgeViewBase.CvCameraViewFrame,
    markerSize: Float,
    arucoDetector: ArucoDetector,
    cameraMatrix: Mat,
    distCoeffs: Mat,
    outputCorners: MutableList<Mat>? = null,
    outputIds: Mat? = null,
): MutableList<MarkersInFrame> {
    // Crear una definición de marcador temporal para el marcador único que estamos detectando
    // Como esta función legacy no especifica IDs de marcadores, asumimos cualquier ID detectado
    // y usamos el tamaño proporcionado
    val markerDefinitions = mutableListOf<MarkerDefinition>()
    
    // Preparar el frame
    val gray = inputFrame.gray()
    
    // Detectar marcadores temporalmente para obtener los IDs
    val tempCorners: MutableList<Mat> = mutableListOf()
    val tempIds: Mat = Mat()
    arucoDetector.detectMarkers(gray, tempCorners, tempIds)
    
    // Crear definiciones de marcadores para cada ID detectado
    if (tempCorners.size > 0 && tempCorners.size.toLong() == tempIds.total()) {
        for (i in 0 until tempCorners.size) {
            val markerId = tempIds[i, 0][0].toInt()
            
            // Crear una definición temporal para este marcador usando el constructor legacy
            val markerDef = MarkerDefinition(
                id = markerId,
                x = 0.0f, // Posición temporal (no se usa para esta función legacy)
                y = 0.0f,
                z = 0.0f,
                size = markerSize
            )
            
            markerDefinitions.add(markerDef)
        }
    }
    
    // Si no se detectaron marcadores, retornar lista vacía
    if (markerDefinitions.isEmpty()) {
        return mutableListOf()
    }
    
    // Crear instancia de MarkersDetector con las definiciones temporales
    val markersDetector = MarkersDetector(
        markerDefinition = markerDefinitions,
        cameraMatrix = cameraMatrix,
        distCoeffs = distCoeffs
    )
    
    // Usar MarkersDetector para realizar la detección completa con todas las optimizaciones
    // Nota: MarkersDetector crea su propio ArucoDetector internamente con parámetros optimizados
    return markersDetector.detectMarkers(
        inputFrame = inputFrame,
        outputCorners = outputCorners,
        outputIds = outputIds
    )
}