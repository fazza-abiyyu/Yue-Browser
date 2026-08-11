package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.presentation.BrowserViewModel
import com.yue.browser.presentation.setHttpsOnlyModeEnabled
import com.yue.browser.presentation.setDoNotTrackEnabled
import com.yue.browser.presentation.setBlockThirdPartyCookiesEnabled
import com.yue.browser.presentation.setFingerprintProtectionEnabled
import com.yue.browser.presentation.setReferrerControlEnabled
import com.yue.browser.presentation.setSafeBrowsingEnabled

data class SearchEngine(
    val name: String,
    val url: String,
    val icon: @Composable (Modifier) -> Unit
)



private val defaultSearchEngines = listOf(
    SearchEngine("Google", "https://www.google.com/search?q=", { GoogleIcon(it) }),
    SearchEngine("Bing", "https://www.bing.com/search?q=", { BingIcon(it) }),
    SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=", { DuckDuckGoIcon(it) }),
    SearchEngine("Yahoo", "https://search.yahoo.com/search?p=", { YahooIcon(it) })
)

enum class SettingsSubPage {
    MAIN, UMUM, PERSONALISASI, PRIVACY_SECURITY, BACKUP
}

private sealed class SettingsEntry {
    data class Header(val label: String) : SettingsEntry()
    data class Toggle(
        val icon: ImageVector,
        val title: String,
        val subtitle: String,
        val isChecked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : SettingsEntry()
    data class Clickable(
        val icon: ImageVector,
        val title: String,
        val subtitle: String?,
        val onClick: () -> Unit
    ) : SettingsEntry()
    data class Divider(val indent: Boolean = false) : SettingsEntry()
    data class SearchEngineItem(
        val engine: SearchEngine,
        val isActive: Boolean,
        val onClick: () -> Unit
    ) : SettingsEntry()
    data object CustomSearch : SettingsEntry()
    data object AboutSection : SettingsEntry()
    data class TextButton(
        val text: String,
        val onClick: () -> Unit
    ) : SettingsEntry()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    onAdblockFiltersClick: () -> Unit,
    onLockedWebsitesClick: () -> Unit = {},
    onPasswordManagerClick: () -> Unit = {},
    onPlaybackSettingsClick: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    var showIpDnsChecker by remember { mutableStateOf(false) }
    var showSitePermissions by remember { mutableStateOf(false) }

    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearCookiesSelected by remember { mutableStateOf(true) }
    var clearCacheSelected by remember { mutableStateOf(true) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Export/Import password dialogs
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var showSkipConfirmationDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var pendingImportJson by remember { mutableStateOf("") }

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = viewModel.exportData(exportPassword)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, R.string.settings_export_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_export_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                val isEncrypted = try {
                    val pwObj = org.json.JSONObject(json).optJSONObject("passwords")
                    pwObj != null && pwObj.optBoolean("encrypted", false)
                } catch (_: Exception) { false }

                if (isEncrypted) {
                    pendingImportJson = json
                    showImportPasswordDialog = true
                } else {
                    val result = viewModel.importData(json)
                    if (result.success) {
                        Toast.makeText(context, R.string.settings_import_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.settings_import_failed, result.message), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.settings_import_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val shouldShow = { text: String ->
        searchQuery.isBlank() || text.contains(searchQuery, ignoreCase = true)
    }

    var currentSubPage by remember { mutableStateOf(SettingsSubPage.MAIN) }

    androidx.activity.compose.BackHandler(
        enabled = currentSubPage != SettingsSubPage.MAIN || searchQuery.isNotBlank()
    ) {
        if (searchQuery.isNotBlank()) {
            searchQuery = ""
        } else if (currentSubPage != SettingsSubPage.MAIN) {
            currentSubPage = SettingsSubPage.MAIN
        }
    }

    val entries = buildList {
        if (searchQuery.isNotBlank()) {
            add(SettingsEntry.Header(stringResource(R.string.settings_section_general)))
            add(SettingsEntry.Toggle(
                icon = Icons.Default.Code,
                title = stringResource(R.string.settings_javascript),
                subtitle = stringResource(R.string.settings_javascript_subtitle),
                isChecked = settings.isJavaScriptEnabled,
                onCheckedChange = { viewModel.toggleJavaScript(it) }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Toggle(
                icon = Icons.Default.ZoomIn,
                title = stringResource(R.string.settings_page_zoom),
                subtitle = stringResource(R.string.settings_page_zoom_subtitle),
                isChecked = settings.isZoomEnabled,
                onCheckedChange = { viewModel.toggleZoom(it) }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Clickable(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_language),
                subtitle = when (settings.appLanguage) {
                    "en" -> stringResource(R.string.settings_language_en)
                    "id", "in" -> stringResource(R.string.settings_language_id)
                    else -> stringResource(R.string.settings_language_system)
                },
                onClick = { showLanguageDialog = true }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Clickable(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.settings_clear_data),
                subtitle = stringResource(R.string.settings_clear_data_subtitle),
                onClick = { showClearDataDialog = true }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Header(stringResource(R.string.settings_section_search_engine)))
            defaultSearchEngines.forEach { engine ->
                add(SettingsEntry.SearchEngineItem(
                    engine = engine,
                    isActive = settings.searchEngineUrl == engine.url,
                    onClick = { viewModel.setSearchEngineUrl(engine.url) }
                ))
            }
            add(SettingsEntry.Divider(indent = true))
            add(SettingsEntry.CustomSearch)
            add(SettingsEntry.Divider())
            add(SettingsEntry.Header(stringResource(R.string.settings_section_adblock)))
            add(SettingsEntry.Toggle(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.settings_adblock_enable),
                subtitle = stringResource(R.string.settings_adblock_subtitle),
                isChecked = settings.isAdBlockEnabled,
                onCheckedChange = { viewModel.toggleAdBlock(it) }
            ))
            add(SettingsEntry.Divider())
            val totalFilters = settings.customAdBlockFilters.size +
                    settings.blockedCssSelectors.values.sumOf { it.size }
            if (settings.isAdBlockEnabled || totalFilters > 0) {
                add(SettingsEntry.Clickable(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.settings_custom_filters),
                    subtitle = if (totalFilters > 0) stringResource(R.string.settings_custom_filters_count, totalFilters) else stringResource(R.string.settings_custom_filters_empty),
                    onClick = { onAdblockFiltersClick() }
                ))
                add(SettingsEntry.Divider())
            }
            add(SettingsEntry.Header(stringResource(R.string.settings_privacy_and_security)))
            add(SettingsEntry.Toggle(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.settings_https_only_mode),
                subtitle = stringResource(R.string.settings_https_only_mode_subtitle),
                isChecked = settings.isHttpsOnlyModeEnabled,
                onCheckedChange = { viewModel.setHttpsOnlyModeEnabled(it) }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Toggle(
                icon = Icons.Default.VisibilityOff,
                title = stringResource(R.string.settings_do_not_track),
                subtitle = stringResource(R.string.settings_do_not_track_subtitle),
                isChecked = settings.isDoNotTrackEnabled,
                onCheckedChange = { viewModel.setDoNotTrackEnabled(it) }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Clickable(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_ip_dns_checker),
                subtitle = stringResource(R.string.settings_ip_dns_checker_subtitle),
                onClick = { showIpDnsChecker = true }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Clickable(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_site_permissions),
                subtitle = stringResource(R.string.settings_site_permissions_subtitle),
                onClick = { showSitePermissions = true }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Header(stringResource(R.string.settings_section_website_lock)))
            val lockedCount = settings.lockedDomains.size
            val pinSet = settings.webLockPinHash.isNotBlank()
            add(SettingsEntry.Clickable(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_website_lock),
                subtitle = when {
                    !pinSet -> stringResource(R.string.settings_lock_summary_pin_not_set)
                    lockedCount == 0 -> stringResource(R.string.settings_lock_summary_no_websites)
                    else -> stringResource(R.string.settings_lock_summary_active, lockedCount)
                },
                onClick = { onLockedWebsitesClick() }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Clickable(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.password_title),
                subtitle = null,
                onClick = { onPasswordManagerClick() }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Header(stringResource(R.string.settings_section_playback)))
            add(SettingsEntry.Clickable(
                icon = Icons.Default.PlayArrow,
                title = stringResource(R.string.settings_playback_video_settings),
                subtitle = stringResource(R.string.settings_playback_video_settings_subtitle),
                onClick = { onPlaybackSettingsClick() }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Header(stringResource(R.string.settings_section_backup)))
            add(SettingsEntry.Clickable(
                icon = Icons.Default.Share,
                title = stringResource(R.string.settings_export),
                subtitle = null,
                onClick = { showExportPasswordDialog = true }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Clickable(
                icon = Icons.Default.Restore,
                title = stringResource(R.string.settings_import),
                subtitle = null,
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            ))
            add(SettingsEntry.Divider())
            add(SettingsEntry.Header(stringResource(R.string.settings_section_about)))
            add(SettingsEntry.AboutSection)
        } else {
            when (currentSubPage) {
                SettingsSubPage.MAIN -> {
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_cat_general),
                        subtitle = stringResource(R.string.settings_cat_general_summary),
                        onClick = { currentSubPage = SettingsSubPage.UMUM }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Tv,
                        title = stringResource(R.string.settings_cat_personalization),
                        subtitle = stringResource(R.string.settings_cat_personalization_summary),
                        onClick = { currentSubPage = SettingsSubPage.PERSONALISASI }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Shield,
                        title = stringResource(R.string.settings_cat_privacy_security),
                        subtitle = stringResource(R.string.settings_cat_privacy_security_summary),
                        onClick = { currentSubPage = SettingsSubPage.PRIVACY_SECURITY }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.password_title),
                        subtitle = stringResource(R.string.settings_cat_passwords_summary),
                        onClick = { onPasswordManagerClick() }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.settings_cat_backup),
                        subtitle = stringResource(R.string.settings_cat_backup_summary),
                        onClick = { currentSubPage = SettingsSubPage.BACKUP }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Header(stringResource(R.string.settings_section_about)))
                    add(SettingsEntry.AboutSection)
                }
                SettingsSubPage.UMUM -> {
                    add(SettingsEntry.Header(stringResource(R.string.settings_cat_general)))
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.Code,
                        title = stringResource(R.string.settings_javascript),
                        subtitle = stringResource(R.string.settings_javascript_subtitle),
                        isChecked = settings.isJavaScriptEnabled,
                        onCheckedChange = { viewModel.toggleJavaScript(it) }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.ZoomIn,
                        title = stringResource(R.string.settings_page_zoom),
                        subtitle = stringResource(R.string.settings_page_zoom_subtitle),
                        isChecked = settings.isZoomEnabled,
                        onCheckedChange = { viewModel.toggleZoom(it) }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = when (settings.appLanguage) {
                            "en" -> stringResource(R.string.settings_language_en)
                            "id", "in" -> stringResource(R.string.settings_language_id)
                            else -> stringResource(R.string.settings_language_system)
                        },
                        onClick = { showLanguageDialog = true }
                    ))
                    add(SettingsEntry.Divider())
                }
                SettingsSubPage.BACKUP -> {
                    add(SettingsEntry.Header(stringResource(R.string.settings_cat_backup)))
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.settings_export),
                        subtitle = stringResource(R.string.settings_export_subtitle),
                        onClick = { showExportPasswordDialog = true }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Restore,
                        title = stringResource(R.string.settings_import),
                        subtitle = stringResource(R.string.settings_import_subtitle),
                        onClick = { importLauncher.launch(arrayOf("application/json")) }
                    ))
                    add(SettingsEntry.Divider())
                }
                SettingsSubPage.PERSONALISASI -> {
                    add(SettingsEntry.Header(stringResource(R.string.settings_section_search_engine)))
                    defaultSearchEngines.forEach { engine ->
                        add(SettingsEntry.SearchEngineItem(
                            engine = engine,
                            isActive = settings.searchEngineUrl == engine.url,
                            onClick = { viewModel.setSearchEngineUrl(engine.url) }
                        ))
                    }
                    add(SettingsEntry.Divider(indent = true))
                    add(SettingsEntry.CustomSearch)
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Header(stringResource(R.string.settings_header_adblock_playback)))
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.Shield,
                        title = stringResource(R.string.settings_adblock_enable),
                        subtitle = stringResource(R.string.settings_adblock_subtitle),
                        isChecked = settings.isAdBlockEnabled,
                        onCheckedChange = { viewModel.toggleAdBlock(it) }
                    ))
                    add(SettingsEntry.Divider())
                    val totalFilters = settings.customAdBlockFilters.size +
                            settings.blockedCssSelectors.values.sumOf { it.size }
                    if (settings.isAdBlockEnabled || totalFilters > 0) {
                        add(SettingsEntry.Clickable(
                            icon = Icons.Default.Shield,
                            title = stringResource(R.string.settings_custom_filters),
                            subtitle = if (totalFilters > 0) stringResource(R.string.settings_custom_filters_count, totalFilters) else stringResource(R.string.settings_custom_filters_empty),
                            onClick = { onAdblockFiltersClick() }
                        ))
                        add(SettingsEntry.Divider())
                    }
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.PlayArrow,
                        title = stringResource(R.string.settings_playback_video_settings),
                        subtitle = stringResource(R.string.settings_playback_video_settings_subtitle),
                        onClick = { onPlaybackSettingsClick() }
                    ))
                    add(SettingsEntry.Divider())
                }
                SettingsSubPage.PRIVACY_SECURITY -> {
                    add(SettingsEntry.Header(stringResource(R.string.settings_header_privacy)))
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.VisibilityOff,
                        title = stringResource(R.string.settings_do_not_track),
                        subtitle = stringResource(R.string.settings_do_not_track_subtitle),
                        isChecked = settings.isDoNotTrackEnabled,
                        onCheckedChange = { viewModel.setDoNotTrackEnabled(it) }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.settings_third_party_cookies),
                        subtitle = stringResource(R.string.settings_third_party_cookies_summary),
                        isChecked = settings.isBlockThirdPartyCookiesEnabled,
                        onCheckedChange = { viewModel.setBlockThirdPartyCookiesEnabled(it) }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.VisibilityOff,
                        title = stringResource(R.string.settings_fingerprint_protection),
                        subtitle = stringResource(R.string.settings_fingerprint_protection_summary),
                        isChecked = settings.isFingerprintProtectionEnabled,
                        onCheckedChange = { viewModel.setFingerprintProtectionEnabled(it) }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_referrer_control),
                        subtitle = stringResource(R.string.settings_referrer_control_summary),
                        isChecked = settings.isReferrerControlEnabled,
                        onCheckedChange = { viewModel.setReferrerControlEnabled(it) }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_ip_dns_checker),
                        subtitle = stringResource(R.string.settings_ip_dns_checker_subtitle),
                        onClick = { showIpDnsChecker = true }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.settings_site_permissions),
                        subtitle = stringResource(R.string.settings_site_permissions_subtitle),
                        onClick = { showSitePermissions = true }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Header(stringResource(R.string.settings_header_security)))
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.Shield,
                        title = stringResource(R.string.settings_safe_browsing),
                        subtitle = stringResource(R.string.settings_safe_browsing_summary),
                        isChecked = settings.isSafeBrowsingEnabled,
                        onCheckedChange = { viewModel.setSafeBrowsingEnabled(it) }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Toggle(
                        icon = Icons.Default.Shield,
                        title = stringResource(R.string.settings_https_only_mode),
                        subtitle = stringResource(R.string.settings_https_only_mode_subtitle),
                        isChecked = settings.isHttpsOnlyModeEnabled,
                        onCheckedChange = { viewModel.setHttpsOnlyModeEnabled(it) }
                    ))
                    add(SettingsEntry.Divider())
                    val lockedCount = settings.lockedDomains.size
                    val pinSet = settings.webLockPinHash.isNotBlank()
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.settings_website_lock),
                        subtitle = when {
                            !pinSet -> stringResource(R.string.settings_lock_summary_pin_not_set)
                            lockedCount == 0 -> stringResource(R.string.settings_lock_summary_no_websites)
                            else -> stringResource(R.string.settings_lock_summary_active, lockedCount)
                        },
                        onClick = { onLockedWebsitesClick() }
                    ))
                    add(SettingsEntry.Divider())
                    add(SettingsEntry.Clickable(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.settings_clear_data),
                        subtitle = stringResource(R.string.settings_clear_data_subtitle),
                        onClick = { showClearDataDialog = true }
                    ))
                    add(SettingsEntry.Divider())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "Search Settings"
                            currentSubPage == SettingsSubPage.MAIN -> stringResource(R.string.settings_title)
                            currentSubPage == SettingsSubPage.UMUM -> stringResource(R.string.settings_cat_general)
                            currentSubPage == SettingsSubPage.PERSONALISASI -> stringResource(R.string.settings_cat_personalization)
                            currentSubPage == SettingsSubPage.PRIVACY_SECURITY -> stringResource(R.string.settings_cat_privacy_security)
                            currentSubPage == SettingsSubPage.BACKUP -> stringResource(R.string.settings_cat_backup)
                            else -> stringResource(R.string.settings_title)
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSubPage != SettingsSubPage.MAIN && searchQuery.isBlank()) {
                            currentSubPage = SettingsSubPage.MAIN
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.settings_search_hint), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

    val customSearchLabel = stringResource(R.string.settings_custom_search)
    val aboutSectionLabel = stringResource(R.string.settings_section_about)

    val filteredEntries = remember(entries, searchQuery, customSearchLabel, aboutSectionLabel) {
        if (searchQuery.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                when (entry) {
                    is SettingsEntry.Header -> {
                        val startIdx = entries.indexOf(entry)
                        val afterStart = entries.subList(startIdx + 1, entries.size)
                        val endIdx = afterStart.indexOfFirst { it is SettingsEntry.Header }
                        val sectionContent = if (endIdx == -1) afterStart else afterStart.subList(0, endIdx)
                        entry.label.contains(searchQuery, ignoreCase = true) ||
                        sectionContent.any { contentEntry ->
                            when (contentEntry) {
                                is SettingsEntry.Toggle ->
                                    contentEntry.title.contains(searchQuery, ignoreCase = true) ||
                                    contentEntry.subtitle.contains(searchQuery, ignoreCase = true)
                                is SettingsEntry.Clickable ->
                                    contentEntry.title.contains(searchQuery, ignoreCase = true) ||
                                    (contentEntry.subtitle != null && contentEntry.subtitle.contains(searchQuery, ignoreCase = true))
                                is SettingsEntry.SearchEngineItem ->
                                    contentEntry.engine.name.contains(searchQuery, ignoreCase = true)
                                is SettingsEntry.CustomSearch ->
                                    customSearchLabel.contains(searchQuery, ignoreCase = true)
                                is SettingsEntry.AboutSection ->
                                    aboutSectionLabel.contains(searchQuery, ignoreCase = true)
                                else -> false
                            }
                        }
                    }
                    is SettingsEntry.Divider -> false
                    is SettingsEntry.Toggle ->
                        entry.title.contains(searchQuery, ignoreCase = true) ||
                        entry.subtitle.contains(searchQuery, ignoreCase = true)
                    is SettingsEntry.Clickable ->
                        entry.title.contains(searchQuery, ignoreCase = true) ||
                        (entry.subtitle != null && entry.subtitle.contains(searchQuery, ignoreCase = true))
                    is SettingsEntry.SearchEngineItem ->
                        entry.engine.name.contains(searchQuery, ignoreCase = true)
                    is SettingsEntry.CustomSearch ->
                        customSearchLabel.contains(searchQuery, ignoreCase = true)
                    is SettingsEntry.AboutSection ->
                        aboutSectionLabel.contains(searchQuery, ignoreCase = true)
                    is SettingsEntry.TextButton -> true
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(filteredEntries) { entry ->
            when (entry) {
                is SettingsEntry.Header -> {
                    SectionHeader(entry.label)
                }
                is SettingsEntry.Toggle -> {
                    SettingsItem(
                        icon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                        title = entry.title,
                        subtitle = entry.subtitle,
                        trailing = {
                            Switch(
                                checked = entry.isChecked,
                                onCheckedChange = entry.onCheckedChange
                            )
                        }
                    )
                }
                is SettingsEntry.Clickable -> {
                    SettingsItem(
                        icon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                        title = entry.title,
                        subtitle = entry.subtitle,
                        trailing = null,
                        onClick = entry.onClick
                    )
                }
                is SettingsEntry.Divider -> {
                    SettingsDivider(indent = entry.indent)
                }
                is SettingsEntry.SearchEngineItem -> {
                    val engine = entry.engine
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { entry.onClick() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        engine.icon(Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = engine.name,
                            fontSize = 15.sp,
                            fontWeight = if (entry.isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (entry.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        if (entry.isActive) {
                            Text(stringResource(R.string.settings_search_active), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                is SettingsEntry.CustomSearch -> {
                    var customUrl by remember { mutableStateOf("") }
                    val isCustomActive = defaultSearchEngines.none { it.url == settings.searchEngineUrl }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isCustomActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "*",
                                    color = if (isCustomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.settings_custom_search),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCustomActive) {
                                Text(stringResource(R.string.settings_search_active), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                placeholder = { Text(stringResource(R.string.settings_custom_search_placeholder), fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    val trimmed = customUrl.trim()
                                    if (trimmed.isNotBlank()) {
                                        viewModel.setSearchEngineUrl(trimmed)
                                    }
                                },
                                enabled = customUrl.trim().isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(stringResource(R.string.settings_custom_search_apply), fontSize = 13.sp)
                            }
                        }
                        if (isCustomActive) {
                            Text(
                                text = settings.searchEngineUrl,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                is SettingsEntry.AboutSection -> {
                    AboutSectionContent(
                        onOpenUrl = { url ->
                            viewModel.createNewTab(context, url)
                            onBack()
                        }
                    )
                }
                is SettingsEntry.TextButton -> {
                    if (shouldShow(entry.text)) {
                        Text("")
                    }
                }
            }
        }
    }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(stringResource(R.string.settings_clear_data_dialog_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearCookiesSelected = !clearCookiesSelected }
                            .padding(vertical = 6.dp)
                    ) {
                        Checkbox(checked = clearCookiesSelected, onCheckedChange = { clearCookiesSelected = it })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_cookies), fontSize = 14.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearCacheSelected = !clearCacheSelected }
                            .padding(vertical = 6.dp)
                    ) {
                        Checkbox(checked = clearCacheSelected, onCheckedChange = { clearCacheSelected = it })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_cache), fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBrowserData(context, clearCookiesSelected, clearCacheSelected)
                    showClearDataDialog = false
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(stringResource(R.string.settings_language), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    val options = listOf("system", "en", "in")
                    options.forEach { option ->
                        val label = when (option) {
                            "en" -> stringResource(R.string.settings_language_en)
                            "id", "in" -> stringResource(R.string.settings_language_id)
                            else -> stringResource(R.string.settings_language_system)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLanguage(option)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = settings.appLanguage == option || (option == "in" && settings.appLanguage == "id"),
                                onClick = {
                                    viewModel.setAppLanguage(option)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Export password dialog
    if (showExportPasswordDialog) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var showPw by remember { mutableStateOf(false) }
        var showConfirmPw by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val pwShort = stringResource(R.string.settings_export_password_short)
        val pwMismatch = stringResource(R.string.settings_export_password_mismatch)
        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(stringResource(R.string.settings_export_password_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(stringResource(R.string.settings_export_password_message), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text(stringResource(R.string.settings_export_password_label)) },
                        singleLine = true,
                        visualTransformation = if (showPw) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = { Text(stringResource(R.string.settings_export_password_confirm)) },
                        singleLine = true,
                        visualTransformation = if (showConfirmPw) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPw = !showConfirmPw }) {
                                Icon(if (showConfirmPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (error != null) {
                        Text(error!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (password.length < 4) {
                        error = pwShort
                    } else if (password != confirmPassword) {
                        error = pwMismatch
                    } else {
                        exportPassword = password
                        showExportPasswordDialog = false
                        exportFileLauncher.launch("yue-settings-backup.json")
                    }
                }) {
                    Text(stringResource(R.string.settings_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Import password dialog
    if (showImportPasswordDialog) {
        var password by remember { mutableStateOf("") }
        var showPw by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var showSkipOption by remember { mutableStateOf(false) }
        val importLabel = stringResource(R.string.settings_import)
        val cancelLabel = stringResource(R.string.cancel)
        val successMsg = stringResource(R.string.settings_import_success)
        AlertDialog(
            onDismissRequest = {
                showImportPasswordDialog = false
                pendingImportJson = ""
            },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(stringResource(R.string.settings_import_password_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(stringResource(R.string.settings_import_password_message), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text(stringResource(R.string.settings_import_password_label)) },
                        singleLine = true,
                        visualTransformation = if (showPw) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        isError = error != null,
                        supportingText = if (error != null) {{ Text(error!!) }} else null
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    TextButton(onClick = {
                        val result = viewModel.importData(pendingImportJson, password)
                        if (result.success) {
                            Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                            showImportPasswordDialog = false
                            pendingImportJson = ""
                        } else {
                            error = context.getString(R.string.wrong_password)
                            showSkipOption = true
                        }
                    }) {
                        Text(importLabel)
                    }
                    if (showSkipOption) {
                        TextButton(onClick = {
                            showSkipConfirmationDialog = true
                        }) {
                            Text(stringResource(R.string.skip_passwords))
                        }
                    }
                    TextButton(onClick = {
                        showImportPasswordDialog = false
                        pendingImportJson = ""
                    }) {
                        Text(cancelLabel)
                    }
                }
            },
            dismissButton = null
        )
    }

    if (showSkipConfirmationDialog) {
        val successMsg = stringResource(R.string.settings_import_success)
        AlertDialog(
            onDismissRequest = { showSkipConfirmationDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(stringResource(R.string.skip_passwords), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(stringResource(R.string.skip_passwords_description), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = viewModel.importData(pendingImportJson, skipPasswords = true)
                    if (result.success) {
                        Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                        showImportPasswordDialog = false
                        showSkipConfirmationDialog = false
                        pendingImportJson = ""
                    }
                }) {
                    Text(stringResource(R.string.skip_passwords))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirmationDialog = false }) {
                    Text(stringResource(R.string.back))
                }
            }
        )
    }

    if (showIpDnsChecker) {
        IpDnsCheckerScreen(onBack = { showIpDnsChecker = false })
    }

    if (showSitePermissions) {
        SitePermissionsScreen(viewModel = viewModel, onBack = { showSitePermissions = false })
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp, end = 16.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun SettingsItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: @Composable (() -> Unit)?,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(modifier = Modifier.width(24.dp)) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingsDivider(indent: Boolean = false) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (indent) 54.dp else 0.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun AboutSectionContent(onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brandColor = if (isDarkTheme) Color(0xFFDB2777) else Color(0xFFEC4899)
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Yue",
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            color = brandColor,
            letterSpacing = (-0.5).sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.settings_about_version, versionName),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.settings_about_description),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onOpenUrl("https://yue.abiyyu.xyz")
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_about_website),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/fazza-abiyyu/Yue-Browser"))
                    context.startActivity(intent)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_about_github),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = stringResource(R.string.settings_about_license),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
    }
}
