package com.yue.browser.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            domain = "Pengaturan Kunci Website",
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
            }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kunci Website", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isPinSet) showAddDialog = true
                        else showPinSetupDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah domain")
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
                    text = "KEAMANAN",
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
                            "PIN Kunci Website",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            if (isPinSet) "PIN aktif — ketuk untuk mengubah" else "PIN belum diatur",
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
                            if (isPinSet) "Ubah" else "Atur PIN",
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

            // Locked Domains Section
            item {
                Text(
                    text = "WEBSITE TERKUNCI",
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }

            if (lockedDomains.isEmpty()) {
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
                            "Belum ada website yang dikunci",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            "Ketuk + untuk menambahkan",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                items(lockedDomains, key = { it }) { domain ->
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
                                contentDescription = "Hapus",
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

    // Dialog: Tambah domain
    if (showAddDialog) {
        AddDomainDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { domain ->
                viewModel.addLockedDomain(domain)
                showAddDialog = false
            }
        )
    }

    // Dialog: Setup PIN pertama kali
    if (showPinSetupDialog) {
        PinSetupDialog(
            title = "Buat PIN Kunci Website",
            onDismiss = { showPinSetupDialog = false },
            onConfirm = { pin ->
                viewModel.setupWebLockPin(pin)
                showPinSetupDialog = false
                showAddDialog = true
            }
        )
    }

    // Dialog: Ubah PIN (perlu verifikasi PIN lama)
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

    // Konfirmasi hapus domain
    domainToDelete?.let { domain ->
        AlertDialog(
            onDismissRequest = { domainToDelete = null },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Hapus kunci?", fontWeight = FontWeight.SemiBold) },
            text = { Text("\"$domain\" tidak akan terkunci lagi.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeLockedDomain(domain)
                    domainToDelete = null
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { domainToDelete = null }) { Text("Batal") }
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
        title = { Text("Tambah Domain", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Masukkan domain yang ingin dikunci:", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("contoh: youtube.com", fontSize = 13.sp) },
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
                Text("Tambah")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
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
    var step by remember { mutableIntStateOf(1) } // 1=input, 2=confirm
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    if (step == 1) "Masukkan PIN baru (4–6 digit):" else "Konfirmasi PIN:",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = if (step == 1) pin1 else pin2,
                    onValueChange = { v ->
                        val digits = v.filter { it.isDigit() }.take(6)
                        if (step == 1) pin1 = digits else pin2 = digits
                        error = ""
                    },
                    placeholder = { Text("••••••", fontSize = 18.sp) },
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
                    if (step == 1) {
                        if (pin1.length < 4) {
                            error = "PIN minimal 4 digit"
                        } else {
                            step = 2
                        }
                    } else {
                        if (pin1 == pin2) {
                            onConfirm(pin1)
                        } else {
                            error = "PIN tidak cocok"
                            pin2 = ""
                        }
                    }
                },
                enabled = if (step == 1) pin1.length >= 4 else pin2.length >= 4
            ) {
                Text(if (step == 1) "Lanjut" else "Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (step == 2) { step = 1; pin2 = "" } else onDismiss()
            }) { Text(if (step == 2) "Kembali" else "Batal") }
        }
    )
}

@Composable
private fun PinChangeDialog(
    onDismiss: () -> Unit,
    onVerifyOld: (String) -> Boolean,
    onConfirmNew: (String) -> Unit
) {
    var step by remember { mutableIntStateOf(0) } // 0=verify old, 1=setup new
    var oldPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    if (step == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Verifikasi PIN Lama", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("Masukkan PIN saat ini:", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter { c -> c.isDigit() }.take(6); error = "" },
                        placeholder = { Text("••••••", fontSize = 18.sp) },
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
                        if (onVerifyOld(oldPin)) step = 1 else { error = "PIN salah"; oldPin = "" }
                    },
                    enabled = oldPin.length >= 4
                ) { Text("Verifikasi") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
        )
    } else {
        PinSetupDialog(
            title = "Buat PIN Baru",
            onDismiss = onDismiss,
            onConfirm = onConfirmNew
        )
    }
}
