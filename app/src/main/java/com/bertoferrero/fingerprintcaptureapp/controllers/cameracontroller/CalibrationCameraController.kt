// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.content.Context
import android.widget.Toast
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.Size
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters

class CalibrationCameraController(
    private val context: Context,
    private val onCalibrationFinished: () -> Unit,
    var arucoDictionaryType: Int = org.opencv.objdetect.Objdetect.DICT_6X6_250,
    var charucoXSquares: Int = 7,
    var charucoYSquares: Int = 5,
    var charucoSquareLength: Float = 0.035f,
    var charucoMarkerLength: Float = 0.018f
    ) : ICameraController {

    private val minSamplesAmount = 15
    private var captureFrame = false
    private var imageSize: Size? = null
    private var calibrating = false
    private var arucoDictionary: org.opencv.objdetect.Dictionary? = null
    private var charucoBoard: org.opencv.objdetect.CharucoBoard? = null
    private var charucoDetector: org.opencv.objdetect.CharucoDetector? = null

    private val objPointsPool: MutableList<Mat> = mutableListOf()
    private val imgPointsPool: MutableList<Mat> = mutableListOf()

    override fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!calibrating) {
            return inputFrame?.rgba() ?: Mat()
        }

        val frame = inputFrame?.rgba() ?: Mat()

        if (imageSize == null) {
            imageSize = Size(frame.width().toDouble(), frame.height().toDouble())
        }

        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
        val gray = inputFrame?.gray()

        val charucoCorners = Mat()
        val charucoIds = Mat()
        charucoDetector?.detectBoard(gray, charucoCorners, charucoIds)

        if (charucoCorners.total() > 5 && charucoCorners.total() == charucoIds.total()) {
            org.opencv.objdetect.Objdetect.drawDetectedCornersCharuco(
                rgb,
                charucoCorners,
                charucoIds
            )

            if (captureFrame) {
                captureFrame = false
                val objPoints = Mat()
                val imgPoints = Mat()
                val detectedCorners: MutableList<Mat> = mutableListOf()
                for (i in 0 until charucoCorners.rows()) {
                    val corner = charucoCorners.row(i)
                    detectedCorners.add(corner)
                }
                try {
                    charucoBoard?.matchImagePoints(
                        detectedCorners,
                        charucoIds,
                        objPoints,
                        imgPoints
                    )
                    objPointsPool.add(objPoints)
                    imgPointsPool.add(imgPoints)

                    if (objPointsPool.size >= minSamplesAmount) {
                        finishProcess()
                    } else {
                        Toast.makeText(
                            context,
                            "${minSamplesAmount - objPointsPool.size} samples left",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error capturing frame", Toast.LENGTH_SHORT).show()
                }
            }
        }
        captureFrame = false

        return rgb
    }

    override fun initProcess() {
        if (!calibrating) {
            calibrating = true
            arucoDictionary =
                org.opencv.objdetect.Objdetect.getPredefinedDictionary(arucoDictionaryType)
            charucoBoard = org.opencv.objdetect.CharucoBoard(
                Size(
                    charucoXSquares.toDouble(),
                    charucoYSquares.toDouble()
                ), charucoSquareLength, charucoMarkerLength, arucoDictionary
            )
            charucoDetector = org.opencv.objdetect.CharucoDetector(charucoBoard)

            objPointsPool.clear()
            imgPointsPool.clear()
        }
    }

    override fun finishProcess() {
        if (calibrating) {
            calibrating = false

            if (objPointsPool.size >= minSamplesAmount) {
                val cameraMatrix = Mat()
                val distCoeffs = Mat()
                val rvecs = mutableListOf<Mat>()
                val tvecs = mutableListOf<Mat>()
                try {
                    val overallRmsError = org.opencv.calib3d.Calib3d.calibrateCamera(
                        objPointsPool,
                        imgPointsPool,
                        imageSize,
                        cameraMatrix,
                        distCoeffs,
                        rvecs,
                        tvecs
                    )
                    val calibrationObject = CameraCalibrationParameters(cameraMatrix, distCoeffs)
                    calibrationObject.saveParameters(context)

                    var calibrationCheck = CameraCalibrationParameters.loadParameters(context)

                    Toast.makeText(context, "Camera calibrated, error: ${overallRmsError}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error calibrating camera", Toast.LENGTH_SHORT).show()
                } finally {
                    cameraMatrix.release()
                    distCoeffs.release()
                    rvecs.forEach { it.release() }
                    tvecs.forEach { it.release() }
                }
            }

            arucoDictionary = null
            charucoBoard = null
            charucoDetector = null
            objPointsPool.clear()
            imgPointsPool.clear()
            imageSize = null

            onCalibrationFinished()
        }
    }

    fun captureFrame() {
        captureFrame = true
    }
}