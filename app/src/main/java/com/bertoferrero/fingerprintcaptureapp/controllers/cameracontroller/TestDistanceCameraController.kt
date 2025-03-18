// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast
import com.bertoferrero.fingerprintcaptureapp.lib.openCvTools.detectMarkersNoCalibrationMethod1
import com.bertoferrero.fingerprintcaptureapp.lib.openCvTools.detectMarkersNoCalibrationMethod2
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import kotlin.math.round

class TestDistanceCameraController(
    private val context: Context,
    public var markerSize: Float = 0.173f,
    public var arucoDictionaryType: Int = org.opencv.objdetect.Objdetect.DICT_6X6_250
) : ICameraController {
    // Running variables
    private var running = false
    private var arucoDetector: org.opencv.objdetect.ArucoDetector? = null
    // Camera calibration parameters
    private lateinit var cameraMatrix: Mat
    private lateinit var distCoeffs: Mat
    private var focalLengthMillimeters: Float = 0.0f
    private var horizontalFOV: Float = 0.0f
    private var sensorSizeWidth = 0.0f

    //TODO construct to get the camera matrix and distCoeffs, if not, warn the user with a toast but run with empty values
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

        //Get focal length
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraList = cameraManager.cameraIdList
        val cameraId = cameraList.firstOrNull()
        if(cameraId != null) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            focalLengthMillimeters =
                characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull() ?: 0.0f
            if(focalLengthMillimeters > 0.0f) {
                //https://stackoverflow.com/questions/39965408/what-is-the-android-camera2-api-equivalent-of-camera-parameters-gethorizontalvie
                val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                horizontalFOV = 2 * kotlin.math.atan(sensorSize?.width?.div(2 * focalLengthMillimeters) ?: 0.0f)
                sensorSizeWidth = sensorSize?.width ?: 0.0f
            }
        }
    }

    override fun initProcess() {
        if (!running) {
            running = true
            val arucoDictionary =
                org.opencv.objdetect.Objdetect.getPredefinedDictionary(arucoDictionaryType)
            val arucoDetectorParameters = org.opencv.objdetect.DetectorParameters()
            arucoDetector =
                org.opencv.objdetect.ArucoDetector(arucoDictionary, arucoDetectorParameters)

        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            arucoDetector = null
        }
    }

    override fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Prepare the input frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)

        var imageSize = Size(frame.width().toDouble(), frame.height().toDouble())

        //Detectamos los marcadores
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        //val detectedMarkers = detectMarkers(inputFrame, markerSize, arucoDetector!!, cameraMatrix, distCoeffs, corners, ids)
        val detectedMarkers = detectMarkersNoCalibrationMethod1(inputFrame, markerSize*1000, focalLengthMillimeters, arucoDetector!!, corners, ids)
        //val detectedMarkers = detectMarkersNoCalibrationMethod2(inputFrame, markerSize*1000, horizontalFOV, imageSize, focalLengthMillimeters, sensorSizeWidth, arucoDetector!!, corners, ids)

        if(detectedMarkers.size == 0){
            return rgb
        }

        //Imprimimos la información
        val disctCoeffsMatOfDouble = MatOfDouble(distCoeffs)
        org.opencv.objdetect.Objdetect.drawDetectedMarkers(rgb, corners, ids)
        for( i in 0 until detectedMarkers.size){
            /*org.opencv.calib3d.Calib3d.drawFrameAxes(
                rgb,
                cameraMatrix,
                disctCoeffsMatOfDouble,
                detectedMarkers[i].rvecs,
                detectedMarkers[i].tvecs,
                0.04f,
                3
            )*/

            // Convert distance to meters
            val distance = detectedMarkers[i].distance / 1000

            org.opencv.imgproc.Imgproc.putText(
                rgb,
                "Distance: ${round(distance * 10000) / 10000}m",
                org.opencv.core.Point(
                    detectedMarkers[i].corners[1, 0][0],
                    detectedMarkers[i].corners[1, 0][1]
                ),
                org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
                1.0,
                org.opencv.core.Scalar(255.0, 0.0, 0.0),
                2,
                org.opencv.imgproc.Imgproc.LINE_AA
            )
        }
        return rgb
    }


}