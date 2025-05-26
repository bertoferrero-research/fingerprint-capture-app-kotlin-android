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

class CameraSamplerViewModel(
): ViewModel() {

    //UI

    var initDelay by mutableLongStateOf(0)

    var isRunning by mutableStateOf(false)
        private set

    var initButtonEnabled by mutableStateOf(false)
        private set

    var outputFolderUri: Uri? = null
        private set

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

        Toast.makeText(context, "Sample captured", Toast.LENGTH_SHORT).show()

        return frame
    }

}
