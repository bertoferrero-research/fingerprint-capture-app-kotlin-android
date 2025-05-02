// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.util.Log
import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersDetector
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.CvCameraViewFrameMockFromImage
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.GlobalPositioner
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.PositionKalmanFilter
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import com.bertoferrero.fingerprintcaptureapp.models.MarkerDefinition
import kotlinx.coroutines.yield
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.Objdetect
import org.opencv.videoio.VideoCapture
import org.opencv.videoio.Videoio
import java.io.File
import kotlin.Double
import kotlin.Int
import kotlin.math.roundToInt


class TestPositioningRotationController(
    public var markerSize: Float = 0.1765f,
    public var arucoDictionaryType: Int = Objdetect.DICT_6X6_250,
    public var markersDefinition: List<MarkerDefinition> = listOf(),
    public var samplesLimit: Int = 0,
    public var multipleMarkersBehaviour: MultipleMarkersBehaviour = MultipleMarkersBehaviour.CLOSEST,
    public var closestMarkersUsed: Int = 0,
    public var testingImageFrame: CvCameraViewFrameMockFromImage? = null,
    public var testingVideoFrame: File? = null,
    private val onSamplesLimitReached: (List<TestPositioningRotationSample>) -> Unit,
) : ICameraController {
    // Running variables
    private var running = false
    private var markersDetector: MarkersDetector? = null
    private var markersId: List<Int>? = null
    public var kalmanFilter = PositionKalmanFilter()
        private set
    public var samples: MutableList<TestPositioningRotationSample> = mutableListOf()
        private set

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
            markersId = markersDefinition.map { it.id }
            positioner = GlobalPositioner(markersDefinition)
            samples = mutableListOf()
            kalmanFilter.initProcess()
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            markersDetector = null
            markersId = null
            positioner = null
            if (testingVideoFrame != null) {
                testingVideoFrame!!.deleteOnExit()
                testingVideoFrame = null
            }
        }
    }

    suspend fun startImageSimulation() {
        //Check if the image has been defined
        if (testingImageFrame?.rgba() == null) {
            throw Exception("Testing image is not defined")
        }

        //Start the testing loop
        while (running) {
            processFrame(testingImageFrame)
            yield() // Cede el control para evitar bloquear el hilo
        }
    }

    suspend fun startVideoSimulation() {
        //Check the video already exists
        if (testingVideoFrame == null) {
            throw Exception("Testing video is not defined")
        }

        //Load the video
        val videoCapture = VideoCapture()
        if (!videoCapture.open(testingVideoFrame!!.absolutePath)) {
            throw Exception("OpenCV cannot load this video")
        }

        samplesLimit =
            0 //For security reasons, we require to ensure this code body is fully executed

        // FPS and delay per frame
        val fps = videoCapture.get(Videoio.CAP_PROP_FPS)
        val frameDelayMillis = if (fps > 0) (1000 / fps).toLong() else 33L

        val frame = Mat()
        var processedFrames = 0
        var droppedFrames = 0
        val startGlobalTime = System.currentTimeMillis()

        try {
            while (running) {
                val startTime = System.currentTimeMillis()

                val readSuccess = videoCapture.read(frame)
                if (!readSuccess || frame.empty()) break

                val simulatedFrame = CvCameraViewFrameMockFromImage(frame.clone())

                // Procesamiento del frame
                val result = processFrame(simulatedFrame)
                processedFrames++

                // Calcular duración del procesamiento
                val processingTime = System.currentTimeMillis() - startTime
                val remainingTime = frameDelayMillis - processingTime

                if (remainingTime > 0) {
                    Thread.sleep(remainingTime)
                } else {
                    // Caída de frame: descartar uno o más frames para compensar
                    val framesToDrop = (processingTime / frameDelayMillis).toInt()
                    for (i in 0 until framesToDrop) {
                        if (!videoCapture.read(Mat())) break
                        droppedFrames++
                    }
                }

                // FPS simulados
                val elapsedTimeSec = (System.currentTimeMillis() - startGlobalTime) / 1000.0
                val simulatedFps = if (elapsedTimeSec > 0) processedFrames / elapsedTimeSec else 0.0

                // Stats
                Log.d(
                    "Simulating video - Frame info",
                    "Processed: $processedFrames, Dropped: $droppedFrames, FPS: %.2f".format(
                        simulatedFps
                    )
                )
                yield()
            }
        } catch (e: Exception) {
            videoCapture.release()
            throw e
        }

        videoCapture.release()

        Log.d("Simulating video - End", "Processed: $processedFrames, Dropped: $droppedFrames")

        onSamplesLimitReached(samples)
    }

    override fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {

        //Check if we have to skip this frame
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }
        if (samplesLimit > 0 && samples.size >= samplesLimit) {
            return inputFrame.rgba()
        }


        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        val detectedMarkers = markersDetector!!.detectMarkers(
            inputFrame, markersId!!, corners, ids
        )
        if (detectedMarkers.isEmpty()) {
            return inputFrame.rgba()
        }

        // Prepare the output frame
        val frame = inputFrame.rgba()
        val rgb = Mat()
        Imgproc.cvtColor(frame, rgb, Imgproc.COLOR_RGBA2RGB)

        // Calculate the camera position
        val (amountMarkersEmployed, posArray) = positioner?.getPositionFromArucoMarkers(
            detectedMarkers,
            multipleMarkersBehaviour,
            closestMarkersUsed
        ) ?: return rgb


        //KALMAN FILTER
        val posArrayFiltered = kalmanFilter.updateWithTimestampControl(
            posArray[0].toFloat(),
            posArray[1].toFloat(),
            posArray[2].toFloat()
        )

        //Append the sample
        samples.add(
            TestPositioningRotationSample(
                multipleMarkersBehaviour = multipleMarkersBehaviour,
                amountMarkersEmployed = amountMarkersEmployed,
                kalmanQ = kalmanFilter.covQ,
                kalmanR = kalmanFilter.covR,
                rawX = posArray[0].toFloat(),
                rawY = posArray[1].toFloat(),
                rawZ = posArray[2].toFloat(),
                kalmanX = posArrayFiltered[0],
                kalmanY = posArrayFiltered[1],
                kalmanZ = posArrayFiltered[2]
            )
        )
        if (samplesLimit > 0 && samples.size >= samplesLimit) {
            //Remove the rows that not accomplish with the minimal markers amount required
            if (closestMarkersUsed > 0) {
                samples = samples.filter {
                    it.amountMarkersEmployed == closestMarkersUsed
                } as MutableList<TestPositioningRotationSample>
            }
            onSamplesLimitReached(samples)
        }

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

class TestPositioningRotationSample(
    val timestamp: Long = System.currentTimeMillis(),
    val multipleMarkersBehaviour: MultipleMarkersBehaviour,
    val amountMarkersEmployed: Int,
    val kalmanQ: Double,
    val kalmanR: Double,
    val rawX: Float,
    val rawY: Float,
    val rawZ: Float,
    val kalmanX: Float,
    val kalmanY: Float,
    val kalmanZ: Float,
)