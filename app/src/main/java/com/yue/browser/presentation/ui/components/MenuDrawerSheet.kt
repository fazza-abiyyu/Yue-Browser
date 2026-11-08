package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.yue.browser.presentation.ui.tabswitcher.IncognitoIcon
import com.yue.browser.domain.model.BrowserTab
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.ZoomIn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi

@Composable
fun ClockIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = tint,
            radius = r - 2.dp.toPx(),
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = tint,
            start = center,
            end = Offset(center.x, center.y - r + 6.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = center,
            end = Offset(center.x + r - 8.dp.toPx(), center.y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun DownloadIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.15f),
            end = Offset(w * 0.5f, h * 0.65f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.5f),
            end = Offset(w * 0.5f, h * 0.65f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.65f, h * 0.5f),
            end = Offset(w * 0.5f, h * 0.65f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.2f, h * 0.75f),
            end = Offset(w * 0.2f, h * 0.85f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.2f, h * 0.85f),
            end = Offset(w * 0.8f, h * 0.85f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.8f, h * 0.85f),
            end = Offset(w * 0.8f, h * 0.75f),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun BookmarkPlusIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        val bookmarkPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, h * 0.12f)
            lineTo(w * 0.75f, h * 0.12f)
            lineTo(w * 0.75f, h * 0.88f)
            lineTo(w * 0.5f, h * 0.68f)
            lineTo(w * 0.25f, h * 0.88f)
            close()
        }
        drawPath(path = bookmarkPath, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))

        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.26f),
            end = Offset(w * 0.5f, h * 0.54f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, h * 0.4f),
            end = Offset(w * 0.64f, h * 0.4f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ThemeToggleIcon(modifier: Modifier = Modifier, isDark: Boolean, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val r = w * 0.35f
        val center = Offset(w * 0.5f, h * 0.5f)

        if (isDark) {
            drawCircle(
                color = tint,
                radius = r,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
            val rayLength = 4.dp.toPx()
            val gap = 3.dp.toPx()
            for (i in 0 until 8) {
                val angle = i * Math.PI / 4
                val startX = center.x + (r + gap) * Math.cos(angle).toFloat()
                val startY = center.y + (r + gap) * Math.sin(angle).toFloat()
                val endX = center.x + (r + gap + rayLength) * Math.cos(angle).toFloat()
                val endY = center.y + (r + gap + rayLength) * Math.sin(angle).toFloat()
                drawLine(
                    color = tint,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        } else {
            drawCircle(
                color = tint,
                radius = r,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = tint,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
            )
        }
    }
}

@Composable
fun MonitorIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.55f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.7f),
            end = Offset(w * 0.5f, h * 0.85f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.3f, h * 0.85f),
            end = Offset(w * 0.7f, h * 0.85f),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun PuzzleIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawRect(
            color = tint,
            topLeft = Offset(w * 0.25f, h * 0.25f),
            size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = tint,
            radius = w * 0.1f,
            center = Offset(w * 0.5f, h * 0.25f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = tint,
            radius = w * 0.1f,
            center = Offset(w * 0.75f, h * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun TerjemahIcon(modifier: Modifier = Modifier, tint: Color) {
    com.yue.browser.presentation.ui.TranslateIcon(
        modifier = modifier.size(24.dp),
        tint = tint
    )
}

@Composable
fun BlockSelectorIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawCircle(
            color = tint,
            radius = w * 0.28f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        drawCircle(color = tint, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.5f))
        drawLine(color = tint, start = Offset(w * 0.5f, h * 0.08f), end = Offset(w * 0.5f, h * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.5f, h * 0.78f), end = Offset(w * 0.5f, h * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.08f, h * 0.5f), end = Offset(w * 0.22f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.78f, h * 0.5f), end = Offset(w * 0.92f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun StarLineIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.62f, h * 0.42f)
            lineTo(w * 0.95f, h * 0.42f)
            lineTo(w * 0.68f, h * 0.62f)
            lineTo(w * 0.78f, h * 0.92f)
            lineTo(w * 0.5f, h * 0.73f)
            lineTo(w * 0.22f, h * 0.92f)
            lineTo(w * 0.32f, h * 0.62f)
            lineTo(w * 0.05f, h * 0.42f)
            lineTo(w * 0.38f, h * 0.42f)
            close()
        }
        drawPath(path, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun HomeLineIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(color = tint, start = Offset(w * 0.5f, h * 0.12f), end = Offset(w * 0.12f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.5f, h * 0.12f), end = Offset(w * 0.88f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.22f, h * 0.4f), end = Offset(w * 0.22f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.78f, h * 0.4f), end = Offset(w * 0.78f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.22f, h * 0.88f), end = Offset(w * 0.78f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun SettingsLineIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val cx = w * 0.5f
        val cy = h * 0.5f

        val path = androidx.compose.ui.graphics.Path().apply {
            val numTeeth = 8
            val rMin = w * 0.25f
            val rMax = w * 0.36f
            val toothWidthAngle = Math.PI / 18.0
            val slopeWidthAngle = Math.PI / 24.0

            for (i in 0 until numTeeth) {
                val baseAngle = i * (2.0 * Math.PI / numTeeth)

                val a1 = baseAngle - toothWidthAngle - slopeWidthAngle
                val p1x = cx + rMin * Math.cos(a1).toFloat()
                val p1y = cy + rMin * Math.sin(a1).toFloat()

                val a2 = baseAngle - toothWidthAngle
                val p2x = cx + rMax * Math.cos(a2).toFloat()
                val p2y = cy + rMax * Math.sin(a2).toFloat()

                val a3 = baseAngle + toothWidthAngle
                val p3x = cx + rMax * Math.cos(a3).toFloat()
                val p3y = cy + rMax * Math.sin(a3).toFloat()

                val a4 = baseAngle + toothWidthAngle + slopeWidthAngle
                val p4x = cx + rMin * Math.cos(a4).toFloat()
                val p4y = cy + rMin * Math.sin(a4).toFloat()

                if (i == 0) {
                    moveTo(p1x, p1y)
                } else {
                    lineTo(p1x, p1y)
                }
                lineTo(p2x, p2y)
                lineTo(p3x, p3y)
                lineTo(p4x, p4y)
            }
            close()
        }

        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        drawCircle(
            color = tint,
            radius = w * 0.11f,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
    }
}

@Composable
fun CodeIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(tint, Offset(w * 0.25f, h * 0.2f), Offset(w * 0.25f, h * 0.8f), stroke)
        drawLine(tint, Offset(w * 0.75f, h * 0.2f), Offset(w * 0.75f, h * 0.8f), stroke)
        drawLine(tint, Offset(w * 0.35f, h * 0.1f), Offset(w * 0.65f, h * 0.5f), stroke)
        drawLine(tint, Offset(w * 0.35f, h * 0.9f), Offset(w * 0.65f, h * 0.5f), stroke)
    }
}

@Composable
fun SearchLineIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val cx = w * 0.38f
        val cy = h * 0.38f
        val r = w * 0.26f
        drawCircle(tint, r, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        drawLine(tint, Offset(cx + r * 0.7f, cy + r * 0.7f), Offset(w * 0.82f, h * 0.82f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun ShareLineIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val nodeRadius = w * 0.11f

        val p1 = Offset(w * 0.28f, h * 0.5f)
        val p2 = Offset(w * 0.72f, h * 0.25f)
        val p3 = Offset(w * 0.72f, h * 0.75f)

        drawLine(color = tint, start = p1, end = p2, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = p1, end = p3, strokeWidth = stroke, cap = StrokeCap.Round)

        drawCircle(color = tint, radius = nodeRadius, center = p1, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        drawCircle(color = tint, radius = nodeRadius, center = p2, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        drawCircle(color = tint, radius = nodeRadius, center = p3, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuDrawerSheet(
    version: String,
    isDesktopSite: Boolean,
    onDesktopSiteToggle: (Boolean) -> Unit,
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onAddBookmarkClick: (android.content.Context) -> Unit,
    onNewIncognitoTab: () -> Unit,
    onShareUrl: (String) -> Unit,
    onTranslateClick: () -> Unit,
    onBlockSelectorClick: () -> Unit,
    onSaveOfflineClick: () -> Unit,
    onOfflinePagesClick: () -> Unit,
    onFindInPageClick: () -> Unit,
    currentUrl: String,
    activeTab: BrowserTab,
    // Item "Setelan Situs" di menu tablet (butuh situs aktif)
    showSiteSettings: Boolean = false,
    onSiteSettingsClick: () -> Unit = {},
    // Kustomisasi drawer: id item yang tampil + urutannya (docs/ui-redesign-spec.md S8)
    drawerItems: List<String> = com.yue.browser.domain.model.DEFAULT_DRAWER_ITEMS,
    onDrawerItemsChange: (List<String>) -> Unit = {},
    // Susunan grid "kolom x baris": 5x2 / 4x2 / 4x3 / 5x4
    drawerGrid: String = "4x3",
    onDrawerGridChange: (String) -> Unit = {},
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val coroutineScope = rememberCoroutineScope()
    val dismissWithAnimation: () -> Unit = {
        isVisible = false
        coroutineScope.launch {
            delay(150)
            onDismiss()
        }
    }

    BackHandler(enabled = isVisible) {
        dismissWithAnimation()
    }

    val rememberedContentColor = remember(contentColor) { contentColor }
    val rememberedTextLabelColor = remember(textLabelColor) { textLabelColor }
    val rememberedIsDarkMode = remember(isDarkMode) { isDarkMode }
    val rememberedIsDesktopSite = remember(isDesktopSite) { isDesktopSite }

    Box(
        modifier = Modifier.fillMaxSize().zIndex(10f),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        dismissWithAnimation()
                    }
            )
        }

        val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600

        if (isTablet) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            dismissWithAnimation()
                        },
                    contentAlignment = Alignment.TopEnd
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 92.dp, end = 16.dp)
                            .width(260.dp)
                            .heightIn(max = 540.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { },
                        color = backgroundColor,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shadowElevation = 6.dp
                    ) {
                        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            // Section 1: Navigation & New Tabs
                            item {
                                ListMenuItem(
                                    icon = { HomeLineIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_home),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onNavigate("yue://newtab"); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { IncognitoIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_incognito),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onNewIncognitoTab(); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { BookmarkPlusIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_add_bookmark),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onAddBookmarkClick(context); dismissWithAnimation() }
                                )
                            }
                            // Setelan situs — alternatif long-press pill di tablet (feedback user 2026-11-07)
                            if (showSiteSettings) {
                                item {
                                    ListMenuItem(
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Language,
                                                contentDescription = null,
                                                tint = rememberedContentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        label = stringResource(R.string.browser_site_settings),
                                        textColor = rememberedTextLabelColor,
                                        onClick = { onSiteSettingsClick(); dismissWithAnimation() }
                                    )
                                }
                            }

                            // Zoom Row
                            item {
                                var zoomPercent by remember { mutableStateOf(activeTab.session.getTextZoom()) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.ZoomIn,
                                            contentDescription = "Zoom",
                                            tint = rememberedContentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Zoom",
                                        fontSize = 13.sp,
                                        color = rememberedTextLabelColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Zoom Out (-)
                                        IconButton(
                                            onClick = {
                                                val newZoom = (zoomPercent - 10).coerceIn(50, 200)
                                                activeTab.session.setTextZoom(newZoom)
                                                zoomPercent = newZoom
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = rememberedTextLabelColor)
                                        }

                                        // Zoom Percentage
                                        Text(
                                            text = "$zoomPercent%",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = rememberedTextLabelColor,
                                            modifier = Modifier
                                                .clickable {
                                                    activeTab.session.setTextZoom(100)
                                                    zoomPercent = 100
                                                }
                                                .padding(horizontal = 4.dp)
                                        )

                                        // Zoom In (+)
                                        IconButton(
                                            onClick = {
                                                val newZoom = (zoomPercent + 10).coerceIn(50, 200)
                                                activeTab.session.setTextZoom(newZoom)
                                                zoomPercent = newZoom
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = rememberedTextLabelColor)
                                        }
                                    }
                                }
                            }

                            item { Divider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp)) }

                            // Section 2: Library
                            item {
                                ListMenuItem(
                                    icon = { StarLineIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_bookmarks),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onBookmarksClick(); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { ClockIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_history),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onHistoryClick(); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { DownloadIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_downloads),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onDownloadsClick(); dismissWithAnimation() }
                                )
                            }

                            item { Divider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp)) }

                            // Section 3: Web Page Controls
                            item {
                                ListMenuItem(
                                    icon = {
                                        MonitorIcon(
                                            tint = if (rememberedIsDesktopSite) MaterialTheme.colorScheme.primary else rememberedContentColor
                                        )
                                    },
                                    label = stringResource(R.string.menu_desktop),
                                    textColor = rememberedTextLabelColor,
                                    trailingContent = {
                                        if (rememberedIsDesktopSite) {
                                            Text("✓", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        }
                                    },
                                    onClick = { onDesktopSiteToggle(!rememberedIsDesktopSite); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { TerjemahIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_translate),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onTranslateClick(); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { SearchLineIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_find_in_page),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onFindInPageClick(); dismissWithAnimation() }
                                )
                            }

                            item { Divider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp)) }

                            // Section 4: Offline & Security
                            item {
                                ListMenuItem(
                                    icon = { BlockSelectorIcon(tint = MaterialTheme.colorScheme.error) },
                                    label = stringResource(R.string.menu_block),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onBlockSelectorClick(); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { ShareLineIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_share),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onShareUrl(currentUrl); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = rememberedContentColor, modifier = Modifier.size(20.dp)) },
                                    label = stringResource(R.string.menu_save_offline),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onSaveOfflineClick(); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { Icon(Icons.Default.OfflinePin, contentDescription = null, tint = rememberedContentColor, modifier = Modifier.size(20.dp)) },
                                    label = stringResource(R.string.menu_offline_pages),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onOfflinePagesClick(); dismissWithAnimation() }
                                )
                            }

                            item { Divider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp)) }

                            // Section 5: Settings & Theme
                            item {
                                ListMenuItem(
                                    icon = { ThemeToggleIcon(isDark = rememberedIsDarkMode, tint = rememberedContentColor) },
                                    label = stringResource(if (rememberedIsDarkMode) R.string.menu_light_mode else R.string.menu_dark_mode),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onDarkModeToggle(!rememberedIsDarkMode); dismissWithAnimation() }
                                )
                            }
                            item {
                                ListMenuItem(
                                    icon = { SettingsLineIcon(tint = rememberedContentColor) },
                                    label = stringResource(R.string.menu_settings),
                                    textColor = rememberedTextLabelColor,
                                    onClick = { onSettingsClick(); dismissWithAnimation() }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(150)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(150)
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { },
                    color = backgroundColor,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DragHandle(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val pagerState = rememberPagerState(pageCount = { 2 })

                        // ——— Menu kustom: urutan & item tampilan diatur user (2026-11-07) ———
                        var isEditing by remember { mutableStateOf(false) }

                        val catalog = listOf(
                            DrawerEntry("bookmarks", stringResource(R.string.menu_bookmarks), MaterialTheme.colorScheme.surfaceVariant, { StarLineIcon(tint = it) }) { onBookmarksClick(); dismissWithAnimation() },
                            DrawerEntry("history", stringResource(R.string.menu_history), MaterialTheme.colorScheme.surfaceVariant, { ClockIcon(tint = it) }) { onHistoryClick(); dismissWithAnimation() },
                            DrawerEntry("downloads", stringResource(R.string.menu_downloads), MaterialTheme.colorScheme.surfaceVariant, { DownloadIcon(tint = it) }) { onDownloadsClick(); dismissWithAnimation() },
                            DrawerEntry("settings", stringResource(R.string.menu_settings), MaterialTheme.colorScheme.surfaceVariant, { SettingsLineIcon(tint = it) }) { onSettingsClick(); dismissWithAnimation() },
                            DrawerEntry("home", stringResource(R.string.menu_home), MaterialTheme.colorScheme.surfaceVariant, { HomeLineIcon(tint = it) }) { onNavigate("yue://newtab"); dismissWithAnimation() },
                            DrawerEntry("incognito", stringResource(R.string.menu_incognito), MaterialTheme.colorScheme.surfaceVariant, { IncognitoIcon(tint = it) }) { onNewIncognitoTab(); dismissWithAnimation() },
                            DrawerEntry("addBookmark", stringResource(R.string.menu_add_bookmark), MaterialTheme.colorScheme.surfaceVariant, { BookmarkPlusIcon(tint = it) }) { onAddBookmarkClick(context); dismissWithAnimation() },
                            DrawerEntry("darkMode", stringResource(if (rememberedIsDarkMode) R.string.menu_light_mode else R.string.menu_dark_mode), MaterialTheme.colorScheme.surfaceVariant, { ThemeToggleIcon(isDark = rememberedIsDarkMode, tint = it) }) { onDarkModeToggle(!rememberedIsDarkMode); dismissWithAnimation() },
                            DrawerEntry("desktopSite", stringResource(R.string.menu_desktop), if (rememberedIsDesktopSite) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant, { MonitorIcon(tint = if (rememberedIsDesktopSite) MaterialTheme.colorScheme.primary else it) }) { onDesktopSiteToggle(!rememberedIsDesktopSite); dismissWithAnimation() },
                            DrawerEntry("translate", stringResource(R.string.menu_translate), MaterialTheme.colorScheme.surfaceVariant, { TerjemahIcon(tint = it) }) { onTranslateClick(); dismissWithAnimation() },
                            DrawerEntry("blockSelector", stringResource(R.string.menu_block), MaterialTheme.colorScheme.surfaceVariant, { BlockSelectorIcon(tint = MaterialTheme.colorScheme.error) }) { onBlockSelectorClick(); dismissWithAnimation() },
                            DrawerEntry("share", stringResource(R.string.menu_share), MaterialTheme.colorScheme.surfaceVariant, { ShareLineIcon(tint = it) }) { onShareUrl(currentUrl); dismissWithAnimation() },
                            DrawerEntry("saveOffline", stringResource(R.string.menu_save_offline), MaterialTheme.colorScheme.surfaceVariant, { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = it, modifier = Modifier.size(24.dp)) }) { onSaveOfflineClick(); dismissWithAnimation() },
                            DrawerEntry("offlinePages", stringResource(R.string.menu_offline_pages), MaterialTheme.colorScheme.surfaceVariant, { Icon(Icons.Default.OfflinePin, contentDescription = null, tint = it, modifier = Modifier.size(24.dp)) }) { onOfflinePagesClick(); dismissWithAnimation() },
                            DrawerEntry("findInPage", stringResource(R.string.menu_find_in_page), MaterialTheme.colorScheme.surfaceVariant, { SearchLineIcon(tint = it) }) { onFindInPageClick(); dismissWithAnimation() }
                        )
                        val byId = remember(catalog) { catalog.associateBy { it.id } }
                        val visibleIds = drawerItems.filter { byId.containsKey(it) }.ifEmpty { com.yue.browser.domain.model.DEFAULT_DRAWER_ITEMS }
                        val hiddenIds = catalog.map { it.id }.filter { it !in visibleIds }

                        // Header: judul + tombol Edit/Selesai
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.menu),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { isEditing = !isEditing }) {
                                Text(stringResource(if (isEditing) R.string.menu_done else R.string.menu_edit))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val columns = drawerGrid.substringBefore("x").toIntOrNull()?.coerceIn(3, 5) ?: 4
                        val rows = drawerGrid.substringAfter("x").toIntOrNull()?.coerceIn(1, 4) ?: 3

                        if (!isEditing) {
                            // Grid item sesuai urutan, pilihan user, dan susunan terpilih
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = (rows * 106).dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                visibleIds.chunked(columns).forEach { rowIds ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        rowIds.forEach { id ->
                                            val entry = byId.getValue(id)
                                            GridMenuItem(
                                                icon = { entry.icon(rememberedContentColor) },
                                                label = entry.label,
                                                textColor = rememberedTextLabelColor,
                                                containerColor = entry.containerColor,
                                                onClick = entry.onClick
                                            )
                                        }
                                        repeat(columns - rowIds.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        } else {
                            // ——— Mode edit: drag & drop urutan + pilih susunan + tap tampil/sembunyi ———
                            var dragId by remember { mutableStateOf<String?>(null) }
                            var dragPos by remember { mutableStateOf(Offset.Zero) }
                            var hoveredId by remember { mutableStateOf<String?>(null) }
                            var slotBounds by remember { mutableStateOf(mapOf<String, Rect>()) }
                            var itemRoot by remember { mutableStateOf(mapOf<String, Offset>()) }

                            Text(
                                text = stringResource(R.string.menu_grid),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("5x2", "4x2", "4x3", "5x4").forEach { preset ->
                                    val selected = preset == drawerGrid
                                    Box(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                1.dp,
                                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                MaterialTheme.shapes.small
                                            )
                                            .clickable { onDrawerGridChange(preset) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = preset.replace("x", "×"),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.menu_edit_hint_drag),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = (rows * 110).dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                visibleIds.chunked(columns).forEach { rowIds ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        rowIds.forEach { id ->
                                            val entry = byId.getValue(id)
                                            DrawerEditItem(
                                                entry = entry,
                                                isDragged = dragId == id,
                                                isHovered = hoveredId == id && dragId != null && dragId != id,
                                                ghostOffset = if (dragId == id) dragPos - (slotBounds[id]?.center ?: Offset.Zero) else Offset.Zero,
                                                onPositioned = { bounds, rootPos ->
                                                    slotBounds = slotBounds + (id to bounds)
                                                    itemRoot = itemRoot + (id to rootPos)
                                                },
                                                onTap = { onDrawerItemsChange(visibleIds - id) },
                                                onDragStart = { startLocal ->
                                                    dragId = id
                                                    dragPos = (itemRoot[id] ?: Offset.Zero) + startLocal
                                                },
                                                onDrag = { delta ->
                                                    dragPos += delta
                                                    hoveredId = slotBounds.entries
                                                        .firstOrNull { it.key != id && it.value.contains(dragPos) }?.key
                                                },
                                                onDragEnd = {
                                                    val target = hoveredId
                                                    val from = dragId
                                                    if (from != null && target != null && from != target) {
                                                        val list = visibleIds.toMutableList()
                                                        val fromIdx = list.indexOf(from)
                                                        val toIdx = list.indexOf(target)
                                                        if (fromIdx != -1 && toIdx != -1) {
                                                            list.removeAt(fromIdx)
                                                            list.add(toIdx, from)
                                                            onDrawerItemsChange(list)
                                                        }
                                                    }
                                                    dragId = null
                                                    hoveredId = null
                                                    dragPos = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    dragId = null
                                                    hoveredId = null
                                                    dragPos = Offset.Zero
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowIds.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            if (hiddenIds.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.menu_hidden),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    hiddenIds.forEach { id ->
                                        val entry = byId.getValue(id)
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .clickable { onDrawerItemsChange(visibleIds + id) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                entry.icon(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = entry.label,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
        }
    }
}

/** Satu item katalog drawer (id dipersist di settings.drawerItems). */
private data class DrawerEntry(
    val id: String,
    val label: String,
    val containerColor: Color,
    val icon: @Composable (Color) -> Unit,
    val onClick: () -> Unit
)

/** Baris mode edit drawer: ikon + label + ↑↓ (urutan) + tampil/sembunyi. */
@Composable
private fun DrawerEditItem(
    entry: DrawerEntry,
    isDragged: Boolean,
    isHovered: Boolean,
    ghostOffset: Offset,
    onPositioned: (Rect, Offset) -> Unit,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(vertical = 4.dp)
            .onGloballyPositioned { coords ->
                onPositioned(coords.boundsInRoot(), coords.positionInRoot())
            }
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                if (isDragged) {
                    translationX = ghostOffset.x
                    translationY = ghostOffset.y
                    scaleX = 1.1f
                    scaleY = 1.1f
                }
            }
            .pointerInput(entry.id, entry.label) {
                detectDragGesturesAfterLongPress(
                    onDragStart = onDragStart,
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = onDragCancel
                )
            }
            .clickable(onClick = onTap)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isHovered) 1.5.dp else 0.dp,
                    color = if (isHovered) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            entry.icon(MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = entry.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DragHandle(color: Color) {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .width(44.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
fun ListMenuItem(
    icon: @Composable () -> Unit,
    label: String,
    textColor: Color,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(modifier = androidx.compose.ui.Modifier.size(20.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            icon()
        }
        Spacer(modifier = androidx.compose.ui.Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = textColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            modifier = androidx.compose.ui.Modifier.weight(1f)
        )
        if (trailingContent != null) {
            Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
fun RowScope.GridMenuItem(
    icon: @Composable () -> Unit,
    label: String,
    textColor: Color,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(15.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = textColor.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
