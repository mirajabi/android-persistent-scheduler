package com.example.scheduler.lib.scheduler

import android.content.Context
import android.util.Log
import com.example.scheduler.lib.core.SchedulerConfig
import com.example.scheduler.lib.core.SchedulerManager
import com.example.scheduler.lib.core.ServiceModule
import kotlinx.coroutines.*

class SchedulerModule(private val config: SchedulerConfig) : ServiceModule {

    override val id: String = "SchedulerModule"
    private var isRunning = false
    private var job: Job? = null
    private var statusText = "Idle"

    override fun start(context: Context, updateNotification: () -> Unit) {
        isRunning = true
        statusText = "Starting..."
        updateNotification()

        job = CoroutineScope(Dispatchers.IO).launch {
            val interval = SchedulerManager.getInterval()
            var timeRemaining = interval

            while (isRunning) {
                // Update status
                val formattedTime = formatTime(timeRemaining)
                statusText = String.format(config.countdownFormat, formattedTime)
                withContext(Dispatchers.Main) { updateNotification() }

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

    override fun stop() {
        isRunning = false
        job?.cancel()
        statusText = "Stopped"
    }

    override fun getStatus(): String {
        return "Scheduler: $statusText"
    }

    private suspend fun executeWork() {
        Log.d("SchedulerModule", "Executing scheduled work...")
        val callback = SchedulerManager.getCallback()
        if (callback != null) {
            callback.onWork()
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
