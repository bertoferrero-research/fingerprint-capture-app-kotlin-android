package com.bertoferrero.fingerprintcaptureapp.views

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import com.bertoferrero.fingerprintcaptureapp.models.CameraCalibrationParameters
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField

class CalibrationParametersEditorScreen : Screen {
    @Composable
    override fun Content() {

        val context = LocalContext.current
        val calibrationParameters = remember { CameraCalibrationParameters.loadParameters(context) }

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                for (i in 0 until calibrationParameters.cameraMatrix.rows()) {
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (j in 0 until calibrationParameters.cameraMatrix.cols()) {
                                item {
                                    NumberField<Double>(
                                        value = calibrationParameters.cameraMatrix.get(i, j)[0],
                                        onValueChange = {
                                            calibrationParameters.cameraMatrix.put(i, j, it)
                                        }
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}