package com.example.scheduler.lib.download

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    PAUSED,
    STOPPED,
    COMPLETED,
    FAILED
}
