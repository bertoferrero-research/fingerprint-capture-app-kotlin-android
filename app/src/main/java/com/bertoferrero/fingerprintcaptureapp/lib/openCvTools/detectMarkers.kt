package com.bertoferrero.fingerprintcaptureapp.lib.openCvTools

import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * Data class to store the information of the detected markers.
 *
 * @param markerId The ID of the detected marker.
 * @param corners The corners of the detected marker.
 * @param rvecs The rotation vectors of the detected marker.
 * @param tvecs The translation vectors of the detected marker.
 * @param distance The distance of the detected marker.
 */
class MarkersInFrame(
    val markerId: Int,
    val corners: MatOfPoint2f,
    val rvecs: Mat,
    val tvecs: Mat,
    val distance: Double,
)

/**
 * Detects markers in the input frame and returns the detected markers with their pose and distance.
 *
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
    arucoDetector: org.opencv.objdetect.ArucoDetector,
    cameraMatrix: Mat,
    distCoeffs: Mat,
    outputCorners: MutableList<Mat>? = null,
    outputIds: Mat? = null
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
    val disctCoeffsMatOfDouble = MatOfDouble(distCoeffs)

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


                // Estimate the pose
                org.opencv.calib3d.Calib3d.solvePnP(
                    objectPoints,
                    cornerMatOfPoint2f,
                    cameraMatrix,
                    disctCoeffsMatOfDouble,
                    rvecs,
                    tvecs,
                    false,
                    org.opencv.calib3d.Calib3d.SOLVEPNP_ITERATIVE
                )

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