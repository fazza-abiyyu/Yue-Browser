package com.yue.browser.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.R
import com.yue.browser.presentation.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playback_settings_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // BACKGROUND PLAY SECTION
            SectionHeader(text = stringResource(R.string.playback_section_background_play))
            
            SettingsToggleItem(
                title = stringResource(R.string.playback_normal_title),
                subtitle = stringResource(R.string.playback_normal_subtitle),
                checked = settings.isBackgroundPlayEnabledNormal,
                onCheckedChange = { viewModel.toggleBackgroundPlayNormal(it) }
            )
            
            SettingsToggleItem(
                title = stringResource(R.string.playback_private_title),
                subtitle = stringResource(R.string.playback_private_subtitle),
                checked = settings.isBackgroundPlayEnabledPrivate,
                onCheckedChange = { viewModel.toggleBackgroundPlayPrivate(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // HOLD TO SPEEDUP SECTION
            SectionHeader(text = stringResource(R.string.playback_section_gesture_speedup))

            SettingsToggleItem(
                title = stringResource(R.string.playback_hold_speedup_title),
                subtitle = stringResource(R.string.playback_hold_speedup_subtitle),
                checked = settings.isVideoSpeedupEnabled,
                onCheckedChange = { viewModel.toggleVideoSpeedup(it) }
            )

            if (settings.isVideoSpeedupEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.playback_multiplier),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${settings.videoSpeedupRate}x",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Chips
                    Text(
                        text = stringResource(R.string.playback_quick_presets),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // A horizontal row of FilterChips for presets (limited to 3.0x to avoid vertical line rendering on standard screens)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(0.25f, 0.5f, 1.0f, 2.0f, 3.0f)
                        presets.forEach { rate ->
                            val isSelected = settings.videoSpeedupRate == rate
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setVideoSpeedupRate(rate) },
                                label = { Text("${rate}x", fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.playback_custom_speed),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Slider & Manual Text Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Slider(
                            value = settings.videoSpeedupRate.coerceIn(0.25f, 16.0f),
                            onValueChange = { 
                                val rounded = Math.round(it * 4f) / 4f
                                viewModel.setVideoSpeedupRate(rounded.coerceIn(0.25f, 16f))
                            },
                            valueRange = 0.25f..16f,
                            steps = 62,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        var textValue by remember(settings.videoSpeedupRate) {
                            mutableStateOf(settings.videoSpeedupRate.toString())
                        }
                        
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { newValue ->
                                // Allow digits and dot
                                val clean = newValue.filter { it.isDigit() || it == '.' }
                                textValue = clean
                                val parsed = clean.toFloatOrNull()
                                if (parsed != null && parsed > 0f) {
                                    viewModel.setVideoSpeedupRate(parsed.coerceIn(0.01f, 100f))
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier.width(76.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.playback_clamp_warning),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
