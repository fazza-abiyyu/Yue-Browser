package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

@Composable
fun WebLockOverlay(
    domain: String,
    onUnlocked: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    hasBiometric: Boolean = false,
    onBiometricRequest: (() -> Unit)? = null,
    isDark: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val bgColor = if (isDark) Color.Black else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF1A1A1A)
    val subTextColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
    val accentColor = Color(0xFFFF69B4)
    val pinEmptyBg = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8E8)
    val pinEmptyBorder = if (isDark) Color(0xFF444455) else Color(0xFFCCCCCC)
    val numpadBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF0F0F0)
    val errorColor = Color(0xFFFF4444)

    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            repeat(4) {
                shakeOffset.animateTo(if (it % 2 == 0) 16f else -16f, animationSpec = tween(60))
            }
            shakeOffset.animateTo(0f, animationSpec = tween(60))
            delay(1200)
            isError = false
            errorMsg = ""
        }
    }

    val lockScale by rememberInfiniteTransition(label = "lock").animateFloat(
        initialValue = 1f, targetValue = 1.08f, label = "lockPulse",
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.27f), accentColor.copy(alpha = 0f))
                        )
                    )
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer(scaleX = lockScale, scaleY = lockScale)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.weblock_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = domain,
                fontSize = 13.sp,
                color = subTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shakeOffset.value.dp)
            ) {
                repeat(6) { index ->
                    val filled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) accentColor
                                else pinEmptyBg
                            )
                            .border(
                                1.dp,
                                if (isError) errorColor
                                else if (filled) accentColor
                                else pinEmptyBorder,
                                CircleShape
                            )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (isError) errorMsg else " ",
                fontSize = 13.sp,
                color = errorColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            val keys = listOf("1","2","3","4","5","6","7","8","9","⌫","0","👆")
            val grid = keys.chunked(3)
            grid.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    row.forEach { key ->
                        val pinWrongStr = context.getString(R.string.weblock_pin_wrong)
                        NumpadKey(
                            label = key,
                            isBiometric = key == "👆" && hasBiometric,
                            isBackspace = key == "⌫",
                            enabled = when {
                                key == "👆" -> hasBiometric
                                key == "⌫" -> pin.isNotEmpty()
                                else -> pin.length < 6
                            },
                            onClick = {
                                when (key) {
                                    "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    "👆" -> onBiometricRequest?.invoke()
                                    else -> {
                                        if (pin.length < 6) {
                                            pin += key
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (pin.length == 6) {
                                                if (onVerifyPin(pin)) {
                                                    onUnlocked()
                                                } else {
                                                    isError = true
                                                    errorMsg = pinWrongStr
                                                    pin = ""
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            isDark = isDark,
                            accentColor = accentColor,
                            numpadBg = numpadBg,
                            textColor = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumpadKey(
    label: String,
    isBiometric: Boolean,
    isBackspace: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    accentColor: Color,
    numpadBg: Color,
    textColor: Color
) {
    val alpha = if (enabled) 1f else 0.3f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (isBackspace || isBiometric) Color.Transparent
                else numpadBg
            )
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            )
    ) {
        when {
            isBiometric -> Icon(
                Icons.Default.Fingerprint,
                contentDescription = stringResource(R.string.weblock_biometric),
                tint = accentColor.copy(alpha = alpha),
                modifier = Modifier.size(28.dp)
            )
            isBackspace -> Icon(
                Icons.Default.Backspace,
                contentDescription = stringResource(R.string.weblock_backspace),
                tint = textColor.copy(alpha = alpha),
                modifier = Modifier.size(22.dp)
            )
            else -> Text(
                text = label,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(alpha = alpha)
            )
        }
    }
}

fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFailed: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onFailed()
        }
        override fun onAuthenticationFailed() {
            onFailed()
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.weblock_biometric_title))
        .setSubtitle(activity.getString(R.string.weblock_biometric_subtitle))
        .setNegativeButtonText(activity.getString(R.string.weblock_biometric_negative))
        .build()
    prompt.authenticate(info)
}

fun isBiometricAvailable(context: android.content.Context): Boolean {
    val bm = BiometricManager.from(context)
    return bm.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS
}
