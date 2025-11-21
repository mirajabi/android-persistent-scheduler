package com.example.scheduler.lib.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.scheduler.lib.receiver.AlarmReceiver

/**
 * Singleton manager for handling the scheduling of background tasks.
 *
 * This class provides methods to initialize the scheduler with a callback,
 * schedule the next run, and cancel pending alarms. It handles the logic
 * for different Android versions, including Doze mode adaptations.
 */
object SchedulerManager {

    private const val TAG = "SchedulerManager"
    private const val REQUEST_CODE = 1001

    /**
     * Interval for older Android versions (Pre-Marshmallow).
     * Set to 5 minutes.
     */
    private const val INTERVAL_LEGACY = 5 * 60 * 1000L
    
    /**
     * Interval for Android Marshmallow (6.0) and above.
     * Set to 15 minutes to comply with Doze mode restrictions.
     */
    private const val INTERVAL_DOZE = 15 * 60 * 1000L

    private var callback: SchedulerCallback? = null
    private var config: SchedulerConfig = SchedulerConfig()

    /**
     * Initializes the SchedulerManager with user configuration.
     *
     * This method MUST be called before the scheduler starts running, typically in your Application class
     * or the main Activity's onCreate.
     *
     * @param callback The [SchedulerCallback] instance that will be executed when the alarm fires.
     * @param config The [SchedulerConfig] object containing all settings.
     */
    fun init(
        callback: SchedulerCallback,
        config: SchedulerConfig = SchedulerConfig()
    ) {
        this.callback = callback
        this.config = config
    }

    /**
     * Returns the registered callback.
     */
    fun getCallback(): SchedulerCallback? = callback
    
    /**
     * Returns the current configuration.
     */
    fun getConfig(): SchedulerConfig = config

    /**
     * Starts the scheduler.
     *
     * If persistent mode is enabled, this starts the service immediately.
     * Otherwise, it schedules the next run via AlarmManager.
     *
     * @param context The application context.
     */
    fun start(context: Context) {
        if (config.enableCountdown) {
            // Start persistent service immediately
            val intent = Intent(context, com.example.scheduler.lib.service.SchedulerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            // Schedule next run (legacy/alarm mode)
            scheduleNextRun(context)
        }
    }

    /**
     * Schedules the next execution of the background task.
     *
     * This method calculates the appropriate interval based on the Android version
     * and sets an exact alarm (allowing while idle on M+).
     *
     * @param context The application context.
     */
    fun scheduleNextRun(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)

        val triggerTime = System.currentTimeMillis() + getInterval()

        Log.d(TAG, "Scheduling next run at $triggerTime (Interval: ${getInterval()}ms)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ (Marshmallow) - Use setExactAndAllowWhileIdle for Doze mode
            // Note: This will still be throttled by the system to ~9-15 mins if the app is idle
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            // Android < 6.0 - Use setExact
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    /**
     * Cancels any pending scheduled tasks.
     *
     * @param context The application context.
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Determines the scheduling interval based on the Android SDK version.
     *
     * @return The interval in milliseconds.
     */
    /**
     * Determines the scheduling interval based on the Android SDK version.
     *
     * @return The interval in milliseconds.
     */
    fun getInterval(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            INTERVAL_DOZE
        } else {
            INTERVAL_LEGACY
        }
    }
}
