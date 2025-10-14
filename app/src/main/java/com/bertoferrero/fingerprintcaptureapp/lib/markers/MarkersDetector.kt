package com.bertoferrero.fingerprintcaptureapp.lib.markers

import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import org.opencv.android.CameraBridgeViewBase
import org.opencv.calib3d.Calib3d
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detects markers in the input frame and returns the detected markers with their distance.
 * This class employs the focal length of the camera to estimate the distance to the markers instead of using the camera matrix and distortion coefficients.
 */
class MarkersDetector(
    var markerDefinition: List<MarkerDefinition>,
    private val arucoDictionaryType: Int = Objdetect.DICT_6X6_250,
    private val cameraMatrix: Mat,
    private val distCoeffs: Mat
) {

    /**
     * Aruco detector with improved corner detection accuracy
     */
    private val arucoDetector = run {
        val detectorParams = DetectorParameters()
        try {
            // Intentar activar refinamiento de esquinas subpíxel para mayor precisión
            // En OpenCV para Android, esto puede mejorar la precisión de detección
            detectorParams._cornerRefinementMethod = 1 // 1 = CORNER_REFINE_SUBPIX
        } catch (e: Exception) {
            // Si no está disponible, usar configuración por defecto
        }
        ArucoDetector(
            Objdetect.getPredefinedDictionary(arucoDictionaryType),
            detectorParams
        )
    }

    /**
     * Marker size map indexed by marker ID.
     */
    private val markerSizeMap = markerDefinition.associateBy({ it.id }, { it.size })

    /**
     * Marker max distance map indexed by marker ID.
     */
    private val markerMaxDistanceMap = markerDefinition.associateBy({ it.id }, { it.max_distance })

    /**
     * Markers ID to be detected.
     */
    private val markersId = markerDefinition.map { it.id }

    /**
     * Object points for the markers, calculated based on the marker size.
     */
    private fun getObjectPoints( markerId: Int): MatOfPoint3f {
        val size = markerSizeMap[markerId] ?: throw IllegalArgumentException("Marker ID $markerId not found in marker definitions.")
        return MatOfPoint3f(
            Point3(-(size / 2.0), (size / 2.0), 0.0),
            Point3((size / 2.0), (size / 2.0), 0.0),
            Point3((size / 2.0), -(size / 2.0), 0.0),
            Point3(-(size / 2.0), -(size / 2.0), 0.0)
        )
    }

    /**
     * Calcula el error de reproyección manualmente para una pose dada.
     * Basado en el método de ChatGPT - más preciso que confiar en solvePnPGeneric.
     * 
     * @param objectPoints Puntos 3D del marcador en coordenadas del objeto
     * @param imagePoints Puntos 2D detectados en la imagen
     * @param rvec Vector de rotación de la pose
     * @param tvec Vector de translación de la pose
     * @param cameraMatrix Matriz de cámara
     * @param distCoeffs Coeficientes de distorsión (como MatOfDouble)
     * @return Error RMS de reproyección en píxeles
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
            // Proyectar los puntos 3D a 2D usando la pose estimada
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
            
            // Retornar error RMS (Root Mean Square)
            return sqrt(sumSquaredErrors / projected.size)
            
        } catch (e: Exception) {
            e.printStackTrace()
            return Double.MAX_VALUE
        }
    }

    /**
     * Detects markers in the input frame and returns the detected markers with their distance.
     * MarkersInFrame objects will not contain the pose information as the camera matrix and distortion coefficients are not used here.
     */
    fun detectMarkers(
        inputFrame: CameraBridgeViewBase.CvCameraViewFrame,
        outputCorners: MutableList<Mat>? = null,
        outputIds: Mat? = null,
    ): MutableList<MarkersInFrame> {
        //Prepare return
        val returnData: MutableList<MarkersInFrame> = mutableListOf()

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
                var markerId = ids[i, 0][0].toInt()
                if (!markersId.contains(markerId)) {
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
                            getObjectPoints(markerId),
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
                            getObjectPoints(markerId),
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
                                getObjectPoints(markerId),
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

                    // Check max distance if defined
                    val maxDistance = markerMaxDistanceMap[markerId]
                    if (maxDistance != null && distance > maxDistance){
                        continue
                    }


                    returnData.add(
                        MarkersInFrame(
                            markerId,
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

}