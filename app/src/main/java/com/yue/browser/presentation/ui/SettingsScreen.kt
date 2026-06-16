package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

@Composable
private fun GoogleIcon(modifier: Modifier) {
    val blue = Color(0xFF4285F4)
    val red = Color(0xFFEA4335)
    val yellow = Color(0xFFFBBC05)
    val green = Color(0xFF34A853)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
            val dotSize = size.width / 3.8f
            val gap = size.width / 5f
            val top = (size.height - dotSize * 2 - gap) / 2f
            val left = (size.width - dotSize * 2 - gap) / 2f
            drawCircle(blue, dotSize / 2f, Offset(left + dotSize / 2f, top + dotSize / 2f))
            drawCircle(red, dotSize / 2f, Offset(left + dotSize + gap + dotSize / 2f, top + dotSize / 2f))
            drawCircle(yellow, dotSize / 2f, Offset(left + dotSize / 2f, top + dotSize + gap + dotSize / 2f))
            drawCircle(green, dotSize / 2f, Offset(left + dotSize + gap + dotSize / 2f, top + dotSize + gap + dotSize / 2f))
        }
    }
}

@Composable
private fun BingIcon(modifier: Modifier) {
    val bingBlue = Color(0xFF008373)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
            val cx = size.width / 2f; val cy = size.height / 2f; val r = size.width / 2.4f
            drawCircle(bingBlue.copy(alpha = 0.15f), r, Offset(cx, cy))
            val path = Path().apply {
                moveTo(cx - r * 0.35f, cy + r * 0.2f)
                lineTo(cx - r * 0.35f, cy - r * 0.5f)
                lineTo(cx + r * 0.25f, cy - r * 0.15f)
                lineTo(cx - r * 0.05f, cy + r * 0.05f)
                lineTo(cx + r * 0.35f, cy - r * 0.15f)
                lineTo(cx + r * 0.35f, cy + r * 0.4f)
                lineTo(cx - r * 0.35f, cy + r * 0.2f)
                close()
            }
            drawPath(path, bingBlue)
        }
    }
}

@Composable
private fun DuckDuckGoIcon(modifier: Modifier) {
    val ddgOrange = Color(0xFFDE5833)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
            val cx = size.width / 2f; val cy = size.height / 2f; val r = size.width / 2.5f
            drawCircle(ddgOrange.copy(alpha = 0.15f), r, Offset(cx, cy))
            // Duck silhouette: simple circle with beak
            drawCircle(ddgOrange, r * 0.6f, Offset(cx - r * 0.05f, cy - r * 0.05f))
            val eyeR = r * 0.12f
            drawCircle(Color.White, eyeR, Offset(cx + r * 0.15f, cy - r * 0.15f))
            drawCircle(Color.Black, eyeR * 0.5f, Offset(cx + r * 0.15f, cy - r * 0.15f))
            // Beak
            val beak = Path().apply {
                moveTo(cx + r * 0.4f, cy + r * 0.05f)
                lineTo(cx + r * 0.7f, cy + r * 0.1f)
                lineTo(cx + r * 0.4f, cy + r * 0.2f)
                close()
            }
            drawPath(beak, ddgOrange)
        }
    }
}

@Composable
private fun YahooIcon(modifier: Modifier) {
    val yahooPurple = Color(0xFF6001D2)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
            val cx = size.width / 2f; val cy = size.height / 2f; val r = size.width / 2.5f
            drawCircle(yahooPurple.copy(alpha = 0.15f), r, Offset(cx, cy))
            // "Y" shape
            val path = Path().apply {
                moveTo(cx - r * 0.3f, cy - r * 0.35f)
                lineTo(cx, cy - r * 0.05f)
                lineTo(cx + r * 0.3f, cy - r * 0.35f)
                lineTo(cx + r * 0.15f, cy - r * 0.35f)
                lineTo(cx, cy - r * 0.15f)
                lineTo(cx - r * 0.15f, cy - r * 0.35f)
                close()
                moveTo(cx, cy - r * 0.05f)
                lineTo(cx, cy + r * 0.45f)
                lineTo(cx - r * 0.1f, cy + r * 0.45f)
                lineTo(cx - r * 0.1f, cy - r * 0.05f)
                close()
            }
            drawPath(path, yahooPurple)
        }
    }
}

private val defaultSearchEngines = listOf(
    SearchEngine("Google", "https://www.google.com/search?q=", { GoogleIcon(it) }),
    SearchEngine("Bing", "https://www.bing.com/search?q=", { BingIcon(it) }),
    SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=", { DuckDuckGoIcon(it) }),
    SearchEngine("Yahoo", "https://search.yahoo.com/search?p=", { YahooIcon(it) })
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    onAdblockFiltersClick: () -> Unit,
    onLockedWebsitesClick: () -> Unit = {},
    onPasswordManagerClick: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearCookiesSelected by remember { mutableStateOf(true) }
    var clearCacheSelected by remember { mutableStateOf(true) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Umum
            item { SectionHeader(stringResource(R.string.settings_section_general)) }
            item {
                SettingsItem(
                    icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.settings_javascript),
                    subtitle = stringResource(R.string.settings_javascript_subtitle),
                    trailing = {
                        Switch(
                            checked = settings.isJavaScriptEnabled,
                            onCheckedChange = { viewModel.toggleJavaScript(it) }
                        )
                    }
                )
            }
            item { SettingsDivider() }

            item {
                SettingsItem(
                    icon = { Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.settings_page_zoom),
                    subtitle = stringResource(R.string.settings_page_zoom_subtitle),
                    trailing = {
                        Switch(
                            checked = settings.isZoomEnabled,
                            onCheckedChange = { viewModel.toggleZoom(it) }
                        )
                    }
                )
            }
            item { SettingsDivider() }

            item {
                SettingsItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) },
                    title = stringResource(R.string.settings_clear_data),
                    subtitle = stringResource(R.string.settings_clear_data_subtitle),
                    trailing = null,
                    onClick = { showClearDataDialog = true }
                )
            }
            item { SettingsDivider() }

            // Mesin pencari
            item { SectionHeader(stringResource(R.string.settings_section_search_engine)) }
            defaultSearchEngines.forEachIndexed { index, engine ->
                val isActive = settings.searchEngineUrl == engine.url
                item(key = engine.url) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setSearchEngineUrl(engine.url) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        engine.icon(Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = engine.name,
                            fontSize = 15.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        if (isActive) {
                            Text(stringResource(R.string.settings_search_active), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (index < defaultSearchEngines.size - 1) {
                    item { SettingsDivider(indent = true) }
                }
            }

            // Custom search engine
            item { SettingsDivider(indent = true) }
            item(key = "custom_search") {
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
            item { SettingsDivider() }

            // Pemblokir iklan
            item { SectionHeader(stringResource(R.string.settings_section_adblock)) }
            item {
                SettingsItem(
                    icon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.settings_adblock_enable),
                    subtitle = stringResource(R.string.settings_adblock_subtitle),
                    trailing = {
                        Switch(
                            checked = settings.isAdBlockEnabled,
                            onCheckedChange = { viewModel.toggleAdBlock(it) }
                        )
                    }
                )
            }
            item { SettingsDivider() }

            val totalFilters = settings.customAdBlockFilters.size +
                    settings.blockedCssSelectors.values.sumOf { it.size }
            if (settings.isAdBlockEnabled || totalFilters > 0) {
                item {
                    SettingsItem(
                        icon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                        title = stringResource(R.string.settings_custom_filters),
                        subtitle = if (totalFilters > 0) stringResource(R.string.settings_custom_filters_count, totalFilters) else stringResource(R.string.settings_custom_filters_empty),
                        trailing = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { onAdblockFiltersClick() }
                    )
                }
                item { SettingsDivider() }
            }

            // Kunci Website
            item { SectionHeader(stringResource(R.string.settings_section_website_lock)) }
            item {
                val lockedCount = settings.lockedDomains.size
                val pinSet = settings.webLockPinHash.isNotBlank()
                SettingsItem(
                    icon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = stringResource(R.string.settings_website_lock),
                    subtitle = when {
                        !pinSet -> stringResource(R.string.settings_lock_summary_pin_not_set)
                        lockedCount == 0 -> stringResource(R.string.settings_lock_summary_no_websites)
                        else -> stringResource(R.string.settings_lock_summary_active, lockedCount)
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { onLockedWebsitesClick() }
                )
            }
            item { SettingsDivider() }

            // Password Manager
            item {
                SettingsItem(
                    icon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = stringResource(R.string.password_title),
                    subtitle = null,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { onPasswordManagerClick() }
                )
            }
            item { SettingsDivider() }

            // Pemutaran
            item { SectionHeader(stringResource(R.string.settings_section_playback)) }
            item {
                SettingsItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.settings_playback_normal),
                    subtitle = stringResource(R.string.settings_playback_normal_subtitle),
                    trailing = {
                        Switch(
                            checked = settings.isBackgroundPlayEnabledNormal,
                            onCheckedChange = { viewModel.toggleBackgroundPlayNormal(it) }
                        )
                    }
                )
            }
            item { SettingsDivider() }
            item {
                SettingsItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.settings_playback_private),
                    subtitle = stringResource(R.string.settings_playback_private_subtitle),
                    trailing = {
                        Switch(
                            checked = settings.isBackgroundPlayEnabledPrivate,
                            onCheckedChange = { viewModel.toggleBackgroundPlayPrivate(it) }
                        )
                    }
                )
            }
            item { SettingsDivider() }

            // Export/Import
            item { SectionHeader("Export / Import") }
            item {
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        try {
                            val json = viewModel.exportData()
                            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                            android.widget.Toast.makeText(context, "Export successful", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                            val result = viewModel.importData(json)
                            if (result.success) {
                                android.widget.Toast.makeText(context, "Import successful", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("browser_settings.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export", fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import", fontSize = 14.sp)
                    }
                }
            }
            item { SettingsDivider() }
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
