package ir.codemarket.app

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrash(thread.name, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        logEvent("Application Started", "App initialized successfully.")
    }

    fun logEvent(tag: String, message: String) {
        val log = "--- Event: $tag ---\nTime: ${getCurrentTime()}\nApp Version: ${getAppVersion()}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\nThread: ${Thread.currentThread().name}\nMessage: $message\n----------------------------------"
        writeToFile(log)
    }

    fun logNetwork(endpoint: String, payload: String, response: String?, code: Int) {
        val log = "--- Network Request ---\nTime: ${getCurrentTime()}\nEndpoint: $endpoint\nPayload: $payload\nResponse Code: $code\nResponse Body: $response ?: "No Response"\n----------------------------------"
        writeToFile(log)
    }

    private fun logCrash(threadName: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        val log = "!!! CRASH DETECTED !!!\nTime: ${getCurrentTime()}\nPackage: ${context.packageName}\nApp Version: ${getAppVersion()}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid Version: ${Build.VERSION.RELEASE}\nThread: $threadName\nMemory: ${Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()} / ${Runtime.getRuntime().maxMemory()}\nException: ${throwable.javaClass.name}\nMessage: ${throwable.message}\nStackTrace: \n$stackTrace\n!!!!!!!!!!!!!!!!!!!!!!!"
        writeToFile(log)
    }

    private fun writeToFile(content: String) {
        try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                val logFile = File(downloadsDir, "CodeMarket_Logs.txt")
                if (!logFile.exists()) logFile.createNewFile()
                logFile.appendText("\n\n$content")
            }
        } catch (e: Exception) { }
    }

    private fun getCurrentTime(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    private fun getAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (e: Exception) { "Unknown" }
}