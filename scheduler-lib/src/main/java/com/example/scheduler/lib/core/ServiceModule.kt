package com.example.scheduler.lib.core

import android.content.Context

/**
 * Interface for modules that run within the CoreService.
 */
interface ServiceModule {
    /**
     * Unique identifier for the module.
     */
    val id: String

    /**
     * Called when the module is started.
     * @param context The service context.
     * @param updateNotification A lambda to request a notification update.
     */
    fun start(context: Context, updateNotification: () -> Unit)

    /**
     * Called when the module is stopped.
     */
    fun stop()

    /**
     * Returns the current status text to be displayed in the notification.
     */
    fun getStatus(): String
}
