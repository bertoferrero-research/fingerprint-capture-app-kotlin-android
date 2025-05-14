package com.bertoferrero.fingerprintcaptureapp.viewmodels.capture

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner
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

    var pendingSamplesToSave by mutableStateOf(false)
        private set

    var capturedSamplesCounter by mutableIntStateOf(0)
        private set

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startCapture(context: Context) {
        capturedSamplesCounter = 0
        macHistory.clear()
        //TODO variables de estado para el running (numero de muestras, tiempo transcurrido)
        bleScanner = BleScanner(context) {
            Log.d(
                "OfflineCapture",
                "Device found: ${it.device.address}, RSSI: ${it.rssi}"
            )
            val macAddress = it.device.address
            if(!macFilterList.isEmpty()){
                //TODO adapt the mac address if it is required
                if(!macFilterList.contains(macAddress)){
                    Log.d(
                        "OfflineCapture",
                        "Device omitted: ${it.device.address}"
                    )
                    return@BleScanner
                }
            }
            macHistory.add(RssiSample(
                macAddress = macAddress,
                rssi = it.rssi,
                posX = x,
                posY = y,
                posZ = z
            ))
            capturedSamplesCounter++
        }
        bleScanner?.startScan()

        //Control time
        if(minutesLimit > 0){
            timer = Timer()
            timer?.schedule(minutesLimit*60000L){
                timer = null
                if(isRunning){
                    stopCapture()
                }
            }
        }

        isRunning = true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopCapture() {
        if(isRunning) {
            timer?.cancel()
            bleScanner?.stopScan()
            bleScanner = null
            if (macHistory.isNotEmpty()) {
                pendingSamplesToSave = true
            }
            isRunning = false
        }
    }

    fun saveCaptureFile(context: Context) {
        pendingSamplesToSave = false
        //TODO
        var samples = macHistory
        if (outputFolderUri == null || samples.isEmpty()) {
            return
        }
        //Transform samples into csv
        val header = listOf(
            "timestamp",
            "mac_address",
            "rssi",
            "pos_x",
            "pos_y",
            "pos_z"
        ).joinToString(",")

        val rows = samples.map { sample ->
            listOf(
                sample.timestamp,
                sample.macAddress,
                sample.rssi,
                sample.posX,
                sample.posY,
                sample.posZ
            ).joinToString(",")
        }

        val csvString = (listOf(header) + rows).joinToString("\n")

        //Save the file
        val folder = DocumentFile.fromTreeUri(context, outputFolderUri!!)
        val timestamp = System.currentTimeMillis()
        val fileName = "offlinecapture__${x}_${y}_${z}__$timestamp.csv"

        val newFile = folder?.createFile("text/csv", fileName)
        newFile?.uri?.let { fileUri ->
            context.contentResolver.openOutputStream(fileUri)?.use { out ->
                out.write(csvString.toByteArray())
            }
        }

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