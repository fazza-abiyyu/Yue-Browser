package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.yue.browser.domain.model.SpeedDialConfig
import com.yue.browser.presentation.ui.ScannerActivity

@Composable
fun NewTabHomeScreen(
    speedDials: List<SpeedDialConfig>,
    searchEngineUrl: String,
    onSearchClick: () -> Unit,
    onSpeedDialClick: (String) -> Unit,
    onScanResult: ((String, Boolean) -> Unit)? = null,
    isIncognito: Boolean = false,
    modifier: Modifier = Modifier
) {
    val brandingColor = if (isIncognito) Color(0xFFFF002C) else MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val scanResult = data?.getStringExtra("scan_result") ?: ""
            val isUrl = data?.getBooleanExtra("scan_is_url", false) ?: false
            if (scanResult.isNotEmpty()) {
                onScanResult?.invoke(scanResult, isUrl)
                    ?: run {
                        // Default behavior: if URL, load it. Otherwise, search it.
                        val url = if (isUrl) scanResult else "${searchEngineUrl}${java.net.URLEncoder.encode(scanResult, "UTF-8")}"
                        onSpeedDialClick(url)
                    }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isWide = configuration.screenWidthDp > 600
        val columnsCount = if (isWide) 6 else 4
        
        val titleSize = if (isWide) 56.sp else 40.sp
        val subtitleSize = if (isWide) 14.sp else 12.sp
        val searchHintSize = if (isWide) 15.sp else 14.sp
        val dialIconSize = if (isWide) 60.dp else 52.dp
        val dialLetterSize = if (isWide) 24.sp else 22.sp
        val dialNameSize = if (isWide) 12.sp else 11.sp

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // Branding minimal
            Text(
                text = "Yue",
                fontSize = titleSize,
                fontWeight = FontWeight.Light,
                color = brandingColor,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Atmospheric Clarity",
                fontSize = subtitleSize,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Search bar minimal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp)
            ) {
                com.yue.browser.presentation.ui.SearchEngineIcon(
                    url = searchEngineUrl,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.newtab_search_hint),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                    fontSize = searchHintSize,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSearchClick() }
                )
                // Scanner icon on the right (pink/red themed)
                IconButton(
                    onClick = {
                        scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR code",
                        tint = brandingColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // Speed dial grid - tanpa label section
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(speedDials) { dial ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSpeedDialClick(dial.url) }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(dialIconSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val parsedColor = remember(dial.iconBgColorHex) {
                                try {
                                    Color(android.graphics.Color.parseColor("#" + dial.iconBgColorHex))
                                } catch (e: Exception) {
                                    Color.Gray
                                }
                            }
                            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            val textColor = if (isDarkTheme && parsedColor.luminance() < 0.2f) {
                                Color.White
                            } else {
                                parsedColor
                            }
                            Text(
                                text = dial.iconLetter,
                                color = textColor,
                                fontSize = dialLetterSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dial.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = dialNameSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
