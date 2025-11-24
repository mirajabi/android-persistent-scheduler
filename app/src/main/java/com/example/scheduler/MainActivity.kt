package com.example.scheduler

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.scheduler.lib.core.SchedulerCallback
import com.example.scheduler.lib.core.SchedulerManager
import com.example.scheduler.lib.download.DownloadBroadcaster
import com.example.scheduler.lib.util.BatteryOptimizationHelper
import com.example.scheduler.lib.worker.WatchdogWorker
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusTextView: TextView

    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra(DownloadBroadcaster.EXTRA_PROGRESS, 0) ?: 0
            val status = intent?.getStringExtra(DownloadBroadcaster.EXTRA_STATUS) ?: ""
            
            runOnUiThread {
                progressBar.progress = progress
                statusTextView.text = "Download Status: $status"
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize UI elements
        progressBar = findViewById(R.id.progress_download)
        statusTextView = findViewById(R.id.tv_download_status)
        
        // Register broadcast receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadBroadcaster.ACTION_DOWNLOAD_PROGRESS)
        )

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

        // Add a new button for testing download (assuming you add it to layout or just reuse for demo)
        // For this demo, I'll just auto-start a download after 5 seconds if you want, 
        // or better, let's add it to the startServices flow for now to demonstrate capability.
        
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
        // Request storage permissions first
        requestStoragePermissions()
        
        // Start the CoreService which starts all modules
        
        // Example Download - will save to public Downloads folder
        val downloadConfig = com.example.scheduler.lib.download.DownloadConfig(
            url = "https://www.dl.farsroid.com/ap/VLC-Mobile-Remote-Premium-2.92.0(www.farsroid.com).apk",
            destinationPath = "", // Will be ignored since usePublicDownloads = true
            fileName = "VLC-Mobile-Remote.apk",
            usePublicDownloads = true,
            showNotificationActions = true
        )

        com.example.scheduler.lib.core.ServiceManager.addModule(
            com.example.scheduler.lib.download.DownloadModule(downloadConfig) { success ->
                Log.d("MainActivity", "Download finished: $success")
                runOnUiThread {
                    Toast.makeText(this, "Download finished: $success", Toast.LENGTH_LONG).show()
                }
            }
        )

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
    
    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver)
    }
}
