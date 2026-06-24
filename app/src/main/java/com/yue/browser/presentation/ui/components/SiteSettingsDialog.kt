package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.presentation.BrowserViewModel
import com.yue.browser.presentation.ui.PinSetupDialog
import com.yue.browser.presentation.ui.PinVerifyDialog

@Composable
fun SiteSettingsDialog(
    activeTab: BrowserTab,
    settings: BrowserSettings,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    onWebsiteLocked: (String) -> Unit,
    onWebsiteUnlocked: (String) -> Unit,
    onPinCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val pageUrl = activeTab.url
    val hostName = remember(pageUrl) {
        try {
            android.net.Uri.parse(pageUrl).host ?: pageUrl
        } catch (e: Exception) {
            pageUrl
        }
    }

    val cleanHost = remember(hostName) { hostName.trim().removePrefix("www.").lowercase() }
    var jsEnabled by remember { mutableStateOf(activeTab.session.isJavaScriptEnabled()) }
    var desktopEnabled by remember { mutableStateOf(activeTab.session.isDesktopModeEnabled()) }
    var adblockEnabled by remember {
        mutableStateOf(
            settings.isAdBlockEnabled && !settings.adblockWhitelistedDomains.any {
                cleanHost == it || cleanHost.endsWith(".$it")
            }
        )
    }
    var darkmodeEnabled by remember {
        mutableStateOf(
            settings.isDarkModeSimulated && !settings.darkmodeWhitelistedDomains.any {
                cleanHost == it || cleanHost.endsWith(".$it")
            }
        )
    }
    val isLocked = settings.lockedDomains.contains(cleanHost)
    var showPinSetupForDialog by remember { mutableStateOf(false) }
    var showPinVerifyForDialog by remember { mutableStateOf(false) }
    var pendingLockAction by remember { mutableStateOf<Boolean?>(null) }

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dialogShape = RoundedCornerShape(16.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(
            width = 1.dp,
            color = if (isSystemDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
            shape = dialogShape
        ),
        shape = dialogShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.browser_site_settings),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hostName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.browser_javascript),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.browser_javascript_subtitle),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = jsEnabled,
                                onCheckedChange = { checked ->
                                    jsEnabled = checked
                                    activeTab.session.setJavaScriptEnabled(checked)
                                    viewModel.reloadActiveTab()
                                    android.widget.Toast.makeText(context, context.getString(R.string.browser_javascript_changed), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.browser_desktop_mode),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.browser_desktop_mode_subtitle),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = desktopEnabled,
                                onCheckedChange = { checked ->
                                    desktopEnabled = checked
                                    activeTab.session.setDesktopModeEnabled(checked)
                                    viewModel.reloadActiveTab()
                                    android.widget.Toast.makeText(context, context.getString(R.string.browser_desktop_mode_changed), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        if (settings.isAdBlockEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.browser_adblock),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.browser_adblock_subtitle),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = adblockEnabled,
                                    onCheckedChange = { checked ->
                                        adblockEnabled = checked
                                        if (checked) {
                                            viewModel.removeAdblockWhitelistedDomain(cleanHost)
                                        } else {
                                            viewModel.addAdblockWhitelistedDomain(cleanHost)
                                        }
                                        viewModel.reloadActiveTab()
                                    },
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                        }

                        if (settings.isDarkModeSimulated) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.browser_darkmode),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.browser_darkmode_subtitle),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = darkmodeEnabled,
                                    onCheckedChange = { checked ->
                                        darkmodeEnabled = checked
                                        if (checked) {
                                            viewModel.removeDarkmodeWhitelistedDomain(cleanHost)
                                        } else {
                                            viewModel.addDarkmodeWhitelistedDomain(cleanHost)
                                        }
                                        viewModel.reloadActiveTab()
                                    },
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.browser_lock_website),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.browser_lock_website_subtitle),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isLocked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (settings.webLockPinHash.isBlank()) {
                                            showPinSetupForDialog = true
                                        } else {
                                            pendingLockAction = true
                                            showPinVerifyForDialog = true
                                        }
                                    } else {
                                        pendingLockAction = false
                                        showPinVerifyForDialog = true
                                    }
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addCustomFilter(cleanHost)
                                    viewModel.reloadActiveTab()
                                    android.widget.Toast.makeText(context, context.getString(R.string.browser_block_domain_done, cleanHost), android.widget.Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.browser_block_domain),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val cookieManager = android.webkit.CookieManager.getInstance()
                                        val cookieString = cookieManager.getCookie(pageUrl)
                                        if (cookieString != null) {
                                            val cookies = cookieString.split(";")
                                            for (cookie in cookies) {
                                                val parts = cookie.split("=")
                                                if (parts.isNotEmpty()) {
                                                    val name = parts[0].trim()
                                                    cookieManager.setCookie(pageUrl, "$name=; Domain=$hostName; Path=/; Max-Age=-1; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                                    cookieManager.setCookie(pageUrl, "$name=; Domain=.$hostName; Path=/; Max-Age=-1; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                                    val baseDomain = hostName.removePrefix("www.").removePrefix("m.")
                                                    if (baseDomain != hostName) {
                                                        cookieManager.setCookie(pageUrl, "$name=; Domain=$baseDomain; Path=/; Max-Age=-1; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                                        cookieManager.setCookie(pageUrl, "$name=; Domain=.$baseDomain; Path=/; Max-Age=-1; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                                    }
                                                }
                                            }
                                            cookieManager.flush()
                                        }
                                        activeTab.session.evaluateJavascript(
                                            "try { localStorage.clear(); sessionStorage.clear(); } catch(e) {};",
                                            null
                                        )
                                        val uri = android.net.Uri.parse(pageUrl)
                                        val origin = "${uri.scheme}://${uri.host}"
                                        android.webkit.WebStorage.getInstance().deleteOrigin(origin)
                                        android.widget.Toast.makeText(context, context.getString(R.string.browser_cookies_cleared, hostName), android.widget.Toast.LENGTH_LONG).show()
                                        onDismiss()
                                        viewModel.reloadActiveTab()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, context.getString(R.string.browser_cookies_clear_failed, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.browser_clear_cookies),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )

    if (showPinSetupForDialog) {
        PinSetupDialog(
            title = stringResource(R.string.browser_lock_website),
            onDismiss = { showPinSetupForDialog = false },
            onConfirm = { pin ->
                viewModel.setupWebLockPin(pin)
                viewModel.addLockedDomain(cleanHost)
                showPinSetupForDialog = false
                onPinCreated(cleanHost)
            }
        )
    }

    if (showPinVerifyForDialog) {
        val action = pendingLockAction
        PinVerifyDialog(
            title = stringResource(if (action == true) R.string.browser_lock_website else R.string.browser_unlock_website),
            message = stringResource(if (action == true) R.string.browser_enter_pin_lock else R.string.browser_enter_pin_unlock, cleanHost),
            onVerify = { pin -> viewModel.verifyWebLockPin(pin) },
            maxAttempts = settings.webLockMaxAttempts,
            attemptsEnabled = settings.webLockAttemptsEnabled,
            onDismiss = { showPinVerifyForDialog = false; pendingLockAction = null },
            onConfirmed = {
                if (action == true) {
                    viewModel.addLockedDomain(cleanHost)
                    onWebsiteLocked(cleanHost)
                } else {
                    viewModel.removeLockedDomain(cleanHost)
                    onWebsiteUnlocked(cleanHost)
                }
                showPinVerifyForDialog = false
                pendingLockAction = null
            }
        )
    }
}
