package com.yue.browser.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.domain.model.BookmarkItem
import com.yue.browser.domain.model.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SuggestionType { HISTORY, AUTOCOMPLETE, SEARCH, BOOKMARK }

data class SearchSuggestion(
    val title: String,
    val url: String,
    val type: SuggestionType,
    val visitCount: Int = 0
)

suspend fun fetchGoogleSuggestions(query: String): List<String> {
    if (query.isBlank()) return emptyList()
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&q=${URLEncoder.encode(query, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            val response = connection.inputStream.bufferedReader().readText()
            val jsonArray = JSONArray(response)
            if (jsonArray.length() > 1) {
                val suggestionsArray = jsonArray.getJSONArray(1)
                val list = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length()) {
                    list.add(suggestionsArray.getString(i))
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchOverlay(
    initialInput: String,
    history: List<HistoryItem> = emptyList(),
    bookmarks: List<BookmarkItem> = emptyList(),
    onRemoveHistory: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    isDarkMode: Boolean
) {
    val startInput = if (initialInput == "yue://newtab" || initialInput.isBlank()) "" else initialInput
    var searchInput by remember { 
        mutableStateOf(
            TextFieldValue(
                text = startInput,
                selection = TextRange(0, startInput.length)
            )
        ) 
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val urlColor = if (isDarkMode) Color(0xFFF28B82) else Color(0xFFD93025) // Red/Orange for URL
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }

    LaunchedEffect(searchInput.text, history, bookmarks) {
        val query = searchInput.text
        if (query.isNotBlank()) {
            val list = mutableListOf<SearchSuggestion>()
            val isProbablyUrl = query.contains(".") && !query.contains(" ")
            
            if (isProbablyUrl) {
                list.add(SearchSuggestion(title = query, url = query, type = SuggestionType.SEARCH))
            } else {
                list.add(SearchSuggestion(title = query, url = "Google Search", type = SuggestionType.SEARCH))
            }

            // 1. History matches filtered by query (real data)
            val queryLower = query.lowercase(java.util.Locale.ROOT)
            val filteredHistory = history
                .filter {
                    it.title.lowercase(java.util.Locale.ROOT).contains(queryLower) ||
                    it.url.lowercase(java.util.Locale.ROOT).contains(queryLower)
                }
                .sortedWith(compareByDescending<HistoryItem> { it.visitCount }.thenByDescending { it.timestamp })
                .take(5)

            filteredHistory.forEach { item ->
                list.add(SearchSuggestion(item.title, item.url, SuggestionType.HISTORY, item.visitCount))
            }

            // 2. Bookmarks match (if any)
            val filteredBookmarks = bookmarks
                .filter {
                    it.title.lowercase(java.util.Locale.ROOT).contains(queryLower) ||
                    it.url.lowercase(java.util.Locale.ROOT).contains(queryLower)
                }
                .take(3)
            filteredBookmarks.forEach { item ->
                list.add(SearchSuggestion(item.title, item.url, SuggestionType.BOOKMARK, 0))
            }

            // 3. Fetch autocomplete asynchronously
            val fetched = fetchGoogleSuggestions(query)
            fetched.take(6).forEach { sugg ->
                if (sugg != query && list.size < 10) {
                    list.add(SearchSuggestion(title = sugg, url = sugg, type = SuggestionType.AUTOCOMPLETE))
                }
            }
            suggestions = list
        } else {
            // Empty state: handled separately (2-section rendering below), clear suggestions
            suggestions = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // Search Bar (Brave style: no outline, clear background, full width with bottom border)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(backgroundColor)
        ) {
            IconButton(onClick = {
                // If it's empty, dismiss. If not, just search? No, back button dismisses.
                onDismiss()
            }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search icon",
                    tint = placeholderColor
                )
            }
            
            TextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search or type URL", color = placeholderColor, fontSize = 16.sp) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        onSearch(searchInput.text)
                        keyboardController?.hide()
                    }
                ),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            if (searchInput.text.isNotEmpty()) {
                IconButton(onClick = { searchInput = TextFieldValue("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint = placeholderColor
                    )
                }
            }
        }

        Divider(color = dividerColor, thickness = 1.dp)

        // === EMPTY STATE: 2 sections (Riwayat 4 terbaru + Bookmark) ===
        if (searchInput.text.isBlank()) {
            val recentHistory = history
                .filter { it.url.startsWith("http") }
                .sortedByDescending { it.timestamp }
                .take(4)
            val bookmarkList = bookmarks

            if (recentHistory.isEmpty() && bookmarkList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Mulai browsing",
                            color = placeholderColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Riwayat & bookmark akan muncul disini",
                            color = placeholderColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // --- Section 1: Riwayat (4 terbaru by timestamp) ---
                    if (recentHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Riwayat",
                                    color = placeholderColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        items(recentHistory) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSearch(item.url)
                                        keyboardController?.hide()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.History,
                                        contentDescription = null,
                                        tint = placeholderColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = textColor,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.url,
                                        color = urlColor,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onRemoveHistory(item.url) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Remove from history",
                                        tint = placeholderColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // --- Section 2: Bookmark ---
                    if (bookmarkList.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bookmark",
                                    color = placeholderColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        items(bookmarkList) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSearch(item.url)
                                        keyboardController?.hide()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.BookmarkBorder,
                                        contentDescription = null,
                                        tint = placeholderColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = textColor,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.url,
                                        color = urlColor,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        onSearch(item.url)
                                        keyboardController?.hide()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowForward,
                                        contentDescription = "Open",
                                        tint = placeholderColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // === QUERY STATE: suggestions list (history match + autocomplete) ===
            if (suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestions) { sugg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSearch(if (sugg.type == SuggestionType.AUTOCOMPLETE || sugg.type == SuggestionType.SEARCH) sugg.title else sugg.url)
                                    keyboardController?.hide()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Leading Icon
                            val iconVector = when (sugg.type) {
                                SuggestionType.HISTORY -> Icons.Outlined.History
                                SuggestionType.BOOKMARK -> Icons.Outlined.BookmarkBorder
                                else -> Icons.Outlined.Search
                            }
                            val iconBg = Color.Transparent
                            val iconTint = placeholderColor

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Texts
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sugg.title,
                                    color = textColor,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (sugg.type == SuggestionType.HISTORY || sugg.type == SuggestionType.BOOKMARK) {
                                    Text(
                                        text = sugg.url,
                                        color = urlColor,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Trailing Actions
                            if (sugg.type == SuggestionType.HISTORY) {
                                IconButton(
                                    onClick = { onRemoveHistory(sugg.url) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Remove from history",
                                        tint = placeholderColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { searchInput = TextFieldValue(text = sugg.title, selection = TextRange(sugg.title.length)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowForward,
                                        contentDescription = "Fill",
                                        tint = placeholderColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // No results for current query
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada hasil",
                        color = placeholderColor,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

fun formatUrlOrQuery(input: String, searchEngineUrl: String = "https://www.google.com/search?q="): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return "https://www.google.com"

    val isProbablyUrl = trimmed.contains(".") && !trimmed.contains(" ")
    return if (isProbablyUrl) {
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    } else {
        "${searchEngineUrl}${URLEncoder.encode(trimmed, "UTF-8")}"
    }
}
