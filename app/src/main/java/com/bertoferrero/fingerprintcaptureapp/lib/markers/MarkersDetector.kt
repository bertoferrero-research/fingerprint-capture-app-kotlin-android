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
    private val distCoeffs: Mat,
    private val markerMaxAngle: Double? = null,
    private val markerMinPixelSize: Double? = null
) {

    /**
     * Aruco detector with improved corner detection accuracy and strict parameters
     */
    private val arucoDetector = run {
        MarkersDetector.constructArucoDetector(arucoDictionaryType)
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
                            
                            // Criterio 2: Calcular error de reproyección real
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

                    // Refinamiento de pose con Levenberg-Marquardt o VVS (ChatGPT recommendation)
                    // Mejora significativamente la precisión, especialmente a distancias > 5m
                    refinePose(markerId, cornerMatOfPoint2f, cameraMatrix, disctCoeffsMatOfDouble, rvecs, tvecs)

                    // Descarte tvec z en negativo (cheirality check)
                    if (tvecs[2, 0][0] < 0) {
                        continue
                    }

                    // Validación de ángulo de vista del marcador (ChatGPT recommendation)
                    if (markerMaxAngle != null && !validateMarkerViewingAngle(rvecs, tvecs, markerMaxAngle)) {
                        continue
                    }

                    // Validación de tamaño mínimo en píxeles (ChatGPT recommendation)
                    if (markerMinPixelSize != null && !validateMarkerPixelSize(cornerMatOfPoint2f, markerMinPixelSize)) {
                        continue
                    }

                    // Calcular la distancia del marcador para validaciones adicionales
                    val markerDistance = calculateMarkerDistance(tvecs)

                    // Check max distance if defined
                    val maxDistance = markerMaxDistanceMap[markerId]
                    if (maxDistance != null && markerDistance > maxDistance){
                        continue
                    }


                    returnData.add(
                        MarkersInFrame(
                            markerId,
                            cornerMatOfPoint2f,
                            rvecs,
                            tvecs,
                            markerDistance
                        )
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }


        return returnData
    }

    /**
     * Valida si el ángulo de vista del marcador es aceptable para una detección precisa.
     * Calcula el ángulo entre el eje Z del marcador (normal) y la dirección de vista de la cámara.
     * Marcadores con ángulos muy oblicuos (>75°) pueden producir estimaciones imprecisas.
     * 
     * @param rvecs Vector de rotación del marcador
     * @param tvecs Vector de traducción del marcador
     * @param maxAngleDegrees Ángulo máximo aceptable en grados (por defecto 75°)
     * @return true si el ángulo es válido, false si debe ser rechazado
     */
    private fun validateMarkerViewingAngle(
        rvecs: Mat, 
        tvecs: Mat, 
        maxAngleDegrees: Double = 75.0
    ): Boolean {
        try {
            // Crear el vector normal del marcador (eje Z apuntando hacia arriba)
            val markerNormal = Mat(3, 1, org.opencv.core.CvType.CV_64F)
            markerNormal.put(0, 0, 0.0, 0.0, 1.0) // Eje Z del marcador (normal)
            
            // Convertir el vector de rotación a matriz de rotación
            val r_marker_cam = Mat()
            org.opencv.calib3d.Calib3d.Rodrigues(rvecs, r_marker_cam)
            
            // Transformar el vector normal del marcador a coordenadas de cámara
            val markerNormalInCamera = Mat()
            org.opencv.core.Core.gemm(r_marker_cam, markerNormal, 1.0, Mat(), 0.0, markerNormalInCamera)
            
            // Obtener la dirección de vista normalizada (desde cámara hacia el marcador)
            val tvecArray = DoubleArray(3)
            tvecs.get(0, 0, tvecArray)
            val markerDistance = calculateMarkerDistance(tvecs)
            
            // Calcular el producto punto entre el normal del marcador y la dirección de vista
            val dotProduct = markerNormalInCamera[0, 0][0] * (tvecArray[0] / markerDistance) + 
                           markerNormalInCamera[1, 0][0] * (tvecArray[1] / markerDistance) + 
                           markerNormalInCamera[2, 0][0] * (tvecArray[2] / markerDistance)
            
            // Calcular el ángulo entre los vectores
            val angle = Math.toDegrees(Math.acos(Math.abs(dotProduct).coerceIn(-1.0, 1.0)))
            
            // Retornar true si el ángulo es aceptable
            return angle <= maxAngleDegrees
            
        } catch (e: Exception) {
            // En caso de error, ser conservador y rechazar el marcador
            android.util.Log.w("MarkersDetector", "Error validating marker viewing angle: ${e.message}")
            return false
        }
    }

    /**
     * Valida si el tamaño del marcador en píxeles es suficiente para una detección precisa.
     * Marcadores demasiado pequeños pueden producir estimaciones de pose imprecisas debido
     * a la falta de resolución en las esquinas detectadas.
     * 
     * @param cornerMatOfPoint2f Esquinas detectadas del marcador
     * @param minPixelSize Tamaño mínimo requerido en píxeles (por defecto 60px)
     * @return true si el tamaño es válido, false si debe ser rechazado
     */
    private fun validateMarkerPixelSize(
        cornerMatOfPoint2f: MatOfPoint2f, 
        minPixelSize: Double = 60.0
    ): Boolean {
        try {
            val cornerPoints = cornerMatOfPoint2f.toArray()
            
            // Verificar que tenemos al menos 4 esquinas
            if (cornerPoints.size < 4) {
                return false
            }
            
            // Calcular el ancho aproximado del marcador en píxeles
            // Usamos la distancia entre las dos primeras esquinas como referencia
            val markerWidthPx = kotlin.math.sqrt(
                (cornerPoints[1].x - cornerPoints[0].x).pow(2) + 
                (cornerPoints[1].y - cornerPoints[0].y).pow(2)
            )
            
            // Retornar true si el marcador es suficientemente grande
            return markerWidthPx >= minPixelSize
            
        } catch (e: Exception) {
            // En caso de error, ser conservador y rechazar el marcador
            android.util.Log.w("MarkersDetector", "Error validating marker pixel size: ${e.message}")
            return false
        }
    }

    /**
     * Calcula la distancia euclidiana del marcador desde la cámara.
     * 
     * @param tvecs Vector de traducción del marcador
     * @return Distancia en unidades de la calibración de cámara
     */
    private fun calculateMarkerDistance(tvecs: Mat): Double {
        val tvecArray = DoubleArray(3)
        tvecs.get(0, 0, tvecArray)
        return kotlin.math.sqrt(
            tvecArray[0] * tvecArray[0] + 
            tvecArray[1] * tvecArray[1] + 
            tvecArray[2] * tvecArray[2]
        )
    }

    /**
     * Refina la pose del marcador usando métodos de optimización iterativa.
     * Intenta primero Levenberg-Marquardt y luego VVS como alternativa.
     * Este refinamiento mejora significativamente la precisión, especialmente a distancias > 5m.
     * 
     * @param markerId ID del marcador para obtener sus puntos 3D
     * @param cornerMatOfPoint2f Esquinas detectadas del marcador en píxeles
     * @param cameraMatrix Matriz de cámara calibrada
     * @param distCoeffs Coeficientes de distorsión como MatOfDouble
     * @param rvecs Vector de rotación a refinar (entrada y salida)
     * @param tvecs Vector de traducción a refinar (entrada y salida)
     * @return true si el refinamiento fue exitoso, false si no estaba disponible
     */
    private fun refinePose(
        markerId: Int,
        cornerMatOfPoint2f: MatOfPoint2f,
        cameraMatrix: Mat,
        distCoeffs: MatOfDouble,
        rvecs: Mat,
        tvecs: Mat
    ): Boolean {
        return try {
            // Método preferido: Levenberg-Marquardt (más preciso)
            Calib3d.solvePnPRefineLM(
                getObjectPoints(markerId),
                cornerMatOfPoint2f,
                cameraMatrix,
                distCoeffs,
                rvecs,
                tvecs
            )
            true
        } catch (e: Exception) {
            // Método alternativo: VVS (Visible-Virtual-Shifted)
            try {
                Calib3d.solvePnPRefineVVS(
                    getObjectPoints(markerId),
                    cornerMatOfPoint2f,
                    cameraMatrix,
                    distCoeffs,
                    rvecs,
                    tvecs
                )
                true
            } catch (e2: Exception) {
                // Ambos métodos no disponibles
                android.util.Log.w("MarkersDetector", 
                    "Pose refinement not available, using original pose. LM: ${e.message}, VVS: ${e2.message}")
                false
            }
        }
    }

    companion object {
        
        /**
         * Factory method to create an ArucoDetector instance.
         * 
         * @param arucoDictionaryType Type of ArUco dictionary to use (default is DICT_6X6_250).
         * @return Configured ArucoDetector instance.
         */
        fun constructArucoDetector(
                arucoDictionaryType: Int = Objdetect.DICT_6X6_250,
        ): ArucoDetector {
            val detectorParams = DetectorParameters()
            try {
                // Activar refinamiento de esquinas subpíxel para mayor precisión
                detectorParams._cornerRefinementMethod = 1 // 1 = CORNER_REFINE_SUBPIX

                // Parámetros recomendados para evitar falsos positivos
                // Subir minMarkerPerimeterRate para evitar marcadores demasiado pequeños
                // minMarkerPerimeterRate: tamaño mínimo relativo del perímetro del marcador.
                //   -> evita analizar contornos demasiado pequeños (ruido o falsos marcadores).
                //   -> valor típico: 0.03–0.05
                detectorParams._minMarkerPerimeterRate = 0.05 // Default: 0.03, subir a 0.05-0.1

                // Subir minCornerDistanceRate para evitar esquinas demasiado cercanas
                // minCornerDistanceRate: distancia mínima entre las esquinas del marcador (relativa al tamaño del marcador).
                //   -> evita que se detecten esquinas demasiado juntas o solapadas (marcadores deformados o falsos).
                //   -> valor típico: 0.05
                detectorParams._minCornerDistanceRate = 0.08 // Default: 0.05, subir a 0.08-0.1

                // Parámetros adicionales para mejorar detección a distancia
                detectorParams._adaptiveThreshWinSizeMin = 3 // Default: 3
                detectorParams._adaptiveThreshWinSizeMax = 23 // Default: 23
                detectorParams._adaptiveThreshWinSizeStep = 10 // Default: 10

                // Mejoras para marcadores pequeños en imagen
                detectorParams._minMarkerDistanceRate = 0.125 // Default: 0.125 Separación mínima entre marcadores
                detectorParams._cornerRefinementWinSize = 5 // Default: 5 Ventana para refinamiento
                detectorParams._cornerRefinementMaxIterations = 30 // Default: 30 Iteraciones de refinamiento
            } catch (e: Exception) {
                // Si no están disponibles todos los parámetros, usar configuración por defecto
            }
            return ArucoDetector(
                    Objdetect.getPredefinedDictionary(arucoDictionaryType),
                    detectorParams
            )
        }
    }

}