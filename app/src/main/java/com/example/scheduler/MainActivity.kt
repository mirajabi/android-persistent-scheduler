package com.example.scheduler

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.scheduler.lib.core.SchedulerCallback
import com.example.scheduler.lib.core.SchedulerManager
import com.example.scheduler.lib.util.BatteryOptimizationHelper
import com.example.scheduler.lib.worker.WatchdogWorker
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ServiceManager with modules
        val schedulerConfig = com.example.scheduler.lib.core.SchedulerConfig(
            notificationTitle = "My Custom Scheduler",
            notificationContent = "Waiting for next run...",
            notificationIcon = android.R.drawable.ic_lock_idle_alarm,
            enableCountdown = true,
            countdownFormat = "Next run in: %s"
        )

        com.example.scheduler.lib.core.ServiceManager
            .addModule(
            com.example.scheduler.lib.scheduler.SchedulerModule(schedulerConfig)
        ).addModule(
            com.example.scheduler.lib.http.HttpServerModule(port = 8030) { requestData ->
                Log.d("MainActivity", "Http Request: $requestData")
                
                // Show toast on main thread
                runOnUiThread {
                    Toast.makeText(this, "Http: $requestData", Toast.LENGTH_SHORT).show()
                }
                
                // Return response to client
                "<html><body><h1>Hello from Android!</h1><p>Received: $requestData</p></body></html>"
            }
        )

        // Initialize SchedulerManager for callback (still needed for SchedulerModule logic)
        SchedulerManager.init(
            callback = object : SchedulerCallback {
                override suspend fun onWork() {
                    Log.d("MainActivity", "User Callback: Doing background work...")
                    delay(3000)
                    Log.d("MainActivity", "User Callback: Work finished!")
                }
            },
            config = schedulerConfig
        )

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            startServices()
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        // 1. Battery Optimization
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)

        // 2. Exact Alarm Permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }

        // 3. Notification Permission (Android 13+)
        BatteryOptimizationHelper.requestNotificationPermission(this)
    }

    private fun startServices() {
        // Start the CoreService which starts all modules
        com.example.scheduler.lib.core.ServiceManager.start(this)
        Toast.makeText(this, "Services Started", Toast.LENGTH_SHORT).show()

        // Start the Watchdog (runs every 15 mins approx)
        val watchdogRequest = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SchedulerWatchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            watchdogRequest
        )
    }
}
