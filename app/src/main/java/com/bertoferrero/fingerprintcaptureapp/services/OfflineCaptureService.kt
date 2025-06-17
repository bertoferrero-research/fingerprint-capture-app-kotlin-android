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

    private var bleScanner: BleScanner? = null
    private var timer: Timer? = null
    private var macHistory = mutableListOf<RssiSample>()
    private var outputFolderUri: Uri? = null
    private var x: Float = 0f
    private var y: Float = 0f
    private var z: Float = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        x = intent?.getFloatExtra(EXTRA_X, 0f) ?: 0f
        y = intent?.getFloatExtra(EXTRA_Y, 0f) ?: 0f
        z = intent?.getFloatExtra(EXTRA_Z, 0f) ?: 0f
        val minutesLimit = intent?.getIntExtra(EXTRA_MINUTES_LIMIT, 0) ?: 0
        val macFilterList = intent?.getStringArrayListExtra(EXTRA_MAC_FILTER_LIST) ?: arrayListOf()
        outputFolderUri = intent?.getParcelableExtra(EXTRA_OUTPUT_FOLDER_URI)

        startForeground(NOTIFICATION_ID, createNotification())
        startBleCapture(x, y, z, minutesLimit, macFilterList)
        return START_NOT_STICKY
    }

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

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startBleCapture(
        x: Float,
        y: Float,
        z: Float,
        minutesLimit: Int,
        macFilterList: List<String>
    ) {
        macHistory.clear()
        bleScanner = BleScanner(this, macFilterList) {
            macHistory.add(
                RssiSample(
                    macAddress = it.device.address,
                    rssi = it.rssi,
                    posX = x,
                    posY = y,
                    posZ = z
                )
            )
        }
        bleScanner?.startScan()
        if (minutesLimit > 0) {
            timer = Timer()
            timer?.schedule(minutesLimit * 60000L) {
                stopSelf()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        timer?.cancel()
        bleScanner?.stopScan()
        bleScanner = null
        saveCaptureFile()
        super.onDestroy()
    }

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
        // Notificar a la UI que el archivo se ha guardado
        val intent = Intent("com.bertoferrero.fingerprintcaptureapp.SAVE_COMPLETE")
        sendBroadcast(intent)
    }
}
