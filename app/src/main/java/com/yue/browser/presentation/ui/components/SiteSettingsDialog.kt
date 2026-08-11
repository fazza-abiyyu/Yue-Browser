package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
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
import com.yue.browser.presentation.setSitePermission
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

    val webView = activeTab.session.view as? android.webkit.WebView
    val sslCertificate = remember(activeTab.url) { webView?.certificate }
    var showCertificateDialog by remember { mutableStateOf(false) }

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
                val tiles = remember(jsEnabled, desktopEnabled, adblockEnabled, darkmodeEnabled, isLocked, settings.isAdBlockEnabled, settings.isDarkModeSimulated, sslCertificate) {
                    buildList {
                        add(TileData(context.getString(R.string.browser_javascript), context.getString(R.string.browser_javascript_subtitle), Icons.Default.Code, jsEnabled) {
                            val next = !jsEnabled
                            jsEnabled = next
                            activeTab.session.setJavaScriptEnabled(next)
                            viewModel.reloadActiveTab()
                            android.widget.Toast.makeText(context, context.getString(R.string.browser_javascript_changed), android.widget.Toast.LENGTH_SHORT).show()
                        })
                        add(TileData(context.getString(R.string.browser_desktop_mode), context.getString(R.string.browser_desktop_mode_subtitle), Icons.Default.Computer, desktopEnabled) {
                            val next = !desktopEnabled
                            desktopEnabled = next
                            activeTab.session.setDesktopModeEnabled(next)
                            viewModel.reloadActiveTab()
                            android.widget.Toast.makeText(context, context.getString(R.string.browser_desktop_mode_changed), android.widget.Toast.LENGTH_SHORT).show()
                        })
                        if (settings.isAdBlockEnabled) {
                            add(TileData(context.getString(R.string.browser_adblock), context.getString(R.string.browser_adblock_subtitle), Icons.Default.Shield, adblockEnabled) {
                                val next = !adblockEnabled
                                adblockEnabled = next
                                if (next) {
                                    viewModel.removeAdblockWhitelistedDomain(cleanHost)
                                } else {
                                    viewModel.addAdblockWhitelistedDomain(cleanHost)
                                }
                                viewModel.reloadActiveTab()
                            })
                        }
                        if (settings.isDarkModeSimulated) {
                            add(TileData(context.getString(R.string.browser_darkmode), context.getString(R.string.browser_darkmode_subtitle), Icons.Default.DarkMode, darkmodeEnabled) {
                                val next = !darkmodeEnabled
                                darkmodeEnabled = next
                                if (next) {
                                    viewModel.removeDarkmodeWhitelistedDomain(cleanHost)
                                } else {
                                    viewModel.addDarkmodeWhitelistedDomain(cleanHost)
                                }
                                viewModel.reloadActiveTab()
                            })
                        }
                        add(TileData(context.getString(R.string.browser_lock_website), context.getString(R.string.browser_lock_website_subtitle), Icons.Default.Lock, isLocked) {
                            val next = !isLocked
                            if (next) {
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
                        })
                        add(TileData(
                            title = context.getString(R.string.ssl_cert_title),
                            subtitle = if (sslCertificate != null) context.getString(R.string.ssl_cert_verified) else context.getString(R.string.ssl_cert_unencrypted),
                            icon = if (sslCertificate != null) Icons.Default.VerifiedUser else Icons.Default.Warning,
                            isOn = sslCertificate != null,
                            onClick = {
                                showCertificateDialog = true
                            }
                        ))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chunked = tiles.chunked(2)
                    chunked.forEach { rowTiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTiles.forEach { tile ->
                                QuickSettingTile(
                                    title = tile.title,
                                    subtitle = tile.subtitle,
                                    icon = tile.icon,
                                    isOn = tile.isOn,
                                    onClick = tile.onClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowTiles.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                val currentPermissions = settings.sitePermissions[cleanHost] ?: emptyMap()
                val locationAllowed = currentPermissions["location"] == true
                val cameraAllowed = currentPermissions["camera"] == true
                val micAllowed = currentPermissions["microphone"] == true

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_site_permissions),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentPermissions.any { it.value == true }) {
                            Text(
                                text = stringResource(R.string.permission_clear_all),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable {
                                    viewModel.setSitePermission(cleanHost, "location", null)
                                    viewModel.setSitePermission(cleanHost, "camera", null)
                                    viewModel.setSitePermission(cleanHost, "microphone", null)
                                    viewModel.reloadActiveTab()
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        val inactiveBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        val activeBorder = MaterialTheme.colorScheme.primary
                        val inactiveBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        val activeTint = MaterialTheme.colorScheme.primary
                        val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

                        // Lokasi Grid Item
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (locationAllowed) activeBg else inactiveBg)
                                .border(1.dp, if (locationAllowed) activeBorder else inactiveBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.setSitePermission(cleanHost, "location", !locationAllowed) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (locationAllowed) activeTint else inactiveTint)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.permission_location), fontSize = 11.sp, fontWeight = if (locationAllowed) FontWeight.Bold else FontWeight.Normal, color = if (locationAllowed) activeTint else inactiveTint)
                            }
                        }

                        // Kamera Grid Item
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (cameraAllowed) activeBg else inactiveBg)
                                .border(1.dp, if (cameraAllowed) activeBorder else inactiveBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.setSitePermission(cleanHost, "camera", !cameraAllowed) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (cameraAllowed) activeTint else inactiveTint)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.permission_camera), fontSize = 11.sp, fontWeight = if (cameraAllowed) FontWeight.Bold else FontWeight.Normal, color = if (cameraAllowed) activeTint else inactiveTint)
                            }
                        }

                        // Mikrofon Grid Item
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (micAllowed) activeBg else inactiveBg)
                                .border(1.dp, if (micAllowed) activeBorder else inactiveBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.setSitePermission(cleanHost, "microphone", !micAllowed) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (micAllowed) activeTint else inactiveTint)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.permission_microphone), fontSize = 11.sp, fontWeight = if (micAllowed) FontWeight.Bold else FontWeight.Normal, color = if (micAllowed) activeTint else inactiveTint)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Blokir Domain Button
                    OutlinedButton(
                        onClick = {
                            viewModel.addCustomFilter(cleanHost)
                            viewModel.reloadActiveTab()
                            android.widget.Toast.makeText(context, context.getString(R.string.browser_block_domain_done, cleanHost), android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.browser_block_domain), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    // Hapus Cookie Button
                    OutlinedButton(
                        onClick = {
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
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.browser_clear_cookies), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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

    if (showCertificateDialog) {
        AlertDialog(
            onDismissRequest = { showCertificateDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (sslCertificate != null) Icons.Default.VerifiedUser else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (sslCertificate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(stringResource(R.string.ssl_cert_detail_title), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (sslCertificate != null) {
                        Text(stringResource(R.string.ssl_cert_secure_msg), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.ssl_cert_issued_to), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(sslCertificate.issuedTo?.cName ?: sslCertificate.issuedTo?.dName ?: "N/A", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            Text(stringResource(R.string.ssl_cert_issued_by), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(sslCertificate.issuedBy?.cName ?: sslCertificate.issuedBy?.dName ?: "N/A", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            Text(stringResource(R.string.ssl_cert_validity), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                stringResource(
                                    R.string.ssl_cert_validity_from_to,
                                    sslCertificate.validNotBeforeDate?.toString() ?: "N/A",
                                    sslCertificate.validNotAfterDate?.toString() ?: "N/A"
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(stringResource(R.string.ssl_cert_unsecure_msg), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCertificateDialog = false }) {
                    Text(stringResource(R.string.ssl_cert_close), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

private data class TileData(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isOn: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun QuickSettingTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val inactiveBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val activeBorder = MaterialTheme.colorScheme.primary
    val inactiveBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val activeTint = MaterialTheme.colorScheme.primary
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOn) activeBg else inactiveBg)
            .border(1.dp, if (isOn) activeBorder else inactiveBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isOn) activeTint else inactiveTint
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOn) activeTint else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
