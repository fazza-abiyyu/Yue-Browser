package com.yue.browser.presentation.ui.tabswitcher

import com.yue.browser.presentation.ui.*

import com.yue.browser.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.domain.model.TabGroup

@Composable
fun GroupDetailOverlay(
    activeDetailGroupId: String?,
    onActiveDetailGroupIdChange: (String?) -> Unit,
    groups: Map<String, TabGroup>,
    filteredTabsWithIndex: List<Pair<Int, BrowserTab>>,
    lockedTabIds: Set<String>,
    activeTabIndex: Int,
    isDark: Boolean,
    cardOutlineColor: Color,
    textColor: Color,
    subTextColor: Color,
    onTabSelect: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onRemoveTabFromGroup: (String) -> Unit,
    onCreateTabInGroup: (String) -> Unit,
    onMoveTab: (Int, Int) -> Unit,
    onRenameClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    draggedTabId: String?,
    onDraggedTabIdChange: (String?) -> Unit,
    dragOffset: Offset,
    onDragOffsetChange: (Offset) -> Unit,
    touchPosition: Offset,
    onTouchPositionChange: (Offset) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    parentCoordinates: LayoutCoordinates?,
    detailCardBounds: MutableMap<String, Rect>,
    removeDropZoneBounds: Rect?,
    onRemoveDropZoneBoundsChange: (Rect?) -> Unit
) {
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = activeDetailGroupId != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val groupId = activeDetailGroupId ?: return@AnimatedVisibility
        val group = groups[groupId] ?: return@AnimatedVisibility
        val groupTabs = filteredTabsWithIndex.filter { it.second.groupId == groupId }
        val groupColor = GroupColors.getOrNull(group.colorIndex) ?: Color.Blue

        if (groupTabs.isEmpty()) {
            onActiveDetailGroupIdChange(null)
            return@AnimatedVisibility
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onActiveDetailGroupIdChange(null) },
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
                    IconButton(onClick = { onActiveDetailGroupIdChange(null) }) {
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
                            .clickable { onRenameClick(groupId) }
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
                                    onDeleteClick(groupId)
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
                                            onDraggedTabIdChange(tab.id)
                                            onDragStateChanged(true)
                                            val bounds = detailCardBounds[tab.id]
                                            if (bounds != null) {
                                                onTouchPositionChange(bounds.topLeft)
                                                onDragOffsetChange(Offset.Zero)
                                            }
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
                                                // Normally show context menu or select tab on click,
                                                // but long press might just cancel.
                                            }
                                            onDraggedTabIdChange(null)
                                            onDragStateChanged(false)
                                        },
                                        onDragCancel = {
                                            onDraggedTabIdChange(null)
                                            onDragStateChanged(false)
                                        },
                                        onDrag = { change, dragAmount ->
                                            accumulatedDrag += dragAmount
                                            if (!hasPassedThreshold) {
                                                if (accumulatedDrag.getDistance() > thresholdPx) {
                                                    hasPassedThreshold = true
                                                    onDraggedTabIdChange(tab.id)
                                                    onDragStateChanged(true)
                                                    val bounds = detailCardBounds[tab.id]
                                                    if (bounds != null) {
                                                        onTouchPositionChange(bounds.topLeft + change.position)
                                                        onDragOffsetChange(Offset.Zero)
                                                    }
                                                }
                                            } else {
                                                onDragOffsetChange(dragOffset + dragAmount)
                                                onTouchPositionChange(touchPosition + dragAmount)
                                                
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
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + fadeIn(),
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
                                    onRemoveDropZoneBoundsChange(parent.localBoundingBoxOf(coords))
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
