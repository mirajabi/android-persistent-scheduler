package com.example.scheduler.lib.http

import android.content.Context
import android.util.Log
import com.example.scheduler.lib.core.ServiceModule
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class HttpServerModule(
    private val port: Int = 8030,
    private val callback: (String) -> String
) : ServiceModule {

    override val id: String = "HttpServerModule"
    private var isRunning = false
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    override fun start(context: Context, updateNotification: () -> Unit) {
        isRunning = true
        updateNotification()

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("HttpServerModule", "Server started on port $port")

                while (isRunning) {
                    val client = serverSocket?.accept()
                    client?.let { handleClient(it) }
                }
            } catch (e: Exception) {
                Log.e("HttpServerModule", "Server error", e)
            }
        }
    }

    override fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("HttpServerModule", "Error closing server", e)
        }
        job?.cancel()
    }

    override fun getStatus(): String {
        return if (isRunning) "Http: :$port" else "Http: Stopped"
    }

    private fun handleClient(socket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                // Read request line
                val requestLine = reader.readLine()
                if (requestLine != null) {
                    Log.d("HttpServerModule", "Request: $requestLine")
                    
                    // Simple parsing (e.g., "GET /?param=value HTTP/1.1")
                    val parts = requestLine.split(" ")
                    if (parts.size >= 2) {
                        val method = parts[0]
                        val path = parts[1]
                        
                        val requestData = "Method: $method, Path: $path"
                        
                        // Get response from callback (blocking call on IO thread is fine here)
                        // Note: We are NOT switching to Main thread here because we need the return value immediately.
                        // If the user needs Main thread, they should handle it inside the callback carefully or we use runBlocking.
                        // For simplicity and performance, we run it here.
                        val responseBody = callback(requestData)

                        // Send response
                        writer.println("HTTP/1.1 200 OK")
                        writer.println("Content-Type: text/plain")
                        writer.println("Content-Length: ${responseBody.length}")
                        writer.println()
                        writer.print(responseBody)
                        writer.flush()
                    }
                }

                socket.close()
            } catch (e: Exception) {
                Log.e("HttpServerModule", "Client handling error", e)
            }
        }
    }
}
