package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
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
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Download,
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                val sortedDownloads = downloads.sortedWith(compareBy<com.yue.browser.domain.model.DownloadItem> {
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (download.status) {
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            DownloadStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            DownloadStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (download.status) {
                        DownloadStatus.DOWNLOADING -> Icons.Default.Downloading
                        DownloadStatus.COMPLETED -> Icons.Default.Folder
                        DownloadStatus.FAILED -> Icons.Default.Refresh
                        DownloadStatus.PAUSED -> Icons.Default.Pause
                        DownloadStatus.PENDING -> Icons.Default.Download
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
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

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when (download.status) {
                        DownloadStatus.DOWNLOADING -> {
                            val activeChunks = download.chunks.count { it.status == ChunkStatus.DOWNLOADING }
                            val suffix = if (activeChunks > 0) " (${stringResource(R.string.download_connections_count, activeChunks)})" else ""
                            stringResource(R.string.download_status_downloading, download.progress.toString(), suffix)
                        }
                        DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused)
                        DownloadStatus.COMPLETED -> stringResource(R.string.download_status_completed)
                        DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
                        DownloadStatus.PENDING -> stringResource(R.string.download_status_pending)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                when (download.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Pause, stringResource(R.string.download_pause), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    DownloadStatus.PAUSED, DownloadStatus.PENDING -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.download_resume), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onOpenFile, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Folder, stringResource(R.string.download_open), tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onRewrite, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.download_retry), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.download_remove), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
        }

        // Progress bar
        if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.PENDING) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${download.progress}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatFileSize(download.downloadedSize) + " / " + formatFileSize(download.totalSize),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = download.progress / 100f,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            if (download.chunks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ChunkProgressBar(download = download)
            }
        } else if (download.status == DownloadStatus.COMPLETED) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.download_size, formatFileSize(download.totalSize)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (download.status == DownloadStatus.FAILED) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onRewrite, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.download_retry_button), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(stringResource(R.string.download_edit_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        },
        text = {
            Column {
                Text(
                    download.fileName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(stringResource(R.string.download_update_link), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    placeholder = { Text(stringResource(R.string.download_link_placeholder), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Text(
                    stringResource(R.string.download_link_hint),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                Text(stringResource(R.string.download_parallel_connections), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.download_connections_count, connectionCount), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = connectionCount.toFloat(),
                        onValueChange = { connectionCount = it.toInt().coerceIn(1, 16) },
                        valueRange = 1f..16f,
                        steps = 14,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    stringResource(R.string.download_connections_hint),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onChangeConnectionCount(connectionCount)
            }) {
                Text(stringResource(R.string.use))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
