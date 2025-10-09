package com.bertoferrero.fingerprintcaptureapp.viewmodels.capture

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import com.bertoferrero.fingerprintcaptureapp.services.RssiCaptureService
import com.bertoferrero.fingerprintcaptureapp.services.RssiCaptureService.Companion.ACTION_TIMER_FINISHED
import com.bertoferrero.fingerprintcaptureapp.services.RssiCaptureService.Companion.ACTION_SAMPLE_CAPTURED
import com.bertoferrero.fingerprintcaptureapp.services.RssiCaptureService.Companion.ACTION_SCAN_FAILED
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
                        RssiCaptureService.ACTION_TIMER_FINISHED -> {
                            context?.let { stopCapture(it) }
                        }
                        RssiCaptureService.ACTION_SAMPLE_CAPTURED -> {
                            capturedSamplesCounter = intent.getIntExtra(RssiCaptureService.EXTRA_CAPTURED_SAMPLES, 0)
                        }
                        RssiCaptureService.ACTION_SCAN_FAILED -> {
                            context?.let {
                                Toast.makeText(
                                    it,
                                    "BLE scan failed. Please check if Bluetooth is enabled.",
                                    Toast.LENGTH_LONG
                                ).show()
                                isRunning = false
                            }
                        }
                    }
                }
            }
            
            val intentFilter = IntentFilter().apply {
                addAction(RssiCaptureService.ACTION_TIMER_FINISHED)
                addAction(RssiCaptureService.ACTION_SAMPLE_CAPTURED)
                addAction(RssiCaptureService.ACTION_SCAN_FAILED)
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

    /**
     * Verifica si la app está exenta de optimización de batería
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // En versiones anteriores no hay optimización de batería
        }
    }

    /**
     * Solicita al usuario que deshabilite la optimización de batería para esta app
     */
    fun requestBatteryOptimizationExemption(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = context.packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            } else {
                null // Ya está exenta
            }
        } else {
            null // No necesario en versiones anteriores
        }
    }

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

        // Verificar optimización de batería
        if (!isBatteryOptimizationIgnored(context)) {
            Log.w("OfflineCaptureViewModel", "Battery optimization is enabled - service may be killed")
            
            // Intentar abrir configuración automáticamente
            try {
                val batteryIntent = requestBatteryOptimizationExemption(context)
                if (batteryIntent != null) {
                    context.startActivity(batteryIntent)
                    Toast.makeText(
                        context,
                        "Please allow the app to run in background for continuous capture",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Recommended: Disable battery optimization for continuous capture",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("OfflineCaptureViewModel", "Error opening battery optimization settings", e)
                Toast.makeText(
                    context,
                    "Could not open battery settings. Please configure it manually in Settings",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        capturedSamplesCounter = 0
        
        try {
            // Lanzar el Foreground Service con los parámetros
            val intent = Intent(context, RssiCaptureService::class.java).apply {
                putExtra(RssiCaptureService.EXTRA_X, x)
                putExtra(RssiCaptureService.EXTRA_Y, y)
                putExtra(RssiCaptureService.EXTRA_Z, z)
                putExtra(RssiCaptureService.EXTRA_INIT_DELAY_SECONDS, initDelaySeconds)
                putExtra(RssiCaptureService.EXTRA_MINUTES_LIMIT, minutesLimit)
                putStringArrayListExtra(RssiCaptureService.EXTRA_MAC_FILTER_LIST, ArrayList(macFilterList))
                outputFolderUri?.let { putExtra(RssiCaptureService.EXTRA_OUTPUT_FOLDER_URI, it) }
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
        val intent = Intent(context, RssiCaptureService::class.java)
        context.stopService(intent)
        isRunning = false
    }

    // END - PROCESS

    override fun onCleared() {
        super.onCleared()
        unregisterBroadcastReceiver()
    }
}