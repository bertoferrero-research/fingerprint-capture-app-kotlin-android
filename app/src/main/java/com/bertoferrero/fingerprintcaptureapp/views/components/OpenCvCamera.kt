package com.bertoferrero.fingerprintcaptureapp.views.components

import android.os.Build
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bertoferrero.fingerprintcaptureapp.components.CameraPermissionsViewModel
import com.bertoferrero.fingerprintcaptureapp.components.FixedFocusJavaCamera2View
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.PermissionsControllerFactory
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import org.opencv.android.CameraBridgeViewBase

class OpenCvCamera (
    private val cameraListener :CameraBridgeViewBase.CvCameraViewListener2
) {


    @Composable
    protected fun checkPermissions(): Boolean {
        val factory: PermissionsControllerFactory = rememberPermissionsControllerFactory()
        val controller: PermissionsController = remember(factory) { factory.createPermissionsController() }
        BindEffect(controller)

        val viewModel = viewModel{
            CameraPermissionsViewModel(controller)
        }

        when(viewModel.state){
            PermissionState.Granted -> {
                return true
            }
            /*PermissionState.Denied -> {
                cameraPermissionGranted = false
            }
            PermissionState.DeniedAlways -> {
                cameraPermissionGranted = false
            }*/
            else -> {
                viewModel.provideOrRequestCameraPermission()
            }
        }
        return false
    }

    @Composable
    fun Render(
        modifier: Modifier = Modifier
    ){
        if(checkPermissions()){
            RenderCameraView(modifier)
            //RenderNativeCameraView()
        }
    }


    /**
     * This method should be called to render the camera view
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    private fun RenderCameraView(
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val cameraView = remember { FixedFocusJavaCamera2View(context, 0) }

        AndroidView(
            factory = { cameraView },
            modifier = modifier.fillMaxSize(),
            update = {
                it.visibility = SurfaceView.VISIBLE
                //it.setCameraIndex(CameraCharacteristics.LENS_FACING_FRONT)
                //it.setMaxFrameSize(80000, 80000)
                it.enableFpsMeter()
                it.setCvCameraViewListener(cameraListener)
                it.setCameraPermissionGranted()
                it.focusable = CameraBridgeViewBase.NOT_FOCUSABLE
                it.enableView()
            },
            onRelease = {
                it.disableView()
            }
        )
    }

    @Composable
    private fun RenderNativeCameraView(){
        val context = LocalContext.current
        val lifeCycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
        val previewView = remember { PreviewView(context) }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val cameraSelector =
                    CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                cameraProvider.bindToLifecycle(lifeCycleOwner, cameraSelector, preview)
            })
    }

}