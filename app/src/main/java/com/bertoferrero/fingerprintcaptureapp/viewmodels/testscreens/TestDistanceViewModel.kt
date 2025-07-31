package com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestDistanceCameraController
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager
import kotlinx.coroutines.launch

class TestDistanceViewModel : ViewModel() {

    val settingsManager = SettingsParametersManager()
    private var _cameraController: TestDistanceCameraController? = null
    
    val cameraController: TestDistanceCameraController
        get() = _cameraController ?: throw IllegalStateException("Camera controller not initialized. Call initializeController first.")

    var isRunning by mutableStateOf(false)
        private set

    fun initializeController(context: Context) {
        if (_cameraController == null) {
            _cameraController = TestDistanceCameraController(
                context = context,
                markerSize = settingsManager.markerSize,
                arucoDictionaryType = settingsManager.arucoDictionaryType,
                testingImageFrame = null
            )
        }
    }

    fun startTest() {
        cameraController.initProcess()
        isRunning = true
    }

    fun stopTest() {
        cameraController.finishProcess()
        isRunning = false
    }

    fun updateMarkerSize(size: Float) {
        cameraController.markerSize = size
        settingsManager.markerSize = size
    }

    fun updateMethod(method: Int) {
        cameraController.method = method
    }

    fun updateArucoType(type: Int) {
        settingsManager.arucoDictionaryType = type
        cameraController.arucoDictionaryType = type
    }
}
