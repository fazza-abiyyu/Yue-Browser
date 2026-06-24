package com.yue.browser.presentation.ui.components

import androidx.compose.runtime.Composable
import com.yue.browser.presentation.BrowserViewModel
import com.yue.browser.presentation.ui.SettingsScreen
import com.yue.browser.presentation.ui.PlaybackSettingsScreen
import com.yue.browser.presentation.ui.HistoryScreen
import com.yue.browser.presentation.ui.BookmarksScreen
import com.yue.browser.presentation.ui.OfflinePagesScreen
import com.yue.browser.presentation.ui.downloads.DownloadsScreen
import com.yue.browser.presentation.ui.AdblockFiltersScreen
import com.yue.browser.presentation.ui.LockedWebsitesScreen
import com.yue.browser.presentation.ui.PasswordManagerScreen
import com.yue.browser.presentation.ui.isBiometricAvailable
import com.yue.browser.presentation.ui.showBiometricPrompt

@Composable
internal fun MainBrowserScreensOverlays(
    viewModel: BrowserViewModel,
    showSettingsScreen: Boolean,
    onSettingsScreenChange: (Boolean) -> Unit,
    showPlaybackSettingsScreen: Boolean,
    onPlaybackSettingsScreenChange: (Boolean) -> Unit,
    showHistoryScreen: Boolean,
    onHistoryScreenChange: (Boolean) -> Unit,
    showBookmarksScreen: Boolean,
    onBookmarksScreenChange: (Boolean) -> Unit,
    showOfflinePagesScreen: Boolean,
    onOfflinePagesScreenChange: (Boolean) -> Unit,
    showDownloadsScreen: Boolean,
    onDownloadsScreenChange: (Boolean) -> Unit,
    showAdblockFiltersScreen: Boolean,
    onAdblockFiltersScreenChange: (Boolean) -> Unit,
    showLockedWebsitesScreen: Boolean,
    onLockedWebsitesScreenChange: (Boolean) -> Unit,
    showPasswordManagerScreen: Boolean,
    onPasswordManagerScreenChange: (Boolean) -> Unit,
    context: android.content.Context
) {
    if (showSettingsScreen) {
        SettingsScreen(
            viewModel = viewModel,
            onBack = { onSettingsScreenChange(false) },
            onAdblockFiltersClick = {
                onSettingsScreenChange(false)
                onAdblockFiltersScreenChange(true)
            },
            onLockedWebsitesClick = {
                onSettingsScreenChange(false)
                onLockedWebsitesScreenChange(true)
            },
            onPasswordManagerClick = {
                val hasBio = isBiometricAvailable(context)
                if (hasBio) {
                    val fragActivity = context as? androidx.fragment.app.FragmentActivity
                    if (fragActivity != null) {
                        showBiometricPrompt(
                            activity = fragActivity,
                            onSuccess = {
                                onSettingsScreenChange(false)
                                onPasswordManagerScreenChange(true)
                            },
                            onFailed = {},
                            title = "Password Manager",
                            subtitle = "Authenticate to access saved passwords"
                        )
                    } else {
                        onSettingsScreenChange(false)
                        onPasswordManagerScreenChange(true)
                    }
                } else {
                    onSettingsScreenChange(false)
                    onPasswordManagerScreenChange(true)
                }
            },
            onPlaybackSettingsClick = {
                onSettingsScreenChange(false)
                onPlaybackSettingsScreenChange(true)
            }
        )
    }

    if (showPlaybackSettingsScreen) {
        PlaybackSettingsScreen(
            viewModel = viewModel,
            onBack = {
                onPlaybackSettingsScreenChange(false)
                onSettingsScreenChange(true)
            }
        )
    }

    if (showHistoryScreen) {
        HistoryScreen(
            viewModel = viewModel,
            onBack = { onHistoryScreenChange(false) }
        )
    }

    if (showBookmarksScreen) {
        BookmarksScreen(
            viewModel = viewModel,
            onBack = { onBookmarksScreenChange(false) }
        )
    }

    if (showOfflinePagesScreen) {
        OfflinePagesScreen(
            viewModel = viewModel,
            onBack = { onOfflinePagesScreenChange(false) }
        )
    }

    if (showDownloadsScreen) {
        DownloadsScreen(
            viewModel = viewModel,
            onBack = { onDownloadsScreenChange(false) },
            context = context
        )
    }

    if (showAdblockFiltersScreen) {
        AdblockFiltersScreen(
            viewModel = viewModel,
            onBack = {
                onAdblockFiltersScreenChange(false)
                onSettingsScreenChange(true)
            }
        )
    }

    if (showLockedWebsitesScreen) {
        LockedWebsitesScreen(
            viewModel = viewModel,
            onBack = {
                onLockedWebsitesScreenChange(false)
                onSettingsScreenChange(true)
            }
        )
    }

    if (showPasswordManagerScreen) {
        PasswordManagerScreen(
            viewModel = viewModel,
            onBack = {
                onPasswordManagerScreenChange(false)
                onSettingsScreenChange(true)
            }
        )
    }
}
