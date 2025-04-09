// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.content.Context
import android.widget.Toast
import com.bertoferrero.fingerprintcaptureapp.lib.eulerAnglesToRotationMatrix
import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersDetector
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.GlobalPositioner
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.PositionKalmanFilter
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import com.bertoferrero.fingerprintcaptureapp.models.MarkerPosition
import com.bertoferrero.fingerprintcaptureapp.models.MarkerRotation
import org.opencv.android.CameraBridgeViewBase
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.Objdetect
import kotlin.math.roundToInt


class TestPositioningRotationController(
    private val context: Context,
    public var markerSize: Float = 0.1765f,
    public var arucoDictionaryType: Int = Objdetect.DICT_6X6_250,
    public var marker1: MarkerDefinition = MarkerDefinition(
        27, MarkerPosition(0f, 0.552f, 1.5f),
        MarkerRotation(90f, 0f, 90f)
    ),
    public var marker2: MarkerDefinition = MarkerDefinition(
        3,
        MarkerPosition(0f, 1.103f, 1.5f),
        MarkerRotation(90f, 0f, 90f)
    ),
) : ICameraController {
    // Running variables
    private var running = false
    private var markersDetector: MarkersDetector? = null
    private var markersId: List<Int>? = null
    private var kalmanFilter: PositionKalmanFilter? = null
    private var lastFrameTime: Long? = null

    // Camera calibration parameters
    private var cameraMatrix: Mat
    private var distCoeffs: Mat

    // Positioner
    private var positioner: GlobalPositioner? = null

    init {
        //Load camera correction parameters
        try {
            val calibrationParameters = CameraCalibrationParameters.loadParameters(context)
            cameraMatrix = calibrationParameters.cameraMatrix
            distCoeffs = calibrationParameters.distCoeffs
        } catch (e: Exception) {
            Toast.makeText(context, "No camera calibration parameters found", Toast.LENGTH_SHORT)
                .show()
            cameraMatrix = Mat()
            distCoeffs = Mat()
        }
    }

    override fun initProcess() {
        if (!running) {
            running = true
            markersDetector =
                MarkersDetector(markerSize, arucoDictionaryType, cameraMatrix, distCoeffs)
            markersId = listOf<Int>(marker1.id, marker2.id)
            positioner = GlobalPositioner(listOf(marker1, marker2))
            kalmanFilter = PositionKalmanFilter()
            lastFrameTime = null
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            markersDetector = null
            markersId = null
            positioner = null
            kalmanFilter = null
            lastFrameTime = null
        }
    }

    override fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {

        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        val detectedMarkers = markersDetector!!.detectMarkers(
            inputFrame, markersId!!, corners, ids
        )

        // Prepare the output frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        Imgproc.cvtColor(frame, rgb, Imgproc.COLOR_RGBA2RGB)

        // Calculate the camera position
        val posArray = positioner?.getPositionFromArucoMarkers(
            detectedMarkers,
            MultipleMarkersBehaviour.WEIGHTED_AVERAGE
        ) ?: return rgb


        //KALMAN FILTER
        val posArrayFiltered = kalmanFilter!!.updateWithTimestampControl(
            posArray[0].toFloat(),
            posArray[1].toFloat(),
            posArray[2].toFloat()
        )

        //Print the position
        println("Position: (${posArray[0]}, ${posArray[1]}, ${posArray[2]})")
        println("Position Filtered: (${posArrayFiltered[0]}, ${posArrayFiltered[1]}, ${posArrayFiltered[2]})")
        //Also in the image
        org.opencv.imgproc.Imgproc.putText(
            rgb,
            "Position: (${(posArray[0] * 1000).roundToInt() / 1000.0}, ${(posArray[1] * 1000).roundToInt() / 1000.0}, ${(posArray[2] * 1000).roundToInt() / 1000.0})",
            org.opencv.core.Point(
                25.0, 25.0
            ),
            org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            org.opencv.core.Scalar(255.0, 0.0, 0.0),
            2,
            org.opencv.imgproc.Imgproc.LINE_AA
        )
        org.opencv.imgproc.Imgproc.putText(
            rgb,
            "Position: (${(posArrayFiltered[0] * 1000).roundToInt() / 1000.0}, ${(posArrayFiltered[1] * 1000).roundToInt() / 1000.0}, ${(posArrayFiltered[2] * 1000).roundToInt() / 1000.0})",
            org.opencv.core.Point(
                25.0, 50.0
            ),
            org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
            1.0,
            org.opencv.core.Scalar(255.0, 0.0, 0.0),
            2,
            org.opencv.imgproc.Imgproc.LINE_AA
        )

        return rgb
    }


}