package com.example.scheduler.lib.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.scheduler.lib.core.SchedulerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SchedulerService : Service() {

    companion object {
        private const val TAG = "SchedulerService"
        private const val CHANNEL_ID = "SchedulerChannel"
        private const val NOTIFICATION_ID = 1
    }

    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service Started")
        
        val config = SchedulerManager.getConfig()
        startForeground(NOTIFICATION_ID, createNotification(config.notificationContent))

        if (config.enableCountdown) {
            if (!isRunning) {
                isRunning = true
                startPersistentLoop()
            }
        } else {
            // Standard one-off execution
            CoroutineScope(Dispatchers.IO).launch {
                executeWork()
                SchedulerManager.scheduleNextRun(applicationContext)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startPersistentLoop() {
        CoroutineScope(Dispatchers.IO).launch {
            val config = SchedulerManager.getConfig()
            val interval = SchedulerManager.getInterval()
            var timeRemaining = interval

            while (isRunning) {
                // Update notification
                val formattedTime = formatTime(timeRemaining)
                val content = String.format(config.countdownFormat, formattedTime)
                updateNotification(content)

                delay(1000)
                timeRemaining -= 1000

                if (timeRemaining <= 0) {
                    // Time to work!
                    executeWork()
                    timeRemaining = interval // Reset timer
                }
            }
        }
    }

    private suspend fun executeWork() {
        Log.d(TAG, "Executing scheduled work...")
        val callback = SchedulerManager.getCallback()
        if (callback != null) {
            callback.onWork()
        } else {
            Log.w(TAG, "No callback registered! Make sure to call SchedulerManager.init()")
        }
        Log.d(TAG, "Work finished.")
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun createNotification(content: String): Notification {
        val config = SchedulerManager.getConfig()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(config.notificationTitle)
            .setContentText(content)
            .setSmallIcon(config.notificationIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true) // Prevent sound/vibration on updates
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val config = SchedulerManager.getConfig()
            val channel = NotificationChannel(
                CHANNEL_ID,
                config.channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
