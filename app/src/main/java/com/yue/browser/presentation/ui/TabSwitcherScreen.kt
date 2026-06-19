package com.yue.browser.presentation.ui

import com.yue.browser.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.TabGroup

// Curated harmonious group color indicators
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
            Color(0xFF000000)
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
    lockedTabIds: Set<String> = emptySet(),
    activeTabIndex: Int,
    showPrivateTabsOnly: Boolean,
    onPrivateToggle: (Boolean) -> Unit,
    onTabSelect: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onCloseAll: () -> Unit,
    onSettingsClick: () -> Unit,
    isAppDarkMode: Boolean,
    isIncognitoLocked: Boolean = false,
    onUnlock: () -> Unit = {},
    groups: Map<String, TabGroup> = emptyMap(),
    onCreateGroup: (name: String, colorIndex: Int, tabIds: List<String>) -> Unit = { _, _, _ -> },
    onAddTabToGroup: (tabId: String, groupId: String) -> Unit = { _, _ -> },
    onRemoveTabFromGroup: (tabId: String) -> Unit = { _ -> },
    onRenameGroup: (groupId: String, newName: String) -> Unit = { _, _ -> },
    onUpdateGroupColor: (groupId: String, colorIndex: Int) -> Unit = { _, _ -> },
    onDeleteGroup: (groupId: String) -> Unit = { _ -> },
    onMoveTab: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onCreateTabInGroup: (groupId: String) -> Unit = {},
    onDragStateChanged: (Boolean) -> Unit = {}
) {
    val isDark = isAppDarkMode
    val context = LocalContext.current
    val density = LocalDensity.current
    val accentNormal = Color(0xFFEC4899)
    val accentPrivate = Color(0xFFFF002C)
    val backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFFF8F9FA)
    val cardOutlineColor = if (isDark) Color(0xFF1A1A1C) else Color(0xFFE1E3E4)
    val textColor = if (isDark) Color.White else Color(0xFF191C1D)
    val subTextColor = if (isDark) Color.LightGray.copy(alpha = 0.6f) else Color(0xFF4D6172)
    val headerPillBgColor = if (isDark) Color(0xFF121212) else Color(0xFFEDEEEF)
    val bannerBgColor = if (isDark) Color(0xFF121212) else Color.White
    val activeTabPillBg = if (isDark) Color(0xFF222222) else Color.White
    val activeTabText = if (showPrivateTabsOnly) accentPrivate else accentNormal
    val inactiveTabText = if (isDark) Color.Gray else Color.Gray.copy(alpha = 0.5f)

    var searchQuery by remember { mutableStateOf("") }
    
    // UI states for Tab Groups
    var activeDetailGroupId by remember { mutableStateOf<String?>(null) }
    var showCreateGroupDialogForIds by remember { mutableStateOf<List<String>?>(null) }
    var showDeleteGroupConfirmationId by remember { mutableStateOf<String?>(null) }
    var showEditGroupDialogId by remember { mutableStateOf<String?>(null) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedTabIds = remember { mutableStateMapOf<String, Boolean>() }
    var showContextMenuForTabId by remember { mutableStateOf<String?>(null) }
    var showMoveToGroupForTabId by remember { mutableStateOf<String?>(null) }

    // Drag and Drop tracking states
    var draggedTabId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var touchPosition by remember { mutableStateOf(Offset.Zero) }
    val mainCardBounds = remember { mutableStateMapOf<String, Rect>() }
    val detailCardBounds = remember { mutableStateMapOf<String, Rect>() }
    var removeDropZoneBounds by remember { mutableStateOf<Rect?>(null) }
    var parentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Filtered tabs with original indices
    val filteredTabsWithIndex = remember(tabs, showPrivateTabsOnly, searchQuery, groups) {
        tabs.mapIndexed { index, tab -> index to tab }
            .filter { it.second.isPrivate == showPrivateTabsOnly }
            .filter { (_, tab) ->
                if (searchQuery.isBlank()) true
                else {
                    val query = searchQuery.trim().lowercase()
                    val groupName = tab.groupId?.let { gId -> groups[gId]?.name }?.trim()?.lowercase() ?: ""
                    tab.title.lowercase().contains(query) ||
                    tab.url.lowercase().contains(query) ||
                    groupName.contains(query)
                }
            }
    }

    // Switcher list items: group them
    val switcherItems = remember(filteredTabsWithIndex, groups) {
        val list = mutableListOf<SwitcherItem>()
        val seenGroups = mutableSetOf<String>()
        filteredTabsWithIndex.forEach { (originalIndex, tab) ->
            val gId = tab.groupId
            if (gId == null) {
                list.add(SwitcherItem.StandaloneTab(originalIndex, tab))
            } else {
                if (gId !in seenGroups) {
                    seenGroups.add(gId)
                    val group = groups[gId]
                    if (group != null) {
                        val groupTabs = filteredTabsWithIndex.filter { it.second.groupId == gId }
                        list.add(SwitcherItem.TabGroupItem(gId, group, groupTabs))
                    } else {
                        list.add(SwitcherItem.StandaloneTab(originalIndex, tab))
                    }
                }
            }
        }
        list
    }

    // Hover detection state
    val hoveredId by remember(touchPosition, draggedTabId, activeDetailGroupId) {
        derivedStateOf {
            if (draggedTabId == null || activeDetailGroupId != null) null
            else {
                mainCardBounds.entries
                    .filter { it.key != draggedTabId }
                    .find { it.value.contains(touchPosition) }
                    ?.key
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onGloballyPositioned { parentCoordinates = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
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
                                color = if (!showPrivateTabsOnly) accentNormal else inactiveTabText,
                                modifier = Modifier.graphicsLayer { translationY = -1.5f }
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

                // Far Right: Settings & Select Mode Toggle
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showHeaderMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showHeaderMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.settings),
                                tint = if (isDark) Color.White else Color(0xFF1A1A1A)
                            )
                        }
                        DropdownMenu(
                            expanded = showHeaderMenu,
                            onDismissRequest = { showHeaderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tab_switcher_select)) },
                                onClick = {
                                    showHeaderMenu = false
                                    isMultiSelectMode = true
                                    selectedTabIds.clear()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tab_switcher_close_all)) },
                                onClick = {
                                    showHeaderMenu = false
                                    onCloseAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                onClick = {
                                    showHeaderMenu = false
                                    onSettingsClick()
                                }
                            )
                        }
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
                            text = stringResource(R.string.tab_switcher_incognito_locked),
                            color = textColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.tab_switcher_incognito_subtitle),
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
                                text = stringResource(R.string.tab_switcher_unlock),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))

                // Search Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDark) Color(0xFF121212) else Color(0xFFEDEEEF))
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
                                    text = stringResource(R.string.tab_switcher_search_placeholder),
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
                                contentDescription = stringResource(R.string.clear),
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
                                text = stringResource(R.string.tab_switcher_inactive_count, inactiveTabs.size),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = stringResource(R.string.tab_switcher_inactive_subtitle),
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

                // Main Grid of items
                if (switcherItems.isEmpty() && searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tab_switcher_no_results, searchQuery),
                            color = subTextColor,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val activeSwitcherIdx = remember(activeTabIndex, switcherItems) {
                        switcherItems.indexOfFirst { item ->
                            when (item) {
                                is SwitcherItem.StandaloneTab -> item.originalIndex == activeTabIndex
                                is SwitcherItem.TabGroupItem -> item.tabs.any { it.first == activeTabIndex }
                            }
                        }.coerceAtLeast(0)
                    }
                    val gridState = rememberLazyGridState(
                        initialFirstVisibleItemIndex = activeSwitcherIdx
                    )
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(switcherItems, key = { item ->
                            when (item) {
                                is SwitcherItem.StandaloneTab -> "tab_${item.tab.id}"
                                is SwitcherItem.TabGroupItem -> "group_${item.groupId}"
                            }
                        }) { item ->
                            when (item) {
                                is SwitcherItem.StandaloneTab -> {
                                    val tabId = item.tab.id
                                    val isTabSelected = selectedTabIds[tabId] == true
                                    val isHovered = hoveredId == tabId

                                    Box(
                                        modifier = Modifier
                                            .onGloballyPositioned { coords ->
                                                val parent = parentCoordinates
                                                if (parent != null && coords.isAttached) {
                                                    mainCardBounds[tabId] = parent.localBoundingBoxOf(coords)
                                                }
                                            }
                                            .pointerInput(tabId) {
                                                if (!isMultiSelectMode) {
                                                    var hasPassedThreshold = false
                                                    var accumulatedDrag = Offset.Zero
                                                    val thresholdPx = with(density) { 8.dp.toPx() }
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { _ ->
                                                            hasPassedThreshold = false
                                                            accumulatedDrag = Offset.Zero
                                                        },
                                                        onDragEnd = {
                                                            if (hasPassedThreshold) {
                                                                val target = hoveredId
                                                                if (target != null) {
                                                                    val isTargetGroup = groups.containsKey(target)
                                                                    if (isTargetGroup) {
                                                                        onAddTabToGroup(tabId, target)
                                                                    } else {
                                                                        // Drop onto another standalone tab to create a group
                                                                        showCreateGroupDialogForIds = listOf(tabId, target)
                                                                    }
                                                                }
                                                            } else {
                                                                showContextMenuForTabId = tabId
                                                            }
                                                            draggedTabId = null
                                                            onDragStateChanged(false)
                                                        },
                                                        onDragCancel = {
                                                            draggedTabId = null
                                                            onDragStateChanged(false)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            accumulatedDrag += dragAmount
                                                            if (!hasPassedThreshold) {
                                                                if (accumulatedDrag.getDistance() > thresholdPx) {
                                                                    hasPassedThreshold = true
                                                                    draggedTabId = tabId
                                                                    onDragStateChanged(true)
                                                                    val bounds = mainCardBounds[tabId]
                                                                    if (bounds != null) {
                                                                        touchPosition = bounds.topLeft + change.position
                                                                        dragOffset = Offset.Zero
                                                                    }
                                                                }
                                                            } else {
                                                                dragOffset += dragAmount
                                                                touchPosition += dragAmount
                                                            }
                                                            change.consume()
                                                        }
                                                    )
                                                }
                                            }
                                            .alpha(if (draggedTabId == tabId) 0.3f else 1.0f)
                                            .scale(if (isHovered) 1.05f else 1.0f)
                                    ) {
                                        TabCard(
                                            originalIndex = item.originalIndex,
                                            tab = item.tab,
                                            isLocked = item.tab.id in lockedTabIds,
                                            isActive = item.originalIndex == activeTabIndex,
                                            isHovered = isHovered,
                                            isDark = isDark,
                                            cardOutlineColor = cardOutlineColor,
                                            textColor = textColor,
                                            onTabSelect = { idx ->
                                                if (isMultiSelectMode) {
                                                    selectedTabIds[tabId] = !isTabSelected
                                                } else {
                                                    onTabSelect(idx)
                                                }
                                            },
                                            onTabClose = { onTabClose(item.originalIndex) }
                                        )

                                        if (isMultiSelectMode) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isTabSelected) accentNormal else Color.Black.copy(alpha = 0.4f))
                                                    .border(1.5.dp, Color.White, CircleShape)
                                                    .clickable { selectedTabIds[tabId] = !isTabSelected }
                                                    .align(Alignment.TopStart),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isTabSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                is SwitcherItem.TabGroupItem -> {
                                    val groupId = item.groupId
                                    val isHovered = hoveredId == groupId

                                    Box(
                                        modifier = Modifier
                                            .onGloballyPositioned { coords ->
                                                val parent = parentCoordinates
                                                if (parent != null && coords.isAttached) {
                                                    mainCardBounds[groupId] = parent.localBoundingBoxOf(coords)
                                                }
                                            }
                                            .scale(if (isHovered) 1.05f else 1.0f)
                                    ) {
                                        GroupCard(
                                            group = item.group,
                                            tabs = item.tabs,
                                            lockedTabIds = lockedTabIds,
                                            isActive = item.tabs.any { it.first == activeTabIndex },
                                            isHovered = isHovered,
                                            isDark = isDark,
                                            cardOutlineColor = cardOutlineColor,
                                            textColor = textColor,
                                            onClick = {
                                                if (isMultiSelectMode) {
                                                    // Toggle selection of all tabs in this group
                                                    val anyUnselected = item.tabs.any { selectedTabIds[it.second.id] != true }
                                                    item.tabs.forEach { selectedTabIds[it.second.id] = anyUnselected }
                                                } else {
                                                    activeDetailGroupId = groupId
                                                }
                                            },
                                            onRename = {
                                                showEditGroupDialogId = groupId
                                            },
                                            onDelete = {
                                                showDeleteGroupConfirmationId = groupId
                                            }
                                        )

                                        if (isMultiSelectMode) {
                                            val allSelected = item.tabs.all { selectedTabIds[it.second.id] == true }
                                            val anySelected = item.tabs.any { selectedTabIds[it.second.id] == true }
                                            Box(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(if (allSelected) accentNormal else if (anySelected) accentNormal.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f))
                                                    .border(1.5.dp, Color.White, CircleShape)
                                                    .clickable {
                                                        item.tabs.forEach { selectedTabIds[it.second.id] = !allSelected }
                                                    }
                                                    .align(Alignment.TopStart),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (allSelected || anySelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // 8. Group Detail Overlay Screen
        AnimatedVisibility(
            visible = activeDetailGroupId != null,
            enter = fadeIn(), // clean fade-in
            exit = fadeOut()
        ) {
            val groupId = activeDetailGroupId ?: return@AnimatedVisibility
            val group = groups[groupId] ?: return@AnimatedVisibility
            val groupTabs = filteredTabsWithIndex.filter { it.second.groupId == groupId }
            val groupColor = GroupColors.getOrNull(group.colorIndex) ?: Color.Blue

            if (groupTabs.isEmpty()) {
                activeDetailGroupId = null
                return@AnimatedVisibility
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { activeDetailGroupId = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.7f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isDark) Color(0xFF1E1E24) else Color.White)
                        .border(1.5.dp, groupColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .clickable(enabled = false) {}
                        .padding(16.dp)
                ) {
                    // Header details
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        IconButton(onClick = { activeDetailGroupId = null }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = textColor
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showEditGroupDialogId = groupId }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(groupColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = group.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.tab_switcher_edit_group),
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                onCreateTabInGroup(groupId)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_tab),
                                tint = textColor
                            )
                        }

                        // Group detail menu
                        var showDetailMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showDetailMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.menu),
                                    tint = textColor
                                )
                            }
                            DropdownMenu(
                                expanded = showDetailMenu,
                                onDismissRequest = { showDetailMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tab_switcher_delete_group)) },
                                    onClick = {
                                        showDetailMenu = false
                                        showDeleteGroupConfirmationId = groupId
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Group Tabs Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(groupTabs, key = { it.second.id }) { (originalIndex, tab) ->
                            val isCardDragged = draggedTabId == tab.id

                            Box(
                                modifier = Modifier
                                    .onGloballyPositioned { coords ->
                                        val parent = parentCoordinates
                                        if (parent != null && coords.isAttached) {
                                            detailCardBounds[tab.id] = parent.localBoundingBoxOf(coords)
                                        }
                                    }
                                    .pointerInput(tab.id) {
                                        var hasPassedThreshold = false
                                        var accumulatedDrag = Offset.Zero
                                        val thresholdPx = with(density) { 8.dp.toPx() }
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { _ ->
                                                hasPassedThreshold = false
                                                accumulatedDrag = Offset.Zero
                                            },
                                            onDragEnd = {
                                                if (hasPassedThreshold) {
                                                    val dropZone = removeDropZoneBounds
                                                    if (dropZone != null && dropZone.contains(touchPosition)) {
                                                        onRemoveTabFromGroup(tab.id)
                                                    }
                                                } else {
                                                    showContextMenuForTabId = tab.id
                                                }
                                                draggedTabId = null
                                                onDragStateChanged(false)
                                            },
                                            onDragCancel = {
                                                draggedTabId = null
                                                onDragStateChanged(false)
                                            },
                                            onDrag = { change, dragAmount ->
                                                accumulatedDrag += dragAmount
                                                if (!hasPassedThreshold) {
                                                    if (accumulatedDrag.getDistance() > thresholdPx) {
                                                        hasPassedThreshold = true
                                                        draggedTabId = tab.id
                                                        onDragStateChanged(true)
                                                        val bounds = detailCardBounds[tab.id]
                                                        if (bounds != null) {
                                                            touchPosition = bounds.topLeft + change.position
                                                            dragOffset = Offset.Zero
                                                        }
                                                    }
                                                } else {
                                                    dragOffset += dragAmount
                                                    touchPosition += dragAmount
                                                    
                                                    // Live reordering logic
                                                    val currentIdx = groupTabs.indexOfFirst { it.second.id == tab.id }
                                                    val hoveredTab = groupTabs.find { other ->
                                                        other.second.id != tab.id &&
                                                        detailCardBounds[other.second.id]?.contains(touchPosition) == true
                                                    }
                                                    if (hoveredTab != null) {
                                                        val targetIdx = groupTabs.indexOf(hoveredTab)
                                                        if (currentIdx != -1 && targetIdx != -1 && currentIdx != targetIdx) {
                                                            onMoveTab(groupTabs[currentIdx].first, groupTabs[targetIdx].first)
                                                        }
                                                    }
                                                }
                                                change.consume()
                                            }
                                        )
                                    }
                                    .alpha(if (isCardDragged) 0.3f else 1.0f)
                            ) {
                                TabCard(
                                    originalIndex = originalIndex,
                                    tab = tab,
                                    isLocked = tab.id in lockedTabIds,
                                    isActive = originalIndex == activeTabIndex,
                                    isHovered = false,
                                    isDark = isDark,
                                    cardOutlineColor = cardOutlineColor,
                                    textColor = textColor,
                                    onTabSelect = onTabSelect,
                                    onTabClose = { onTabClose(originalIndex) },
                                    groupColor = groupColor
                                )
                            }
                        }
                    }

                    // Remove From Group Drop Zone
                    val isDropZoneHovered = removeDropZoneBounds?.contains(touchPosition) == true
                    AnimatedVisibility(
                        visible = draggedTabId != null,
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        val activeColor = groupColor
                        val normalBg = if (isDark) Color(0xFF16161A) else Color(0xFFF0F1F2)
                        val activeBg = activeColor.copy(alpha = 0.08f)
                        val normalBorderColor = cardOutlineColor
                        val activeBorderColor = activeColor
                        val normalTextColor = subTextColor
                        val activeTextColor = activeColor

                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(if (isDropZoneHovered) activeBg else normalBg, RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isDropZoneHovered) 1.5.dp else 1.dp,
                                    color = if (isDropZoneHovered) activeBorderColor else normalBorderColor,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .onGloballyPositioned { coords ->
                                    val parent = parentCoordinates
                                    if (parent != null && coords.isAttached) {
                                        removeDropZoneBounds = parent.localBoundingBoxOf(coords)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.tab_switcher_move_to_main),
                                color = if (isDropZoneHovered) activeTextColor else normalTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // 9. Floating Drag Preview
    if (draggedTabId != null) {
        val tab = tabs.find { it.id == draggedTabId }
        if (tab != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            touchPosition.x.roundToInt() - 80.dp.toPx().roundToInt(),
                            touchPosition.y.roundToInt() - 100.dp.toPx().roundToInt()
                        )
                    }
                    .width(160.dp)
                    .aspectRatio(1f / 1.2f)
                    .alpha(0.8f)
                    .scale(1.05f)
                    .zIndex(100f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val strokeWidth = 1.5.dp.toPx()
                            val cornerRadius = 16.dp.toPx()
                            drawRoundRect(
                                color = if (tab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899),
                                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                                size = size,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF1A1A1C) else Color.White)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(if (isDark) Color(0xFF121214) else Color(0xFFF2F3F4))
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = tab.title,
                            fontSize = 9.sp,
                            maxLines = 1,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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


    // 11. Multi-Select Actions Bar
    if (isMultiSelectMode && activeDetailGroupId == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(Alignment.Bottom)
                .zIndex(90f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1E1E24) else Color.White,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selectedCount = selectedTabIds.count { it.value }
                    Text(
                        text = stringResource(R.string.tab_switcher_selected_count, selectedCount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            isMultiSelectMode = false
                            selectedTabIds.clear()
                        }) {
                            Text(stringResource(R.string.cancel))
                        }

                        Button(
                            onClick = {
                                val ids = selectedTabIds.filter { it.value }.keys.toList()
                                showCreateGroupDialogForIds = ids
                            },
                            enabled = selectedCount >= 1,
                            colors = ButtonDefaults.buttonColors(containerColor = accentNormal)
                        ) {
                            Text(stringResource(R.string.tab_switcher_new_group_button))
                        }

                        IconButton(
                            onClick = {
                                val ids = selectedTabIds.filter { it.value }.keys.toSet()
                                val tabsToClose = tabs.mapIndexed { idx, t -> idx to t }
                                    .filter { it.second.id in ids }
                                    .sortedByDescending { it.first }
                                
                                tabsToClose.forEach { (originalIndex, _) ->
                                    onTabClose(originalIndex)
                                }
                                
                                isMultiSelectMode = false
                                selectedTabIds.clear()
                            },
                            enabled = selectedCount >= 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.tab_switcher_delete_selected),
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }

    // 12. Create Group Dialog
    if (showCreateGroupDialogForIds != null) {
        val ids = showCreateGroupDialogForIds!!
        CreateGroupDialog(
            onConfirm = { name, colorIndex ->
                onCreateGroup(name, colorIndex, ids)
                showCreateGroupDialogForIds = null
                isMultiSelectMode = false
                selectedTabIds.clear()
            },
            onDismiss = {
                showCreateGroupDialogForIds = null
            }
        )
    }

    // 13. Delete Group Dialog
    if (showDeleteGroupConfirmationId != null) {
        val groupId = showDeleteGroupConfirmationId!!
        AlertDialog(
            onDismissRequest = { showDeleteGroupConfirmationId = null },
            title = { Text(stringResource(R.string.tab_switcher_delete_group_title)) },
            text = { Text(stringResource(R.string.tab_switcher_delete_group_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGroup(groupId)
                        showDeleteGroupConfirmationId = null
                        activeDetailGroupId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupConfirmationId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 13.5 Edit Group Dialog
    if (showEditGroupDialogId != null) {
        val groupId = showEditGroupDialogId!!
        val group = groups[groupId]
        if (group != null) {
            EditGroupDialog(
                group = group,
                onConfirm = { name, colorIndex ->
                    onRenameGroup(groupId, name)
                    onUpdateGroupColor(groupId, colorIndex)
                    showEditGroupDialogId = null
                },
                onDismiss = {
                    showEditGroupDialogId = null
                }
            )
        }
    }

    // 14. Tab Context Menu Dialog
    if (showContextMenuForTabId != null) {
        val tabId = showContextMenuForTabId!!
        val tab = tabs.find { it.id == tabId }
        val originalIndex = tabs.indexOfFirst { it.id == tabId }
        
        if (tab != null && originalIndex != -1) {
            AlertDialog(
                onDismissRequest = { showContextMenuForTabId = null },
                title = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (tab.groupId != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onRemoveTabFromGroup(tabId)
                                        showContextMenuForTabId = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(R.string.tab_switcher_remove_from_group), fontSize = 16.sp)
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMoveToGroupForTabId = tabId
                                    showContextMenuForTabId = null
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.tab_switcher_move_to_group), fontSize = 16.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTabClose(originalIndex)
                                    showContextMenuForTabId = null
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.tab_switcher_close_tab), color = Color.Red, fontSize = 16.sp)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showContextMenuForTabId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    // 15. Move to Group Dialog
    if (showMoveToGroupForTabId != null) {
        val tabId = showMoveToGroupForTabId!!
        MoveToGroupDialog(
            groups = groups,
            onGroupSelected = { gId ->
                onAddTabToGroup(tabId, gId)
                showMoveToGroupForTabId = null
            },
            onCreateNewGroup = {
                showCreateGroupDialogForIds = listOf(tabId)
                showMoveToGroupForTabId = null
            },
            onDismiss = {
                showMoveToGroupForTabId = null
            }
        )
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
            val isLocked = tab != null && tab.id in lockedTabIds

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
                    val cornerRadius = 16.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        topLeft = androidx.compose.ui.geometry.Offset.Zero,
                        size = size,
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
fun CreateGroupDialog(
    onConfirm: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_switcher_create_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tab_switcher_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.tab_switcher_choose_color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GroupColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    width = if (selectedColorIndex == index) 2.dp else 0.dp,
                                    color = if (selectedColorIndex == index) color else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { selectedColorIndex = index }
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, selectedColorIndex)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EditGroupDialog(
    group: TabGroup,
    onConfirm: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var selectedColorIndex by remember { mutableStateOf(group.colorIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_switcher_edit_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tab_switcher_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.tab_switcher_choose_color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GroupColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    width = if (selectedColorIndex == index) 2.dp else 0.dp,
                                    color = if (selectedColorIndex == index) color else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable { selectedColorIndex = index }
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, selectedColorIndex)
                    }
                },
                enabled = name.isNotBlank()
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

@Composable
fun MoveToGroupDialog(
    groups: Map<String, TabGroup>,
    onGroupSelected: (String) -> Unit,
    onCreateNewGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_switcher_move_to_group_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.tab_switcher_no_groups), color = Color.Gray, fontSize = 14.sp)
                } else {
                    groups.values.forEach { group ->
                        val groupColor = GroupColors.getOrNull(group.colorIndex) ?: Color.Blue
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGroupSelected(group.id) }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(groupColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = group.name, fontSize = 16.sp)
                        }
                    }
                    HorizontalDivider()
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreateNewGroup() }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = stringResource(R.string.tab_switcher_new_group_option), color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun TabCard(
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
                        val cornerRadius = 16.dp.toPx()
                        drawRoundRect(
                            color = borderColor,
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = size,
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
