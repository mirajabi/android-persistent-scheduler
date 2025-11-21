package com.example.scheduler.lib.core

import android.content.Context
import android.content.Intent
import android.os.Build

object ServiceManager {

    private val modules = mutableListOf<ServiceModule>()

    fun addModule(module: ServiceModule): ServiceManager {
        modules.add(module)
        return this
    }

    fun getModules(): List<ServiceModule> = modules

    fun start(context: Context) {
        val intent = Intent(context, CoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        val intent = Intent(context, CoreService::class.java)
        context.stopService(intent)
    }
}
