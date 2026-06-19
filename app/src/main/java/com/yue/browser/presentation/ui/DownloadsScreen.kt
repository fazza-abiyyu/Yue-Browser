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
    var searchQuery by remember { mutableStateOf("") }

    val filteredDownloads = remember(downloads, searchQuery) {
        if (searchQuery.isBlank()) downloads
        else downloads.filter {
            it.fileName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
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
                                val file = java.io.File(download.filePath)
                                if (file.exists()) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".fileprovider",
                                        file
                                    )
                                    intent.setDataAndType(uri, android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                                        file.extension
                                    ) ?: "*/*")
                                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(intent)
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
}

@Composable
private fun DownloadItemRow(
    download: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onRewrite: () -> Unit,
    onOpenFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                    imageVector = when (download.status) {
                        DownloadStatus.DOWNLOADING -> Icons.Outlined.Downloading
                        DownloadStatus.COMPLETED -> Icons.Outlined.Folder
                        DownloadStatus.FAILED -> Icons.Outlined.Refresh
                        DownloadStatus.PAUSED -> Icons.Outlined.Pause
                        DownloadStatus.PENDING -> Icons.Outlined.Download
                    },
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
                            IconButton(onClick = onOpenFile, modifier = Modifier.size(32.dp)) {
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
