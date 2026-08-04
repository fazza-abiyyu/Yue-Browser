package com.yue.browser

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.yue.browser.presentation.BrowserViewModel
import com.yue.browser.presentation.theme.YueTheme
import com.yue.browser.presentation.ui.MainBrowserScreen

import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity() {

    companion object {
        private var activeActivity: java.lang.ref.WeakReference<MainActivity>? = null
        var isProcessRecreation: Boolean = false

        fun getActiveActivity(): MainActivity? {
            return activeActivity?.get()
        }
    }

    private lateinit var viewModel: BrowserViewModel

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("show_downloads", false) && ::viewModel.isInitialized) {
            viewModel.triggerShowDownloads()
        }
        handleUrlIntent(intent)
    }

    private fun handleUrlIntent(intent: Intent?) {
        if (intent == null || !::viewModel.isInitialized) return
        if (intent.action == Intent.ACTION_VIEW) {
            val url = intent.dataString
            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                viewModel.createNewTab(this, url)
                intent.data = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeActivity = java.lang.ref.WeakReference(this)
        isProcessRecreation = savedInstanceState != null

        // Enable edge-to-edge so the app extends under system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Make navigation bar transparent so bottom bar seamlessly blends
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Enable web contents debugging for Chrome DevTools inspection
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)

        // Initialize CookieManager EARLY agar cookie store mulai dimuat dari disk
        // sebelum WebView pertama dibuat. Cold start tanpa ini bisa menyebabkan
        // cookie dari sesi sebelumnya (terutama OAuth Google/Microsoft) tidak
        // terkirim di request pertama karena CookieManager masih loading async.
        try {
            val cm = android.webkit.CookieManager.getInstance()
            cm.setAcceptCookie(true)
        } catch (_: Exception) {}

        // Initialize persistent repositories with Application Context
        com.yue.browser.data.engine.UserAgentManager.init(applicationContext)
        com.yue.browser.data.repository.SettingsRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.HistoryRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.BookmarkRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.DownloadRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.UserScriptRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.OfflinePageRepositoryImpl.instance.initialize(applicationContext)



        // Use standard ViewModelProvider to instantiate the ViewModel without extra Compose ViewModel library
        viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]

        if (intent?.getBooleanExtra("show_downloads", false) == true) {
            viewModel.triggerShowDownloads()
        }

        handleUrlIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()
            val tabs by viewModel.tabs.collectAsState()
            val activeTabIndex by viewModel.activeTabIndex.collectAsState()

            // Sync night mode with the app setting
            val currentTargetNightMode = when (settings.appThemeMode) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            androidx.compose.runtime.LaunchedEffect(settings.appThemeMode) {
                if (AppCompatDelegate.getDefaultNightMode() != currentTargetNightMode) {
                    AppCompatDelegate.setDefaultNightMode(currentTargetNightMode)
                }
            }

            val isDarkTheme = when (settings.appThemeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            // Toggle FLAG_SECURE when the active tab is private (incognito).
            // This prevents screenshots/recordings AND signals keyboards (e.g. Gboard)
            // to switch into their own incognito/private mode automatically.
            val isActivePrivate = tabs.getOrNull(activeTabIndex)?.isPrivate == true
            SideEffect {
                if (isActivePrivate) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            YueTheme(isDarkMode = isDarkTheme) {
                MainBrowserScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeActivity?.get() == this) {
            activeActivity = null
        }
    }

    override fun onResume() {
        super.onResume()
        isEnteringPip = false
        android.util.Log.d("YuePip", "onResume, isInPictureInPictureMode: $isInPictureInPictureMode")
        if (::viewModel.isInitialized) {
            viewModel.setInPipMode(isInPictureInPictureMode)
        }
    }

    var isEnteringPip = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::viewModel.isInitialized) {
            val settings = viewModel.settings.value
            val hasVideo = com.yue.browser.data.engine.MediaSessionManager.activeMediaSessionId.value != null
            if (settings.isAutoPipEnabled && hasVideo) {
                enterPipMode()
            }
        }
    }

    private fun enterPipMode() {
        // Temporarily disabled PiP feature
        if (true) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                isEnteringPip = true
                val builder = android.app.PictureInPictureParams.Builder()
                // Set 16:9 aspect ratio for video playback
                builder.setAspectRatio(android.util.Rational(16, 9))
                enterPictureInPictureMode(builder.build())
            } catch (e: Exception) {
                isEnteringPip = false
                android.util.Log.e("MainActivity", "Failed to enter PiP mode", e)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        android.util.Log.d("YuePip", "onPictureInPictureModeChanged (2 params): $isInPictureInPictureMode")
        if (::viewModel.isInitialized) {
            viewModel.setInPipMode(isInPictureInPictureMode)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        android.util.Log.d("YuePip", "onPictureInPictureModeChanged (1 param): $isInPictureInPictureMode")
        if (::viewModel.isInitialized) {
            viewModel.setInPipMode(isInPictureInPictureMode)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.saveTabs(this)

            // Flush cookies to disk before the process can be killed (app update/background death)
            // Tanpa flush, cookies dari sesi terakhir (termasuk OAuth Google/Microsoft) bisa hilang.
            try {
                android.webkit.CookieManager.getInstance().flush()
            } catch (_: Exception) {}

            // Pause media if play in background is disabled for the current active tab type
            val activeIndex = viewModel.activeTabIndex.value
            val tabs = viewModel.tabs.value
            val activeTab = tabs.getOrNull(activeIndex)
            if (activeTab != null) {
                val isPrivate = activeTab.isPrivate
                val settings = viewModel.settings.value
                val keepPlaying = if (isPrivate) settings.isBackgroundPlayEnabledPrivate else settings.isBackgroundPlayEnabledNormal
                val isPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    isInPictureInPictureMode
                } else false
                if (!keepPlaying && !isPip && !isEnteringPip) {
                    com.yue.browser.data.engine.MediaSessionManager.onPauseTriggered()
                } else {
                    val webView = activeTab.session.view as? android.webkit.WebView
                    webView?.let { wv ->
                        // Force resume immediately and also after short delays to override system pauses
                        wv.post { try { wv.onResume() } catch (_: Exception) {} }
                        wv.postDelayed({ try { wv.onResume() } catch (_: Exception) {} }, 100)
                        wv.postDelayed({ try { wv.onResume() } catch (_: Exception) {} }, 300)
                        wv.postDelayed({ try { wv.onResume() } catch (_: Exception) {} }, 600)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) {
            val activeIndex = viewModel.activeTabIndex.value
            val tabs = viewModel.tabs.value
            val activeTab = tabs.getOrNull(activeIndex)
            if (activeTab != null) {
                val isPrivate = activeTab.isPrivate
                val settings = viewModel.settings.value
                val keepPlaying = if (isPrivate) settings.isBackgroundPlayEnabledPrivate else settings.isBackgroundPlayEnabledNormal
                val isPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    isInPictureInPictureMode
                } else false

                if (keepPlaying || isPip || isEnteringPip) {
                    val webView = activeTab.session.view as? android.webkit.WebView
                    webView?.let { wv ->
                        wv.post { try { wv.onResume() } catch (_: Exception) {} }
                        wv.postDelayed({ try { wv.onResume() } catch (_: Exception) {} }, 100)
                        wv.postDelayed({ try { wv.onResume() } catch (_: Exception) {} }, 300)
                        wv.postDelayed({ try { wv.onResume() } catch (_: Exception) {} }, 600)
                    }
                }
            }
        }
    }

}
