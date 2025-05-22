package com.bertoferrero.fingerprintcaptureapp.viewmodels.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bertoferrero.fingerprintcaptureapp.controllers.settings.CameraSamplerController
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import kotlin.concurrent.schedule

class CameraSamplerViewModel(
    var initDelay: Long = 0
): ViewModel() {

    //UI

    var isRunning by mutableStateOf(false)
        private set

    var initButtonEnabled by mutableStateOf(false)
        private set

    var outputFolderUri: Uri? = null
        private set

    // Tipo de captura: "photo" o "video"
    var captureType by mutableStateOf("photo")
        private set

    fun setCaptureType(type: String) {
        captureType = type
    }

    fun startProcess() {
        isRunning = true
        cameraSamplerController = CameraSamplerController()
    }

    fun stopProcess(){
        isRunning = false
        cameraSamplerController = null
    }

    fun updateOutputFolderUri(uri: Uri) {
        outputFolderUri = uri
        evaluateEnableButtonTest()
    }
    fun evaluateEnableButtonTest() {
        initButtonEnabled = outputFolderUri !== null;
    }

    // PROCESS

    private var cameraSamplerController: CameraSamplerController? = null

    private var timer: java.util.Timer? = null

    private var takeSample = false

    fun takeSample(){
        //Captura directa
        if(initDelay <= 0) {
            takeSample = true
        }
        //Captura retardada
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

    fun processFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?, context: Context): Mat {
        if (!isRunning || !takeSample) {
            return inputFrame?.rgba() ?: Mat()
        }
        takeSample = false

        val frame = cameraSamplerController!!.processAndStoreSampleFrame(
            inputFrame,
            context,
            outputFolderUri!!
        )

        return frame
    }

}
