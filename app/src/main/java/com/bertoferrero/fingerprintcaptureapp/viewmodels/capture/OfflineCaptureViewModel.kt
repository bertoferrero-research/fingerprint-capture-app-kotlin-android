package com.bertoferrero.fingerprintcaptureapp.viewmodels.capture

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner
import com.bertoferrero.fingerprintcaptureapp.services.OfflineCaptureService
import com.bertoferrero.fingerprintcaptureapp.services.OfflineCaptureService.Companion.ACTION_TIMER_FINISHED
import com.bertoferrero.fingerprintcaptureapp.services.OfflineCaptureService.Companion.ACTION_SAMPLE_CAPTURED
import com.bertoferrero.fingerprintcaptureapp.services.OfflineCaptureService.Companion.ACTION_SCAN_FAILED
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
//import dagger.hilt.android.lifecycle.HiltViewModel

//No consigo que el hilt funcione con esta version de kotlin
//@HiltViewModel
class OfflineCaptureViewModel(
    application: Application
): AndroidViewModel(application) {

    //UI

    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var minutesLimit: Int = 0
    var initDelaySeconds: Int = 0

    var isRunning by mutableStateOf(false)
        private set

    var capturedSamplesCounter by mutableIntStateOf(0)
        internal set

    var initButtonEnabled by mutableStateOf(false)
        private set

    var outputFolderUri: Uri? = null
        private set

    private var timer: java.util.Timer? = null

    //TODO better in controller
    var macFilterList: List<String> = listOf()
        private set

    // BroadcastReceiver for service events
    private var broadcastReceiver: BroadcastReceiver? = null

    private fun updateInitButtonState() {
        initButtonEnabled = outputFolderUri != null
    }

    fun loadMacFilterListFromJson(jsonString: String) {
        val type = object : TypeToken<List<String>>() {}.type
        macFilterList = Gson().fromJson(jsonString, type)
    }

    fun updateOutputFolderUri(uri: Uri) {
        outputFolderUri = uri
        updateInitButtonState()
    }

    // END - UI

    // Broadcast receiver management
    fun registerBroadcastReceiver() {
        if (broadcastReceiver == null) {
            broadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        OfflineCaptureService.ACTION_TIMER_FINISHED -> {
                            context?.let { stopCapture(it) }
                        }
                        OfflineCaptureService.ACTION_SAMPLE_CAPTURED -> {
                            capturedSamplesCounter = intent.getIntExtra(OfflineCaptureService.EXTRA_CAPTURED_SAMPLES, 0)
                        }
                        OfflineCaptureService.ACTION_SCAN_FAILED -> {
                            context?.let {
                                Toast.makeText(
                                    it,
                                    "BL scan failed. Please, check if the BL is enabled.",
                                    Toast.LENGTH_LONG
                                ).show()
                                isRunning = false
                            }
                        }
                    }
                }
            }
            
            val intentFilter = IntentFilter().apply {
                addAction(OfflineCaptureService.ACTION_TIMER_FINISHED)
                addAction(OfflineCaptureService.ACTION_SAMPLE_CAPTURED)
                addAction(OfflineCaptureService.ACTION_SCAN_FAILED)
            }
            
            ContextCompat.registerReceiver(
                getApplication(),
                broadcastReceiver!!,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    fun unregisterBroadcastReceiver() {
        broadcastReceiver?.let { receiver ->
            try {
                getApplication<Application>().unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Receiver was already unregistered, which is fine
                Log.d("OfflineCaptureViewModel", "Receiver was already unregistered")
            }
            broadcastReceiver = null
        }
    }

    // PROCESS

    var bleScanner: BleScanner? = null

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startCapture(context: Context): Boolean {
        // Validate input parameters
        if (outputFolderUri == null) {
            Log.e("OfflineCaptureViewModel", "Output folder URI is null")
            return false
        }
        
        if (minutesLimit < 0) {
            Log.e("OfflineCaptureViewModel", "Minutes limit cannot be negative")
            return false
        }

        if (initDelaySeconds < 0) {
            Log.e("OfflineCaptureViewModel", "Initialization delay cannot be negative")
            return false
        }
        
        capturedSamplesCounter = 0
        
        try {
            // Lanzar el Foreground Service con los parámetros
            val intent = Intent(context, OfflineCaptureService::class.java).apply {
                putExtra(OfflineCaptureService.EXTRA_X, x)
                putExtra(OfflineCaptureService.EXTRA_Y, y)
                putExtra(OfflineCaptureService.EXTRA_Z, z)
                putExtra(OfflineCaptureService.EXTRA_INIT_DELAY_SECONDS, initDelaySeconds)
                putExtra(OfflineCaptureService.EXTRA_MINUTES_LIMIT, minutesLimit)
                putStringArrayListExtra(OfflineCaptureService.EXTRA_MAC_FILTER_LIST, ArrayList(macFilterList))
                outputFolderUri?.let { putExtra(OfflineCaptureService.EXTRA_OUTPUT_FOLDER_URI, it) }
            }
            context.startForegroundService(intent)
            isRunning = true
            return true
        } catch (e: Exception) {
            Log.e("OfflineCaptureViewModel", "Error starting capture service", e)
            return false
        }
    }

    fun stopCapture(context: Context) {
        // Detener el Foreground Service
        val intent = Intent(context, OfflineCaptureService::class.java)
        context.stopService(intent)
        isRunning = false
    }

    // END - PROCESS

    override fun onCleared() {
        super.onCleared()
        unregisterBroadcastReceiver()
    }
}