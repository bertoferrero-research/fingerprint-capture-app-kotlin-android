package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.CvCameraViewFrameMockFromImage
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.MatFromFile
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.Size

data class CalibrationBatchResult(
    var fileName: String,
    var processed: Boolean,
    var cornersDetected: Int = 0,
    var error: String? = null
)

class BatchCalibrationController(
    private val context: Context,
    private val onCalibrationFinished: (Boolean, String) -> Unit,
    var arucoDictionaryType: Int = org.opencv.objdetect.Objdetect.DICT_6X6_250,
    var charucoXSquares: Int = 7,
    var charucoYSquares: Int = 5,
    var charucoSquareLength: Float = 0.035f,
    var charucoMarkerLength: Float = 0.018f
) {

    private var imageSize: Size? = null
    private var arucoDictionary: org.opencv.objdetect.Dictionary? = null
    private var charucoBoard: org.opencv.objdetect.CharucoBoard? = null
    private var charucoDetector: org.opencv.objdetect.CharucoDetector? = null

    private val objPointsPool: MutableList<Mat> = mutableListOf()
    private val imgPointsPool: MutableList<Mat> = mutableListOf()

    fun initializeDetector() {
        arucoDictionary = org.opencv.objdetect.Objdetect.getPredefinedDictionary(arucoDictionaryType)
        charucoBoard = org.opencv.objdetect.CharucoBoard(
            Size(
                charucoXSquares.toDouble(),
                charucoYSquares.toDouble()
            ), charucoSquareLength, charucoMarkerLength, arucoDictionary
        )
        charucoDetector = org.opencv.objdetect.CharucoDetector(charucoBoard)
        objPointsPool.clear()
        imgPointsPool.clear()
        imageSize = null
    }

    suspend fun processBatchCalibration(inputFolderUri: Uri): List<CalibrationBatchResult> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<CalibrationBatchResult>()
            
            try {
                initializeDetector()
                
                val inputFolder = DocumentFile.fromTreeUri(context, inputFolderUri)
                val matFiles = inputFolder?.listFiles()?.filter { 
                    it.name?.endsWith(".matphoto", ignoreCase = true) == true
                } ?: emptyList()

                for (file in matFiles) {
                    try {
                        val result = processMatFile(file)
                        results.add(result)
                    } catch (e: Exception) {
                        results.add(
                            CalibrationBatchResult(
                                fileName = file.name ?: "Unknown",
                                processed = false,
                                error = e.message ?: "Processing error"
                            )
                        )
                    }
                }

                // Perform calibration if we have enough samples
                if (objPointsPool.size >= 10) { // Minimum samples for calibration
                    val overallRmsError = performCalibration()
                    onCalibrationFinished(true, "Calibration completed successfully with ${objPointsPool.size} samples and RMS error: $overallRmsError")
                } else {
                    onCalibrationFinished(false, "Insufficient samples for calibration. Need at least 10, got ${objPointsPool.size}")
                }

            } catch (e: Exception) {
                onCalibrationFinished(false, "Batch calibration failed: ${e.message}")
            } finally {
                cleanup()
            }

            results
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun processMatFile(file: DocumentFile): CalibrationBatchResult {
        return withContext(Dispatchers.IO) {
            try {
                // Load MAT file
                val inputStream = context.contentResolver.openInputStream(file.uri)
                    ?: throw Exception("Cannot open file: ${file.name}")
                
                val mat = MatFromFile(inputStream)
                inputStream.close()

                // Set image size from first image
                if (imageSize == null) {
                    imageSize = Size(mat.width().toDouble(), mat.height().toDouble())
                }

                // Create mock frame for processing
                val mockFrame = CvCameraViewFrameMockFromImage(mat)
                val result = processFrameForCalibration(mockFrame)

                result.fileName = file.name ?: "Unknown"

                result
            } catch (e: Exception) {
                CalibrationBatchResult(
                    fileName = file.name ?: "Unknown",
                    processed = false,
                    error = e.message ?: "Processing error"
                )
            }
        }
    }

    private fun processFrameForCalibration(inputFrame: CvCameraViewFrameMockFromImage): CalibrationBatchResult {
        try {
            val gray = inputFrame.gray()
            
            val charucoCorners = Mat()
            val charucoIds = Mat()
            charucoDetector?.detectBoard(gray, charucoCorners, charucoIds)

            if (charucoCorners.total() > 5 && charucoCorners.total() == charucoIds.total()) {
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

                    return CalibrationBatchResult(
                        fileName = "",
                        processed = true,
                        cornersDetected = charucoCorners.total().toInt()
                    )
                } catch (e: Exception) {
                    return CalibrationBatchResult(
                        fileName = "",
                        processed = false,
                        error = "Error matching image points: ${e.message}"
                    )
                }
            } else {
                return CalibrationBatchResult(
                    fileName = "",
                    processed = false,
                    cornersDetected = charucoCorners.total().toInt(),
                    error = "Insufficient corners detected"
                )
            }
        } catch (e: Exception) {
            return CalibrationBatchResult(
                fileName = "",
                processed = false,
                error = "Frame processing error: ${e.message}"
            )
        }
    }

    private fun performCalibration(): Double {
        if (objPointsPool.size < 10) {
            throw Exception("Insufficient samples for calibration")
        }

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
            calibrationObject.saveParameters()

            // Verify calibration was saved
            val calibrationCheck = CameraCalibrationParameters.loadParameters()

            return overallRmsError
            //Toast.makeText(context, "Camera calibrated successfully! RMS Error: $overallRmsError", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            throw Exception("Calibration failed: ${e.message}")
        } finally {
            cameraMatrix.release()
            distCoeffs.release()
            rvecs.forEach { it.release() }
            tvecs.forEach { it.release() }
        }
    }

    private fun cleanup() {
        arucoDictionary = null
        charucoBoard = null
        charucoDetector = null
        objPointsPool.forEach { it.release() }
        imgPointsPool.forEach { it.release() }
        objPointsPool.clear()
        imgPointsPool.clear()
        imageSize = null
    }

    fun getSamplesCount(): Int = objPointsPool.size
}
