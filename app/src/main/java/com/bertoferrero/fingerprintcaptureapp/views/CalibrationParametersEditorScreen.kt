package com.bertoferrero.fingerprintcaptureapp.views

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField

class CalibrationParametersEditorScreen : Screen {

    @Composable
    fun getCameraCharacteristics() {
        val context = LocalContext.current
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraList = cameraManager.cameraIdList
        val cameraId = cameraList.first()
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val focalLength =
            characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.first()
        val a = 1

    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val calibrationParameters = remember {
            CameraCalibrationParameters.loadParameters(
                throwExceptionIfEmpty = false
            )
        }

        getCameraCharacteristics()

        BackHandler {
            calibrationParameters.saveParameters()
            navigator.pop()
        }

        Scaffold(
            modifier = Modifier.padding(horizontal = 10.dp)
        ) { innerPadding ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {


                    Text(
                        text = "Camera Matrix",
                        modifier = Modifier.padding(16.dp)
                    )

                    for (i in 0 until calibrationParameters.cameraMatrix.rows()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (j in 0 until calibrationParameters.cameraMatrix.cols()) {

                                NumberField<Double>(
                                    value = calibrationParameters.cameraMatrix.get(i, j)[0],
                                    onValueChange = {
                                        calibrationParameters.cameraMatrix.put(i, j, it)
                                    },
                                    modifier = Modifier.fillParentMaxWidth(0.33f).padding(2.dp)
                                )

                            }
                        }
                    }

                    Text(
                        text = "Distortion coefficients",
                        modifier = Modifier.padding(16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        NumberField<Double>(
                            value = calibrationParameters.distCoeffs.get(0, 0)[0],
                            onValueChange = {
                                calibrationParameters.distCoeffs.put(0, 0, it)
                            },
                            modifier = Modifier.fillParentMaxWidth(0.50f).padding(2.dp),
                            label = { Text("k1") }
                        )
                        NumberField<Double>(
                            value = calibrationParameters.distCoeffs.get(0, 1)[0],
                            onValueChange = {
                                calibrationParameters.distCoeffs.put(0, 1, it)
                            },
                            modifier = Modifier.fillParentMaxWidth(0.50f).padding(2.dp),
                            label = { Text("k2") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        NumberField<Double>(
                            value = calibrationParameters.distCoeffs.get(0, 2)[0],
                            onValueChange = {
                                calibrationParameters.distCoeffs.put(0, 2, it)
                            },
                            modifier = Modifier.fillParentMaxWidth(0.50f).padding(2.dp),
                            label = { Text("p1") }
                        )
                        NumberField<Double>(
                            value = calibrationParameters.distCoeffs.get(0, 3)[0],
                            onValueChange = {
                                calibrationParameters.distCoeffs.put(0, 3, it)
                            },
                            modifier = Modifier.fillParentMaxWidth(0.50f).padding(2.dp),
                            label = { Text("p2") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        NumberField<Double>(
                            value = calibrationParameters.distCoeffs.get(0, 4)[0],
                            onValueChange = {
                                calibrationParameters.distCoeffs.put(0, 4, it)
                            },
                            modifier = Modifier.fillParentMaxWidth(0.50f).padding(2.dp),
                            label = { Text("k3") }
                        )
                    }


                }

            }
        }
    }
}