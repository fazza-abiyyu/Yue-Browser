package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import com.yue.browser.presentation.theme.DarkPill
import com.yue.browser.presentation.theme.yueColors
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.yue.browser.domain.model.SpeedDialConfig
import com.yue.browser.presentation.ui.ScannerActivity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTabHomeScreen(
    speedDials: List<SpeedDialConfig>,
    searchEngineUrl: String,
    onSearchClick: () -> Unit,
    onSpeedDialClick: (String) -> Unit,
    onScanResult: ((String, Boolean) -> Unit)? = null,
    isIncognito: Boolean = false,
    // Kustomisasi shortcut (feedback user 2026-11-07): user dials + opsi isi slot kosong
    autoSpeedDialUrls: Set<String> = emptySet(),
    onAddSpeedDial: (name: String, url: String) -> Unit = { _, _ -> },
    onRemoveSpeedDial: (url: String) -> Unit = {},
    // Ketuk ikon engine di pill → pilih engine terdaftar (feedback user 2026-11-07)
    onSelectSearchEngine: (String) -> Unit = {},
    // Grup settingan: tampil/tidak + jumlah maksimal
    showShortcuts: Boolean = true,
    shortcutLimit: Int = 12,
    modifier: Modifier = Modifier
) {
    val brandingColor = if (isIncognito) MaterialTheme.yueColors.privateAccent else MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val scanResult = data?.getStringExtra("scan_result") ?: ""
            val isUrl = data?.getBooleanExtra("scan_is_url", false) ?: false
            if (scanResult.isNotEmpty()) {
                onScanResult?.invoke(scanResult, isUrl)
                    ?: run {
                        // Default behavior: if URL, load it. Otherwise, search it.
                        val url = if (isUrl) scanResult else "${searchEngineUrl}${java.net.URLEncoder.encode(scanResult, "UTF-8")}"
                        onSpeedDialClick(url)
                    }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isWide = configuration.screenWidthDp > 600
        val columnsCount = if (isWide) 6 else 4
        
        val titleSize = if (isWide) 56.sp else 40.sp
        val subtitleSize = if (isWide) 14.sp else 12.sp
        val searchHintSize = if (isWide) 15.sp else 14.sp
        val dialIconSize = if (isWide) 60.dp else 52.dp
        val dialLetterSize = if (isWide) 24.sp else 22.sp
        val dialNameSize = if (isWide) 12.sp else 11.sp

        var dialogDial by remember { mutableStateOf<SpeedDialConfig?>(null) }
        var showAddDialog by remember { mutableStateOf(false) }

        // Tanpa section shortcut → wordmark + search diposisikan tengah biar seimbang (feedback user 2026-11-07)
        val centerAlone = !(showShortcuts && shortcutLimit > 0)

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (centerAlone) Spacer(modifier = Modifier.weight(1f)) else Spacer(modifier = Modifier.height(56.dp))

            // Branding minimal
            Text(
                text = "Yue",
                fontSize = titleSize,
                fontWeight = FontWeight.Light,
                color = brandingColor,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Atmospheric Clarity",
                fontSize = subtitleSize,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Search bar minimal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp)
            ) {
                // Ikon engine — ketuk untuk pilih engine terdaftar (dulu: dismiss overlay)
            var showEngineMenu by remember { mutableStateOf(false) }
            var engineAnchorSize by remember { mutableStateOf(IntSize.Zero) }
            val engineInteraction = remember { MutableInteractionSource() }
            val engineHovered by engineInteraction.collectIsHoveredAsState()
            val engineAccent = if (isIncognito) MaterialTheme.yueColors.privateAccent else MaterialTheme.colorScheme.primary
            val engineMenuBg = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
                DarkPill else MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .onSizeChanged { engineAnchorSize = it }
                    .clip(CircleShape)
                    .background(if (engineHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
                    .hoverable(engineInteraction)
                    .clickable(
                        interactionSource = engineInteraction,
                        indication = rememberRipple(color = MaterialTheme.colorScheme.onSurface)
                    ) { showEngineMenu = true }
                    .padding(6.dp)
            ) {
                com.yue.browser.presentation.ui.SearchEngineIcon(
                    url = searchEngineUrl,
                    modifier = Modifier.size(20.dp),
                    isPrivate = isIncognito
                )
                if (showEngineMenu) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(0, engineAnchorSize.height),
                        onDismissRequest = { showEngineMenu = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = engineMenuBg,
                            tonalElevation = 0.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                com.yue.browser.presentation.ui.defaultSearchEngines.forEach { engine ->
                                    val isActive = engine.url == searchEngineUrl
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = rememberRipple(color = MaterialTheme.colorScheme.onSurface)
                                            ) {
                                                onSelectSearchEngine(engine.url)
                                                showEngineMenu = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        engine.icon(Modifier.size(20.dp), isIncognito)
                                        Spacer(Modifier.width(10.dp))
                                        Text(engine.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                        if (isActive) {
                                            Spacer(Modifier.width(16.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                            tint = engineAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
            Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.newtab_search_hint),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                    fontSize = searchHintSize,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSearchClick() }
                )
                // Scanner icon on the right (pink/red themed)
                IconButton(
                    onClick = {
                        scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR code",
                        tint = brandingColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (centerAlone) {
                // Sedikit di atas tengah — spacer bawah lebih besar (feedback user 2026-11-07)
                Spacer(modifier = Modifier.weight(1.3f))
            } else {
                Spacer(Modifier.height(36.dp))

                // Speed dial grid - tanpa label section (bisa dimatikan dari Settings)
                if (showShortcuts && shortcutLimit > 0) {
                LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(speedDials) { dial ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onSpeedDialClick(dial.url) },
                                onLongClick = { dialogDial = dial }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(dialIconSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val parsedColor = remember(dial.iconBgColorHex) {
                                try {
                                    Color(android.graphics.Color.parseColor("#" + dial.iconBgColorHex))
                                } catch (e: Exception) {
                                    Color.Gray
                                }
                            }
                            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            val textColor = if (isDarkTheme && parsedColor.luminance() < 0.2f) {
                                Color.White
                            } else {
                                parsedColor
                            }
                            Text(
                                text = dial.iconLetter,
                                color = textColor,
                                fontSize = dialLetterSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dial.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = dialNameSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Tile "+" — tambah shortcut manual (maks shortcutLimit)
                if (speedDials.size < shortcutLimit) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAddDialog = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dialIconSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.newtab_add_shortcut),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // Tanpa label — ikon + sudah cukup jelas (feedback user 2026-11-07)
                        }
                    }
                }
            }
            }
            }
        }

        // Dialog shortcut (long-press dial): hapus milik user / pin auto-fill
        dialogDial?.let { dial ->
            AlertDialog(
                onDismissRequest = { dialogDial = null },
                title = { Text(dial.name, fontSize = 17.sp) },
                text = { Text(dial.url, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    if (dial.url in autoSpeedDialUrls) {
                        TextButton(onClick = { onAddSpeedDial(dial.name, dial.url); dialogDial = null }) {
                            Text(stringResource(R.string.newtab_pin_shortcut))
                        }
                    } else {
                        TextButton(onClick = { onRemoveSpeedDial(dial.url); dialogDial = null }) {
                            Text(stringResource(R.string.newtab_remove_shortcut), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogDial = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        // Dialog tambah shortcut manual
        if (showAddDialog) {
            var urlInput by remember { mutableStateOf("") }
            var nameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.newtab_add_shortcut)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text(stringResource(R.string.newtab_hint_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text(stringResource(R.string.newtab_hint_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = urlInput.isNotBlank(),
                        onClick = {
                            val raw = urlInput.trim()
                            val host = try {
                                android.net.Uri.parse(if (raw.startsWith("http")) raw else "https://$raw")
                                    .host?.removePrefix("www.") ?: ""
                            } catch (e: Exception) { "" }
                            val name = nameInput.trim().ifBlank { host.replaceFirstChar { it.uppercase() } }
                            onAddSpeedDial(name, raw)
                            showAddDialog = false
                        }
                    ) { Text(stringResource(R.string.newtab_action_add)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}
