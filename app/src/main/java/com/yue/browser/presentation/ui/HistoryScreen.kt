package com.yue.browser.presentation.ui

import com.yue.browser.R
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.HistoryItem
import com.yue.browser.presentation.*
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val historyItems by viewModel.history.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(historyItems, searchQuery) {
        if (searchQuery.isBlank()) historyItems
        else historyItems.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    val context = LocalContext.current
    val groupedItems = remember(filteredItems, context) {
        groupByDate(filteredItems, context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (historyItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text(stringResource(R.string.history_clear_all), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
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
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.history_search_hint), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            if (historyItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.history_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_no_match),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groupedItems.forEach { (label, items) ->
                        item {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(items) { item ->
                            HistoryItem(
                                title = item.title,
                                url = item.url,
                                timestamp = item.timestamp,
                                onClick = {
                                    viewModel.loadUriInActiveTab(item.url)
                                    onBack()
                                },
                                onDelete = { viewModel.removeHistory(item.url) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    title: String,
    url: String,
    timestamp: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = url,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatRelativeTime(timestamp, context),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long, context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> context.getString(R.string.history_just_now)
        diff < 3_600_000 -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            context.getString(R.string.history_minutes_ago, minutes.toInt())
        }
        diff < 86_400_000 -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            context.getString(R.string.history_hours_ago, hours.toInt())
        }
        diff < 172_800_000 -> context.getString(R.string.history_yesterday)
        diff < 604_800_000 -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            context.getString(R.string.history_days_ago, days.toInt())
        }
        else -> {
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

private sealed class HistoryGroupKey {
    object Today : HistoryGroupKey()
    object Yesterday : HistoryGroupKey()
    object ThisWeek : HistoryGroupKey()
    data class MonthYear(val timestamp: Long, val monthYearStr: String) : HistoryGroupKey()
}

private fun compareKeys(k1: HistoryGroupKey, k2: HistoryGroupKey): Int {
    val priority = fun(k: HistoryGroupKey): Int = when (k) {
        is HistoryGroupKey.Today -> 0
        is HistoryGroupKey.Yesterday -> 1
        is HistoryGroupKey.ThisWeek -> 2
        is HistoryGroupKey.MonthYear -> 3
    }
    val p1 = priority(k1)
    val p2 = priority(k2)
    if (p1 != p2) return p1.compareTo(p2)

    if (k1 is HistoryGroupKey.MonthYear && k2 is HistoryGroupKey.MonthYear) {
        return k2.timestamp.compareTo(k1.timestamp)
    }
    return 0
}

private fun groupByDate(items: List<HistoryItem>, context: Context): List<Pair<String, List<HistoryItem>>> {
    if (items.isEmpty()) return emptyList()

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val yesterdayStart = todayStart - 86_400_000L
    val weekStart = todayStart - 7 * 86_400_000L

    val groups = mutableMapOf<HistoryGroupKey, MutableList<HistoryItem>>()

    items.forEach { item ->
        val key = when {
            item.timestamp >= todayStart -> HistoryGroupKey.Today
            item.timestamp >= yesterdayStart -> HistoryGroupKey.Yesterday
            item.timestamp >= weekStart -> HistoryGroupKey.ThisWeek
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
                val monthFormat = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                val monthYearStr = monthFormat.format(Date(item.timestamp))
                val monthStart = cal.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                HistoryGroupKey.MonthYear(monthStart, monthYearStr)
            }
        }
        groups.getOrPut(key) { mutableListOf() }.add(item)
    }

    val sortedEntries = groups.entries.sortedWith { entry1, entry2 ->
        compareKeys(entry1.key, entry2.key)
    }

    return sortedEntries.map { (key, items) ->
        val label = when (key) {
            is HistoryGroupKey.Today -> context.getString(R.string.history_today)
            is HistoryGroupKey.Yesterday -> context.getString(R.string.history_yesterday)
            is HistoryGroupKey.ThisWeek -> context.getString(R.string.history_this_week)
            is HistoryGroupKey.MonthYear -> key.monthYearStr
        }
        label to items
    }
}
