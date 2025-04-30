package com.bertoferrero.fingerprintcaptureapp.lib.opencv

import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs as cvimage
import org.opencv.imgproc.Imgproc as cvimageproc

class CvCameraViewFrameMockFromImage(
    imagePath: String
) : CameraBridgeViewBase.CvCameraViewFrame {

    private var mat: Mat? = cvimage.imread(imagePath, cvimage.IMREAD_COLOR_RGB)
    private var matGray: Mat? = null

    override fun rgba(): Mat? {
        return mat
    }

    override fun gray(): Mat? {
        return matGray
    }

    override fun release() {
        mat = null
        matGray = null
    }

    init {
        mat?.let {
            cvimageproc.cvtColor(it, matGray, cvimageproc.COLOR_RGBA2GRAY)
        }
    }
}