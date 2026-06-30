package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.BrowserTab
import com.yue.browser.presentation.ui.getLanguageName
import com.yue.browser.presentation.ui.TranslateIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TopTranslateBar(
    activeTab: BrowserTab,
    isStartPage: Boolean,
    showTabSwitcher: Boolean,
    showHistoryScreen: Boolean,
    showBookmarksScreen: Boolean,
    showSettingsScreen: Boolean,
    showDownloadsScreen: Boolean,
    showAdblockFiltersScreen: Boolean,
    isTranslating: Boolean,
    isBottomBarVisible: Boolean,
    isDarkMode: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val showTopTranslateBar = activeTab.isTranslated && !isStartPage && !showTabSwitcher && !showHistoryScreen && !showBookmarksScreen && !showSettingsScreen && !showDownloadsScreen && !showAdblockFiltersScreen
    AnimatedVisibility(
        visible = showTopTranslateBar && isBottomBarVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 420.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(if (activeTab.isPrivate) (if (isDarkMode) Color(0xFF000000) else Color(0xFFF5F5F5)) else MaterialTheme.colorScheme.surface)
                .border(1.dp, if (activeTab.isPrivate) (if (isDarkMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color(0xFFD8D8DC)) else (if (isDarkMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (activeTab.progress < 100 || isTranslating) {
                        CircularProgressIndicator(
                            color = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        TranslateIcon(
                            tint = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeTab.progress < 100 || isTranslating) {
                            stringResource(R.string.browser_translating, getLanguageName(activeTab.translationTarget))
                        } else {
                            stringResource(R.string.browser_translated, getLanguageName(activeTab.translationTarget))
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (activeTab.isPrivate) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.browser_translate_retry),
                            tint = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = if (activeTab.isPrivate) (if (isDarkMode) Color.White else Color(0xFF1A1A1C)) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomTranslateBar(
    showTranslateBar: Boolean,
    activeTab: BrowserTab,
    isStartPage: Boolean,
    showTabSwitcher: Boolean,
    showHistoryScreen: Boolean,
    showBookmarksScreen: Boolean,
    showSettingsScreen: Boolean,
    showDownloadsScreen: Boolean,
    showAdblockFiltersScreen: Boolean,
    isBottomBarVisible: Boolean,
    isDarkMode: Boolean,
    sourceLanguage: String,
    targetLanguage: String,
    showSourceLanguageMenu: Boolean,
    showTargetLanguageMenu: Boolean,
    isTranslating: Boolean,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onSourceLanguageMenuChange: (Boolean) -> Unit,
    onTargetLanguageMenuChange: (Boolean) -> Unit,
    onTranslate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val showBottomTranslateBar = showTranslateBar && !activeTab.isTranslated && !isStartPage && !showTabSwitcher && !showHistoryScreen && !showBookmarksScreen && !showSettingsScreen && !showDownloadsScreen && !showAdblockFiltersScreen
    AnimatedVisibility(
        visible = showBottomTranslateBar && isBottomBarVisible && activeTab.progress >= 100,
        enter = fadeIn() + scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
        exit = fadeOut() + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(bottom = 140.dp)) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp)
                    .widthIn(max = 420.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDarkMode) Color(0xFF000000) else Color.White)
                    .border(1.dp, if (isDarkMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val languagesList = listOf(
                            "in" to stringResource(R.string.lang_id), "en" to stringResource(R.string.lang_en), "zh" to stringResource(R.string.lang_zh),
                            "ja" to stringResource(R.string.lang_ja), "ko" to stringResource(R.string.lang_ko), "fr" to stringResource(R.string.lang_fr),
                            "de" to stringResource(R.string.lang_de), "es" to stringResource(R.string.lang_es), "pt" to stringResource(R.string.lang_pt),
                            "ar" to stringResource(R.string.lang_ar), "hi" to stringResource(R.string.lang_hi)
                        )

                        Text(
                            text = getLanguageName(sourceLanguage),
                            color = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { onSourceLanguageMenuChange(true) }
                                .padding(horizontal = 2.dp)
                        )
                        DropdownMenu(
                            expanded = showSourceLanguageMenu,
                            onDismissRequest = { onSourceLanguageMenuChange(false) },
                            modifier = Modifier
                                .background(if (isDarkMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface)
                                .border(1.dp, if (isDarkMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                        ) {
                            val sourceLanguagesList = listOf("auto" to stringResource(R.string.lang_auto)) + languagesList
                            sourceLanguagesList.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = name,
                                            color = if (isDarkMode) Color(0xFFE3E3E3) else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (sourceLanguage == code) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onSourceLanguageChange(code)
                                        onSourceLanguageMenuChange(false)
                                    }
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = if (isDarkMode) Color(0xFF9AA0A6) else Color(0xFF4D6172),
                            modifier = Modifier.size(14.dp).padding(horizontal = 2.dp)
                        )

                        Text(
                            text = getLanguageName(targetLanguage),
                            color = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { onTargetLanguageMenuChange(true) }
                                .padding(horizontal = 2.dp)
                        )
                        DropdownMenu(
                            expanded = showTargetLanguageMenu,
                            onDismissRequest = { onTargetLanguageMenuChange(false) },
                            modifier = Modifier
                                .background(if (isDarkMode) Color(0xFF000000) else MaterialTheme.colorScheme.surface)
                                .border(1.dp, if (isDarkMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                        ) {
                            languagesList.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = name,
                                            color = if (isDarkMode) Color(0xFFE3E3E3) else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (targetLanguage == code) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        onTargetLanguageChange(code)
                                        onTargetLanguageMenuChange(false)
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onTranslate,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab.isPrivate) Color(0xFFFF002C) else Color(0xFFEC4899)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.browser_translate_button),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = if (isDarkMode) Color(0xFF9AA0A6) else Color(0xFF4D6172),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
