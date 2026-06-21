package com.yue.browser.presentation.ui.tabswitcher

import com.yue.browser.presentation.ui.*

import com.yue.browser.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.TabGroup

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
    
    var activeDetailGroupId by remember { mutableStateOf<String?>(null) }
    var showCreateGroupDialogForIds by remember { mutableStateOf<List<String>?>(null) }
    var showDeleteGroupConfirmationId by remember { mutableStateOf<String?>(null) }
    var showEditGroupDialogId by remember { mutableStateOf<String?>(null) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedTabIds = remember { mutableStateMapOf<String, Boolean>() }
    var showContextMenuForTabId by remember { mutableStateOf<String?>(null) }
    var showMoveToGroupForTabId by remember { mutableStateOf<String?>(null) }

    var draggedTabId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var touchPosition by remember { mutableStateOf(Offset.Zero) }
    val mainCardBounds = remember { mutableStateMapOf<String, Rect>() }
    val detailCardBounds = remember { mutableStateMapOf<String, Rect>() }
    var removeDropZoneBounds by remember { mutableStateOf<Rect?>(null) }
    var parentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

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

        GroupDetailOverlay(
            activeDetailGroupId = activeDetailGroupId,
            onActiveDetailGroupIdChange = { activeDetailGroupId = it },
            groups = groups,
            filteredTabsWithIndex = filteredTabsWithIndex,
            lockedTabIds = lockedTabIds,
            activeTabIndex = activeTabIndex,
            isDark = isDark,
            cardOutlineColor = cardOutlineColor,
            textColor = textColor,
            subTextColor = subTextColor,
            onTabSelect = onTabSelect,
            onTabClose = onTabClose,
            onRemoveTabFromGroup = onRemoveTabFromGroup,
            onCreateTabInGroup = onCreateTabInGroup,
            onMoveTab = onMoveTab,
            onRenameClick = { showEditGroupDialogId = it },
            onDeleteClick = { showDeleteGroupConfirmationId = it },
            draggedTabId = draggedTabId,
            onDraggedTabIdChange = { draggedTabId = it },
            dragOffset = dragOffset,
            onDragOffsetChange = { dragOffset = it },
            touchPosition = touchPosition,
            onTouchPositionChange = { touchPosition = it },
            onDragStateChanged = onDragStateChanged,
            parentCoordinates = parentCoordinates,
            detailCardBounds = detailCardBounds,
            removeDropZoneBounds = removeDropZoneBounds,
            onRemoveDropZoneBoundsChange = { removeDropZoneBounds = it }
        )

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
                                    topLeft = Offset.Zero,
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
    }
