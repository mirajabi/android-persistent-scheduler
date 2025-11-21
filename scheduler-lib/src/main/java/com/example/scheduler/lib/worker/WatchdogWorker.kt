package com.example.scheduler.lib.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.scheduler.lib.core.SchedulerManager
import com.example.scheduler.lib.receiver.AlarmReceiver

class WatchdogWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        Log.d("WatchdogWorker", "Checking scheduler status...")
        
        // Check if the PendingIntent for the alarm already exists
        val intent = Intent(applicationContext, AlarmReceiver::class.java)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        
        val pendingIntent = PendingIntent.getBroadcast(applicationContext, 1001, intent, flags)
        
        if (pendingIntent == null) {
            Log.w("WatchdogWorker", "Scheduler was dead! Restarting...")
            SchedulerManager.scheduleNextRun(applicationContext)
        } else {
            Log.d("WatchdogWorker", "Scheduler is alive.")
        }

        return Result.success()
    }
}
