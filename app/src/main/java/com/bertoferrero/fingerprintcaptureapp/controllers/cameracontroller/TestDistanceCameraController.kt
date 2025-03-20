// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast
import com.bertoferrero.fingerprintcaptureapp.lib.openCvTools.detectMarkers
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tan

class TestDistanceCameraController(
    private val context: Context,
    public var markerSize: Float = 0.173f,
    public var arucoDictionaryType: Int = org.opencv.objdetect.Objdetect.DICT_6X6_250,
    public var method: Int = 1,
) : ICameraController {
    // Running variables
    private var running = false
    private var arucoDetector: org.opencv.objdetect.ArucoDetector? = null

    // Camera calibration parameters
    private lateinit var cameraMatrix: Mat
    private lateinit var distCoeffs: Mat
    private var focalLengthMillimeters: Float = 0.0f
    private var horizontalFOV: Float = 0.0f
    private var verticalFOV: Float = 0.0f
    private var sensorSizeWidth = 0.0f
    private var sensorSizeHeight = 0.0f

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
        if (cameraId != null) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            focalLengthMillimeters =
                characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull() ?: 0.0f
            if (focalLengthMillimeters > 0.0f) {
                //https://stackoverflow.com/questions/39965408/what-is-the-android-camera2-api-equivalent-of-camera-parameters-gethorizontalvie
                val sensorSize =
                    characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                horizontalFOV =
                    2 * kotlin.math.atan(sensorSize?.width?.div(2 * focalLengthMillimeters) ?: 0.0f)
                verticalFOV = 2 * kotlin.math.atan(
                    sensorSize?.height?.div(2 * focalLengthMillimeters) ?: 0.0f
                )
                sensorSizeWidth = sensorSize?.width ?: 0.0f
                sensorSizeHeight = sensorSize?.height ?: 0.0f
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
        return when (method) {
            1 -> methodCalibration(inputFrame)
            2 -> method2Javi(inputFrame)
            3 -> method3JaviWithPixelRatio(inputFrame)
            4 -> method4JaviWithHorizontalFOV(inputFrame)
            5 -> method5JaviWithHorizontalFOV2(inputFrame)
            6 -> method6CalculatedCameraMatrix(inputFrame)
            else -> {
                inputFrame?.rgba() ?: Mat()
            }
        }
    }


    fun methodCalibration(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Prepare the input frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)


        //Detectamos los marcadores
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        val detectedMarkers = detectMarkers(
            inputFrame,
            markerSize,
            arucoDetector!!,
            cameraMatrix,
            distCoeffs,
            corners,
            ids
        )
        if (detectedMarkers.size == 0) {
            return rgb
        }

        //Imprimimos la información
        val disctCoeffsMatOfDouble = MatOfDouble(distCoeffs)
        org.opencv.objdetect.Objdetect.drawDetectedMarkers(rgb, corners, ids)
        for (i in 0 until detectedMarkers.size) {
            org.opencv.calib3d.Calib3d.drawFrameAxes(
                rgb,
                cameraMatrix,
                disctCoeffsMatOfDouble,
                detectedMarkers[i].rvecs,
                detectedMarkers[i].tvecs,
                0.04f,
                3
            )

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

    /**
     * Method 2 proposed by Javi
     * It consist on the equation: z = f * d / D
     * Where z is the distance, f is the focal length, d is the marker size and D is the marker size in pixels
     */
    fun method2Javi(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Prepare the input frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)

        // Prepare the input frame
        val gray = inputFrame.gray()

        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        arucoDetector?.detectMarkers(gray, corners, ids)

        if (corners.size == 0 || corners.size.toLong() != ids.total()) {
            return rgb
        }

        // Process the detected markers
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

                // Calculate the distance (marker size is provided in meters)
                //test actualDistance: 1.045m, landscape,
                //resolution: 2304x1040
                //focalLength: 6.14mm
                //markerSize: 0.175m -> 175mm
                //markerLengthPx: 266.0075186, [ [1163, 197], [1429, 199], [1428, 464], [1163, 462] ]
                //distance: 4.0393595 mm
                //actualDistance: 1.045m
                val distance = focalLengthMillimeters * (markerSize * 1000) / markerLengthPx

                org.opencv.imgproc.Imgproc.putText(
                    rgb,
                    "Distance: $distance",
                    org.opencv.core.Point(
                        cornerMatOfPoint2f[1, 0][0],
                        cornerMatOfPoint2f[1, 0][1]
                    ),
                    org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    org.opencv.core.Scalar(255.0, 0.0, 0.0),
                    2,
                    org.opencv.imgproc.Imgproc.LINE_AA
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return rgb
    }

    /**
     * Method 3 proposed by Javi
     * It consist on the equation: z = f * d / D but transforming the focal length obtained in millimeters to pixels
     * To do this we calculate a simple ratio where ratio = imageWidthPx / sensorWidthMm
     */
    fun method3JaviWithPixelRatio(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Prepare the input frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)


        // Calculate the focal length in pixels
        val pixelRatio = frame.width().toDouble() / sensorSizeWidth
        val focalLength = focalLengthMillimeters * pixelRatio

        // Prepare the input frame
        val gray = inputFrame.gray()

        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        arucoDetector?.detectMarkers(gray, corners, ids)

        if (corners.size == 0 || corners.size.toLong() != ids.total()) {
            return rgb
        }

        // Process the detected markers
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
                //test actualDistance: 1.045m, landscape,
                //resolution: 2304x1040
                //focalLengthPx: 1547.89909
                ////focalLength: 6.14mm
                ////imageWidthPx: 2304px
                ////sensorSizeWidth: 9.1392mm
                ////pixelRatio: 252.10083
                //markerSize: 0.175m -> 175mm
                //markerLengthPx: 270.007, [ [1127, 117], [1397, 119], [1395, 388], [1126, 386] ]
                //distance: 1003.2404 mm -> 1.0032404m
                //actualDistance: 1.045m
                val distance = focalLength * (markerSize * 1000) / markerLengthPx

                org.opencv.imgproc.Imgproc.putText(
                    rgb,
                    "Distance: $distance",
                    org.opencv.core.Point(
                        cornerMatOfPoint2f[1, 0][0],
                        cornerMatOfPoint2f[1, 0][1]
                    ),
                    org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    org.opencv.core.Scalar(255.0, 0.0, 0.0),
                    2,
                    org.opencv.imgproc.Imgproc.LINE_AA
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return rgb
    }

    //¡¡¡¡¡¡¡Usar FOV es lo mismo que calcular el ratio!!!!!!!!

    /**
     * Method 4 based on Javi's method but using the horizontal FOV to calculate the focal length in pixels
     */
    fun method4JaviWithHorizontalFOV(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Prepare the input frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)


        // https://stackoverflow.com/questions/38104517/converting-focal-length-in-millimeters-to-pixels-android
        val focalLength = (frame.width().toDouble() / 2) * (tan(horizontalFOV * 0.5))

        // Prepare the input frame
        val gray = inputFrame.gray()

        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        arucoDetector?.detectMarkers(gray, corners, ids)

        if (corners.size == 0 || corners.size.toLong() != ids.total()) {
            return rgb
        }

        // Process the detected markers
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
                //test actualDistance: 1.045m, landscape,
                //resolution: 2304x1040
                //focalLengthPx: 857.3582508
                ////horizontalFOV: 1.279
                ////imageWidthPx: 2304px
                //markerSize: 0.175m -> 175mm
                //markerLengthPx: 266.01069, [ [1120, 198], [1386, 201], [1384, 464], [1121, 461] ]
                //distance: 564.0156 mm -> 0.5640156m
                //actualDistance: 1.045m
                val distance = focalLength * (markerSize * 1000) / markerLengthPx

                org.opencv.imgproc.Imgproc.putText(
                    rgb,
                    "Distance: $distance",
                    org.opencv.core.Point(
                        cornerMatOfPoint2f[1, 0][0],
                        cornerMatOfPoint2f[1, 0][1]
                    ),
                    org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    org.opencv.core.Scalar(255.0, 0.0, 0.0),
                    2,
                    org.opencv.imgproc.Imgproc.LINE_AA
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return rgb
    }

    /**
     * Method 5 based on method 4 but using a slightly different way to calculate the focal length in pixels
     */
    fun method5JaviWithHorizontalFOV2(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Prepare the input frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)


        // https://stackoverflow.com/questions/38104517/converting-focal-length-in-millimeters-to-pixels-android
        val focalLength = frame.width().toDouble() / (2 * tan(horizontalFOV / 2))

        // Prepare the input frame
        val gray = inputFrame.gray()

        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        arucoDetector?.detectMarkers(gray, corners, ids)

        if (corners.size == 0 || corners.size.toLong() != ids.total()) {
            return rgb
        }

        // Process the detected markers
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
                //test actualDistance: 1.045m, landscape,
                //resolution: 2304x1040
                //focalLengthPx: 1547.89896
                ////horizontalFOV: 1.279
                ////imageWidthPx: 2304px
                //markerSize: 0.175m -> 175mm
                //markerLengthPx: 268.01679, [ [1131, 127], [1399, 130], [1396, 398], [1130, 396] ]
                //distance: 1010.6915 mm -> 1.0106915m
                //actualDistance: 1.045m
                val distance = focalLength * (markerSize * 1000) / markerLengthPx

                org.opencv.imgproc.Imgproc.putText(
                    rgb,
                    "Distance: $distance",
                    org.opencv.core.Point(
                        cornerMatOfPoint2f[1, 0][0],
                        cornerMatOfPoint2f[1, 0][1]
                    ),
                    org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    org.opencv.core.Scalar(255.0, 0.0, 0.0),
                    2,
                    org.opencv.imgproc.Imgproc.LINE_AA
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return rgb
    }

    /**
     * Method 6 is based on method 3 but calculating the camera matrix
     */
    fun method6CalculatedCameraMatrix(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        //test actualDistance: 1.045m, landscape,
        //resolution: 2304x1040
        //focalLengthWidth: 1547.89890
        ////sensorSizeWidth: 9.1392
        //focalLengthHeight: 931.60593
        ////sensorSizeHeight: 6.8544
        //CameraMatrix:
        /*
        [1547.899090340479, 0, 1152;
         0, 931.6059340012142, 520;
         0, 0, 1]
         */
        //distance: 664.897mm

        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }

        // Prepare the input frame
        val frame = inputFrame.rgba() ?: Mat()
        val rgb = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(frame, rgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)


        // Calculate the focal length in pixels
        val pixelRatioWidth = frame.width().toDouble() / sensorSizeWidth
        val pixelRationHeight = frame.height().toDouble() / sensorSizeHeight
        val focalLengthWidth = focalLengthMillimeters * pixelRatioWidth
        val focalLengthHeight = focalLengthMillimeters * pixelRationHeight

        //Create the camera matrix
        val cameraMatrix = Mat(3, 3, org.opencv.core.CvType.CV_64F)
        cameraMatrix.put(0, 0, focalLengthWidth)
        cameraMatrix.put(0, 1, 0.0)
        cameraMatrix.put(0, 2, frame.width().toDouble() / 2)
        cameraMatrix.put(1, 0, 0.0)
        cameraMatrix.put(1, 1, focalLengthHeight)
        cameraMatrix.put(1, 2, frame.height().toDouble() / 2)
        cameraMatrix.put(2, 0, 0.0)
        cameraMatrix.put(2, 1, 0.0)
        cameraMatrix.put(2, 2, 1.0)
        val distCoeffs = Mat()

        // Prepare the input frame
        val gray = inputFrame.gray()

        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        val detectedMarkers = detectMarkers(
            inputFrame,
            markerSize*1000,
            arucoDetector!!,
            cameraMatrix,
            distCoeffs,
            corners,
            ids
        )
        if (detectedMarkers.size == 0) {
            return rgb
        }

        //Imprimimos la información
        val disctCoeffsMatOfDouble = MatOfDouble()
        org.opencv.objdetect.Objdetect.drawDetectedMarkers(rgb, corners, ids)
        for (i in 0 until detectedMarkers.size) {
            org.opencv.calib3d.Calib3d.drawFrameAxes(
                rgb,
                cameraMatrix,
                disctCoeffsMatOfDouble,
                detectedMarkers[i].rvecs,
                detectedMarkers[i].tvecs,
                0.04f,
                3
            )

            // Convert distance to meters
            val distance = detectedMarkers[i].distance

            org.opencv.imgproc.Imgproc.putText(
                rgb,
                "Distance: $distance",
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