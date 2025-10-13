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
import com.bertoferrero.fingerprintcaptureapp.controllers.capture.OnlineCaptureController
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner
import com.bertoferrero.fingerprintcaptureapp.services.RssiCaptureService
import com.bertoferrero.fingerprintcaptureapp.services.RssiCaptureService.Companion.ACTION_TIMER_FINISHED
import com.bertoferrero.fingerprintcaptureapp.services.RssiCaptureService.Companion.ACTION_SAMPLE_CAPTURED
import com.bertoferrero.fingerprintcaptureapp.services.RssiCaptureService.Companion.ACTION_SCAN_FAILED
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * ViewModel para la captura online que combina señales RSSI BLE con imágenes OpenCV.
 * Coordina el RssiCaptureService para BLE y un controlador propio para imágenes.
 * Los parámetros no son persistentes y se configuran por sesión.
 */
class OnlineCaptureViewModel(
    application: Application
): AndroidViewModel(application) {

    // CONFIGURACIÓN (no persistente)

    /** Posición X para las muestras RSSI */
    var x: Float = 0f

    /** Posición Y para las muestras RSSI */
    var y: Float = 0f

    /** Posición Z para las muestras RSSI */
    var z: Float = 0f

    /** Delay inicial antes de comenzar captura (segundos) */
    var initDelaySeconds: Int = 0

    /** Límite de tiempo total de captura (minutos, 0 = ilimitado) */
    var minutesLimit: Int = 0

    /** Frecuencia de muestreo para imágenes (milisegundos) */
    var samplingFrequency: Int = 1000

    /** Límite de imágenes a capturar (0 = ilimitado) */
    var imageLimit: Int = 0

    /** Lista de direcciones MAC a filtrar para BLE */
    var macFilterList: MutableList<String> = mutableListOf()

    // ESTADO DE LA UI

    /** Indica si la captura está en ejecución */
    var isRunning by mutableStateOf(false)
        private set

    /** Contador de muestras RSSI capturadas (del servicio BLE) */
    var capturedSamplesCounter by mutableIntStateOf(0)
        private set

    /** Contador de imágenes capturadas (del controlador) */
    var capturedImagesCounter by mutableIntStateOf(0)
        private set

    /** Indica si el botón de inicio está habilitado */
    var initButtonEnabled by mutableStateOf(false)
        private set

    /** URI de la carpeta de salida para guardar archivos */
    var outputFolderUri: Uri? by mutableStateOf(null)
        private set

    // CONTROLADOR DE IMÁGENES

    /** Controlador para captura de imágenes OpenCV */
    private val imageController: OnlineCaptureController by lazy {
        OnlineCaptureController(
            getApplication<Application>().applicationContext
        ) { imageCount ->
            // Callback cuando se captura una imagen
            updateCapturedImagesCounter(imageCount)
            
            // Nota: El límite de imágenes NO debe detener el proceso.
            // El proceso se detiene únicamente por:
            // 1. El límite de tiempo del RssiCaptureService
            // 2. Cuando el usuario pulse detener manualmente
            // La cámara puede ser estática y no requerir más imágenes
        }
    }

    // GESTIÓN DE BROADCAST RECEIVER

    private var broadcastReceiver: BroadcastReceiver? = null
    private var timer: java.util.Timer? = null

    /**
     * Registra el broadcast receiver para escuchar eventos del RssiCaptureService.
     */
    fun registerBroadcastReceiver() {
        if (broadcastReceiver == null) {
            broadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        RssiCaptureService.ACTION_SCAN_BEGINS -> {                            
                            imageController.initProcess()
                        }
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
                addAction(RssiCaptureService.ACTION_SCAN_BEGINS)
            }
            
            ContextCompat.registerReceiver(
                getApplication(),
                broadcastReceiver!!,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    /**
     * Desregistra el broadcast receiver.
     */
    fun unregisterBroadcastReceiver() {
        broadcastReceiver?.let { receiver ->
            try {
                getApplication<Application>().unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Receiver was already unregistered, which is fine
                Log.d("OnlineCaptureViewModel", "Receiver was already unregistered")
            }
            broadcastReceiver = null
        }
    }

    // GESTIÓN DE CONFIGURACIÓN

    /**
     * Actualiza la URI de la carpeta de salida y evalúa si habilitar el botón de inicio.
     */
    fun updateOutputFolderUri(uri: Uri) {
        outputFolderUri = uri
        evaluateEnableButtonTest()
    }

    /**
     * Evalúa si el botón de inicio debe estar habilitado.
     */
    private fun evaluateEnableButtonTest() {
        initButtonEnabled = outputFolderUri != null && !isRunning
    }

    /**
     * Carga la lista de filtros MAC desde un string JSON.
     */
    fun loadMacFilterListFromJson(jsonString: String) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            val loadedList: List<String> = Gson().fromJson(jsonString, type)
            macFilterList.clear()
            macFilterList.addAll(loadedList)
        } catch (e: Exception) {
            Log.e("OnlineCaptureViewModel", "Error parsing MAC filter JSON", e)
            throw e
        }
    }

    /**
     * Configura el controlador de imágenes con los parámetros actuales.
     */
    private fun configureImageController() {
        imageController.apply {
            samplingFrequency = this@OnlineCaptureViewModel.samplingFrequency
            imageLimit = this@OnlineCaptureViewModel.imageLimit
            outputFolderUri = this@OnlineCaptureViewModel.outputFolderUri
        }
    }

    /**
     * Obtiene la referencia al controlador de imágenes para uso en la UI.
     * La UI necesita esta referencia para integrar con OpenCvCamera.
     * Nota: La configuración se realiza una sola vez en startCapture().
     */
    fun getCameraController(): OnlineCaptureController {
        return imageController
    }

    // GESTIÓN DE CAPTURA

    /**
     * Verifica si la app está exenta de optimización de batería.
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
     * Solicita al usuario que deshabilite la optimización de batería para esta app.
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

    /**
     * Inicia la captura online (BLE + imágenes).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startCapture(context: Context): Boolean {
        // Validar parámetros de entrada
        if (outputFolderUri == null) {
            Log.e("OnlineCaptureViewModel", "Output folder URI is null")
            return false
        }
        
        if (minutesLimit < 0) {
            Log.e("OnlineCaptureViewModel", "Minutes limit cannot be negative")
            return false
        }

        if (initDelaySeconds < 0) {
            Log.e("OnlineCaptureViewModel", "Init delay cannot be negative")
            return false
        }

        // Mostrar mensaje sobre optimización de batería si es necesario
        if (!isBatteryOptimizationIgnored(context)) {
            try {
                val intent = requestBatteryOptimizationExemption(context)
                if (intent != null) {
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
                Log.e("OnlineCaptureViewModel", "Error opening battery optimization settings", e)
                Toast.makeText(
                    context,
                    "Could not open battery settings. Please configure it manually in Settings",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        // Reiniciar contadores
        capturedSamplesCounter = 0
        capturedImagesCounter = 0
        
        try {
            // 1. Configurar e iniciar el controlador de imágenes
            configureImageController()
            
            // 2. Lanzar el RssiCaptureService para captura BLE
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
            evaluateEnableButtonTest()
            
            Log.i("OnlineCaptureViewModel", "Online capture started - BLE Service + Image Controller")
            return true
        } catch (e: Exception) {
            Log.e("OnlineCaptureViewModel", "Error starting capture service", e)
            // Si falla, limpiar el estado
            imageController.finishProcess()
            isRunning = false
            evaluateEnableButtonTest()
            return false
        }
    }

    /**
     * Detiene la captura online (BLE + imágenes).
     */
    fun stopCapture(context: Context) {
        // 1. Detener el controlador de imágenes
        imageController.finishProcess()
        
        // 2. Detener el RssiCaptureService
        val intent = Intent(context, RssiCaptureService::class.java)
        context.stopService(intent)
        
        isRunning = false
        evaluateEnableButtonTest()
        
        Log.i("OnlineCaptureViewModel", "Online capture stopped - BLE Service + Image Controller")
    }

    /**
     * Actualiza el contador de imágenes capturadas.
     * Será llamado por el OnlineCaptureController.
     */
    fun updateCapturedImagesCounter(count: Int) {
        capturedImagesCounter = count
    }

    override fun onCleared() {
        super.onCleared()
        unregisterBroadcastReceiver()
    }
}