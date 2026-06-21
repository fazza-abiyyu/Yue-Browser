package com.yue.browser.presentation.ui.downloads

import com.yue.browser.presentation.ui.*

import com.yue.browser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.ChunkStatus
import com.yue.browser.domain.model.DownloadItem
import com.yue.browser.domain.model.DownloadStatus

internal fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", size / 1024f)
        size < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", size / (1024f * 1024f))
        else -> String.format(java.util.Locale.US, "%.1f GB", size / (1024f * 1024f * 1024f))
    }
}

@Composable
internal fun DownloadItemRow(
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
internal fun ChunkProgressBar(download: DownloadItem) {
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
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun getFileIcon(fileName: String, status: DownloadStatus): androidx.compose.ui.graphics.vector.ImageVector {
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
