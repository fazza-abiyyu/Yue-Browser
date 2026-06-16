package com.yue.browser.presentation.ui.components

import com.yue.browser.R
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
    isDarkMode: Boolean,
    searchEngineUrl: String = "https://www.google.com/search?q=",
    isPrivate: Boolean = false
) {
    val sanitizedInput = try {
        if (initialInput == "yue://newtab" || initialInput.isBlank()) "" else initialInput
    } catch (e: Exception) {
        android.util.Log.e("SearchOverlay", "Error sanitizing initialInput", e)
        ""
    }
    val safeStartLen = try {
        sanitizedInput.length.coerceIn(0, 2048)
    } catch (e: Exception) {
        0
    }
    var searchInput by remember {
        mutableStateOf(
            try {
                TextFieldValue(
                    text = sanitizedInput,
                    selection = TextRange(0, safeStartLen)
                )
            } catch (e: Exception) {
                android.util.Log.e("SearchOverlay", "Error creating TextFieldValue", e)
                TextFieldValue(text = "")
            }
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

    val customSearchEngineName = stringResource(R.string.search_engine_custom)
    val searchEngineName = remember(searchEngineUrl) {
        when {
            searchEngineUrl.contains("google.com") -> "Google"
            searchEngineUrl.contains("bing.com") -> "Bing"
            searchEngineUrl.contains("duckduckgo.com") -> "DuckDuckGo"
            searchEngineUrl.contains("yahoo.com") -> "Yahoo"
            else -> customSearchEngineName
        }
    }

    LaunchedEffect(searchInput.text, history, bookmarks) {
        try {
            val query = searchInput.text
            if (query.isNotBlank()) {
                val list = mutableListOf<SearchSuggestion>()
                val isProbablyUrl = query.contains(".") && !query.contains(" ")

                if (isProbablyUrl) {
                    list.add(SearchSuggestion(title = query, url = query, type = SuggestionType.SEARCH))
                } else {
                    list.add(SearchSuggestion(title = query, url = "$searchEngineName Search", type = SuggestionType.SEARCH))
                }

                // 1. History matches filtered by query (real data)
                val queryLower = query.lowercase(java.util.Locale.ROOT)
                val filteredHistory = try {
                    history
                        .filter {
                            it.title.lowercase(java.util.Locale.ROOT).contains(queryLower) ||
                            it.url.lowercase(java.util.Locale.ROOT).contains(queryLower)
                        }
                        .sortedWith(compareByDescending<HistoryItem> { it.visitCount }.thenByDescending { it.timestamp })
                        .take(5)
                } catch (e: Exception) {
                    emptyList()
                }

                filteredHistory.forEach { item ->
                    list.add(SearchSuggestion(item.title, item.url, SuggestionType.HISTORY, item.visitCount))
                }

                // 2. Bookmarks match (if any)
                val filteredBookmarks = try {
                    bookmarks
                        .filter {
                            it.title.lowercase(java.util.Locale.ROOT).contains(queryLower) ||
                            it.url.lowercase(java.util.Locale.ROOT).contains(queryLower)
                        }
                        .take(3)
                } catch (e: Exception) {
                    emptyList()
                }
                filteredBookmarks.forEach { item ->
                    list.add(SearchSuggestion(item.title, item.url, SuggestionType.BOOKMARK, 0))
                }

                // 3. Fetch autocomplete asynchronously
                try {
                    val fetched = fetchGoogleSuggestions(query)
                    fetched.take(6).forEach { sugg ->
                        if (sugg != query && list.size < 10) {
                            list.add(SearchSuggestion(title = sugg, url = sugg, type = SuggestionType.AUTOCOMPLETE))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SearchOverlay", "Error fetching Google suggestions", e)
                }
                suggestions = list
            } else {
                // Empty state: handled separately (2-section rendering below), clear suggestions
                suggestions = emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchOverlay", "Error in suggestions LaunchedEffect", e)
            suggestions = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            android.util.Log.e("SearchOverlay", "Error requesting focus", e)
        }
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
                    contentDescription = stringResource(R.string.search_icon),
                    tint = placeholderColor
                )
            }
            
            AndroidView(
                factory = { ctx ->
                    object : android.widget.EditText(ctx) {
                        override fun onDetachedFromWindow() {
                            try {
                                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.hideSoftInputFromWindow(windowToken, 0)
                            } catch (e: Exception) {
                                android.util.Log.e("SearchOverlay", "Error hiding keyboard on detach", e)
                            }
                            super.onDetachedFromWindow()
                        }
                    }.apply {
                        background = null
                        maxLines = 1
                        isSingleLine = true
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO

                        setTextColor(if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                        textSize = 16f
                        hint = ctx.getString(R.string.search_or_type_url)
                        setHintTextColor(if (isDarkMode) android.graphics.Color.parseColor("#80FFFFFF") else android.graphics.Color.parseColor("#80000000"))

                        if (isPrivate) {
                            privateImeOptions = "incognito,com.google.android.inputmethod.latin.noPersonalizedLearning,com.microsoft.inputmethod.noPersonalizedLearning,com.microsoft.keyboard.incognitoMode=true"
                            imeOptions = imeOptions or 0x1000000 // IME_FLAG_NO_PERSONALIZED_LEARNING
                        }

                        // Set initial text
                        if (sanitizedInput.isNotEmpty()) {
                            setText(sanitizedInput)
                        }

                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                val newText = s?.toString() ?: ""
                                if (searchInput.text != newText) {
                                    searchInput = TextFieldValue(newText)
                                }
                            }
                            override fun afterTextChanged(s: android.text.Editable?) {}
                        })

                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                                try {
                                    val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                    imm.hideSoftInputFromWindow(windowToken, 0)
                                } catch (e: Exception) {
                                    android.util.Log.e("SearchOverlay", "Error hiding keyboard on action go", e)
                                }
                                onSearch(text.toString())
                                true
                            } else {
                                false
                            }
                        }

                        post {
                            try {
                                requestFocus()
                                // SELECT ALL text (block all) jika ada text, biar user langsung ketik bisa replace URL
                                if (text.isNotEmpty()) {
                                    setSelection(0, text.length)
                                }
                                val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                            } catch (e: Exception) {
                                android.util.Log.e("SearchOverlay", "Error in post focus/select", e)
                            }
                        }
                    }
                },
                update = { editText ->
                    if (editText.text.toString() != searchInput.text) {
                        editText.setText(searchInput.text)
                        editText.setSelection(0, searchInput.text.length) // SELECT ALL (block all), bukan cuma di akhir
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            if (searchInput.text.isNotEmpty()) {
                IconButton(onClick = { searchInput = TextFieldValue("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.clear),
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
                            text = stringResource(R.string.search_empty_title),
                            color = placeholderColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.search_empty_subtitle),
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
                                text = stringResource(R.string.search_section_history),
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
                                        keyboardController?.hide()
                                        onSearch(item.url)
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
                                        contentDescription = stringResource(R.string.remove_from_history),
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
                                text = stringResource(R.string.search_section_bookmark),
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
                                        keyboardController?.hide()
                                        onSearch(item.url)
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
                                        contentDescription = stringResource(R.string.open),
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
                                    keyboardController?.hide()
                                    onSearch(if (sugg.type == SuggestionType.AUTOCOMPLETE || sugg.type == SuggestionType.SEARCH) sugg.title else sugg.url)
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
                                        contentDescription = stringResource(R.string.remove_from_history),
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
                                        contentDescription = stringResource(R.string.fill),
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
                        text = stringResource(R.string.search_no_results),
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
    android.util.Log.d("YueUrl", "formatUrlOrQuery input='$input' trimmed='$trimmed' searchEngine=$searchEngineUrl")
    if (trimmed.isEmpty()) {
        val fallback = searchEngineUrl.substringBefore("?").ifEmpty { "https://www.google.com" }
        android.util.Log.d("YueUrl", "empty input -> $fallback")
        return fallback
    }

    val isProbablyUrl = trimmed.contains(".") && !trimmed.contains(" ")
    val result = if (isProbablyUrl) {
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    } else {
        "${searchEngineUrl}${URLEncoder.encode(trimmed, "UTF-8")}"
    }
    android.util.Log.d("YueUrl", "result=$result isProbablyUrl=$isProbablyUrl")
    return result
}
