package com.yue.browser.presentation.ui.components

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.SpeedDialConfig

@Composable
fun NewTabHomeScreen(
    speedDials: List<SpeedDialConfig>,
    onSearchClick: () -> Unit,
    onSpeedDialClick: (String) -> Unit,
    isIncognito: Boolean = false,
    modifier: Modifier = Modifier
) {
    val brandingColor = if (isIncognito) Color(0xFFFF002C) else MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        // Branding minimal
        Text(
            text = "Yue",
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            color = brandingColor,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Atmospheric Clarity",
            fontSize = 12.sp,
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
                .clickable { onSearchClick() }
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Cari atau masukkan alamat",
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(36.dp))

        // Speed dial grid - tanpa label section
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
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
                            .size(52.dp)
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
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = dial.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

