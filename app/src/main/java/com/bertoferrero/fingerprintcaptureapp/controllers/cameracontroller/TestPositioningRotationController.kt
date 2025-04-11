// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersDetector
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.GlobalPositioner
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.PositionKalmanFilter
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.Objdetect
import kotlin.math.roundToInt


class TestPositioningRotationController(
    public var markerSize: Float = 0.1765f,
    public var arucoDictionaryType: Int = Objdetect.DICT_6X6_250,
    public var markersDefinition : List<MarkerDefinition> = listOf()
) : ICameraController {
    // Running variables
    private var running = false
    private var markersDetector: MarkersDetector? = null
    private var markersId: List<Int>? = null
    public var kalmanFilter = PositionKalmanFilter()
        private set
    private var lastFrameTime: Long? = null

    // Camera calibration parameters
    private var cameraMatrix: Mat
    private var distCoeffs: Mat
    private var calibrationParametersLoaded: Boolean = false
    val isCalibrationParametersLoaded: Boolean
        get() = calibrationParametersLoaded

    // Positioner
    private var positioner: GlobalPositioner? = null

    init {
        //Load camera correction parameters
        try {
            val calibrationParameters = CameraCalibrationParameters.loadParameters()
            cameraMatrix = calibrationParameters.cameraMatrix
            distCoeffs = calibrationParameters.distCoeffs
            calibrationParametersLoaded = true
        } catch (e: Exception) {
            cameraMatrix = Mat()
            distCoeffs = Mat()
        }
    }

    override fun initProcess() {
        if (!running) {
            running = true
            markersDetector =
                MarkersDetector(markerSize, arucoDictionaryType, cameraMatrix, distCoeffs)

            markersId = markersDefinition.map{ it.id }
            positioner = GlobalPositioner(markersDefinition)
            kalmanFilter.initProcess()
            lastFrameTime = null
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            markersDetector = null
            markersId = null
            positioner = null
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
        val posArrayFiltered = kalmanFilter.updateWithTimestampControl(
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