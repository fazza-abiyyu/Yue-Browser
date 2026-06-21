package com.yue.browser.presentation.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.R
import com.yue.browser.domain.model.PasswordEntry
import com.yue.browser.presentation.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val passwords by viewModel.passwords.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var authForReveal by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = viewModel.exportPasswords(passwords)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, context.getString(com.yue.browser.R.string.password_exported), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(com.yue.browser.R.string.password_export_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                val result = viewModel.importPasswords(json)
                if (result.success) {
                    Toast.makeText(context, context.getString(com.yue.browser.R.string.password_imported), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(com.yue.browser.R.string.password_import_failed, result.message), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(com.yue.browser.R.string.password_import_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val exportCsvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csv = viewModel.exportPasswordsCsv(passwords)
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                Toast.makeText(context, context.getString(com.yue.browser.R.string.password_exported_csv), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(com.yue.browser.R.string.password_export_csv_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importCsvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val csv = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                val result = viewModel.importPasswordsCsv(csv)
                if (result.success) {
                    Toast.makeText(context, context.getString(com.yue.browser.R.string.password_imported), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(com.yue.browser.R.string.password_import_csv_failed, result.message), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(com.yue.browser.R.string.password_import_csv_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredPasswords = remember(passwords, searchQuery) {
        if (searchQuery.isBlank()) passwords
        else passwords.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.url.contains(searchQuery, ignoreCase = true) ||
            it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    var showMenuDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.password_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.password_back))
                    }
                },
                actions = {
                    if (passwords.isNotEmpty()) {
                        var showSearch by remember { mutableStateOf(false) }
                        if (showSearch) {
                            IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.password_close_search))
                            }
                        } else {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.password_search))
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenuDropdown = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export JSON") },
                                onClick = { showMenuDropdown = false; exportLauncher.launch("yue_passwords.json") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export CSV") },
                                onClick = { showMenuDropdown = false; exportCsvLauncher.launch("yue_passwords.csv") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import JSON") },
                                onClick = { showMenuDropdown = false; importLauncher.launch(arrayOf("application/json")) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import CSV") },
                                onClick = { showMenuDropdown = false; importCsvLauncher.launch(arrayOf("text/*")) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.password_add_password), tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (passwords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.password_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.password_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPasswords, key = { it.id }) { entry ->
                        PasswordCard(
                            entry = entry,
                            authForReveal = authForReveal,
                            context = context,
                            onClick = { editingEntry = entry },
                            onDelete = { viewModel.deletePassword(entry.id) },
                            onAuthSuccess = { authForReveal = true }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || editingEntry != null) {
        PasswordEditDialog(
            entry = editingEntry,
            onDismiss = {
                showAddDialog = false
                editingEntry = null
            },
            onSave = { entry ->
                if (editingEntry != null) {
                    viewModel.updatePassword(entry)
                } else {
                    viewModel.addPassword(entry)
                }
                showAddDialog = false
                editingEntry = null
            }
        )
    }
}

@Composable
private fun PasswordCard(
    entry: PasswordEntry,
    authForReveal: Boolean,
    context: android.content.Context,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name.ifBlank { entry.url },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (entry.username.isNotBlank()) {
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (showPassword) entry.password else "•".repeat(entry.password.length.coerceIn(6, 16)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            IconButton(onClick = {
                if (showPassword) {
                    showPassword = false
                } else if (authForReveal) {
                    showPassword = true
                } else {
                    val fragActivity = context as? androidx.fragment.app.FragmentActivity
                    if (fragActivity != null) {
                        showBiometricPrompt(
                            activity = fragActivity,
                            onSuccess = {
                                onAuthSuccess()
                                showPassword = true
                            },
                            onFailed = {},
                            title = "Password Manager",
                            subtitle = "Authenticate to reveal password"
                        )
                    } else {
                        showPassword = true
                    }
                }
            }) {
                Icon(
                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPassword) stringResource(R.string.password_hide_password) else stringResource(R.string.password_show_password),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.password_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.password_delete_title)) },
            text = { Text(stringResource(R.string.password_delete_message, entry.name.ifBlank { entry.url })) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.password_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PasswordEditDialog(
    entry: PasswordEntry?,
    onDismiss: () -> Unit,
    onSave: (PasswordEntry) -> Unit
) {
    val isEditing = entry != null
    var name by remember { mutableStateOf(entry?.name ?: "") }
    var url by remember { mutableStateOf(entry?.url ?: "") }
    var username by remember { mutableStateOf(entry?.username ?: "") }
    var password by remember { mutableStateOf(entry?.password ?: "") }
    var note by remember { mutableStateOf(entry?.note ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var showNoteField by remember { mutableStateOf(entry?.note?.isNotEmpty() == true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) stringResource(R.string.password_edit_title) else stringResource(R.string.password_add_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.password_name)) },
                    placeholder = { Text(stringResource(R.string.password_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.password_url)) },
                    placeholder = { Text(stringResource(R.string.password_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.password_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) stringResource(R.string.password_hide) else stringResource(R.string.password_show)
                            )
                        }
                    }
                )
                if (!showNoteField) {
                    TextButton(onClick = { showNoteField = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.password_add_note), fontSize = 13.sp)
                    }
                } else {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.password_note)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank() && url.isBlank() && username.isBlank() && password.isBlank()) return@TextButton
                    onSave(
                        PasswordEntry(
                            id = entry?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { url },
                            url = url,
                            username = username,
                            password = password,
                            note = note,
                            createdAt = entry?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = password.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
