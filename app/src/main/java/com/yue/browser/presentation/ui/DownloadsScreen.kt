package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.ChunkStatus
import com.yue.browser.domain.model.DownloadItem
import com.yue.browser.domain.model.DownloadStatus
import com.yue.browser.presentation.BrowserViewModel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024f)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024f * 1024f))
        else -> String.format("%.1f GB", size / (1024f * 1024f * 1024f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    context: android.content.Context
) {
    val downloads by viewModel.downloads.collectAsState()
    var showEditDialog by remember { mutableStateOf<DownloadItem?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredDownloads = remember(downloads, searchQuery) {
        if (searchQuery.isBlank()) downloads
        else downloads.filter {
            it.fileName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.download_settings_title))
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
                placeholder = { Text(stringResource(R.string.downloads_search_hint), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear), modifier = Modifier.size(18.dp))
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

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.downloads_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (filteredDownloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_no_match),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    val sortedDownloads = filteredDownloads.sortedWith(compareBy<com.yue.browser.domain.model.DownloadItem> {
                        when (it.status) {
                            DownloadStatus.DOWNLOADING -> 0
                            DownloadStatus.PAUSED -> 1
                            DownloadStatus.PENDING -> 2
                            DownloadStatus.FAILED -> 3
                            DownloadStatus.COMPLETED -> 4
                        }
                    }.thenByDescending { it.lastModified })

                    items(sortedDownloads) { download ->
                    DownloadItemRow(
                        download = download,
                        onPause = { viewModel.pauseDownload(download.id) },
                        onResume = { viewModel.resumeDownload(download.id, context) },
                        onRemove = { viewModel.removeDownload(download.id) },
                        onEdit = { showEditDialog = download },
                        onRewrite = { viewModel.rewriteFile(download.id, context) },
                        onOpenFile = {
                            try {
                                val isContentUri = download.filePath.startsWith("content://")
                                val fileExists = if (isContentUri) {
                                    try {
                                        androidx.documentfile.provider.DocumentFile.fromSingleUri(context, android.net.Uri.parse(download.filePath))?.exists() == true
                                    } catch (e: Exception) {
                                        false
                                    }
                                } else {
                                    java.io.File(download.filePath).exists()
                                }

                                if (fileExists) {
                                    val isApk = download.fileName.endsWith(".apk", ignoreCase = true)
                                    val canInstall = if (isApk && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        context.packageManager.canRequestPackageInstalls()
                                    } else {
                                        true
                                    }

                                    if (!canInstall) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            val settingsIntent = android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                android.net.Uri.parse("package:" + context.packageName)
                                            ).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(settingsIntent)
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.download_grant_install_permission),
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                        val uri = if (isContentUri) {
                                            android.net.Uri.parse(download.filePath)
                                        } else {
                                            androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                context.packageName + ".fileprovider",
                                                java.io.File(download.filePath)
                                            )
                                        }
                                        val mimeType = if (isApk) {
                                            "application/vnd.android.package-archive"
                                        } else {
                                            val ext = download.fileName.substringAfterLast('.', "").lowercase()
                                            val resolverType = if (isContentUri) context.contentResolver.getType(uri) else null
                                            resolverType ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                                        }
                                        intent.setDataAndType(uri, mimeType)
                                        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(R.string.download_cannot_open), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, context.getString(R.string.download_cannot_open), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenFolder = {
                            try {
                                val isContentUri = download.filePath.startsWith("content://")
                                var opened = false

                                if (isContentUri) {
                                    val fileUri = android.net.Uri.parse(download.filePath)
                                    val parentUri = getParentFolderUriOfContentUri(fileUri)
                                    if (parentUri != null) {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(parentUri, "vnd.android.document/directory")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            opened = true
                                        } catch (e: Exception) {
                                            // Fallback to safDir or system downloads
                                        }
                                    }
                                    
                                    if (!opened) {
                                        val settings = viewModel.settings.value
                                        val safDir = settings.downloadDirectory.trim()
                                        if (safDir.startsWith("content://")) {
                                            try {
                                                val treeUri = android.net.Uri.parse(safDir)
                                                val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                                                val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setDataAndType(docUri, "vnd.android.document/directory")
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                                opened = true
                                            } catch (e: Exception) {}
                                        }
                                    }
                                } else {
                                    val file = java.io.File(download.filePath)
                                    val parentFile = file.parentFile
                                    if (parentFile != null && parentFile.exists()) {
                                        val docUri = getDocumentUriForFolder(parentFile)
                                        if (docUri != null) {
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setDataAndType(docUri, "vnd.android.document/directory")
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                                opened = true
                                            } catch (e: Exception) {}
                                        }
                                    }
                                }

                                if (!opened) {
                                    val fallbackIntent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(fallbackIntent)
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, context.getString(R.string.download_cannot_open), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

    showEditDialog?.let { download ->
        EditDownloadDialog(
            download = download,
            onDismiss = { showEditDialog = null },
            onReplaceUrl = { newUrl ->
                viewModel.replaceUrlAndResume(download.id, newUrl, context)
                showEditDialog = null
            },
            onRewriteFile = {
                viewModel.rewriteFile(download.id, context)
                showEditDialog = null
            },
            onChangeConnectionCount = { newCount ->
                viewModel.rebuildChunksAndResume(download.id, newCount, context)
                showEditDialog = null
            }
        )
    }

    if (showSettingsDialog) {
        DownloadSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun DownloadItemRow(
    download: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onRewrite: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (download.status == DownloadStatus.COMPLETED) {
                    Modifier.clickable { onOpenFile() }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (download.status) {
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            DownloadStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            DownloadStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileIcon(download.fileName, download.status),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = when (download.status) {
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                        DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary
                        DownloadStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = download.fileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    when (download.status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Pause, stringResource(R.string.download_pause), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.PAUSED, DownloadStatus.PENDING -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.PlayArrow, stringResource(R.string.download_resume), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(onClick = onOpenFolder, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Folder, stringResource(R.string.download_open), tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.FAILED -> {
                            IconButton(onClick = onRewrite, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Refresh, stringResource(R.string.download_retry), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.FAILED) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.download_edit_title), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.download_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                    }
                }

                if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.PENDING) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${download.progress}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatFileSize(download.downloadedSize) + " / " + formatFileSize(download.totalSize),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = when (download.status) {
                            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    if (download.chunks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        ChunkProgressBar(download = download)
                    }
                } else if (download.status == DownloadStatus.COMPLETED) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.download_size, formatFileSize(download.totalSize)),
                            fontSize = 12.sp,
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = onOpenFile, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.download_open), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (download.status == DownloadStatus.FAILED) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.download_status_failed),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = onRewrite, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.download_retry_button), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChunkProgressBar(download: DownloadItem) {
    val chunks = download.chunks
    if (chunks.isEmpty()) return

    val displayChunks = if (chunks.size <= 16) chunks else chunks.take(16)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(14.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            displayChunks.forEach { chunk ->
                val chunkProgress = if (chunk.endByte > chunk.startByte) {
                    (chunk.downloadedByte.toFloat() / (chunk.endByte - chunk.startByte + 1)).coerceIn(0f, 1f)
                } else 0f
                val color = when (chunk.status) {
                    ChunkStatus.COMPLETED -> Color(0xFF4CAF50)
                    ChunkStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                    ChunkStatus.FAILED -> MaterialTheme.colorScheme.error
                    ChunkStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(chunkProgress.coerceIn(0f, 1f))
                            .align(Alignment.BottomStart)
                            .background(color)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendDot(Color(0xFF4CAF50), stringResource(R.string.download_chunk_completed))
            LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.download_chunk_downloading))
            LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f), stringResource(R.string.download_chunk_pending))
            if (chunks.any { it.status == ChunkStatus.FAILED }) {
                LegendDot(MaterialTheme.colorScheme.error, stringResource(R.string.download_chunk_failed))
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDownloadDialog(
    download: DownloadItem,
    onDismiss: () -> Unit,
    onReplaceUrl: (String) -> Unit,
    onRewriteFile: () -> Unit,
    onChangeConnectionCount: (Int) -> Unit
) {
    var newUrl by remember { mutableStateOf("") }
    var connectionCount by remember { mutableStateOf(download.connectionCount.coerceIn(1, 16)) }
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
            Text(
                text = stringResource(R.string.download_edit_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = download.fileName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = stringResource(R.string.download_update_link),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    placeholder = { Text(stringResource(R.string.download_link_placeholder), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Text(
                    text = stringResource(R.string.download_link_hint),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                Text(
                    text = stringResource(R.string.download_parallel_connections),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.download_connections_count, connectionCount),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = connectionCount.toFloat(),
                        onValueChange = { connectionCount = it.toInt().coerceIn(1, 16) },
                        valueRange = 1f..16f,
                        steps = 14,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
                Text(
                    text = stringResource(R.string.download_connections_hint),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onChangeConnectionCount(connectionCount)
                }
            ) {
                Text(
                    text = stringResource(R.string.use),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
fun DownloadSettingsDialog(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var tempDirectory by remember { mutableStateOf(settings.downloadDirectory) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dialogShape = RoundedCornerShape(16.dp)

    val safLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {}
            tempDirectory = uri.toString()
        }
    }

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
            Text(
                text = stringResource(R.string.download_settings_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Connection thread toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.download_settings_multithread),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.download_settings_multithread_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.isDownloadMultiThread,
                        onCheckedChange = { viewModel.setDownloadMultiThread(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                // Delete behavior toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.download_settings_delete_behavior),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.download_settings_delete_behavior_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.isDeletePhysicalFile,
                        onCheckedChange = { viewModel.setDeletePhysicalFile(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                // Destination Folder field
                Text(
                    text = stringResource(R.string.download_settings_directory),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.download_settings_directory_desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { safLauncher.launch(null) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getDisplayPathFromSafUri(tempDirectory),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (tempDirectory.isNotEmpty()) {
                        IconButton(
                            onClick = { tempDirectory = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.setDownloadDirectory(tempDirectory)
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.save),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

private fun getFileIcon(fileName: String, status: DownloadStatus): androidx.compose.ui.graphics.vector.ImageVector {
    if (status != DownloadStatus.COMPLETED) {
        return when (status) {
            DownloadStatus.DOWNLOADING -> Icons.Outlined.Downloading
            DownloadStatus.FAILED -> Icons.Outlined.Refresh
            DownloadStatus.PAUSED -> Icons.Outlined.Pause
            DownloadStatus.PENDING -> Icons.Outlined.Download
            else -> Icons.Outlined.Download
        }
    }
    
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "apk" -> Icons.Outlined.Android
        "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "rtf", "odt", "ods", "odp" -> Icons.Outlined.Description
        "zip", "rar", "tar", "gz", "7z" -> Icons.Outlined.FolderZip
        "mp3", "wav", "ogg", "m4a", "flac", "aac" -> Icons.Outlined.AudioFile
        "mp4", "mkv", "avi", "mov", "flv", "webm", "3gp" -> Icons.Outlined.VideoFile
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Icons.Outlined.Image
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

private fun getDisplayPathFromSafUri(uriString: String): String {
    if (uriString.isEmpty()) return "Default (Download)"
    try {
        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme == "content") {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size >= 2) {
                return parts[1]
            }
        }
    } catch (e: Exception) {
        try {
            return android.net.Uri.parse(uriString).lastPathSegment ?: uriString
        } catch (_: Exception) {}
    }
    return uriString
}

private fun getDocumentUriForFolder(file: java.io.File): android.net.Uri? {
    try {
        val absolutePath = file.absolutePath
        val storagePrefix = "/storage/"
        if (absolutePath.startsWith(storagePrefix)) {
            val remainingPath = absolutePath.substring(storagePrefix.length)
            val segments = remainingPath.split("/")
            if (segments.isNotEmpty()) {
                val storageId = if (segments[0] == "emulated") {
                    if (segments.size > 1 && segments[1] == "0") {
                        "primary"
                    } else {
                        return null
                    }
                } else {
                    segments[0]
                }
                
                val relativePath = if (storageId == "primary") {
                    remainingPath.substring("emulated/0".length).removePrefix("/")
                } else {
                    remainingPath.substring(storageId.length).removePrefix("/")
                }
                
                val docId = "$storageId:$relativePath"
                return android.provider.DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    docId
                )
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return null
}

private fun getParentFolderUriOfContentUri(uri: android.net.Uri): android.net.Uri? {
    try {
        val pathSegments = uri.pathSegments
        if (pathSegments.size >= 4 && pathSegments[0] == "tree" && pathSegments[2] == "document") {
            val authority = uri.authority ?: return null
            val treeId = pathSegments[1]
            val docId = pathSegments[3]
            
            val lastSlash = docId.lastIndexOf('/')
            val parentDocId = if (lastSlash != -1) {
                docId.substring(0, lastSlash)
            } else {
                treeId
            }
            
            val treeUri = android.provider.DocumentsContract.buildTreeDocumentUri(authority, treeId)
            return android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        }
    } catch (e: Exception) {
        // Fallback
    }
    return null
}

