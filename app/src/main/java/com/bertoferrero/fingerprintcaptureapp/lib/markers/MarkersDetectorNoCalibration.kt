package com.bertoferrero.fingerprintcaptureapp.lib.markers

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
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
class MarkersDetectorNoCalibration(
    private val context: Context,
    private val arucoDictionaryType: Int = Objdetect.DICT_6X6_250,
    var frameSize: Size? = null,
) {
    /*
    * Focal parameters
     */
    private var focalInitialized = false
    private var focalLengthMillimeters: Float = 0.0f
    private var focalLengthPixels: Double = 0.0
    private var sensorSizeWidth = 0.0f

    /**
     * Aruco detector with improved corner detection accuracy
     */
    private val arucoDetector = run {
        val detectorParams = DetectorParameters()
        try {
            // Activar refinamiento de esquinas subpíxel para mayor precisión
            detectorParams.set_cornerRefinementMethod(1) // 1 = CORNER_REFINE_SUBPIX
        } catch (e: Exception) {
            // Si no está disponible, usar configuración por defecto
        }
        ArucoDetector(
            Objdetect.getPredefinedDictionary(arucoDictionaryType),
            detectorParams
        )
    }

    init {
        initializeFocalParameters()
    }

    private fun initializeFocalParameters() {
        if (frameSize != null) {
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
                    characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width
                        ?: 0.0f
                if (focalLengthMillimeters > 0.0f && sensorSizeWidth > 0.0f) {
                    val pixelRatio = frameSize!!.width / sensorSizeWidth
                    focalLengthPixels = focalLengthMillimeters * pixelRatio
                }
                focalInitialized = true
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
        filterIds : List<Int> = listOf(),
        outputCorners: MutableList<Mat>? = null,
        outputIds: Mat? = null,
    ): MutableList<MarkersInFrame> {
        // Prepare the input frame
        val gray = inputFrame.gray()

        //Prepare return
        val returnData: MutableList<MarkersInFrame> = mutableListOf()

        //Check if the focal parameters are initialized
        if (!focalInitialized) {
            if(frameSize == null){
                frameSize = Size(gray.width().toDouble(), gray.height().toDouble())
                initializeFocalParameters()
            }

            if (!focalInitialized) {
                Log.e("MarkersDetectorNoCalibration::detectMarkers", "Focal parameters not initialized")
                return returnData
            }
        }

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
                var markerId = ids[i, 0][0].toInt()
                if (filterIds.isNotEmpty() && !filterIds.contains(markerId)) {
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
                    val distance = focalLengthPixels * (markerSize ) / markerLengthPx

                    returnData.add(
                        MarkersInFrame(
                            markerId,
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