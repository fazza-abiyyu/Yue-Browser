package com.yue.browser.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.presentation.BrowserViewModel
import java.util.zip.ZipInputStream

data class AddonItem(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String
)

private fun parseManifestFromUri(context: android.content.Context, uri: android.net.Uri): Map<String, String>? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json" || entry.name.endsWith("/manifest.json")) {
                    val manifestJson = zipStream.bufferedReader().readText()
                    val jsonObj = org.json.JSONObject(manifestJson)

                    val name = jsonObj.optString("name", "").ifBlank { null }
                    val version = jsonObj.optString("version", "").ifBlank { null }
                    val description = jsonObj.optString("description", "").ifBlank { null }

                    val author = when {
                        jsonObj.has("author") -> {
                            val authorVal = jsonObj.get("author")
                            if (authorVal is org.json.JSONObject) {
                                authorVal.optString("name", "").ifBlank { null }
                            } else {
                                authorVal.toString().ifBlank { null }
                            }
                        }
                        jsonObj.has("developer") -> {
                            val dev = jsonObj.optJSONObject("developer")
                            dev?.optString("name", "")?.ifBlank { null }
                        }
                        else -> null
                    }

                    val id = run {
                        val bss = jsonObj.optJSONObject("browser_specific_settings")
                            ?: jsonObj.optJSONObject("applications")
                        bss?.optJSONObject("gecko")?.optString("id", "")?.ifBlank { null }
                    }

                    zipStream.closeEntry()
                    return@use buildMap {
                        name?.let { put("name", it) }
                        version?.let { put("version", it) }
                        author?.let { put("author", it) }
                        description?.let { put("description", it) }
                        id?.let { put("id", it) }
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("AddonsScreen", "Failed to parse manifest from URI: ${e.message}", e)
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    onNavigateToUrl: (String) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val metadata = parseManifestFromUri(context, selectedUri)

                if (metadata == null) {
                    android.widget.Toast.makeText(context, "Gagal membaca manifest.json dari file ekstensi", android.widget.Toast.LENGTH_LONG).show()
                    return@let
                }

                val extensionName = metadata["name"] ?: "Unknown Extension"
                val extensionVersion = metadata["version"] ?: "1.0.0"
                val extensionAuthor = metadata["author"] ?: "Unknown"
                val extensionDescription = metadata["description"] ?: ""

                val extensionId = metadata["id"]
                    ?: extensionName.lowercase(java.util.Locale.ROOT)
                        .replace(Regex("[^a-z0-9]"), "_")
                        .take(64)

                viewModel.saveAddonMetadata(extensionId, extensionName, extensionVersion, extensionAuthor, extensionDescription)

                val extensionDir = java.io.File(context.filesDir, "extensions").apply { mkdirs() }
                val destFile = java.io.File(extensionDir, "$extensionId.xpi")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val runtime = com.yue.browser.data.engine.GeckoViewEngine.getRuntime(context)
                val enabledAddons = settings.enabledAddons.toMutableSet()

                com.yue.browser.data.engine.GeckoExtensionManager.installExtension(
                    context,
                    runtime,
                    destFile,
                    enabledAddons,
                    extensionId
                ) { success, resultId ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (success) {
                            viewModel.toggleAddon(extensionId, true)
                            android.widget.Toast.makeText(context, "$extensionName berhasil dipasang!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Gagal mengaktifkan $extensionName: $resultId", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Gagal memproses file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val addonsList = settings.addonsMetadata.map { (addonId, meta) ->
        AddonItem(
            id = addonId,
            name = meta["name"] ?: addonId,
            version = meta["version"] ?: "1.0.0",
            author = meta["author"] ?: "Unknown",
            description = meta["description"] ?: ""
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add-ons", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
            // Add-ons terpasang
            item { SectionLabel("Terpasang") }
            val activeAddons = addonsList.filter { settings.enabledAddons.contains(it.id) }

            if (activeAddons.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada add-on terpasang",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(activeAddons) { addon ->
                    AddonItemRow(
                        addon = addon,
                        isEnabled = settings.enabledAddons.contains(addon.id),
                        onToggle = { checked -> viewModel.toggleAddon(addon.id, checked) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // Instalasi Manual
            item { SectionLabel("Instalasi") }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pasang dari file .xpi", fontSize = 13.sp)
                    }
                }
                Text(
                    text = "Unduh ekstensi Firefox (.xpi) lalu pasang secara manual.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Dapatkan add-ons baru
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigateToUrl("https://addons.mozilla.org") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cari di Mozilla Add-ons Store", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun AddonItemRow(
    addon: AddonItem,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .padding(6.dp)
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = addon.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "v${addon.version} oleh ${addon.author}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (addon.description.isNotBlank()) {
                Text(
                    text = addon.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = isEnabled, onCheckedChange = onToggle)
    }
}
