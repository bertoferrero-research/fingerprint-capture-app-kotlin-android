package com.bertoferrero.fingerprintcaptureapp.viewmodels.testscreens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestPositioningRotationController
import com.bertoferrero.fingerprintcaptureapp.models.SettingsParametersManager

class TestPositioningRotationViewModel : ViewModel() {

    val settingsManager = SettingsParametersManager()
    val cameraController = TestPositioningRotationController(
        settingsManager.markerSize,
        settingsManager.arucoDictionaryType
    )

    var isRunning by mutableStateOf(false)
        private set

    fun startTest() {
        cameraController.initProcess()
        isRunning = true
    }

    fun stopTest() {
        cameraController.finishProcess()
        isRunning = false
    }

    fun updateMarkerSize(size: Float) {
        settingsManager.markerSize = size
        cameraController.markerSize = size
    }

    fun updateArucoType(type: Int) {
        settingsManager.arucoDictionaryType = type
        cameraController.arucoDictionaryType = type
    }
}
