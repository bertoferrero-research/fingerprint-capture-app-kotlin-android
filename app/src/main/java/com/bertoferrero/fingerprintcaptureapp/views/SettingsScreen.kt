package com.bertoferrero.fingerprintcaptureapp.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.settings.BatchCalibrationScreen
import com.bertoferrero.fingerprintcaptureapp.views.settings.CalibratingScreen
import com.bertoferrero.fingerprintcaptureapp.views.settings.CalibrationParametersEditorScreen

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center

            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Button(
                        onClick = {
                            navigator.push(CalibratingScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Live Calibrate camera")
                    }
                    Button(
                        onClick = {
                            navigator.push(BatchCalibrationScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Batch Camera Calibration")
                    }
                    Button(
                        onClick = {
                            navigator.push(CalibrationParametersEditorScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Calibration Parameters")
                    }

                }

            }
        }

    }
}