package com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller

interface ICameraController {
    fun initProcess()
    fun finishProcess()
    fun processFrame(inputFrame: org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame?): org.opencv.core.Mat
}