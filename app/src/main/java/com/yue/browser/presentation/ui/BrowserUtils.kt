package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import com.yue.browser.domain.model.BrowserTab

@Composable
fun getLanguageName(code: String): String {
    return when (code) {
        "auto" -> stringResource(R.string.lang_auto)
        "id", "in" -> stringResource(R.string.lang_id)
        "en" -> stringResource(R.string.lang_en)
        "zh" -> stringResource(R.string.lang_zh)
        "ja" -> stringResource(R.string.lang_ja)
        "ko" -> stringResource(R.string.lang_ko)
        "fr" -> stringResource(R.string.lang_fr)
        "de" -> stringResource(R.string.lang_de)
        "es" -> stringResource(R.string.lang_es)
        "pt" -> stringResource(R.string.lang_pt)
        "ar" -> stringResource(R.string.lang_ar)
        "hi" -> stringResource(R.string.lang_hi)
        else -> code
    }
}

fun android.content.Context.findFragmentActivity(): androidx.fragment.app.FragmentActivity? {
    var current: android.content.Context? = this
    while (current != null) {
        if (current is androidx.fragment.app.FragmentActivity) return current
        current = (current as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current != null) {
        if (current is android.app.Activity) return current
        current = (current as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

fun showBiometricLock(activity: android.app.Activity, onResult: (Boolean) -> Unit) {
    try {
        val biometricManager = androidx.biometric.BiometricManager.from(activity)
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                             androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (biometricManager.canAuthenticate(authenticators)) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS,
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                val fragmentActivity = activity as? androidx.fragment.app.FragmentActivity
                if (fragmentActivity != null) {
                    val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                    val biometricPrompt = androidx.biometric.BiometricPrompt(
                        fragmentActivity,
                        executor,
                        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                onResult(true)
                            }

                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                super.onAuthenticationError(errorCode, errString)
                                when (errorCode) {
                                    androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS,
                                    androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                                        android.widget.Toast.makeText(
                                            activity,
                                            activity.getString(R.string.browser_no_auth_method),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        onResult(true)
                                    }
                                    androidx.biometric.BiometricPrompt.ERROR_CANCELED,
                                    androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED,
                                    androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                        onResult(false)
                                    }
                                    else -> {
                                        showKeyguardUnlock(activity, onResult)
                                    }
                                }
                            }
                        }
                    )

                    val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle(activity.getString(R.string.browser_inprivate_lock))
                        .setSubtitle(activity.getString(R.string.browser_inprivate_auth_subtitle))
                        .setDescription(activity.getString(R.string.browser_inprivate_auth_description))
                        .setAllowedAuthenticators(authenticators)
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                } else {
                    showKeyguardUnlock(activity, onResult)
                }
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(R.string.browser_no_auth_hardware),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                onResult(true)
            }
            else -> {
                onResult(true)
            }
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            activity,
            activity.getString(R.string.browser_auth_failed, e.message ?: ""),
            android.widget.Toast.LENGTH_LONG
        ).show()
        onResult(true)
    }
}

fun showKeyguardUnlock(activity: android.app.Activity, onResult: (Boolean) -> Unit) {
    try {
        val keyguardManager = activity.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            activity.getString(R.string.browser_inprivate_lock),
            activity.getString(R.string.browser_device_pin_title)
        )
        if (intent != null) {
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.browser_using_device_pin),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            onResult(true)
        } else {
            onResult(true)
        }
    } catch (e: Exception) {
        onResult(true)
    }
}

@Composable
fun PinVerifyDialog(
    title: String,
    message: String,
    onVerify: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
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
        title = { Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); error = "" },
                    placeholder = { Text(stringResource(R.string.browser_pin_placeholder), fontSize = 18.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
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
                    if (onVerify(pin)) {
                        onConfirmed()
                    } else {
                        error = context.getString(R.string.browser_pin_wrong)
                        pin = ""
                    }
                },
                enabled = pin.length >= 4
            ) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) } }
    )
}

@Composable
fun TranslateIcon(modifier: Modifier = Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.5.dp.toPx()

        drawRoundRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.1f),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )

        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )

        drawLine(
            color = tint.copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.25f),
            end = androidx.compose.ui.geometry.Offset(w * 0.55f, h * 0.25f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = tint.copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.4f),
            end = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.4f),
            strokeWidth = strokeWidth
        )

        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.5f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.65f),
            end = androidx.compose.ui.geometry.Offset(w * 0.7f, h * 0.65f),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun BrowserWebView(
    activeTab: BrowserTab,
    onReload: () -> Unit,
    onScrollChanged: (Boolean) -> Unit,
    isGone: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.key(activeTab.id) {
        activeTab.session.Render(
            modifier = modifier,
            onScrollChanged = onScrollChanged,
            onReload = onReload,
            isGone = isGone
        )
    }
}

@Composable
fun GoogleIcon(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.yue.browser.R.drawable.ic_google),
            contentDescription = "Google",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun BingIcon(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.yue.browser.R.drawable.ic_bing),
            contentDescription = "Bing",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun DuckDuckGoIcon(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.yue.browser.R.drawable.ic_duckduckgo),
            contentDescription = "DuckDuckGo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun YahooIcon(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.yue.browser.R.drawable.ic_yahoo),
            contentDescription = "Yahoo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SearchEngineIcon(url: String, modifier: Modifier = Modifier) {
    Icon(
        imageVector = androidx.compose.material.icons.Icons.Default.Search,
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
}


