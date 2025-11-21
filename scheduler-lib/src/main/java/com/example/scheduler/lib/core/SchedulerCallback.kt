package com.example.scheduler.lib.core

/**
 * Interface for defining the background work to be executed by the scheduler.
 *
 * Implement this interface and pass it to [SchedulerManager.init] to define
 * your custom logic.
 */
interface SchedulerCallback {
    
    /**
     * The suspend function that will be called when the scheduled alarm fires.
     * 
     * This function runs within a CoroutineScope (Dispatchers.IO) in the Foreground Service.
     * The service will stay alive (and the notification visible) until this function returns.
     */
    suspend fun onWork()
}
