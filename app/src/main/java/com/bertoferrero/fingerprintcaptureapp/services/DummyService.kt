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
import java.util.*
import kotlin.concurrent.schedule

class DummyService : Service() {
    companion object {
        const val CHANNEL_ID = "offline_capture_dummy"
        const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        startForeground(NOTIFICATION_ID, createNotification())
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Offline BLE Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Offline BLE Capture")
            .setContentText("Sampling BLE devices...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }


}
