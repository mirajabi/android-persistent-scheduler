package com.example.scheduler.lib.core

/**
 * Configuration for the Scheduler.
 *
 * @property notificationTitle The title of the foreground service notification.
 * @property notificationContent The content text of the foreground service notification.
 * @property notificationIcon The resource ID of the icon to use for the notification.
 * @property channelName The name of the notification channel.
 * @property enableCountdown If true, the service runs persistently and shows a countdown timer.
 * @property countdownFormat The format string for the countdown (e.g., "Next run in: %s").
 */
data class SchedulerConfig(
    val notificationTitle: String = "Scheduler Running",
    val notificationContent: String = "Executing background task...",
    val notificationIcon: Int = android.R.drawable.ic_dialog_info,
    val channelName: String = "Scheduler Service",
    val enableCountdown: Boolean = false,
    val countdownFormat: String = "Next run in: %s"
)
