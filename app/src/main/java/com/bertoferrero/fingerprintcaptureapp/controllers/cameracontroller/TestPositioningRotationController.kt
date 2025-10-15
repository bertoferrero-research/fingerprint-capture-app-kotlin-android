// CalibrationManager.kt
package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

import android.util.Log
import com.bertoferrero.fingerprintcaptureapp.lib.markers.MarkersDetector
import com.bertoferrero.fingerprintcaptureapp.lib.opencv.CvCameraViewFrameMockFromImage
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.GlobalPositioner
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.MultipleMarkersBehaviour
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.Position
import com.bertoferrero.fingerprintcaptureapp.lib.positioning.PositionFromMarker
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
    public var arucoDictionaryType: Int = Objdetect.DICT_6X6_250,
    public var markersDefinition: List<MarkerDefinition> = listOf(),
    public var samplesLimit: Int = 0,
    public var multipleMarkersBehaviour: MultipleMarkersBehaviour? = null,
    public var closestMarkersUsed: Int = 0,
    public var testingImageFrame: CvCameraViewFrameMockFromImage? = null,
    public var testingVideoFrame: File? = null,
    public var ransacThreshold: Double = 0.1,
    public var ransacThresholdMax: Double? = 0.4,
    public var sampleSpaceMilliseconds : Int = 1000,
    private val onSamplesLimitReached: (List<TestPositioningRotationSample>) -> Unit,
) : ICameraController {
    // Running variables
    private var running = false
    private var markersDetector: MarkersDetector? = null
    public var kalmanFilter = PositionKalmanFilter()
        private set
    public var samples: MutableList<TestPositioningRotationSample> = mutableListOf()
        private set
    private var lastSampleTimestamp: Long? = null

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
            lastSampleTimestamp = null
            markersDetector =
                MarkersDetector(markersDefinition, arucoDictionaryType, cameraMatrix, distCoeffs)
            positioner = GlobalPositioner(markersDefinition)
            samples = mutableListOf()
            kalmanFilter.initProcess()
        }
    }

    override fun finishProcess() {
        if (running) {
            running = false
            lastSampleTimestamp = null
            markersDetector = null
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
        val frameDelayMillis = if (fps > 0) (1000 / fps).toLong() else 33L //It is the time per frame in ms (time = 1/fps)

        val frame = Mat()
        var processedFrames = 0
        var droppedFrames = 0
        var startGlobalTime: Long? = null

        try {
            while (running) {
                val startTime = System.currentTimeMillis()
                if (startGlobalTime == null){
                    startGlobalTime = startTime
                }

                val readSuccess = videoCapture.read(frame)
                if (!readSuccess || frame.empty()) break

                val simulatedFrame = CvCameraViewFrameMockFromImage(frame.clone())

                // Procesamiento del frame
                val result = processFrame(simulatedFrame, startTime - startGlobalTime)
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
                        Log.d(
                            "Simulating video - Frame info",
                            "Skipping frame"
                        )
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

    override fun processFrame(
        inputFrame: CameraBridgeViewBase.CvCameraViewFrame?
    ): Mat {
        return processFrame(inputFrame, System.currentTimeMillis())
    }

    fun processFrame(
        inputFrame: CameraBridgeViewBase.CvCameraViewFrame?,
        sampleTimestamp: Long): Mat {

        //Check if we have to skip this frame
        if (!running || inputFrame == null) {
            return inputFrame?.rgba() ?: Mat()
        }
        //If it is not the moment of sampling, just skip
        if(lastSampleTimestamp !== null && sampleTimestamp - lastSampleTimestamp!! < sampleSpaceMilliseconds){
            return inputFrame.rgba()
        }

        //Define the multiple markers behaviour to implement
        var multipleMarkersBehaviours: List<MultipleMarkersBehaviour>
        if (multipleMarkersBehaviour == null) {
            multipleMarkersBehaviours = MultipleMarkersBehaviour.entries
        } else {
            multipleMarkersBehaviours =
                listOf(multipleMarkersBehaviour) as List<MultipleMarkersBehaviour>
        }
        //If there is a maximum amount of samples and we reach it, we no need to continue working
        if (samplesLimit > 0 && samples.size >= (samplesLimit * multipleMarkersBehaviours.size)) {
            return inputFrame.rgba()
        }


        // Detect markers
        val corners: MutableList<Mat> = mutableListOf()
        val ids: Mat = Mat()
        val detectedMarkers = markersDetector!!.detectMarkers(
            inputFrame, corners, ids
        )
        if (detectedMarkers.isEmpty()) {
            return inputFrame.rgba()
        }

        // Prepare the output frame
        val frame = inputFrame.rgba()
        val rgb = Mat()
        Imgproc.cvtColor(frame, rgb, Imgproc.COLOR_RGBA2RGB)



        multipleMarkersBehaviours.forEach {
            // Calculate the camera position
            val (position, markersEmployed) = positioner?.getPositionFromArucoMarkers(
                detectedMarkers,
                it,
                closestMarkersUsed,
                ransacThreshold,
                ransacThresholdMax
            ) ?: return rgb


            //KALMAN FILTER (incompatible with multiple markers behaviours
            val posArrayFiltered = if (multipleMarkersBehaviours.size == 1) {
                kalmanFilter.updateWithTimestampControl(
                    position.x.toFloat(),
                    position.y.toFloat(),
                    position.z.toFloat()
                )

            } else {
                floatArrayOf(0.0f, 0.0f, 0.0f)
            }

            //Append the sample
            samples.add(
                TestPositioningRotationSample(
                    timestamp = sampleTimestamp,
                    multipleMarkersBehaviour = it,
                    amountMarkersEmployed = markersEmployed.size,
                    kalmanQ = kalmanFilter.covQ,
                    kalmanR = kalmanFilter.covR,
                    rawX = position.x.toFloat(),
                    rawY = position.y.toFloat(),
                    rawZ = position.z.toFloat(),
                    kalmanX = posArrayFiltered[0],
                    kalmanY = posArrayFiltered[1],
                    kalmanZ = posArrayFiltered[2],
                    markersEmployed = markersEmployed,
                    sampleSpaceMillis = sampleSpaceMilliseconds
                )
            )
            //Update the last sample timestamp
            lastSampleTimestamp = sampleTimestamp

            //If we reached the maximum sample limit, fire the event
            if (samplesLimit > 0 && samples.size >= (samplesLimit * multipleMarkersBehaviours.size)) {
                //Remove the rows that not accomplish with the minimal markers amount required
                /*if (closestMarkersUsed > 0) {
                    samples = samples.filter {
                        it.amountMarkersEmployed == closestMarkersUsed
                    } as MutableList<TestPositioningRotationSample>
                }*/
                onSamplesLimitReached(samples)
            }

            //Print the position
            if (multipleMarkersBehaviours.size == 1) {
                println("Position: (${position.x}, ${position.y}, ${position.z})")
                println("Position Filtered: (${posArrayFiltered[0]}, ${posArrayFiltered[1]}, ${posArrayFiltered[2]})")
                //Also in the image
                org.opencv.imgproc.Imgproc.putText(
                    rgb,
                    "Position: (${(position.x * 1000).roundToInt() / 1000.0}, ${(position.y * 1000).roundToInt() / 1000.0}, ${(position.z * 1000).roundToInt() / 1000.0})",
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
            }
        }



        return rgb
    }
}

class TestPositioningRotationSample(
    val timestamp: Long = System.currentTimeMillis(),
    val multipleMarkersBehaviour: MultipleMarkersBehaviour,
    val amountMarkersEmployed: Int,
    val sampleSpaceMillis: Int,
    val kalmanQ: Double,
    val kalmanR: Double,
    val rawX: Float,
    val rawY: Float,
    val rawZ: Float,
    val kalmanX: Float,
    val kalmanY: Float,
    val kalmanZ: Float,
    val markersEmployed: List<PositionFromMarker>
)