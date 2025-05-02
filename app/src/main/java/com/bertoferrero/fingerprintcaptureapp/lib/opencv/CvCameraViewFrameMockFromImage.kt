package com.bertoferrero.fingerprintcaptureapp.lib.opencv

import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs as cvimage
import org.opencv.imgproc.Imgproc as cvimageproc

class CvCameraViewFrameMockFromImage: CameraBridgeViewBase.CvCameraViewFrame {

    private var mat: Mat? = null
    private var matGray: Mat? = null

    constructor(bytes: ByteArray){
        val matOfByte = org.opencv.core.MatOfByte(*bytes)
        mat = cvimage.imdecode(matOfByte, cvimage.IMREAD_COLOR_RGB)
    }

    constructor(imagePath: String){
        mat = cvimage.imread(imagePath, cvimage.IMREAD_COLOR_RGB)
    }

    constructor(mat: Mat){
        this.mat = mat
    }

    override fun rgba(): Mat? {
        return mat
    }

    override fun gray(): Mat? {
        if(matGray == null) {
            mat?.let {
                matGray = Mat() // Inicializa matGray
                cvimageproc.cvtColor(it, matGray, cvimageproc.COLOR_RGBA2GRAY)
            }
        }
        return matGray
    }

    override fun release() {
        mat = null
        matGray = null
    }
}