package com.example.scheduler.lib.download

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ChunkState(
    val id: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val isCompleted: Boolean
)

object ChunkStateManager {
    private const val PREFS_NAME = "download_chunks"
    private val gson = Gson()
    
    fun saveChunkStates(context: Context, downloadId: String, chunks: List<ChunkState>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(chunks)
        prefs.edit().putString(downloadId, json).apply()
    }
    
    fun loadChunkStates(context: Context, downloadId: String): List<ChunkState>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(downloadId, null) ?: return null
        val type = object : TypeToken<List<ChunkState>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun clearChunkStates(context: Context, downloadId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(downloadId).apply()
    }
}
