package com.bertoferrero.fingerprintcaptureapp.lib.openCvTools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detects markers in the input frame and returns the detected markers with their distance.
 * This class employs the focal length of the camera to estimate the distance to the markers instead of using the camera matrix and distortion coefficients.
 */
class detectMarkersNoCalibration(
    private val context: Context,
    val frameSize: Size,
) {
    private var focalLengthMillimeters: Float = 0.0f
    private var focalLengthPixels: Double = 0.0
    private var sensorSizeWidth = 0.0f

    init {
        //Get focal parameters
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraList = cameraManager.cameraIdList
        val cameraId = cameraList.firstOrNull()
        if (cameraId != null) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            focalLengthMillimeters =
                characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull() ?: 0.0f
            sensorSizeWidth =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0.0f
            if (focalLengthMillimeters > 0.0f && sensorSizeWidth > 0.0f) {
                val pixelRatio = frameSize.width / sensorSizeWidth
                focalLengthPixels = focalLengthMillimeters * pixelRatio
            }
        }
    }

    /**
     * Detects markers in the input frame and returns the detected markers with their distance.
     * MarkersInFrame objects will not contain the pose information as the camera matrix and distortion coefficients are not used here.
     */
    fun detectMarkers(
        inputFrame: CameraBridgeViewBase.CvCameraViewFrame,
        markerSize: Float,
        arucoDetector: org.opencv.objdetect.ArucoDetector,
        outputCorners: MutableList<Mat>? = null,
        outputIds: Mat? = null,
    ): MutableList<MarkersInFrame> {
        //Prepare return
        val returnData: MutableList<MarkersInFrame> = mutableListOf()

        // Prepare the input frame
        val gray = inputFrame.gray()

        /// Detect markers
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

                try {

                    val cornerMatOfPoint2f = MatOfPoint2f(corners[i].reshape(2, 4))

                    //Calculate marker length in px
                    val markerLengthPx = sqrt(
                        (cornerMatOfPoint2f[0, 0][0] - cornerMatOfPoint2f[1, 0][0]).pow(2) +
                                (cornerMatOfPoint2f[0, 0][1] - cornerMatOfPoint2f[1, 0][1]).pow(2)
                    )

                    // Calculate the distance
                    val distance = focalLengthPixels * (markerSize * 1000) / markerLengthPx

                    returnData.add(
                        MarkersInFrame(
                            ids[i, 0][0].toInt(),
                            cornerMatOfPoint2f,
                            null,
                            null,
                            distance,
                            markerLengthPx
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