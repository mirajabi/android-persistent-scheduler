package com.example.scheduler.lib.download

import android.content.Context
import android.util.Log
import com.example.scheduler.lib.core.ServiceModule
import kotlinx.coroutines.*

class DownloadModule(
    private val config: DownloadConfig,
    private val onComplete: (Boolean) -> Unit
) : ServiceModule {

    override val id: String = "DownloadModule_${config.downloadId}"
    private var isRunning = false
    private var statusText = "Idle"
    private var job: Job? = null
    private var downloader: Downloader? = null

    override fun start(context: Context, updateNotification: () -> Unit) {
        isRunning = true
        manualState = DownloadState.DOWNLOADING // Reset state for new download
        statusText = "Starting Download..."
        updateNotification()

        job = CoroutineScope(Dispatchers.IO).launch {
            downloader = Downloader(context, config)
            val success = downloader!!.start { progress, speed, remaining ->
                statusText = "$progress% • $speed • ${formatBytes(remaining)} left"
                
                // Send broadcast to update UI
                DownloadBroadcaster.sendProgress(context, progress, statusText)
                
                // Throttle notification updates
                if (progress % 5 == 0) {
                    withContext(Dispatchers.Main) { updateNotification() }
                }
            }

            statusText = if (success) "Download Complete" else when (downloader?.currentState) {
                DownloadState.PAUSED -> "Paused"
                DownloadState.STOPPED -> "Stopped"
                else -> "Download Failed"
            }
            
            if (success) {
                manualState = DownloadState.COMPLETED
            }
            
            withContext(Dispatchers.Main) {
                updateNotification()
                if (success) {
                    onComplete(success)
                    
                    // Clear status after 3 minutes
                    delay(3 * 60 * 1000L) // 3 minutes
                    statusText = ""
                    manualState = null
                    updateNotification()
                }
            }
            isRunning = false
        }
    }

    override fun stop() {
        isRunning = false
        downloader?.stop()
        job?.cancel()
        manualState = DownloadState.STOPPED
        statusText = "Stopped"
    }

    override fun getStatus(): String {
        return "Download: $statusText"
    }
    
    private var manualState: DownloadState? = null
    private var updateNotificationCallback: (() -> Unit)? = null
    
    fun pause(updateNotification: () -> Unit) {
        downloader?.pause()
        manualState = DownloadState.PAUSED
        statusText = "Paused"
        updateNotification()
    }
    
    fun resume(context: Context, updateNotification: () -> Unit) {
        // Don't allow resuming if already completed or stopped
        if (manualState == DownloadState.COMPLETED || manualState == DownloadState.STOPPED) {
            statusText = if (manualState == DownloadState.COMPLETED) {
                "Download already completed"
            } else {
                "Download was stopped"
            }
            updateNotification()
            return
        }
        
        downloader?.resume()
        manualState = DownloadState.DOWNLOADING
        start(context, updateNotification)
    }
    
    fun getCurrentState(): DownloadState {
        return manualState ?: downloader?.currentState ?: DownloadState.IDLE
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
