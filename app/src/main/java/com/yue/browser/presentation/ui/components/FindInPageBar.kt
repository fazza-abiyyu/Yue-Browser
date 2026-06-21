package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.presentation.BrowserViewModel

@Composable
internal fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    result: BrowserViewModel.FindInPageResult?,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme
    val surfaceColor = color.surface
    val onSurfaceColor = color.onSurface
    val onSurfaceVariantColor = color.onSurfaceVariant
    val surfaceVariantColor = color.surfaceVariant

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val barShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .shadow(8.dp, barShape)
            .clip(barShape)
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = if (isSystemDark) color.primary.copy(alpha = 0.6f) else color.outlineVariant,
                shape = barShape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = onSurfaceVariantColor
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = onSurfaceColor
                ),
                cursorBrush = SolidColor(onSurfaceColor),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.find_in_page_hint),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = onSurfaceVariantColor
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (result != null) {
                Text(
                    text = "${result.activeMatchOrdinal + 1}/${result.numberOfMatches}",
                    fontSize = 15.sp,
                    color = onSurfaceColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.back), modifier = Modifier.size(18.dp), tint = onSurfaceVariantColor)
                }
                IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.forward), modifier = Modifier.size(18.dp), tint = onSurfaceVariantColor)
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceVariantColor)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2715",
                    fontSize = 16.sp,
                    color = onSurfaceVariantColor
                )
            }
        }
    }
}
