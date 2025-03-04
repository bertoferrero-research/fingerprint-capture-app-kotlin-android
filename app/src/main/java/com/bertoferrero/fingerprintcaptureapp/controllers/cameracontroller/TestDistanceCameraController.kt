// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.content.Context
import android.widget.Toast
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3

class TestDistanceCameraController(private val context: Context): ICameraController {

    // Running properties
    public var markerSize = 0.065f
    public var arucoDictionaryType = org.opencv.objdetect.Objdetect.DICT_6X6_250

    // Running variables
    private var running = false
    private var arucoDetector: org.opencv.objdetect.ArucoDetector? = null
    private var objectPoints : MatOfPoint3f? = null //Definición del objeto a buscar

    // Camera calibration parameters
    private lateinit var cameraMatrix: Mat
    private lateinit var distCoeffs: Mat

    //TODO construct to get the camera matrix and distCoeffs, if not, warn the user with a toast but run with empty values
    init{
        //Load camera correction parameters
        try{
            val calibrationParameters = CameraCalibrationParameters.loadParameters(context)
            cameraMatrix = calibrationParameters.cameraMatrix
            distCoeffs = calibrationParameters.distCoeffs
        } catch (e: Exception){
            Toast.makeText(context, "No camera calibration parameters found", Toast.LENGTH_SHORT).show()
            cameraMatrix = Mat()
            distCoeffs = Mat()
        }
    }

    override fun initProcess() {
        if (!running) {
            running = true
            val arucoDictionary = org.opencv.objdetect.Objdetect.getPredefinedDictionary(arucoDictionaryType)
            val arucoDetectorParameters = org.opencv.objdetect.DetectorParameters()
            arucoDetector = org.opencv.objdetect.ArucoDetector(arucoDictionary, arucoDetectorParameters)

            //Definimos el formato del aruco a buscar (un cuadrado)
            val halfMarkerSize = markerSize / 2.0
            objectPoints = MatOfPoint3f(
                Point3(-halfMarkerSize, halfMarkerSize, 0.0),
                Point3(halfMarkerSize, halfMarkerSize, 0.0),
                Point3(halfMarkerSize, -halfMarkerSize, 0.0),
                Point3(-halfMarkerSize, -halfMarkerSize, 0.0)
            )
            objectPoints!!.convertTo(objectPoints, CvType.CV_32FC3)
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            arucoDetector = null
            objectPoints = null
        }
    }

    override fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!running) {
            return inputFrame?.rgba() ?: Mat()
        }
        // copy general parameters to local variables
        var arucoDetectorCp = arucoDetector
        var objectPointsCp = objectPoints

        val frame = inputFrame?.rgba() ?: Mat()

        // Prepare the input frame
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
        val gray = inputFrame?.gray()
        //Undistort the image
        //val undistorted = Mat()
        //org.opencv.calib3d.Calib3d.undistort(gray, undistorted, cameraMatrix, distCoeffs)


        // Detect markers
        val corners : MutableList<Mat> = mutableListOf()
        val ids : Mat = Mat()
        arucoDetectorCp!!.detectMarkers(gray, corners, ids)

        // Process the detected markers
        if(corners.size > 0){
            // Draw the detected markers
            org.opencv.objdetect.Objdetect.drawDetectedMarkers(rgb, corners, ids)

            if(corners.size > 0 && corners.size.toLong() == ids.total()) {
                for (i in 0 until corners.size) {
                    if (corners[i].total() < 4) { //Check here if the corners are enough to estimate the pose, if not we get an exception from solvePnP
                        continue
                    }

                    var rvecs = Mat()
                    var tvecs = Mat()


                    val cornerMatOfPoint2f = MatOfPoint2f(corners[i].reshape(2,4))
                    val disctCoeffsMatOfDouble = MatOfDouble(distCoeffs)

                    try {
                        org.opencv.calib3d.Calib3d.solvePnP(
                            objectPointsCp!!,
                            cornerMatOfPoint2f,
                            cameraMatrix,
                            disctCoeffsMatOfDouble,
                            rvecs,
                            tvecs,
                            false,
                            org.opencv.calib3d.Calib3d.SOLVEPNP_ITERATIVE
                        )

                        // Draw the axis
                        org.opencv.calib3d.Calib3d.drawFrameAxes(
                            rgb,
                            cameraMatrix,
                            disctCoeffsMatOfDouble,
                            rvecs,
                            tvecs,
                            0.04f,
                            3
                        )
                    }
                    catch (e: Exception){
                        println(e)
                    }
                }
            }



        }

        return rgb
    }


}