package com.bertoferrero.fingerprintcaptureapp.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.bertoferrero.fingerprintcaptureapp.R
import com.bertoferrero.fingerprintcaptureapp.lib.BleScanner
import com.bertoferrero.fingerprintcaptureapp.models.RssiSample
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule
import android.os.PowerManager
import androidx.annotation.RequiresApi

class OfflineCaptureService : Service() {
    companion object {
        const val CHANNEL_ID = "offline_capture_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_Z = "z"
        const val EXTRA_INIT_DELAY_SECONDS = "initDelaySeconds"
        const val EXTRA_MINUTES_LIMIT = "minutesLimit"
        const val EXTRA_MAC_FILTER_LIST = "macFilterList"
        const val EXTRA_OUTPUT_FOLDER_URI = "outputFolderUri"
        
        // Broadcast action constants
        const val ACTION_TIMER_FINISHED = "com.bertoferrero.fingerprintcaptureapp.captureservice.TIMER_FINISHED"
        const val ACTION_SAMPLE_CAPTURED = "com.bertoferrero.fingerprintcaptureapp.captureservice.SAMPLE_CAPTURED"
        const val ACTION_SCAN_FAILED = "com.bertoferrero.fingerprintcaptureapp.captureservice.SCAN_FAILED"
        const val EXTRA_CAPTURED_SAMPLES = "capturedSamples"
    }

    // BLE scanner instance
    private var bleScanner: BleScanner? = null
    // Timer for capture duration
    private var timer: Timer? = null
    // Timer for initial delay
    private var initDelayTimer: Timer? = null
    // List to store captured samples
    private var capturedSamplesCounter = 0
    // Output folder URI for saving CSV
    private var outputFolderUri: Uri? = null
    // Capture position parameters
    private var x: Float = 0f
    private var y: Float = 0f
    private var z: Float = 0f

    // Output stream for writing CSV in real time
    private var outputStreams: MutableMap<String, java.io.OutputStream> = mutableMapOf()

    override fun onBind(intent: Intent?): IBinder? = null

    // WakeLock para mantener CPU activa
    private var wakeLock: PowerManager.WakeLock? = null    

    // Entry point when the service is started
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Retrieve parameters from intent
        x = intent?.getFloatExtra(EXTRA_X, 0f) ?: 0f
        y = intent?.getFloatExtra(EXTRA_Y, 0f) ?: 0f
        z = intent?.getFloatExtra(EXTRA_Z, 0f) ?: 0f
        val initDelaySeconds = intent?.getIntExtra(EXTRA_INIT_DELAY_SECONDS, 0) ?: 0
        val minutesLimit = intent?.getIntExtra(EXTRA_MINUTES_LIMIT, 0) ?: 0
        val macFilterList = intent?.getStringArrayListExtra(EXTRA_MAC_FILTER_LIST) ?: arrayListOf()
        outputFolderUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_OUTPUT_FOLDER_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_OUTPUT_FOLDER_URI)
        }

        // Adquirir WakeLock inmediatamente
        acquireWakeLock()

        // Start as a foreground service with a persistent notification
        // IMPORTANTE: Debe ser llamado inmediatamente después del WakeLock
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start BLE capture process
        if(initDelaySeconds == 0) {
            startBleCapture(x, y, z, minutesLimit, macFilterList)
        }
        else{
            initDelayTimer = Timer()
            initDelayTimer?.schedule(initDelaySeconds * 1000L) {
                startBleCapture(x, y, z, minutesLimit, macFilterList)
                initDelayTimer = null
            }
        }
        return START_NOT_STICKY
    }

    

    // Clean up resources and save data when the service is destroyed
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        android.util.Log.i("OfflineCaptureService", "Service being destroyed - cleaning up resources")
        
        // Cancelar timers
        timer?.cancel()
        timer = null
        initDelayTimer?.cancel()
        initDelayTimer = null
        
        // Detener el escáner BLE
        bleScanner?.stopScan()
        bleScanner = null
        
        // Cerrar archivos CSV
        closeCsvFile()

        // Liberar WakeLock de forma segura
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    android.util.Log.i("OfflineCaptureService", "WakeLock released successfully")
                } catch (e: Exception) {
                    android.util.Log.w("OfflineCaptureService", "Error releasing WakeLock", e)
                }
            }
        }
        wakeLock = null

        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OfflineCaptureService::WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 horas máximo
    }

    // Create a persistent notification for the foreground service
    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Offline BLE Capture",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "BLE sampling service running continuously"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Configuración para evitar que el sistema termine el servicio
                setBypassDnd(true)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE Capture Active")
            .setContentText("Continuous BLE sampling in progress...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE Capture Active")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    // Open the CSV file and write the header
    private fun openCsvFile(name: String): java.io.OutputStream {
        val folder = DocumentFile.fromTreeUri(this, outputFolderUri!!)
            ?: throw IllegalStateException("Cannot access output folder")

        val fileName = "${x}_${y}_${z}__${name}.csv"
        val newFile = folder.createFile("text/csv", fileName)
            ?: throw IllegalStateException("Cannot create CSV file")
        
        val outputStream = contentResolver.openOutputStream(newFile.uri)
            ?: throw IllegalStateException("Cannot open output stream")
        
        outputStream.write("timestamp,time,mac_address,rssi,tx_power,pos_x,pos_y,pos_z\n".toByteArray())
        return outputStream
    }

    private fun getCsvOutputStream(name: String): java.io.OutputStream {
        //If the stream is at the opened list, just return it
        if (!outputStreams.containsKey(name)){
            val newStream = openCsvFile(name)
            outputStreams[name] = newStream
        }
        return outputStreams[name]!!
    }

    // Write a single sample to the CSV file
    private fun writeSample(sample: RssiSample) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val dateFormated = dateFormat.format(Date(sample.timestamp))
            val line = "${sample.timestamp},${dateFormated},${sample.macAddress},${sample.rssi},${sample.txPower},${sample.posX},${sample.posY},${sample.posZ}\n"
            // Write the main log
            val writter = getCsvOutputStream("all")
            writter.write(line.toByteArray())
            writter.flush() // Ensure data is written immediately
            // And the MAC-specific log
            val macWriter = getCsvOutputStream(sample.macAddress)
            macWriter.write(line.toByteArray())
            macWriter.flush()
        } catch (e: Exception) {
            android.util.Log.e("OfflineCaptureService", "Error writing sample to CSV", e)
            throw e // Re-throw to be caught by caller
        }
    }


    // Close the CSV file
    private fun closeCsvFile() {
        android.util.Log.i("OfflineCaptureService", "Closing CSV files")
        // Close all output streams
        outputStreams.values.forEach { 
            try {
                it.flush() // Asegurar que todos los datos se escriban antes de cerrar
                it.close()
            } catch (e: Exception) {
                android.util.Log.w("OfflineCaptureService", "Error closing output stream", e)
            }
        }
        outputStreams.clear()
    }

    // Start BLE scanning and handle sample collection
    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBleCapture(
        x: Float,
        y: Float,
        z: Float,
        minutesLimit: Int,
        macFilterList: List<String>
    ) {
        capturedSamplesCounter = 0
        
        bleScanner = BleScanner(
            filterMacs = macFilterList,
            filterMacPrefixes = emptyList() // Por ahora solo usamos filtrado exacto
        ) {
            val sample = RssiSample(
                macAddress = it.device.address,
                rssi = it.rssi,
                txPower = it.txPower,
                posX = x,
                posY = y,
                posZ = z
            )
            try {
                writeSample(sample)
            } catch (e: Exception) {
                android.util.Log.e("OfflineCaptureService", "Error writing sample to CSV", e)
                stopSelf()
            }
            // Notify UI with the number of captured samples
            capturedSamplesCounter++
            val intent = Intent(ACTION_SAMPLE_CAPTURED)
            intent.putExtra(EXTRA_CAPTURED_SAMPLES, capturedSamplesCounter)
            sendBroadcast(intent)
        }
        
        // Intentar iniciar el escaneo
        val scanStarted = bleScanner?.startScan(this) ?: false
        if (!scanStarted) {
            // Enviar broadcast de error
            val errorIntent = Intent(ACTION_SCAN_FAILED)
            sendBroadcast(errorIntent)
            stopSelf()
            return
        }
        // If a time limit is set, schedule automatic stop
        if (minutesLimit > 0) {
            timer = Timer()
            timer?.schedule(minutesLimit * 60000L) {
                // Notify UI that capture has finished due to timer
                val intent = Intent(ACTION_TIMER_FINISHED)
                sendBroadcast(intent)
                stopSelf()
            }
        }
    }
}
