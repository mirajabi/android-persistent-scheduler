package com.example.scheduler.lib.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.scheduler.lib.core.ServiceManager

class CoreService : Service() {

    companion object {
        const val CHANNEL_ID = "CoreServiceChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start all registered modules
        ServiceManager.getModules().forEach { module ->
            module.start(this) {
                updateNotification()
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceManager.getModules().forEach { it.stop() }
    }

    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val statusText = ServiceManager.getModules().joinToString(" | ") { it.getStatus() }
        val content = if (statusText.isBlank()) "Service Running" else statusText

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Core Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Default icon, can be configurable
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Core Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
