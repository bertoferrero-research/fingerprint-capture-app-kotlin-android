package com.bertoferrero.fingerprintcaptureapp.viewmodels.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bertoferrero.fingerprintcaptureapp.controllers.settings.CameraSamplerController
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import kotlin.concurrent.schedule

/**
 * ViewModel for the Camera Sampler feature.
 * Handles both photo and video capture, including initial delay logic, output folder selection,
 * and communication with the CameraSamplerController.
 */
class CameraSamplerViewModel(
): ViewModel() {

    //UI

    var initDelay by mutableLongStateOf(0)

    // Indicates if the process (photo or video) is running
    var isRunning by mutableStateOf(false)
        private set

    // Enables the start button if an output folder is selected
    var initButtonEnabled by mutableStateOf(false)
        private set

    // Output folder URI selected by the user
    var outputFolderUri: Uri? = null
        private set

    // Capture type: "photo" or "video"
    var captureType by mutableStateOf("photo")
        private set

    /**
     * Sets the capture type (photo or video)
     */
    fun setCaptureType(type: String) {
        captureType = type
    }

    /**
     * Starts the capture process. If video, applies the initial delay before starting recording.
     * @param width Frame width (for video)
     * @param height Frame height (for video)
     * @param fps Frames per second (for video)
     */
    fun startProcess(width: Int = 0, height: Int = 0, fps: Double = 30.0) {
        isRunning = true
        cameraSamplerController = CameraSamplerController()
        if (captureType == "video" && outputFolderUri != null && width > 0 && height > 0) {
            videoWidth = width
            videoHeight = height
            videoFps = fps
            videoStarted = false // Will be set to true after delay
            // Apply initial delay before starting video recording
            if (initDelay <= 0) {
                startVideoRecordingInternal()
            } else {
                timer?.cancel()
                timer = java.util.Timer()
                // Schedule the start of video recording after the delay
                timer?.schedule(initDelay) {
                    startVideoRecordingInternal()
                }
            }
        }
    }

    /**
     * Internal helper to start video recording after the delay.
     */
    private fun startVideoRecordingInternal() {
        videoStarted = true
        cameraSamplerController?.startVideoRecording(
            context = lastContext!!,
            outputFolderUri = outputFolderUri!!,
            width = videoWidth,
            height = videoHeight,
            fps = videoFps
        )
    }

    /**
     * Stops the capture process. If video, stops and finalizes the video file.
     */
    fun stopProcess() {
        if (captureType == "video" && videoStarted && outputFolderUri != null) {
            cameraSamplerController?.stopVideoRecording(lastContext!!, outputFolderUri!!)
            videoStarted = false
        }
        isRunning = false
        cameraSamplerController = null
    }

    /**
     * Updates the output folder URI and enables the start button if valid.
     */
    fun updateOutputFolderUri(uri: Uri) {
        outputFolderUri = uri
        evaluateEnableButtonTest()
    }
    fun evaluateEnableButtonTest() {
        initButtonEnabled = outputFolderUri !== null;
    }

    // --- Internal State ---

    // Controller for handling the actual capture and storage logic
    private var cameraSamplerController: CameraSamplerController? = null

    // Timer for handling initial delay (photo or video)
    private var timer: java.util.Timer? = null

    // Flag to trigger photo capture after delay
    private var takeSample = false

    // --- Video State ---
    // Indicates if video recording has started (after delay)
    private var videoStarted = false
    // Video frame width
    private var videoWidth = 0
    // Video frame height
    private var videoHeight = 0
    // Video frames per second
    private var videoFps = 30.0

    // Last context used (needed for controller calls)
    private var lastContext: Context? = null

    /**
     * Triggers a photo capture, applying the initial delay if needed.
     */
    fun takeSample(){
        // Immediate capture
        if(initDelay <= 0) {
            takeSample = true
        }
        // Delayed capture
        else{
            if (timer != null) {
                timer?.cancel()
            }
            timer = java.util.Timer()
            timer?.schedule(initDelay){
                timer = null
                takeSample = true
            }
        }
    }

    /**
     * Processes each camera frame. Handles:
     *  - Video: Only writes frames after the delay (when videoStarted is true)
     *  - Photo: Only processes frame when takeSample is true (after delay)
     *  - Otherwise, returns the preview frame
     * @param inputFrame The camera frame
     * @param context Android context
     * @return The frame to display
     */
    fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?, context: Context): Mat {
        lastContext = context
        if (!isRunning) {
            return inputFrame?.rgba() ?: Mat()
        }
        if (captureType == "video") {
            if (videoStarted) {
                // Save frame to video only after delay
                return cameraSamplerController!!.processAndStoreSampleFrame(
                    inputFrame,
                    context,
                    outputFolderUri!!,
                    isVideo = true
                )
            } else {
                // Show preview but do not record frames during delay
                return inputFrame?.rgba() ?: Mat()
            }
        }
        if (!takeSample) {
            return inputFrame?.rgba() ?: Mat()
        }
        takeSample = false
        val frame = cameraSamplerController!!.processAndStoreSampleFrame(
            inputFrame,
            context,
            outputFolderUri!!,
            isVideo = false
        )

        Toast.makeText(context, "Sample captured", Toast.LENGTH_SHORT).show()

        return frame
    }

}
