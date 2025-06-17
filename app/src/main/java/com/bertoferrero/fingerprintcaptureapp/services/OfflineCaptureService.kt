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
import com.bertoferrero.fingerprintcaptureapp.viewmodels.capture.RssiSample
import java.util.*
import kotlin.concurrent.schedule

class OfflineCaptureService : Service() {
    companion object {
        const val CHANNEL_ID = "offline_capture_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_Z = "z"
        const val EXTRA_MINUTES_LIMIT = "minutesLimit"
        const val EXTRA_MAC_FILTER_LIST = "macFilterList"
        const val EXTRA_OUTPUT_FOLDER_URI = "outputFolderUri"
    }

    // BLE scanner instance
    private var bleScanner: BleScanner? = null
    // Timer for capture duration
    private var timer: Timer? = null
    // List to store captured samples
    private var macHistory = mutableListOf<RssiSample>()
    // Output folder URI for saving CSV
    private var outputFolderUri: Uri? = null
    // Capture position parameters
    private var x: Float = 0f
    private var y: Float = 0f
    private var z: Float = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    // Entry point when the service is started
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Retrieve parameters from intent
        x = intent?.getFloatExtra(EXTRA_X, 0f) ?: 0f
        y = intent?.getFloatExtra(EXTRA_Y, 0f) ?: 0f
        z = intent?.getFloatExtra(EXTRA_Z, 0f) ?: 0f
        val minutesLimit = intent?.getIntExtra(EXTRA_MINUTES_LIMIT, 0) ?: 0
        val macFilterList = intent?.getStringArrayListExtra(EXTRA_MAC_FILTER_LIST) ?: arrayListOf()
        outputFolderUri = intent?.getParcelableExtra(EXTRA_OUTPUT_FOLDER_URI)

        // Start as a foreground service with a persistent notification
        startForeground(NOTIFICATION_ID, createNotification())
        // Start BLE capture process
        startBleCapture(x, y, z, minutesLimit, macFilterList)
        return START_NOT_STICKY
    }

    // Create a persistent notification for the foreground service
    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Offline BLE Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Offline BLE Capture")
            .setContentText("Sampling BLE devices...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    // Start BLE scanning and handle sample collection
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBleCapture(
        x: Float,
        y: Float,
        z: Float,
        minutesLimit: Int,
        macFilterList: List<String>
    ) {
        macHistory.clear()
        // Start BLE scan with filter list
        bleScanner = BleScanner(this, macFilterList) {
            // Add each sample to history
            macHistory.add(
                RssiSample(
                    macAddress = it.device.address,
                    rssi = it.rssi,
                    posX = x,
                    posY = y,
                    posZ = z
                )
            )
            // Notify UI with the number of captured samples
            val intent = Intent("com.bertoferrero.fingerprintcaptureapp.captureservice.SAMPLE_CAPTURED")
            intent.putExtra("capturedSamples", macHistory.size)
            sendBroadcast(intent)
        }
        bleScanner?.startScan()
        // If a time limit is set, schedule automatic stop
        if (minutesLimit > 0) {
            timer = Timer()
            timer?.schedule(minutesLimit * 60000L) {
                // Notify UI that capture has finished due to timer
                val intent = Intent("com.bertoferrero.fingerprintcaptureapp.captureservice.TIMER_FINISHED")
                sendBroadcast(intent)
                stopSelf()
            }
        }
    }

    // Clean up resources and save data when the service is destroyed
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        timer?.cancel()
        bleScanner?.stopScan()
        bleScanner = null
        saveCaptureFile()
        super.onDestroy()
    }

    // Save captured samples to a CSV file in the selected folder
    private fun saveCaptureFile() {
        val samples = macHistory
        if (outputFolderUri == null || samples.isEmpty()) {
            return
        }
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
        val folder = DocumentFile.fromTreeUri(this, outputFolderUri!!)
        val timestamp = System.currentTimeMillis()
        val fileName = "offlinecapture__${x}_${y}_${z}__${timestamp}.csv"
        val newFile = folder?.createFile("text/csv", fileName)
        newFile?.uri?.let { fileUri ->
            contentResolver.openOutputStream(fileUri)?.use { out ->
                out.write(csvString.toByteArray())
            }
        }
        // Notify UI that the file has been saved
        val intent = Intent("com.bertoferrero.fingerprintcaptureapp.captureservice.SAVE_COMPLETE")
        sendBroadcast(intent)
    }
}
