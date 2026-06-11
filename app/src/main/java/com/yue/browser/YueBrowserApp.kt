package com.yue.browser

import android.app.Application
import android.util.Log

class YueBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Setup simple crash reporting / logging uncaught exceptions
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("YueBrowser", "FATAL CRASH in thread: ${thread.name}", throwable)
            
            // Exit application after logging
            System.exit(1)
        }
        
        Log.d("YueBrowser", "Application initialized successfully.")
    }
}
