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
import com.bertoferrero.fingerprintcaptureapp.views.capture.OfflineCaptureScreen
import com.bertoferrero.fingerprintcaptureapp.views.settings.CalibratingScreen
import com.bertoferrero.fingerprintcaptureapp.views.settings.CalibrationParametersEditorScreen
import com.bertoferrero.fingerprintcaptureapp.views.testscreens.TestDistanceScreen
import com.bertoferrero.fingerprintcaptureapp.views.testscreens.TestPositioningRotationScreen
import com.bertoferrero.fingerprintcaptureapp.views.testscreens.TestPositioningTrilaterationScreen
import com.bertoferrero.fingerprintcaptureapp.views.testscreens.TestRssiMonitorScreen
import com.bertoferrero.fingerprintcaptureapp.views.testscreens.TestTrilaterationLibraryScreen

class MainScreen : Screen {
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
                            navigator.push(OfflineCaptureScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Offline Capture")
                    }
                    Button(
                        onClick = {
                            navigator.push(SettingsScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Settings & Tools")
                    }
                    Button(
                        onClick = {
                            navigator.push(TestsScreen())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tests")
                    }

                }

            }
        }

    }
}