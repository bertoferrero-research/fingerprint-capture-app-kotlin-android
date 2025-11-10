package com.bertoferrero.fingerprintcaptureapp.lib.positioning

import com.bertoferrero.fingerprintcaptureapp.lib.eulerAnglesToRotationMatrix
import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersInFrame
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.also
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * GlobalPositioner is a class that calculates the camera position in the world based on detected ArUco markers.
 * It uses the positions and orientations of the markers defined in the configuration.
 *
 * @param markersConfig List of marker definitions containing their positions and orientations in the world.
 */
class GlobalPositioner(
    val markersConfig: List<MarkerDefinition>,
) {

    /**
     * Get the camera position from the detected ArUco markers.
     * @param detectedMarkers List of detected markers in the current frame.
     * @param multipleMarkersBehaviour Behaviour when multiple markers are detected.
     * @return A Pair with the calculated position and the list of each detected marker
     */
    public fun getPositionFromArucoMarkers(
        detectedMarkers: List<MarkersInFrame>,
        multipleMarkersBehaviour: MultipleMarkersBehaviour = MultipleMarkersBehaviour.WEIGHTED_AVERAGE,
        closestMarkersUsed: Int = 0,
        ransacThreshold: Double = 0.2,
        ransacThresholdMax: Double? = null,
        ransacThresholdStep: Double = 0.1
        ): Pair<Position, List<PositionFromMarker>>? {

        var filteredMarkers = detectedMarkers

        // Get the closest marker to the camera if it is required
        if (multipleMarkersBehaviour == MultipleMarkersBehaviour.CLOSEST) {// Remove markers not in the config
            filteredMarkers = filteredMarkers.filter {
                markersConfig.any { marker ->
                    marker.id == it.markerId
                }
            }
            val closestMarker = filteredMarkers.minByOrNull { it.distance } ?: return null
            filteredMarkers = listOf(closestMarker)
        } else if (closestMarkersUsed > 0) {
            filteredMarkers = filteredMarkers.sortedBy { it.distance }.take(closestMarkersUsed)
        }

        // CALCULATE THE CAMERA POSITIONS
        val extractedPositions: MutableList<PositionFromMarker> = mutableListOf()
        for (detectedMarker in filteredMarkers) {
            //Detect the selected marker data
            var markerData = markersConfig.find { marker ->
                marker.id == detectedMarker.markerId
            }

            if (markerData != null) {
                //PROCESS OF CONVERTING THE CAMERA-MARKER INFO INTO CAMERA-WORLD

                // Validación de datos de entrada
                android.util.Log.d("GlobalPositioner", 
                    "Processing Marker ${markerData.id}: " +
                    "Config pos=(${markerData.position.x}, ${markerData.position.y}, ${markerData.position.z}), " +
                    "Config rot=(${markerData.rotation.roll}, ${markerData.rotation.pitch}, ${markerData.rotation.yaw}), " +
                    "Detected distance=${detectedMarker.distance}")

                //1 - RVEC to Rotation matrix
                val r_marker_cam = Mat()
                Calib3d.Rodrigues(detectedMarker.rvecs, r_marker_cam) // 3x3 rotation matrix

                //2 - Inverse the transformation (get the camera pose relative to the marker)
                //T_marker_cam = [R | t] → T_cam_marker = [Rᵗ | -Rᵗ * t]
                val r_cam_marker = r_marker_cam.t() // Transpuesta = inversa para matriz ortonormal
                val t_cam_marker = Mat()
                Core.gemm(
                    r_cam_marker,
                    detectedMarker.tvecs,
                    -1.0,
                    Mat(),
                    0.0,
                    t_cam_marker
                ) // t' = -Rᵗ * t
                

                //3 - Marker pose in the world
                val r_marker_world: Mat? = eulerAnglesToRotationMatrix(
                    Math.toRadians(markerData.rotation.roll.toDouble()),
                    Math.toRadians(markerData.rotation.pitch.toDouble()),
                    Math.toRadians(markerData.rotation.yaw.toDouble())
                )
                
                // Verificar que la matriz de rotación sea válida
                if (r_marker_world == null) {
                    android.util.Log.e("GlobalPositioner", "Failed to create rotation matrix for marker ${markerData.id}")
                    continue
                }
                
                val t_marker_world = Mat(3, 1, CvType.CV_64F)
                t_marker_world.put(
                    0,
                    0,
                    markerData.position.x.toDouble(),
                    markerData.position.y.toDouble(),
                    markerData.position.z.toDouble()
                )

                //4 - Transform camera position to world coordinates
                // t_cam_world = r_marker_world * t_cam_marker + t_marker_world
                val t_temp = Mat()
                Core.gemm(r_marker_world, t_cam_marker, 1.0, Mat(), 0.0, t_temp)
                val t_cam_world = Mat()
                Core.add(t_temp, t_marker_world, t_cam_world)
                
                // Debug: Log valores intermedios para detectar problemas
                val t_cam_marker_array = DoubleArray(3)
                t_cam_marker.get(0, 0, t_cam_marker_array)
                val t_temp_array = DoubleArray(3)
                t_temp.get(0, 0, t_temp_array)
                android.util.Log.d("GlobalPositioner", 
                    "Marker ${markerData.id} intermediate values: " +
                    "t_cam_marker=(${t_cam_marker_array[0]}, ${t_cam_marker_array[1]}, ${t_cam_marker_array[2]}), " +
                    "r_marker_world*t_cam_marker=(${t_temp_array[0]}, ${t_temp_array[1]}, ${t_temp_array[2]})")

                //5 - Extract the camera position
                val posArray = DoubleArray(3)
                t_cam_world.get(0, 0, posArray)

                //avoid negative Z
                if (posArray[2] < 0) {
                    android.util.Log.w("GlobalPositioner", "Discarding marker ${markerData.id} due to negative Z position: ${posArray[2]}")
                    continue
                }

                // Validación y log para debugging de coordenadas
                android.util.Log.d("GlobalPositioner", 
                    "Marker ${markerData.id}: Raw position = (${posArray[0]}, ${posArray[1]}, ${posArray[2]}), Distance = ${detectedMarker.distance}")

                // Retain the extracted position
                extractedPositions.add(
                    PositionFromMarker(
                        markerId = markerData.id,
                        x = posArray[0],
                        y = posArray[1],
                        z = posArray[2],
                        distance = detectedMarker.distance,
                        sourceIdentifier = detectedMarker.sourceIdentifier
                    )
                )
            }
        }


        //Filter data
        var ransacThresholdValue = ransacThreshold
        var returnData: Position?
        do {
            returnData = filterPositionList(extractedPositions, multipleMarkersBehaviour, ransacThresholdValue)
            ransacThresholdValue += ransacThresholdStep
        }while(returnData == null && ransacThresholdMax != null && ransacThresholdValue <= ransacThresholdMax)

        if (returnData == null) {
            return null
        }

        return Pair(returnData, extractedPositions)
    }

    fun filterPositionList(positions: List<PositionFromMarker>, arithmeticFilteringMode: MultipleMarkersBehaviour, ransacThreshold: Double = 0.2): Position?{
        //Prepare return data
        var returnData: Position?

        // -- RANSAC FILTERING
        val filteredPositions = ransacFilterPositions(positions, ransacThreshold)


        // -- ARITHMETIC FILTERING

        // Calculate weighted average of the positions
        if (arithmeticFilteringMode == MultipleMarkersBehaviour.WEIGHTED_AVERAGE || arithmeticFilteringMode == MultipleMarkersBehaviour.AVERAGE) {
            returnData = calculateAveragePosition(
                filteredPositions,
                arithmeticFilteringMode == MultipleMarkersBehaviour.WEIGHTED_AVERAGE
            )
        }
        // Calculate median of the positions
        else if (arithmeticFilteringMode == MultipleMarkersBehaviour.WEIGHTED_MEDIAN || arithmeticFilteringMode == MultipleMarkersBehaviour.MEDIAN) {
            returnData = calculateMedianPosition(
                filteredPositions,
                arithmeticFilteringMode == MultipleMarkersBehaviour.WEIGHTED_MEDIAN
            )
        } else {
            // Return the closest position
            returnData = filteredPositions.firstOrNull()
        }

        return returnData
    }


}



enum class MultipleMarkersBehaviour {
    CLOSEST,
    WEIGHTED_AVERAGE,
    AVERAGE,
    WEIGHTED_MEDIAN,
    MEDIAN
}