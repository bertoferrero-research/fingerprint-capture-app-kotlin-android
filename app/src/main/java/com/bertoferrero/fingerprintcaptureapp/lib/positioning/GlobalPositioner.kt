package com.bertoferrero.fingerprintcaptureapp.lib.positioning

import com.bertoferrero.fingerprintcaptureapp.lib.eulerAnglesToRotationMatrix
import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersInFrame
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

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
        ransacThreshold: Double = 0.2
        ): Pair<Position, List<PositionFromMarker>>? {

        var detectedMarkers = detectedMarkers

        // Get the closest marker to the camera if it is required
        if (multipleMarkersBehaviour == MultipleMarkersBehaviour.CLOSEST) {// Remove markers not in the config
            detectedMarkers = detectedMarkers.filter {
                markersConfig.any { marker ->
                    marker.id == it.markerId
                }
            }
            val closestMarker = detectedMarkers.minByOrNull { it.distance } ?: return null
            detectedMarkers = listOf(closestMarker)
        } else if (closestMarkersUsed > 0) {
            detectedMarkers = detectedMarkers.sortedBy { it.distance }.take(closestMarkersUsed)
        }

        // CALCULATE THE CAMERA POSITIONS
        val extractedPositions: MutableList<PositionFromMarker> = mutableListOf()
        for (detectedMarker in detectedMarkers) {
            //Detect the selected marker data
            var markerData = markersConfig.find { marker ->
                marker.id == detectedMarker.markerId
            }

            if (markerData != null) {
                //PROCESS OF CONVERTING THE CAMERA-MARKER INFO INTO CAMERA-WORLD

                //1 - RVEC to Rotation matrix
                val r_marker_cam = Mat()
                Calib3d.Rodrigues(detectedMarker.rvecs, r_marker_cam) // 3x3 rotation matrix

                //2 - Inverse the transformation (get the camera pose relative to the marker)
                //T_marker_cam = [R | t] → T_cam_marker = [Rᵗ | -Rᵗ * t]
                val r_cam_marker = r_marker_cam.t() // Transpuesta = inversa ortonormal
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
                val t_marker_world = Mat(3, 1, CvType.CV_64F)
                t_marker_world.put(
                    0,
                    0,
                    markerData.position.x.toDouble(),
                    markerData.position.y.toDouble(),
                    markerData.position.z.toDouble()
                )

                //4 - Get the camera-world position
                val t_temp = Mat()
                Core.gemm(r_marker_world, t_cam_marker, 1.0, Mat(), 0.0, t_temp)
                val t_cam_world = Mat()
                Core.add(t_temp, t_marker_world, t_cam_world)

                //5 - Extract the camera position
                val posArray = DoubleArray(3)
                t_cam_world.get(0, 0, posArray)

                // Retain the extracted position
                extractedPositions.add(
                    PositionFromMarker(
                        markerId = markerData.id,
                        x = posArray[0],
                        y = posArray[1],
                        z = posArray[2],
                        distance = detectedMarker.distance
                    )
                )
            }
        }


        //Filter data
        var returnData = filterPositionList(extractedPositions, multipleMarkersBehaviour, ransacThreshold)

        if (returnData == null) {
            return null
        }

        return Pair(returnData, extractedPositions)
    }

    fun filterPositionList(positions: List<PositionFromMarker>, arithmeticFilteringMode: MultipleMarkersBehaviour, ransacThreshold: Double = 0.2): Position?{
        //Prepare return data
        var returnData: Position? = null

        // -- RANSAC FILTERING
        var positions = ransacFilterPositions(positions, ransacThreshold)


        // -- ARITHMETIC FILTERING

        // Calculate weighted average of the positions
        if (arithmeticFilteringMode == MultipleMarkersBehaviour.WEIGHTED_AVERAGE || arithmeticFilteringMode == MultipleMarkersBehaviour.AVERAGE) {
            returnData = calculateAveragePosition(
                positions,
                arithmeticFilteringMode == MultipleMarkersBehaviour.WEIGHTED_AVERAGE
            )
        }
        // Calculate median of the positions
        else if (arithmeticFilteringMode == MultipleMarkersBehaviour.WEIGHTED_MEDIAN || arithmeticFilteringMode == MultipleMarkersBehaviour.MEDIAN) {
            returnData = calculateMedianPosition(
                positions,
                arithmeticFilteringMode == MultipleMarkersBehaviour.WEIGHTED_MEDIAN
            )
        } else {
            // Return the closest position
            returnData = positions.firstOrNull()
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