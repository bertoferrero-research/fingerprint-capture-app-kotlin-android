package com.bertoferrero.fingerprintcaptureapp.lib.markers

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f


/**
 * Data class to store the information of the detected markers.
 *
 * @param markerId The ID of the detected marker.
 * @param corners The corners of the detected marker.
 * @param rvecs The rotation vectors of the detected marker.
 * @param tvecs The translation vectors of the detected marker.
 * @param distance The distance of the detected marker.
 * @param markerWidthPixels The width of the detected marker in pixels (optional).
 */
class MarkersInFrame(
    val markerId: Int,
    val corners: MatOfPoint2f,
    val rvecs: Mat?,
    val tvecs: Mat?,
    val distance: Double,
    val markerWidthPixels: Double? = null,
    val sourceIdentifier: String? = null
)