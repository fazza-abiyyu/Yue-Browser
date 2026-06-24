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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.res.stringResource
import com.yue.browser.presentation.BrowserViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedWebsitesScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val lockedDomains = settings.lockedDomains.sorted()
    val isPinSet = settings.webLockPinHash.isNotBlank()

    var isVerified by remember { mutableStateOf(!isPinSet) }

    if (!isVerified) {
        val context = LocalContext.current
        val hasBio = isBiometricAvailable(context)
        WebLockOverlay(
            domain = stringResource(R.string.locked_websites_settings_redirect),
            onUnlocked = { isVerified = true },
            onVerifyPin = { pin -> viewModel.verifyWebLockPin(pin) },
            hasBiometric = hasBio,
            onBiometricRequest = {
                val fragActivity = context as? FragmentActivity
                if (fragActivity != null) {
                    showBiometricPrompt(
                        activity = fragActivity,
                        onSuccess = { isVerified = true },
                        onFailed = {}
                    )
                }
            },
            isDark = settings.isDarkModeSimulated,
            maxAttempts = settings.webLockMaxAttempts,
            lockDurationMinutes = settings.webLockLockDurationMinutes,
            attemptsEnabled = settings.webLockAttemptsEnabled
        )
        BackHandler {
            onBack()
        }
        return
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var domainToDelete by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val filteredDomains = remember(lockedDomains, searchQuery) {
        if (searchQuery.isBlank()) lockedDomains
        else lockedDomains.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search domains...", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f),
                                focusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            trailingIcon = {
                                IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            }
                        )
                    } else {
                        Text(stringResource(R.string.locked_websites_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (lockedDomains.isNotEmpty()) {
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                            Icon(
                                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (isPinSet) showAddDialog = true
                        else showPinSetupDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.locked_websites_add_domain))
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
            // PIN Section
            item {
                Text(
                    text = stringResource(R.string.locked_websites_security),
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPinSet)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPinSet) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isPinSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.locked_websites_pin_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            if (isPinSet) stringResource(R.string.locked_websites_pin_active) else stringResource(R.string.locked_websites_pin_not_set),
                            fontSize = 12.sp,
                            color = if (isPinSet)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            if (isPinSet) showChangePinDialog = true
                            else showPinSetupDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isPinSet)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            if (isPinSet) stringResource(R.string.change) else stringResource(R.string.set_pin),
                            fontSize = 13.sp,
                            color = if (isPinSet)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            // Auto-Lock Timeout Section
            item {
                val currentTimeout = settings.webLockAutoLockTimeout
                var expanded by remember { mutableStateOf(false) }
                val timeoutOptions = listOf(
                    "0" to stringResource(R.string.settings_auto_lock_instantly),
                    "1" to stringResource(R.string.settings_auto_lock_1min),
                    "5" to stringResource(R.string.settings_auto_lock_5min),
                    "15" to stringResource(R.string.settings_auto_lock_15min),
                    "30" to stringResource(R.string.settings_auto_lock_30min)
                )
                val displayText = timeoutOptions.find { it.first == currentTimeout }?.second
                    ?: stringResource(R.string.settings_auto_lock_instantly)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Box(modifier = Modifier.width(24.dp)) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_auto_lock),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.settings_auto_lock_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Text(
                            text = displayText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            timeoutOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (currentTimeout == value) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        viewModel.setWebLockAutoLockTimeout(value)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            // Failed Attempt Lockout Section
            item {
                Text(
                    text = stringResource(R.string.locked_websites_attempt_lockout),
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.locked_websites_attempt_lockout_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.locked_websites_attempt_lockout_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = settings.webLockAttemptsEnabled,
                        onCheckedChange = { viewModel.setWebLockAttemptsEnabled(it) }
                    )
                }
            }
            if (settings.webLockAttemptsEnabled) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.locked_websites_max_attempts),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        var expandedAttempts by remember { mutableStateOf(false) }
                        val attemptOptions = listOf(3, 5, 10)
                        Box {
                            FilledTonalButton(
                                onClick = { expandedAttempts = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("${settings.webLockMaxAttempts}", fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = expandedAttempts,
                                onDismissRequest = { expandedAttempts = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                attemptOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text("$opt", fontWeight = if (settings.webLockMaxAttempts == opt) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { viewModel.setWebLockMaxAttempts(opt); expandedAttempts = false }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.locked_websites_lock_duration),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        var expandedDuration by remember { mutableStateOf(false) }
                        val durationOptions = listOf(
                            1 to "1 min",
                            5 to "5 min",
                            15 to "15 min",
                            30 to "30 min",
                            60 to "60 min"
                        )
                        Box {
                            FilledTonalButton(
                                onClick = { expandedDuration = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("${settings.webLockLockDurationMinutes} min", fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = expandedDuration,
                                onDismissRequest = { expandedDuration = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                durationOptions.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontWeight = if (settings.webLockLockDurationMinutes == value) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { viewModel.setWebLockLockDurationMinutes(value); expandedDuration = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            // Locked Domains Section
            item {
                Text(
                    text = stringResource(R.string.locked_websites_section),
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }

            if (filteredDomains.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "No matching domains"
                            else stringResource(R.string.locked_websites_empty_title),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            if (searchQuery.isNotBlank()) "Try a different search"
                            else stringResource(R.string.locked_websites_empty_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                items(filteredDomains, key = { it }) { domain ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            domain,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { domainToDelete = domain }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddDomainDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { domain ->
                viewModel.addLockedDomain(domain)
                showAddDialog = false
            }
        )
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            title = stringResource(R.string.locked_websites_setup_pin_title),
            onDismiss = { showPinSetupDialog = false },
            onConfirm = { pin ->
                viewModel.setupWebLockPin(pin)
                showPinSetupDialog = false
                showAddDialog = true
            }
        )
    }

    if (showChangePinDialog) {
        PinChangeDialog(
            onDismiss = { showChangePinDialog = false },
            onVerifyOld = { viewModel.verifyWebLockPin(it) },
            onConfirmNew = { newPin ->
                viewModel.setupWebLockPin(newPin)
                showChangePinDialog = false
            }
        )
    }

    domainToDelete?.let { domain ->
        AlertDialog(
            onDismissRequest = { domainToDelete = null },
            shape = RoundedCornerShape(16.dp),
            title = { Text(stringResource(R.string.locked_websites_delete_title), fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.locked_websites_delete_message, domain)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeLockedDomain(domain)
                    domainToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { domainToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun AddDomainDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text(stringResource(R.string.locked_websites_add_dialog_title), fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(stringResource(R.string.locked_websites_add_dialog_message), fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.locked_websites_domain_placeholder), fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.trim().isNotBlank()) onConfirm(input.trim()) },
                enabled = input.trim().isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun PinSetupDialog(
    title: String = "Buat PIN",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = {
            if (step == 2) { step = 1; pin2 = "" } else onDismiss()
        },
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = {
                    if (step == 2) { step = 1; pin2 = "" } else onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (step == 1) ctx.getString(R.string.locked_websites_setup_pin_step1) else ctx.getString(R.string.locked_websites_setup_pin_step2),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                PinNumpad(
                    pin = if (step == 1) pin1 else pin2,
                    onPinChange = { v ->
                        val digits = v.filter { it.isDigit() }.take(6)
                        if (step == 1) pin1 = digits else pin2 = digits
                        error = ""
                        if (step == 1 && pin1.length >= 4) {
                            step = 2
                        } else if (step == 2 && pin2.length >= 4) {
                            if (pin1 == pin2) {
                                onConfirm(pin1)
                            } else {
                                error = ctx.getString(R.string.locked_websites_pin_mismatch)
                                pin2 = ""
                            }
                        }
                    },
                    isDark = isSystemDark,
                    error = error
                )
            }
        },
        confirmButton = {},
        dismissButton = null
    )
}

@Composable
private fun PinChangeDialog(
    onDismiss: () -> Unit,
    onVerifyOld: (String) -> Boolean,
    onConfirmNew: (String) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var oldPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    if (step == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(16.dp),
            title = { Text(stringResource(R.string.locked_websites_verify_pin_title), fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(stringResource(R.string.locked_websites_verify_pin_message), fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter { c -> c.isDigit() }.take(6); error = "" },
                        placeholder = { Text(stringResource(R.string.locked_websites_pin_placeholder), fontSize = 18.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        )
                    )
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onVerifyOld(oldPin)) step = 1 else { error = ctx.getString(R.string.locked_websites_pin_wrong); oldPin = "" }
                    },
                    enabled = oldPin.length >= 4
                ) { Text(stringResource(R.string.verify)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
    } else {
        PinSetupDialog(
            title = stringResource(R.string.locked_websites_change_pin_title),
            onDismiss = onDismiss,
            onConfirm = onConfirmNew
        )
    }
}
