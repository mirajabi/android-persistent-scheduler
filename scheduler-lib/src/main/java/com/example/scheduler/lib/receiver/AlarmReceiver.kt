package com.example.scheduler.lib.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.scheduler.lib.service.SchedulerService

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val WAKELOCK_TAG = "Scheduler:AlarmReceiverWakeLock"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm Received!")

        // Acquire a temporary WakeLock to ensure the service starts
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        wakeLock.acquire(10 * 1000L) // 10 seconds timeout

        try {
            val serviceIntent = Intent(context, SchedulerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } finally {
            // We release the lock in the service or let it timeout, 
            // but for safety here we just rely on the timeout or release if we were doing work here.
            // Since we are just starting a service, the service will take its own lock or foreground status.
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
