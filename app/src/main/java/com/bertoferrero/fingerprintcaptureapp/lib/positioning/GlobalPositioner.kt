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
     * @return The camera position as a list of [x, y, z] or null if no valid markers are found.
     */
    public fun getPositionFromArucoMarkers(
        detectedMarkers: List<MarkersInFrame>,
        multipleMarkersBehaviour: MultipleMarkersBehaviour = MultipleMarkersBehaviour.CLOSEST,
    ): List<Double>? {

        var detectedMarkers = detectedMarkers

        // Get the closest marker to the camera if it is required
        if(multipleMarkersBehaviour == MultipleMarkersBehaviour.CLOSEST){// Remove markers not in the config
            detectedMarkers = detectedMarkers.filter {
                markersConfig.any {
                        marker -> marker.id == it.markerId
                }
            }
            val closestMarker = detectedMarkers.minByOrNull { it.distance } ?: return null
            detectedMarkers = listOf(closestMarker)
        }

        // CALCULATE THE CAMERA POSITIONS
        val extractedPositions: MutableList<List<Double>> = mutableListOf()
        for (detectedMarker in detectedMarkers) {
            //Detect the selected marker data
            var markerData = markersConfig.find{
                marker -> marker.id == detectedMarker.markerId
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
                    listOf(posArray[0], posArray[1], posArray[2], detectedMarker.distance)
                )
            }
        }

        // Calculate weighted average of the positions
        if(multipleMarkersBehaviour == MultipleMarkersBehaviour.WEIGHTED_AVERAGE) {
                return getWeightedPosition(extractedPositions)
        }

        // Return the closest position
        return extractedPositions.firstOrNull()
    }

    /**
     * Calculate the weighted average of the positions based on their distances.
     * @param positions List of positions, each position is a list of [x, y, z, distance]
     * @return Weighted average position as a list of [x, y, z] or null if no valid positions are provided.
     */
    private fun getWeightedPosition(positions: List<List<Double>>):List<Double>?{

        if(positions.isEmpty()) return null

        var x = 0.0
        var y = 0.0
        var z = 0.0
        var totalWeight  = 0.0

        for (position in positions) {
            // position[0] = x, [1] = y, [2] = z, [3] = distancia

            val distance = position.getOrNull(3) ?: continue

            if (distance <= 0.0) continue // evita división por 0 o distancias inválidas

            val weight = 1.0 / (distance * distance)

            x += position[0] * weight
            y += position[1] * weight
            z += position[2] * weight
            totalWeight += weight
        }

        // Si no se acumuló peso (ninguna posición válida), evita NaN
        if (totalWeight == 0.0) return listOf(0.0, 0.0, 0.0)

        return listOf(
            x / totalWeight,
            y / totalWeight,
            z / totalWeight
        )
    }
}


enum class MultipleMarkersBehaviour {
    CLOSEST,
    WEIGHTED_AVERAGE,
}