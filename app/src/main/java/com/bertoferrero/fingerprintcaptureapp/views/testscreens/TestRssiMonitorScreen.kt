package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bertoferrero.fingerprintcaptureapp.views.components.OpenCvCamera
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresPermission
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bertoferrero.fingerprintcaptureapp.components.permissions.BluetoothScanPermissionsViewModel
import com.bertoferrero.fingerprintcaptureapp.components.permissions.CameraPermissionsViewModel
import com.bertoferrero.fingerprintcaptureapp.components.permissions.CoarseLocationPermissionsViewModel
import com.bertoferrero.fingerprintcaptureapp.controllers.cameracontroller.TestDistanceCameraController
import com.bertoferrero.fingerprintcaptureapp.models.ViewParametersManager
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoDictionaryType
import com.bertoferrero.fingerprintcaptureapp.views.components.ArucoTypeDropdownMenu
import com.bertoferrero.fingerprintcaptureapp.views.components.NumberField
import com.bertoferrero.fingerprintcaptureapp.views.components.SimpleDropdownMenu
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.PermissionsControllerFactory
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import org.opencv.android.CameraBridgeViewBase
import org.opencv.core.Mat

class TestRssiMonitorScreen : Screen {

    private var btManager: BluetoothManager? = null
    private var btAdapter: BluetoothAdapter? = null
    private var btScanner: BluetoothLeScanner? = null

    private var setterRunningContent: (Boolean) -> Unit = {}

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val (runningContent, setRunningContent) = remember { mutableStateOf(false) }
        setterRunningContent = setRunningContent

        if (checkPermissions()) {
            Scaffold(
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {


                    Button(
                        onClick = {
                            if (!runningContent) {
                                initBleScan(context)
                            } else {
                                stopBleScan(context)
                            }
                        }) {
                        if (!runningContent) {
                            Text("Start test")
                        } else {
                            Text("Stop test")
                        }
                    }
                }
            }
        }
    }

    @Composable
    protected fun checkPermissions(): Boolean {
        return checkLocationPermissions() && checkBlePermissions()
    }

    @Composable
    protected fun checkBlePermissions(): Boolean {
        val factory: PermissionsControllerFactory = rememberPermissionsControllerFactory()
        val controller: PermissionsController =
            remember(factory) { factory.createPermissionsController() }
        BindEffect(controller)

        val viewModel = viewModel {
            BluetoothScanPermissionsViewModel(controller)
        }

        when (viewModel.state) {
            PermissionState.Granted -> {
                return true
            }

            else -> {
                viewModel.provideOrRequestPermission()
            }
        }
        return false
    }

    @Composable
    protected fun checkLocationPermissions(): Boolean {
        val factory: PermissionsControllerFactory = rememberPermissionsControllerFactory()
        val controller: PermissionsController = remember(factory) { factory.createPermissionsController() }
        BindEffect(controller)

        val viewModel = viewModel{
            CoarseLocationPermissionsViewModel(controller)
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
                viewModel.provideOrRequestPermission()
            }
        }
        return false
    }

    //Hace falta fine location: https://stackoverflow.com/questions/53050302/bluetoothlescanner-problem-inside-service
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun initBleScan(context: Context) {
        btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager?.adapter
        btScanner = btAdapter?.bluetoothLeScanner

        if (btAdapter != null && !btAdapter!!.isEnabled) {
            Toast.makeText(context, "Please, enable the bluetooth", Toast.LENGTH_SHORT).show()
        } else {
            //BL
            //val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            //context.registerReceiver(blReceiver, filter)
            //btAdapter?.startDiscovery()

            //BLE
            btScanner?.startScan(bleScanCallback)

            setterRunningContent(true)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopBleScan(context: Context) {
        //BL
        //context.unregisterReceiver(blReceiver)

        //BLE
        btScanner?.stopScan(bleScanCallback)

        setterRunningContent(false)
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                Log.d("TestRssiMonitorScreen", "Device found: ${it.device.address}")
                //Toast.makeText(context, "Dispositivo encontrado: ${it.device.address}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach {
                Log.d("TestRssiMonitorScreen", "Batch device found: ${it.device.address}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("TestRssiMonitorScreen", "Scan failed with error: $errorCode")
            //Toast.makeText(context, "Error en el escaneo: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    private val blReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    Log.d("TestRssiMonitorScreen", "Device found: ${it.address}")
                    //Toast.makeText(context, "Dispositivo encontrado: ${it.address}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}