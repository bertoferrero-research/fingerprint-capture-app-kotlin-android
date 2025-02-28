package com.bertoferrero.fingerprintcaptureapp.views
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.bertoferrero.fingerprintcaptureapp.components.OpenCvCamera
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class CalibratingScreen : Screen {
    @Composable
    override fun Content() {
        OpenCvCamera(
            object :
                CameraBridgeViewBase.CvCameraViewListener2 {
                override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                    // Implementa el procesamiento del frame aquí
                    return inputFrame?.rgba() ?: Mat()
                }

                override fun onCameraViewStarted(width: Int, height: Int) {
                    // Implementa la lógica cuando la vista de la cámara comienza
                    var a = 3
                }

                override fun onCameraViewStopped() {
                    // Implementa la lógica cuando la vista de la cámara se detiene
                    var a = 3
                }
            }
        ).Render()
    }
}