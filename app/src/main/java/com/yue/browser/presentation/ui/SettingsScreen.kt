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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Language
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
import com.yue.browser.domain.model.BrowserSettings
import com.yue.browser.presentation.BrowserViewModel

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

    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearCookiesSelected by remember { mutableStateOf(true) }
    var clearCacheSelected by remember { mutableStateOf(true) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val shouldShow = { text: String ->
        searchQuery.isBlank() || text.contains(searchQuery, ignoreCase = true)
    }

    val entries = buildList {
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
            icon = Icons.Default.Palette,
            title = stringResource(R.string.settings_theme),
            subtitle = when (settings.appThemeMode) {
                "light" -> stringResource(R.string.settings_theme_light)
                "dark" -> stringResource(R.string.settings_theme_dark)
                else -> stringResource(R.string.settings_theme_system)
            },
            onClick = { showThemeDialog = true }
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
            title = "Playback & Video Settings",
            subtitle = "Background play and speedup gesture",
            onClick = { onPlaybackSettingsClick() }
        ))
        add(SettingsEntry.Divider())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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

            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(entries) { entry ->
                    when (entry) {
                        is SettingsEntry.Header -> {
                            if (shouldShow(entry.label)) {
                                SectionHeader(entry.label)
                            }
                        }
                        is SettingsEntry.Toggle -> {
                            if (shouldShow(entry.title) || shouldShow(entry.subtitle)) {
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
                        }
                        is SettingsEntry.Clickable -> {
                            if (shouldShow(entry.title) || (entry.subtitle != null && shouldShow(entry.subtitle))) {
                                SettingsItem(
                                    icon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                                    title = entry.title,
                                    subtitle = entry.subtitle,
                                    trailing = null,
                                    onClick = entry.onClick
                                )
                            }
                        }
                        is SettingsEntry.Divider -> {
                            SettingsDivider(indent = entry.indent)
                        }
                        is SettingsEntry.SearchEngineItem -> {
                            val engine = entry.engine
                            if (shouldShow(engine.name)) {
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

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(stringResource(R.string.settings_theme), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    val options = listOf("system", "light", "dark")
                    options.forEach { option ->
                        val label = when (option) {
                            "light" -> stringResource(R.string.settings_theme_light)
                            "dark" -> stringResource(R.string.settings_theme_dark)
                            else -> stringResource(R.string.settings_theme_system)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppThemeMode(option)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = settings.appThemeMode == option,
                                onClick = {
                                    viewModel.setAppThemeMode(option)
                                    showThemeDialog = false
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
                TextButton(onClick = { showThemeDialog = false }) {
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
