package com.bertoferrero.fingerprintcaptureapp.lib.markers

import org.opencv.android.CameraBridgeViewBase
import org.opencv.calib3d.Calib3d
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.objdetect.ArucoDetector
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * Calcula el error de reproyección manualmente para una pose dada.
 * Método de ChatGPT - más preciso que confiar en solvePnPGeneric.
 */
private fun calculateReprojectionError(
    objectPoints: MatOfPoint3f,
    imagePoints: MatOfPoint2f,
    rvec: Mat,
    tvec: Mat,
    cameraMatrix: Mat,
    distCoeffs: MatOfDouble
): Double {
    val projectedPoints = MatOfPoint2f()
    
    try {
        Calib3d.projectPoints(objectPoints, rvec, tvec, cameraMatrix, distCoeffs, projectedPoints)
        
        val projected = projectedPoints.toArray()
        val detected = imagePoints.toArray()
        
        if (projected.size != detected.size) {
            return Double.MAX_VALUE
        }
        
        var sumSquaredErrors = 0.0
        for (i in projected.indices) {
            val dx = projected[i].x - detected[i].x
            val dy = projected[i].y - detected[i].y
            sumSquaredErrors += dx * dx + dy * dy
        }
        
        return sqrt(sumSquaredErrors / projected.size)
        
    } catch (e: Exception) {
        e.printStackTrace()
        return Double.MAX_VALUE
    }
}

/**
 * Detects markers in the input frame and returns the detected markers with their pose and distance.
 *
 * @deprecated use MarkersDetector
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
    //Prepare return
    val returnData: MutableList<MarkersInFrame> = mutableListOf()

    //Create objectPoints
    val halfMarkerSize = markerSize / 2.0
    val objectPoints = MatOfPoint3f(
        Point3(-halfMarkerSize, halfMarkerSize, 0.0),
        Point3(halfMarkerSize, halfMarkerSize, 0.0),
        Point3(halfMarkerSize, -halfMarkerSize, 0.0),
        Point3(-halfMarkerSize, -halfMarkerSize, 0.0)
    )
    //Prepare the distCoeffs in the proper format
    var disctCoeffsMatOfDouble = MatOfDouble()
    try {
        disctCoeffsMatOfDouble = MatOfDouble(distCoeffs)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Prepare the input frame
    val gray = inputFrame.gray()

    // Detect markers
    val corners: MutableList<Mat> = mutableListOf()
    val ids: Mat = Mat()
    arucoDetector.detectMarkers(gray, corners, ids)

    // Copy detected corners and ids to output parameters if they are not null
    outputCorners?.clear()
    outputCorners?.addAll(corners)
    outputIds?.release()
    ids.copyTo(outputIds)

    // Process the detected markers
    if (corners.size > 0 && corners.size.toLong() == ids.total()) {
        for (i in 0 until corners.size) {
            if (corners[i].total() < 4) { //Check here if the corners are enough to estimate the pose, if not we get an exception from solvePnP
                continue
            }

            val rvecs = Mat()
            val tvecs = Mat()

            try {

                val cornerMatOfPoint2f = MatOfPoint2f(corners[i].reshape(2, 4))


                // Estimate the pose usando solvePnPGeneric para obtener ambas soluciones posibles
                val rvecsList = mutableListOf<Mat>()
                val tvecsList = mutableListOf<Mat>()
                val reprojectionErrors = Mat()
                
                val solutionsCount = try {
                    // Intentar usar solvePnPGeneric para obtener todas las soluciones posibles
                    Calib3d.solvePnPGeneric(
                        objectPoints,
                        cornerMatOfPoint2f,
                        cameraMatrix,
                        disctCoeffsMatOfDouble,
                        rvecsList,
                        tvecsList,
                        false,
                        Calib3d.SOLVEPNP_IPPE_SQUARE,
                        rvecs,
                        tvecs,
                        reprojectionErrors
                    )
                } catch (e: Exception) {
                    // Si solvePnPGeneric no está disponible, usar solvePnP tradicional
                    Calib3d.solvePnP(
                        objectPoints,
                        cornerMatOfPoint2f,
                        cameraMatrix,
                        disctCoeffsMatOfDouble,
                        rvecs,
                        tvecs,
                        false,
                        Calib3d.SOLVEPNP_IPPE_SQUARE
                    )
                    1 // Una sola solución
                }
                
                // Si hay múltiples soluciones, elegir la mejor basada en error de reproyección real
                if (solutionsCount > 1 && rvecsList.isNotEmpty() && tvecsList.isNotEmpty()) {
                    var bestSolutionIndex = -1
                    var bestReprojectionError = Double.MAX_VALUE
                    
                    for (j in 0 until minOf(solutionsCount, rvecsList.size, tvecsList.size)) {
                        val currentRvec = rvecsList[j]
                        val currentTvec = tvecsList[j]
                        
                        // Criterio 1: Descartar soluciones con Z negativo (detrás de la cámara)
                        if (currentTvec[2, 0][0] < 0) {
                            continue
                        }
                        
                        // Criterio 2: Calcular error de reproyección real usando el método de ChatGPT
                        val reprojectionError = calculateReprojectionError(
                            objectPoints,
                            cornerMatOfPoint2f,
                            currentRvec,
                            currentTvec,
                            cameraMatrix,
                            disctCoeffsMatOfDouble
                        )
                        
                        // Seleccionar la solución con menor error de reproyección
                        if (reprojectionError < bestReprojectionError) {
                            bestReprojectionError = reprojectionError
                            bestSolutionIndex = j
                        }
                    }
                    
                    // Usar la mejor solución encontrada (si hay una válida)
                    if (bestSolutionIndex >= 0 && bestSolutionIndex < rvecsList.size && bestSolutionIndex < tvecsList.size) {
                        rvecsList[bestSolutionIndex].copyTo(rvecs)
                        tvecsList[bestSolutionIndex].copyTo(tvecs)
                    } else {
                        // Si no hay solución válida, descartar este marcador
                        continue
                    }
                }

                // Descarte tvec z en negativo
                if (tvecs[2, 0][0] < 0) {
                    continue
                }

                // Calculate the distance
                val distance = sqrt(
                    (tvecs[0, 0][0].pow(2) + tvecs[1, 0][0].pow(2) + tvecs[2, 0][0].pow(2))
                )

                returnData.add(
                    MarkersInFrame(
                        ids[i, 0][0].toInt(),
                        cornerMatOfPoint2f,
                        rvecs,
                        tvecs,
                        distance
                    )
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    return returnData
}
