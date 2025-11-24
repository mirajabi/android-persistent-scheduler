package com.example.scheduler.lib.download

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

object DownloadBroadcaster {
    const val ACTION_DOWNLOAD_PROGRESS = "com.example.scheduler.DOWNLOAD_PROGRESS"
    const val EXTRA_PROGRESS = "progress"
    const val EXTRA_STATUS = "status"
    
    fun sendProgress(context: Context, progress: Int, status: String) {
        val intent = Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_STATUS, status)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}
