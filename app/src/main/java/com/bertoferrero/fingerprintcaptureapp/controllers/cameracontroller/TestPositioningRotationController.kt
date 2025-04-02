// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.content.Context
import android.widget.Toast
import com.bertoferrero.fingerprintcaptureapp.lib.eulerAnglesToRotationMatrix
import com.bertoferrero.fingerprintcaptureapp.lib.openCvTools.MarkersDetector
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

    // Camera calibration parameters
    private var cameraMatrix: Mat
    private var distCoeffs: Mat

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
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            markersDetector = null
            markersId = null
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

        if (detectedMarkers.isNotEmpty()) {
            //get the closest marker
            val closestMarker = detectedMarkers.minBy { it.distance }

            //Detect the selected marker data
            var markerData: MarkerDefinition? = null
            when (closestMarker.markerId) {
                marker1.id -> markerData = marker1
                marker2.id -> markerData = marker2
            }

            if (markerData != null) {
                //PROCESS OF CONVERTING THE CAMERA-MARKER INFO INTO CAMERA-WORLD

                //1 - RVEC to Rotation matrix
                val r_marker_cam = Mat()
                Calib3d.Rodrigues(closestMarker.rvecs, r_marker_cam) // 3x3 rotation matrix

                //2 - Inverse the transformation (get the camera pose relative to the marker)
                //T_marker_cam = [R | t] → T_cam_marker = [Rᵗ | -Rᵗ * t]
                val r_cam_marker = r_marker_cam.t() // Transpuesta = inversa ortonormal
                val t_cam_marker = Mat()
                Core.gemm(
                    r_cam_marker,
                    closestMarker.tvecs,
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

                //Print the position
                println("Position: (${posArray[0]}, ${posArray[1]}, ${posArray[2]})")
                //Also in the image
                org.opencv.imgproc.Imgproc.putText(
                    rgb,
                    "Position: (${(posArray[0] * 1000).roundToInt() /1000.0}, ${(posArray[1] * 1000).roundToInt() /1000.0}, ${(posArray[2] * 1000).roundToInt() /1000.0})",
                    org.opencv.core.Point(
                        25.0,25.0
                    ),
                    org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    org.opencv.core.Scalar(255.0, 0.0, 0.0),
                    2,
                    org.opencv.imgproc.Imgproc.LINE_AA
                )
            }
        }

        return rgb
    }


}