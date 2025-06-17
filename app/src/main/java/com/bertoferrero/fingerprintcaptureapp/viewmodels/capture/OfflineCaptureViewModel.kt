package com.bertoferrero.fingerprintcaptureapp.viewmodels.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner
import com.bertoferrero.fingerprintcaptureapp.services.OfflineCaptureService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Timer
import kotlin.concurrent.schedule

class OfflineCaptureViewModel(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var minutesLimit: Int = 0
): ViewModel() {

    //UI

    var isRunning by mutableStateOf(false)
        private set

    var capturedSamplesCounter by mutableIntStateOf(0)

    var initButtonEnabled by mutableStateOf(false)
        private set

    var macHistory = mutableListOf<RssiSample>()

    var outputFolderUri: Uri? = null
        private set

    private var timer: java.util.Timer? = null

    //TODO better in controller
    var macFilterList: List<String> = listOf()
        private set

    fun evaluateEnableButtonTest() {
        initButtonEnabled = outputFolderUri !== null;
    }

    fun loadMacFilterListFromJson(jsonString: String) {
        val type = object : TypeToken<List<String>>() {}.type
        macFilterList = Gson().fromJson(jsonString, type)
    }

    fun updateOutputFolderUri(uri: Uri) {
        outputFolderUri = uri
        evaluateEnableButtonTest()
    }

    // END - UI

    // PROCESS

    var bleScanner: BleScanner? = null

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startCapture(context: Context) {
        capturedSamplesCounter = 0
        // Lanzar el Foreground Service con los parámetros
        val intent = Intent(context, OfflineCaptureService::class.java).apply {
            putExtra(OfflineCaptureService.EXTRA_X, x)
            putExtra(OfflineCaptureService.EXTRA_Y, y)
            putExtra(OfflineCaptureService.EXTRA_Z, z)
            putExtra(OfflineCaptureService.EXTRA_MINUTES_LIMIT, minutesLimit)
            putStringArrayListExtra(OfflineCaptureService.EXTRA_MAC_FILTER_LIST, ArrayList(macFilterList))
            outputFolderUri?.let { putExtra(OfflineCaptureService.EXTRA_OUTPUT_FOLDER_URI, it) }
        }
        context.startForegroundService(intent)
        isRunning = true
    }

    fun stopCapture(context: Context) {
        // Detener el Foreground Service
        val intent = Intent(context, OfflineCaptureService::class.java)
        context.stopService(intent)
        isRunning = false
    }

    // END - PROCESS
}

class RssiSample(
    val timestamp: Long = System.currentTimeMillis(),
    val macAddress: String,
    val rssi: Int,
    val posX: Float,
    val posY: Float,
    val posZ: Float
)