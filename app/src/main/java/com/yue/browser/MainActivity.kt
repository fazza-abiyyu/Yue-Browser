package com.yue.browser

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.yue.browser.presentation.BrowserViewModel
import com.yue.browser.presentation.theme.YueTheme
import com.yue.browser.presentation.ui.MainBrowserScreen

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect

class MainActivity : FragmentActivity() {

    private lateinit var viewModel: BrowserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge so the app extends under system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Make navigation bar transparent so bottom bar seamlessly blends
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Enable web contents debugging for Chrome DevTools inspection
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)

        // Initialize persistent repositories with Application Context
        com.yue.browser.data.engine.UserAgentManager.init(applicationContext)
        com.yue.browser.data.repository.SettingsRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.HistoryRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.BookmarkRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.DownloadRepositoryImpl.instance.initialize(applicationContext)
        com.yue.browser.data.repository.UserScriptRepositoryImpl.instance.initialize(applicationContext)

        // Use standard ViewModelProvider to instantiate the ViewModel without extra Compose ViewModel library
        viewModel = ViewModelProvider(this)[BrowserViewModel::class.java]

        setContent {
            val settings by viewModel.settings.collectAsState()
            val tabs by viewModel.tabs.collectAsState()
            val activeTabIndex by viewModel.activeTabIndex.collectAsState()

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

            YueTheme(isDarkMode = settings.isDarkModeSimulated) {
                MainBrowserScreen(viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.saveTabs(this)
        }
    }
}
