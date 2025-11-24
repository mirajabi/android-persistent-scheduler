package com.example.scheduler.lib.download

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class Downloader(
    private val context: Context,
    private val config: DownloadConfig
) {

    private val TAG = "Downloader"
    private var totalSize: Long = 0
    private val downloadedSize = AtomicLong(0)
    private val chunks = mutableListOf<Chunk>()
    private val isPaused = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)
    
    private var startTime: Long = 0
    private var lastProgressUpdate: Long = 0
    
    var currentState: DownloadState = DownloadState.IDLE
        private set

    data class Chunk(
        val id: Int,
        val startByte: Long,
        val endByte: Long,
        var currentByte: Long,
        var isCompleted: Boolean = false,
        val tempFile: File,
        var job: Job? = null
    )

    suspend fun start(onProgress: suspend (Int, String, Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            currentState = DownloadState.DOWNLOADING
            startTime = System.currentTimeMillis()
            
            // 1. Get File Size
            val url = URL(config.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            totalSize = connection.contentLengthLong
            connection.disconnect()

            if (totalSize <= 0) {
                Log.e(TAG, "Invalid file size: $totalSize")
                currentState = DownloadState.FAILED
                return@withContext false
            }

            Log.d(TAG, "Total size: $totalSize bytes")

            // 1.5 Determine destination directory
            val destDir = getDestinationDirectory()
            if (!destDir.exists()) {
                destDir.mkdirs()
                Log.d(TAG, "Created destination directory: ${destDir.absolutePath}")
            }

            // 2. Try to load existing chunk states
            val savedChunks = ChunkStateManager.loadChunkStates(context, config.downloadId)
            if (savedChunks != null) {
                Log.d(TAG, "Resuming download from saved state")
                restoreChunks(destDir, savedChunks)
            } else {
                Log.d(TAG, "Starting new download")
                initializeChunks(destDir)
            }

            // 3. Download Chunks in Parallel
            val jobs = chunks.filter { !it.isCompleted }.map { chunk ->
                async { downloadChunk(chunk, onProgress) }.also { chunk.job = it }
            }
            jobs.awaitAll()

            // Check if stopped or paused
            if (isStopped.get()) {
                currentState = DownloadState.STOPPED
                cleanupTempFiles()
                return@withContext false
            }
            
            if (isPaused.get()) {
                currentState = DownloadState.PAUSED
                saveChunkStates()
                return@withContext false
            }

            // 4. Validate and Assemble File
            if (chunks.all { it.isCompleted }) {
                var assembled = assembleFile(destDir)
                
                if (assembled) {
                    // 5. Validate APK file (if it's an APK)
                    val finalFile = File(getDestinationDirectory(), config.fileName)
                    if (config.fileName.endsWith(".apk", ignoreCase = true)) {
                        Log.d(TAG, "Validating APK file...")
                        val isValid = validateApk(finalFile)
                        
                        if (!isValid) {
                            Log.e(TAG, "APK validation failed! Attempting to re-download corrupted chunks...")
                            
                            // Delete the corrupted final file
                            finalFile.delete()
                            
                            // Mark all chunks as incomplete to force re-download
                            chunks.forEach { it.isCompleted = false; it.currentByte = it.startByte }
                            
                            // Re-download all chunks
                            val retryJobs = chunks.map { chunk ->
                                async { downloadChunk(chunk, onProgress) }.also { chunk.job = it }
                            }
                            retryJobs.awaitAll()
                            
                            // Try to assemble again
                            assembled = assembleFile(destDir)
                            
                            if (assembled) {
                                // Validate again
                                val isValidAfterRetry = validateApk(finalFile)
                                if (!isValidAfterRetry) {
                                    Log.e(TAG, "APK validation failed after retry")
                                    currentState = DownloadState.FAILED
                                    return@withContext false
                                }
                            } else {
                                Log.e(TAG, "Failed to assemble file after retry")
                                currentState = DownloadState.FAILED
                                return@withContext false
                            }
                        }
                        
                        Log.d(TAG, "APK validation successful!")
                    }
                    
                    currentState = DownloadState.COMPLETED
                    ChunkStateManager.clearChunkStates(context, config.downloadId)
                    return@withContext true
                } else {
                    currentState = DownloadState.FAILED
                    return@withContext false
                }
            } else {
                Log.e(TAG, "Some chunks failed to download")
                currentState = DownloadState.FAILED
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            currentState = DownloadState.FAILED
            return@withContext false
        }
    }

    fun pause() {
        Log.d(TAG, "Pausing download")
        isPaused.set(true)
        currentState = DownloadState.PAUSED
        chunks.forEach { it.job?.cancel() }
        saveChunkStates()
    }

    fun resume() {
        Log.d(TAG, "Resuming download")
        isPaused.set(false)
        currentState = DownloadState.DOWNLOADING
    }

    fun stop() {
        Log.d(TAG, "Stopping download")
        isStopped.set(true)
        currentState = DownloadState.STOPPED
        chunks.forEach { it.job?.cancel() }
        cleanupTempFiles()
        ChunkStateManager.clearChunkStates(context, config.downloadId)
    }

    private fun getDestinationDirectory(): File {
        return if (config.usePublicDownloads) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } else {
            File(config.destinationPath)
        }
    }

    private fun initializeChunks(destDir: File) {
        val chunkSize = totalSize / config.threadCount
        for (i in 0 until config.threadCount) {
            val start = i * chunkSize
            val end = if (i == config.threadCount - 1) totalSize - 1 else (start + chunkSize - 1)
            val tempFile = File(destDir, "${config.fileName}.part$i")
            
            tempFile.parentFile?.mkdirs()
            if (!tempFile.exists()) {
                tempFile.createNewFile()
            }
            
            chunks.add(Chunk(i, start, end, start, false, tempFile))
        }
    }

    private fun restoreChunks(destDir: File, savedChunks: List<ChunkState>) {
        savedChunks.forEach { saved ->
            val tempFile = File(destDir, "${config.fileName}.part${saved.id}")
            
            // Ensure the temp file exists
            if (!tempFile.exists()) {
                tempFile.parentFile?.mkdirs()
                tempFile.createNewFile()
                Log.d(TAG, "Created missing temp file for chunk ${saved.id}")
            }
            
            chunks.add(Chunk(
                id = saved.id,
                startByte = saved.startByte,
                endByte = saved.endByte,
                currentByte = saved.startByte + saved.downloadedBytes,
                isCompleted = saved.isCompleted,
                tempFile = tempFile
            ))
            
            // Update the downloaded size
            if (saved.isCompleted) {
                downloadedSize.addAndGet(saved.endByte - saved.startByte + 1)
            } else {
                downloadedSize.addAndGet(saved.downloadedBytes)
            }
            
            Log.d(TAG, "Restored chunk ${saved.id}: ${saved.downloadedBytes} bytes, completed=${saved.isCompleted}")
        }
    }

    private fun saveChunkStates() {
        val states = chunks.map { chunk ->
            val downloaded = if (chunk.isCompleted) {
                chunk.endByte - chunk.startByte + 1
            } else {
                chunk.currentByte - chunk.startByte
            }
            ChunkState(
                id = chunk.id,
                startByte = chunk.startByte,
                endByte = chunk.endByte,
                downloadedBytes = downloaded,
                isCompleted = chunk.isCompleted
            )
        }
        ChunkStateManager.saveChunkStates(context, config.downloadId, states)
        Log.d(TAG, "Saved chunk states for ${config.downloadId}")
    }

    private suspend fun downloadChunk(chunk: Chunk, onProgress: suspend (Int, String, Long) -> Unit) {
        if (chunk.isCompleted) {
            Log.d(TAG, "Chunk ${chunk.id} already completed, skipping")
            return
        }
        
        try {
            Log.d(TAG, "Starting download chunk ${chunk.id}: ${chunk.currentByte}-${chunk.endByte}")
            
            val url = URL(config.url)
            val connection = url.openConnection() as HttpURLConnection
            val range = "bytes=${chunk.currentByte}-${chunk.endByte}"
            connection.setRequestProperty("Range", range)
            connection.connect()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Chunk ${chunk.id} response code: $responseCode")
            
            if (responseCode != 206 && responseCode != 200) {
                Log.e(TAG, "Invalid response code for chunk ${chunk.id}: $responseCode")
                chunk.isCompleted = false
                return
            }

            val inputStream = connection.inputStream
            val randomAccessFile = RandomAccessFile(chunk.tempFile, "rw")
            randomAccessFile.seek(chunk.currentByte - chunk.startByte)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (withContext(Dispatchers.IO) { inputStream.read(buffer) }.also { bytesRead = it } != -1) {
                // Check for pause/stop
                if (isPaused.get() || isStopped.get()) {
                    Log.d(TAG, "Chunk ${chunk.id} paused/stopped at byte ${chunk.currentByte}")
                    randomAccessFile.channel.force(true) // Force write to disk
                    randomAccessFile.close()
                    inputStream.close()
                    connection.disconnect()
                    
                    // Verify the file exists and has content
                    if (chunk.tempFile.exists() && chunk.tempFile.length() > 0) {
                        Log.d(TAG, "Chunk ${chunk.id} saved: ${chunk.tempFile.length()} bytes")
                    }
                    return
                }
                
                randomAccessFile.write(buffer, 0, bytesRead)
                chunk.currentByte += bytesRead
                
                val currentDownloaded = downloadedSize.addAndGet(bytesRead.toLong())
                val progress = ((currentDownloaded.toDouble() / totalSize) * 100).toInt()
                
                // Calculate speed (bytes per second)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastProgressUpdate >= 500) { // Update every 500ms
                    val elapsedSeconds = (currentTime - startTime) / 1000.0
                    val speed = if (elapsedSeconds > 0) {
                        (currentDownloaded / elapsedSeconds).toLong()
                    } else {
                        0L
                    }
                    val remaining = totalSize - currentDownloaded
                    
                    lastProgressUpdate = currentTime
                    onProgress(progress, formatSpeed(speed), remaining)
                }
            }

            randomAccessFile.channel.force(true) // Force write to disk
            randomAccessFile.close()
            inputStream.close()
            connection.disconnect()
            
            // Verify the file was written
            if (chunk.tempFile.exists() && chunk.tempFile.length() > 0) {
                chunk.isCompleted = true
                Log.d(TAG, "Chunk ${chunk.id} completed: ${chunk.tempFile.length()} bytes written")
            } else {
                chunk.isCompleted = false
                Log.e(TAG, "Chunk ${chunk.id} file verification failed")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading chunk ${chunk.id}", e)
            chunk.isCompleted = false
        }
    }

    private fun assembleFile(destDir: File): Boolean {
        Log.d(TAG, "Assembling file...")
        
        // Verify all chunk files exist before attempting assembly
        chunks.forEach { chunk ->
            if (!chunk.tempFile.exists()) {
                Log.e(TAG, "Chunk file does not exist: ${chunk.tempFile.absolutePath}")
                return false
            }
            if (chunk.tempFile.length() == 0L) {
                Log.e(TAG, "Chunk file is empty: ${chunk.tempFile.absolutePath}")
                return false
            }
            Log.d(TAG, "Chunk ${chunk.id} file exists: ${chunk.tempFile.length()} bytes")
        }
        
        val finalFile = File(destDir, config.fileName)
        if (finalFile.exists()) finalFile.delete()

        val finalAccessFile = RandomAccessFile(finalFile, "rw")
        
        try {
            chunks.sortedBy { it.id }.forEach { chunk ->
                Log.d(TAG, "Assembling chunk ${chunk.id} from ${chunk.tempFile.absolutePath}")
                val partFile = RandomAccessFile(chunk.tempFile, "r")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (partFile.read(buffer).also { bytesRead = it } != -1) {
                    finalAccessFile.write(buffer, 0, bytesRead)
                }
                partFile.close()
                chunk.tempFile.delete() // Cleanup temp file
            }
            Log.d(TAG, "File assembled successfully: ${finalFile.absolutePath}")
            
            // Final validation
            if (finalFile.length() != totalSize) {
                Log.e(TAG, "File size mismatch: expected $totalSize, got ${finalFile.length()}")
                return false
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error assembling file", e)
            return false
        } finally {
            finalAccessFile.close()
        }
    }

    private fun cleanupTempFiles() {
        chunks.forEach { it.tempFile.delete() }
        Log.d(TAG, "Cleaned up temp files")
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }
    
    private fun validateApk(apkFile: File): Boolean {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Log.e(TAG, "APK file does not exist or is empty")
                return false
            }
            
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                0
            )
            
            if (packageInfo == null) {
                Log.e(TAG, "Failed to parse APK - package info is null")
                return false
            }
            
            Log.d(TAG, "APK is valid: ${packageInfo.packageName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "APK validation failed", e)
            false
        }
    }
}
