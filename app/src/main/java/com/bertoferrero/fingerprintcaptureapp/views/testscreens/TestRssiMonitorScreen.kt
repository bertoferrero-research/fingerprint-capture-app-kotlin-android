package com.bertoferrero.fingerprintcaptureapp.views.testscreens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner
import com.bertoferrero.fingerprintcaptureapp.views.components.resolvePermission
import com.bertoferrero.fingerprintcaptureapp.views.components.resolvePermissions
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_SCAN
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.PermissionsControllerFactory
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import dev.icerock.moko.permissions.location.LOCATION
import kotlin.collections.mutableListOf
import androidx.compose.foundation.lazy.items

class TestRssiMonitorScreen : Screen {

    private var bleScanner: BleScanner? = null
    private var setterRunningContent: (Boolean) -> Unit = {}
    private var setterMacHistoryPrinter: (MutableList<String>) -> Unit = {}
    private var macHistory = mutableListOf<String>()

    @SuppressLint("MutableCollectionMutableState")
    @Composable
    override fun Content() {
        val context = LocalContext.current
        var (macHistoryPrinter, setMacHistoryPrinter) = remember { mutableStateOf(mutableListOf<String>()) }
        val (runningContent, setRunningContent) = remember { mutableStateOf(false) }
        setterRunningContent = setRunningContent
        setterMacHistoryPrinter = setMacHistoryPrinter

        if(BleScanner.checkPermissions()) {
            Scaffold(
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {


                    Button(
                        onClick = {
                            if (!runningContent) {
                                initBleScan(context)
                            } else {
                                stopBleScan(context)
                            }
                        },
                        modifier = Modifier.padding(16.dp)) {
                        Text(if (!runningContent) "Start Scan" else "Stop Scan")
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        item {
                            Text("MAC History:", modifier = Modifier.padding(bottom = 8.dp))
                        }
                        items(macHistory) { mac ->
                            Text(mac)
                        }
                    }
                }
            }
        }
    }


    //Hace falta fine location: https://stackoverflow.com/questions/53050302/bluetoothlescanner-problem-inside-service
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @RequiresApi(Build.VERSION_CODES.O)
    fun initBleScan(context: Context) {
        bleScanner = BleScanner() {
            Log.d(
                "TestRssiMonitorScreen",
                "Device found: ${it.device.address}, RSSI: ${it.rssi}, TxPower: ${it.txPower}"
            )
            macHistory.add(it.device.address)
            setterMacHistoryPrinter(macHistory)
        }
        //Reset the log
        macHistory = mutableListOf()
        setterMacHistoryPrinter(macHistory)
        //Start the scan
        bleScanner?.startScan(context)
        setterRunningContent(true)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopBleScan(context: Context) {
        bleScanner?.stopScan()
        bleScanner = null

        setterRunningContent(false)

        macHistory = mutableListOf()
        setterMacHistoryPrinter(macHistory)
    }

}