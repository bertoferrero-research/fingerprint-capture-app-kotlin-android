package com.bertoferrero.fingerprintcaptureapp.lib.markers

import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

// Nota: El cálculo de error de reproyección ahora se maneja en MarkersDetector.kt

/**
 * Detects markers in the input frame and returns the detected markers with their pose and distance.
 *
 * Esta función ahora utiliza internamente MarkersDetector para evitar duplicación de código.
 * Se mantiene para compatibilidad hacia atrás, pero se recomienda usar MarkersDetector directamente.
 *
 * Al no conocer de antemano los IDs de marcador esperados, se apoya en el modo "descubrimiento"
 * de MarkersDetector (lista de definiciones vacía + defaultMarkerSize) para aceptar cualquier ID
 * detectado usando `markerSize` como tamaño — así se detecta una única vez en vez de hacer una
 * primera pasada solo para averiguar qué IDs hay en el frame.
 *
 * @deprecated use MarkersDetector - Esta función es un wrapper que delega a MarkersDetector
 * @param inputFrame The input frame.
 * @param markerSize The size of the marker.
 * @param arucoDictionaryType Type of ArUco dictionary to use for detection.
 * @param cameraMatrix The camera matrix.
 * @param distCoeffs The distortion coefficients.
 * @param outputCorners Mutable list to store the detected corners (optional).
 * @param outputIds Mat to store the detected IDs (optional).
 * @param detectionProfile Perfil de detección/pose a aplicar. Ver [DetectionProfile].
 * @return A mutable list of MarkersInFrame objects containing the information of the detected markers.
 */
fun detectMarkers(
    inputFrame: CameraBridgeViewBase.CvCameraViewFrame,
    markerSize: Float,
    arucoDictionaryType: Int,
    cameraMatrix: Mat,
    distCoeffs: Mat,
    outputCorners: MutableList<Mat>? = null,
    outputIds: Mat? = null,
    detectionProfile: DetectionProfile = DetectionProfile.OPTIMIZED,
): MutableList<MarkersInFrame> {
    val markersDetector = MarkersDetector(
        markerDefinition = emptyList(),
        arucoDictionaryType = arucoDictionaryType,
        cameraMatrix = cameraMatrix,
        distCoeffs = distCoeffs,
        detectionProfile = detectionProfile,
        defaultMarkerSize = markerSize
    )

    return markersDetector.detectMarkers(
        inputFrame = inputFrame,
        outputCorners = outputCorners,
        outputIds = outputIds
    )
}
