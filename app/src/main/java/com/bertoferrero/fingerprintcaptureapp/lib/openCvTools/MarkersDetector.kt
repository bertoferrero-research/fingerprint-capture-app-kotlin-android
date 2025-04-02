package com.bertoferrero.fingerprintcaptureapp.lib.openCvTools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.core.Size
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
    var markerSize: Float,
    private val arucoDictionaryType: Int = Objdetect.DICT_6X6_250,
    private val cameraMatrix: Mat,
    private val distCoeffs: Mat
) {

    /**
     * Aruco detector
     */
    private val arucoDetector = ArucoDetector(
        Objdetect.getPredefinedDictionary(arucoDictionaryType),
        DetectorParameters()
    )

    private val objectPoints = MatOfPoint3f(
        Point3(-(markerSize / 2.0), (markerSize / 2.0), 0.0),
        Point3((markerSize / 2.0), (markerSize / 2.0), 0.0),
        Point3((markerSize / 2.0), -(markerSize / 2.0), 0.0),
        Point3(-(markerSize / 2.0), -(markerSize / 2.0), 0.0)
    )

    /**
     * Detects markers in the input frame and returns the detected markers with their distance.
     * MarkersInFrame objects will not contain the pose information as the camera matrix and distortion coefficients are not used here.
     */
    fun detectMarkers(
        inputFrame: CameraBridgeViewBase.CvCameraViewFrame,
        filterIds : List<Int> = listOf(),
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
                if (filterIds.isNotEmpty() && !filterIds.contains(markerId)) {
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