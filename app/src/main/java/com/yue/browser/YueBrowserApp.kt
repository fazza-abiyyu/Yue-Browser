package com.yue.browser

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YueBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // === CRASH REPORTING: Simpan default handler, lalu logging sebelum delegate ===
        val defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                // 1. Log ke Logcat untuk debugging development
                Log.e("YueBrowser", "FATAL CRASH @ $timestamp | thread: ${thread.name} | ${throwable.message}")
                Log.e("YueBrowser", "Stack trace:\n$stackTrace")

                // 2. Simpan crash report ke file di internal storage (bisa dibaca saat restart berikutnya)
                try {
                    val crashDir = File(filesDir, "crashes")
                    if (!crashDir.exists()) crashDir.mkdirs()
                    val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.log")
                    FileWriter(crashFile).use { fw ->
                        fw.write("=== YUE BROWSER CRASH REPORT ===\n")
                        fw.write("Timestamp: $timestamp\n")
                        fw.write("Thread: ${thread.name} (id=${thread.id})\n")
                        fw.write("Message: ${throwable.message}\n")
                        fw.write("Package: $packageName\n")
                        fw.write("\n=== Stack Trace ===\n")
                        fw.write(stackTrace)
                        fw.write("\n=== Cause ===\n")
                        var cause = throwable.cause
                        while (cause != null) {
                            cause.printStackTrace(PrintWriter(sw))
                            cause = cause.cause
                        }
                        fw.flush()
                    }
                    Log.d("YueBrowser", "Crash report saved: ${crashFile.absolutePath}")
                } catch (ioEx: Exception) {
                    Log.e("YueBrowser", "Failed to write crash report to disk", ioEx)
                }
            } catch (ignored: Exception) {
                // Jangan biarkan error di crash handler menyebabkan crash lebih lanjut
            }

            // === JANGAN panggil System.exit()! ===
            // Delegasikan ke default Android handler:
            // - Default handler memastikan SharedPreferences tersimpan (flush pending writes)
            // - Data pengguna (history, bookmark) aman karena disimpan via repository
            // - Sistem Android dapat melakukan restart aplikasi dengan benar
            defaultUncaughtHandler?.uncaughtException(thread, throwable)
        }

        Log.d("YueBrowser", "Application initialized successfully.")
    }
}
