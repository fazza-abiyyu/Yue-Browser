package com.yue.browser.presentation.ui.tabswitcher

import com.yue.browser.presentation.ui.*

import com.yue.browser.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.TabGroup
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val GroupColors = listOf(
    Color(0xFF3B82F6), // Blue
    Color(0xFFEF4444), // Red
    Color(0xFFF59E0B), // Yellow
    Color(0xFF10B981), // Green
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFF06B6D4), // Teal
    Color(0xFFF97316)  // Orange
)

sealed class SwitcherItem {
    data class StandaloneTab(val originalIndex: Int, val tab: BrowserTab) : SwitcherItem()
    data class TabGroupItem(val groupId: String, val group: TabGroup, val tabs: List<Pair<Int, BrowserTab>>) : SwitcherItem()
}

@Composable
fun IncognitoIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        
        val brimY = h * 0.4f
        drawLine(
            color = tint,
            start = Offset(w * 0.1f, brimY),
            end = Offset(w * 0.9f, brimY),
            strokeWidth = 1.5.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        
        val hatPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, brimY)
            lineTo(w * 0.3f, h * 0.15f)
            quadraticBezierTo(w * 0.5f, h * 0.22f, w * 0.7f, h * 0.15f)
            lineTo(w * 0.75f, brimY)
            close()
        }
        drawPath(path = hatPath, color = tint)
        
        val leftLensCenter = Offset(w * 0.33f, h * 0.65f)
        val rightLensCenter = Offset(w * 0.67f, h * 0.65f)
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
        
        drawLine(
            color = tint,
            start = Offset(leftLensCenter.x + lensRadius, leftLensCenter.y),
            end = Offset(rightLensCenter.x - lensRadius, rightLensCenter.y),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
fun PublicIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(12.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        
        drawCircle(
            color = tint,
            radius = r,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
        )
        
        drawLine(
            color = tint,
            start = Offset(center.x - r, center.y),
            end = Offset(center.x + r, center.y),
            strokeWidth = 1.2.dp.toPx()
        )
        
        drawLine(
            color = tint,
            start = Offset(center.x, center.y - r),
            end = Offset(center.x, center.y + r),
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

        val bgTint = if (isDark) Color(0xFF000000) else Color.White

        if (isPrivate) {
            drawCircle(
                color = tint.copy(alpha = 0.18f),
                radius = r * 1.25f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = tint,
                radius = r,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = bgTint,
                radius = r * 0.92f,
                center = Offset(cx + r * 0.65f, cy)
            )
        } else {
            drawCircle(
                color = tint,
                radius = r,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = bgTint,
                radius = r * 0.85f,
                center = Offset(cx + r * 0.35f, cy)
            )
        }
    }
}

@Composable
fun PreviewSlot(
    tab: BrowserTab?,
    isDark: Boolean,
    accentColor: Color,
    lockedTabIds: Set<String> = emptySet()
) {
    if (tab == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDark) Color(0xFF121214) else Color(0xFFEDEEEF))
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDark) Color(0xFF0F0F11) else Color(0xFFF2F3F4)),
            contentAlignment = Alignment.Center
        ) {
            val isLocked = tab.id in lockedTabIds

            if (!isLocked) {
                if (tab.thumbnail != null) {
                    Image(
                        bitmap = tab.thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    ) {
                        if (tab.favicon != null) {
                            Image(
                                bitmap = tab.favicon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            val letter = tab.title.trim().firstOrNull()?.toString()?.uppercase() ?: "T"
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                  Text(
                                      text = letter,
                                      color = accentColor,
                                      fontSize = 12.sp,
                                      fontWeight = FontWeight.Bold
                                  )
                            }
                        }
                    }
                }
            }

            if (isLocked) {
                val previewBg = if (isDark) Color(0xFF0F0F11) else Color(0xFFF2F3F4)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(previewBg)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.locked),
                        tint = if (isDark) Color.White else Color(0xFF666666),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GroupCard(
    group: TabGroup,
    tabs: List<Pair<Int, BrowserTab>>,
    lockedTabIds: Set<String> = emptySet(),
    isActive: Boolean,
    isHovered: Boolean,
    isDark: Boolean,
    cardOutlineColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groupColor = GroupColors.getOrNull(group.colorIndex) ?: Color.Blue
    val activeBorderColor = groupColor
    val cardBg = if (isDark) Color(0xFF1A1A1C) else Color.White
    val headerBg = if (isDark) Color(0xFF121214) else Color(0xFFF2F3F4)
    val borderNeeded = isActive || isHovered
    val borderWidth = if (borderNeeded) 2.dp else 0.8.dp
    val borderColor = if (borderNeeded) activeBorderColor else cardOutlineColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f / 1.2f)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val strokeWidth = borderWidth.toPx()
                    val halfStroke = strokeWidth / 2f
                    val cornerRadius = 16.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(halfStroke, halfStroke),
                        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                    )
                }
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(headerBg)
                    .padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(groupColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = group.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.tab_switcher_group_menu),
                            tint = if (isDark) Color.LightGray else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tab_switcher_edit_group_menu)) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tab_switcher_delete_group_menu)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PreviewSlot(tabs.getOrNull(0)?.second, isDark, activeBorderColor, lockedTabIds)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PreviewSlot(tabs.getOrNull(1)?.second, isDark, activeBorderColor, lockedTabIds)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PreviewSlot(tabs.getOrNull(2)?.second, isDark, activeBorderColor, lockedTabIds)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val totalTabs = tabs.size
                            if (totalTabs > 4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDark) Color(0xFF28282A) else Color(0xFFE1E3E4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${totalTabs - 3}",
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                PreviewSlot(tabs.getOrNull(3)?.second, isDark, activeBorderColor, lockedTabIds)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabCard(
    originalIndex: Int,
    tab: BrowserTab,
    isLocked: Boolean = false,
    isActive: Boolean,
    isHovered: Boolean,
    isDark: Boolean,
    cardOutlineColor: Color,
    textColor: Color,
    onTabSelect: (Int) -> Unit,
    onTabClose: () -> Unit,
    modifier: Modifier = Modifier,
    groupColor: Color? = null
) {
    val isStart = tab.url == "yue://newtab" || tab.url.isBlank()
    val accentNormal = Color(0xFFEC4899)
    val accentPrivate = Color(0xFFFF002C)
    val activeBorderColor = groupColor ?: (if (tab.isPrivate) accentPrivate else accentNormal)
    val cardBg = if (isDark) Color(0xFF1A1A1C) else Color.White
    val headerBg = if (isDark) Color(0xFF121214) else Color(0xFFF2F3F4)
    val density = LocalDensity.current
    val borderNeeded = isActive || isHovered
    val borderWidth = if (borderNeeded) 2.dp else 0.8.dp
    val borderColor = if (borderNeeded) activeBorderColor else cardOutlineColor

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
        modifier = modifier
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
                .fillMaxSize()
                .clickable {
                    onTabSelect(originalIndex)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val strokeWidth = borderWidth.toPx()
                        val halfStroke = strokeWidth / 2f
                        val cornerRadius = 16.dp.toPx()
                        drawRoundRect(
                            color = borderColor,
                            topLeft = Offset(halfStroke, halfStroke),
                            size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(headerBg)
                        .padding(horizontal = 8.dp)
                ) {
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

                    Text(
                        text = if (isStart) stringResource(R.string.tab_switcher_default_title) else tab.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color.White else Color(0xFF191C1D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onTabClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_tab),
                            tint = if (isDark) Color.LightGray.copy(alpha = 0.7f) else Color(0xFF667889),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(if (isDark) Color(0xFF0F0F11) else Color(0xFFF2F3F4))
                ) {
                    if (!isLocked && tab.thumbnail != null) {
                        Image(
                            bitmap = tab.thumbnail.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isLocked) {
                        val previewBg = if (isDark) Color(0xFF0F0F11) else Color(0xFFF2F3F4)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(previewBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.locked),
                                    tint = if (isDark) Color.White else Color(0xFF666666),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.locked),
                                    color = if (isDark) Color.White else Color(0xFF666666),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
