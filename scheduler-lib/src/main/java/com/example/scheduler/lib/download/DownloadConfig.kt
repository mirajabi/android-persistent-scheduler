package com.example.scheduler.lib.download

import java.util.UUID

data class DownloadConfig(
    val url: String,
    val destinationPath: String,
    val threadCount: Int = 4,
    val fileName: String,
    val downloadId: String = UUID.randomUUID().toString(),
    val usePublicDownloads: Boolean = true,
    val showNotificationActions: Boolean = true
)
