package com.bertoferrero.fingerprintcaptureapp.components

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import org.opencv.android.JavaCamera2View


/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */
@SuppressLint("ViewConstructor")
class FixedFocusJavaCamera2View(context: Context, cameraId: Int) : JavaCamera2View(context, cameraId) {


    override fun allocateSessionStateCallback(): CameraCaptureSession.StateCallback {
        return object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                if (null == mCameraDevice) {
                    return  // camera is already closed
                }
                mCaptureSession = cameraCaptureSession
                try {
                    mPreviewRequestBuilder!!.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF
                    )
                    mPreviewRequestBuilder!!.set(
                        CaptureRequest.LENS_FOCUS_DISTANCE,
                        0.0f
                    )
                    mPreviewRequestBuilder!!.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    )

                    mCaptureSession!!.setRepeatingRequest(
                        mPreviewRequestBuilder!!.build(),
                        null,
                        mBackgroundHandler
                    )
                } catch (e: Exception) {
                }
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            }
        }
    }

}
