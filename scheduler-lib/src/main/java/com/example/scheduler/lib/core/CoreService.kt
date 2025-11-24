package com.example.scheduler.lib.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.scheduler.lib.download.DownloadModule
import com.example.scheduler.lib.download.DownloadState

class CoreService : Service() {

    companion object {
        const val CHANNEL_ID = "CoreServiceChannel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_PAUSE = "com.example.scheduler.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.scheduler.ACTION_RESUME"
        const val ACTION_STOP = "com.example.scheduler.ACTION_STOP"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle download control actions
        when (intent?.action) {
            ACTION_PAUSE -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                handlePause(downloadId)
            }
            ACTION_RESUME -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                handleResume(downloadId)
            }
            ACTION_STOP -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                handleStop(downloadId)
            }
            else -> {
                // Start all registered modules
                ServiceManager.getModules().forEach { module ->
                    module.start(this) {
                        updateNotification()
                    }
                }
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceManager.getModules().forEach { it.stop() }
    }

    private fun handlePause(downloadId: String?) {
        ServiceManager.getModules()
            .filterIsInstance<DownloadModule>()
            .find { it.id.contains(downloadId ?: "") }
            ?.pause { updateNotification() }
    }

    private fun handleResume(downloadId: String?) {
        ServiceManager.getModules()
            .filterIsInstance<DownloadModule>()
            .find { it.id.contains(downloadId ?: "") }
            ?.resume(this) { updateNotification() }
    }

    private fun handleStop(downloadId: String?) {
        ServiceManager.getModules()
            .filterIsInstance<DownloadModule>()
            .find { it.id.contains(downloadId ?: "") }
            ?.stop()
        updateNotification()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val statusText = ServiceManager.getModules().joinToString(" | ") { it.getStatus() }
        val content = if (statusText.isBlank()) "Service Running" else statusText
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Core Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)

        // Add action buttons for download modules (only if downloading or paused)
        val downloadModules = ServiceManager.getModules().filterIsInstance<DownloadModule>()
        downloadModules.firstOrNull()?.let { downloadModule ->
            val currentState = downloadModule.getCurrentState()
            
            // Only show buttons if downloading or paused
            if (currentState == DownloadState.DOWNLOADING || currentState == DownloadState.PAUSED) {
                val downloadId = downloadModule.id.substringAfter("DownloadModule_")
                
                val pauseIntent = Intent(this, CoreService::class.java).apply {
                    action = ACTION_PAUSE
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                }
                val pausePendingIntent = PendingIntent.getService(
                    this, 1, pauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val resumeIntent = Intent(this, CoreService::class.java).apply {
                    action = ACTION_RESUME
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                }
                val resumePendingIntent = PendingIntent.getService(
                    this, 2, resumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val stopIntent = Intent(this, CoreService::class.java).apply {
                    action = ACTION_STOP
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                }
                val stopPendingIntent = PendingIntent.getService(
                    this, 3, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
                builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
                builder.addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            }
        }

        return builder.build()
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
