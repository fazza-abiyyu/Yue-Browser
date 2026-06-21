package com.yue.browser.presentation.ui.downloads

import com.yue.browser.presentation.ui.*

import com.yue.browser.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.DownloadItem
import com.yue.browser.domain.model.DownloadStatus
import com.yue.browser.presentation.*

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
                    val sortedDownloads = filteredDownloads.sortedWith(compareBy<DownloadItem> {
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
