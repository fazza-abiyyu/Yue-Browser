package com.yue.browser.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.BrowserTab

@Composable
fun IncognitoIcon(modifier: Modifier = Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        
        // Draw hat brim
        val brimY = h * 0.4f
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.1f, brimY),
            end = androidx.compose.ui.geometry.Offset(w * 0.9f, brimY),
            strokeWidth = 1.5.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        
        // Draw hat top (fedora-like)
        val hatPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, brimY)
            lineTo(w * 0.3f, h * 0.15f)
            quadraticBezierTo(w * 0.5f, h * 0.22f, w * 0.7f, h * 0.15f)
            lineTo(w * 0.75f, brimY)
            close()
        }
        drawPath(path = hatPath, color = tint)
        
        // Draw glasses
        val leftLensCenter = androidx.compose.ui.geometry.Offset(w * 0.33f, h * 0.65f)
        val rightLensCenter = androidx.compose.ui.geometry.Offset(w * 0.67f, h * 0.65f)
        val lensRadius = w * 0.13f
        
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = leftLensCenter,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
        
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = rightLensCenter,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
        
        // Draw bridge
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(leftLensCenter.x + lensRadius, leftLensCenter.y),
            end = androidx.compose.ui.geometry.Offset(rightLensCenter.x - lensRadius, rightLensCenter.y),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
fun PublicIcon(modifier: Modifier = Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(12.dp)) {
        val r = size.minDimension / 2f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        
        drawCircle(
            color = tint,
            radius = r,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
        )
        
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(center.x - r, center.y),
            end = androidx.compose.ui.geometry.Offset(center.x + r, center.y),
            strokeWidth = 1.2.dp.toPx()
        )
        
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(center.x, center.y - r),
            end = androidx.compose.ui.geometry.Offset(center.x, center.y + r),
            strokeWidth = 1.2.dp.toPx()
        )
    }
}

@Composable
fun MoonIcon(
    modifier: Modifier = Modifier,
    tint: Color,
    isPrivate: Boolean,
    isDark: Boolean
) {
    Canvas(modifier = modifier.size(12.dp)) {

        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        val bgTint = if (isDark)
            Color(0xFF1A1A1C)
        else
            Color.White

        if (isPrivate) {

            // Blood moon glow
            drawCircle(
                color = tint.copy(alpha = 0.18f),
                radius = r * 1.25f,
                center = Offset(cx, cy)
            )

            // Main moon
            drawCircle(
                color = tint,
                radius = r,
                center = Offset(cx, cy)
            )

            // Aggressive crescent
            drawCircle(
                color = bgTint,
                radius = r * 0.92f,
                center = Offset(
                    cx + r * 0.65f,
                    cy
                )
            )

        } else {

            // Normal moon
            drawCircle(
                color = tint,
                radius = r,
                center = Offset(cx, cy)
            )

            drawCircle(
                color = bgTint,
                radius = r * 0.85f,
                center = Offset(
                    cx + r * 0.35f,
                    cy
                )
            )
        }
    }
}

@Composable
fun TabSwitcherScreen(
    tabs: List<BrowserTab>,
    activeTabIndex: Int,
    showPrivateTabsOnly: Boolean,
    onPrivateToggle: (Boolean) -> Unit,
    onTabSelect: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onCloseAll: () -> Unit,
    onSettingsClick: () -> Unit,
    isAppDarkMode: Boolean,
    isIncognitoLocked: Boolean = false,
    onUnlock: () -> Unit = {}
) {
    val isDark = isAppDarkMode
    val accentNormal = Color(0xFFEC4899)
    val accentPrivate = Color(0xFFFF002C)
    val backgroundColor = if (isDark) Color(0xFF0F0F11) else Color(0xFFF8F9FA)
    val cardOutlineColor = if (isDark) Color(0xFF28282A) else Color(0xFFE1E3E4)
    val textColor = if (isDark) Color.White else Color(0xFF191C1D)
    val subTextColor = if (isDark) Color.LightGray.copy(alpha = 0.6f) else Color(0xFF4D6172)
    val headerPillBgColor = if (isDark) Color(0xFF222224) else Color(0xFFEDEEEF)
    val bannerBgColor = if (isDark) Color(0xFF222224) else Color.White
    val activeTabPillBg = if (isDark) Color(0xFF2D2D30) else Color.White
    val activeTabText = if (showPrivateTabsOnly) accentPrivate else accentNormal
    val inactiveTabText = if (isDark) Color.Gray else Color.Gray.copy(alpha = 0.5f)

    var searchQuery by remember { mutableStateOf("") }

    val filteredTabsWithIndex = tabs.mapIndexed { index, tab -> index to tab }
        .filter { it.second.isPrivate == showPrivateTabsOnly }
        .filter {
            if (searchQuery.isBlank()) true
            else {
                val query = searchQuery.lowercase()
                it.second.title.lowercase().contains(query) ||
                it.second.url.lowercase().contains(query)
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Appbar Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            // Center Segmented Control Pill - 2 buttons: Normal + Private
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(140.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(headerPillBgColor)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Normal Tabs
                val normalCount = tabs.count { !it.isPrivate }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (!showPrivateTabsOnly) activeTabPillBg else Color.Transparent)
                        .clickable { onPrivateToggle(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(1.5.dp, if (!showPrivateTabsOnly) accentNormal else inactiveTabText, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = normalCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!showPrivateTabsOnly) accentNormal else inactiveTabText
                        )
                    }
                }

                // Button 2: Private Tabs
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (showPrivateTabsOnly) activeTabPillBg else Color.Transparent)
                        .clickable { onPrivateToggle(true) },
                    contentAlignment = Alignment.Center
                ) {
                    IncognitoIcon(
                        tint = if (showPrivateTabsOnly) accentPrivate else inactiveTabText
                    )
                }
            }

            // Far Right: Settings
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Settings",
                        tint = if (isDark) Color.White else Color(0xFF1A1A1A)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showPrivateTabsOnly && isIncognitoLocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF000000) else Color(0xFFF2F2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        IncognitoIcon(
                            tint = Color(0xFFFF002C),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Tab Inkognito Terkunci",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Gunakan sidik jari atau PIN perangkat Anda untuk membuka kunci",
                        color = subTextColor,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = onUnlock,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF002C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Buka Kunci",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))

            // Search Bar (functional - filters by title & URL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) Color(0xFF222224) else Color(0xFFEDEEEF))
                    .padding(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = textColor,
                        fontSize = 14.sp
                    )
                ) { innerTextField ->
                    if (searchQuery.isBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cari tab atau alamat situs",
                                color = if (isDark) Color.LightGray.copy(alpha = 0.4f) else Color(0xFF6B7280),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        innerTextField()
                    }
                }
                if (searchQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { searchQuery = "" }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val thresholdMs = 21L * 24 * 60 * 60 * 1000
            val currentTime = System.currentTimeMillis()
            val inactiveTabs = filteredTabsWithIndex.filter { (_, tab) ->
                currentTime - tab.lastAccessed > thresholdMs
            }

            if (inactiveTabs.isNotEmpty()) {
                // Inactive Tabs Banner (Cleaner & No hard border)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bannerBgColor)
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "(${inactiveTabs.size}) item tidak aktif",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "Tab dan grup yang tidak terpakai atau duplikat",
                            fontSize = 11.sp,
                            color = subTextColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Grid of tabs (or "no results" message)
            if (filteredTabsWithIndex.isEmpty() && searchQuery.isNotBlank()) {
                // No search results
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Tidak menemukan tab dengan \"${searchQuery}\"",
                            color = subTextColor,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredTabsWithIndex, key = { it.second.id }) { (originalIndex, tab) ->
                        TabCard(
                            originalIndex = originalIndex,
                            tab = tab,
                            isActive = originalIndex == activeTabIndex,
                            isDark = isDark,
                            cardOutlineColor = cardOutlineColor,
                            textColor = textColor,
                            onTabSelect = onTabSelect,
                            onTabClose = { onTabClose(originalIndex) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TabCard(
    originalIndex: Int,
    tab: BrowserTab,
    isActive: Boolean,
    isDark: Boolean,
    cardOutlineColor: Color,
    textColor: Color,
    onTabSelect: (Int) -> Unit,
    onTabClose: () -> Unit
) {
    val isStart = tab.url == "yue://newtab" || tab.url.isBlank()
    val accentNormal = Color(0xFFEC4899)
    val accentPrivate = Color(0xFFFF002C)
    val activeBorderColor = if (tab.isPrivate) accentPrivate else accentNormal
    val cardBg = if (isDark) Color(0xFF1A1A1C) else Color.White
    val headerBg = if (isDark) Color(0xFF121214) else Color(0xFFF2F3F4)
    val density = LocalDensity.current

    val offsetX = remember { Animatable(0f) }
    var isDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (isDismissed) {
        LaunchedEffect(Unit) {
            onTabClose()
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / 1.2f)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = with(density) { 50.dp.toPx() }
                        scope.launch {
                            if (abs(offsetX.value) > threshold) {
                                isDismissed = true
                            } else {
                                offsetX.animateTo(0f, tween(durationMillis = 200))
                            }
                        }
                    }
                ) { change, dragAmount ->
                    scope.launch {
                        offsetX.snapTo(offsetX.value + dragAmount)
                    }
                    change.consume()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(
                    width = if (isActive) 2.dp else 0.8.dp,
                    color = if (isActive) activeBorderColor else cardOutlineColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { onTabSelect(originalIndex) }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Minimal header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(headerBg)
                        .padding(horizontal = 8.dp)
                ) {
                    // Favicon
                    if (isStart) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF28282A) else Color(0xFFE1E3E4)),
                            contentAlignment = Alignment.Center
                        ) {
                            MoonIcon(tint = if (tab.isPrivate) accentPrivate else accentNormal, isPrivate = tab.isPrivate, isDark = isDark)
                        }
                    } else if (tab.favicon != null) {
                        Image(
                            bitmap = tab.favicon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background((if (tab.isPrivate) accentPrivate else accentNormal).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PublicIcon(tint = if (tab.isPrivate) accentPrivate else accentNormal)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Title
                    Text(
                        text = if (isStart) "Yue tab" else tab.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color.White else Color(0xFF191C1D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Close button (smaller)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onTabClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            tint = if (isDark) Color.LightGray.copy(alpha = 0.7f) else Color(0xFF667889),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Web preview (takes rest of space)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(if (isDark) Color(0xFF0F0F11) else Color(0xFFF2F3F4))
                ) {
                    if (tab.thumbnail != null) {
                        Image(
                            bitmap = tab.thumbnail.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
