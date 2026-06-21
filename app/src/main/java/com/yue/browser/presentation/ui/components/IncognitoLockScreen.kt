package com.yue.browser.presentation.ui.components

import com.yue.browser.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yue.browser.presentation.ui.tabswitcher.IncognitoIcon

@Composable
fun IncognitoLockScreen(
    isDarkModeActive: Boolean,
    showNoAuthBypassText: Boolean,
    onBiometricUnlock: () -> Unit,
    onOpenNormalTab: () -> Unit
) {
    val lockBg = if (isDarkModeActive) Color(0xFF000000) else Color(0xFFF5F5F5)
    val lockIconBg = if (isDarkModeActive) Color(0xFF1A1A1A) else Color(0xFFE8E8EC)
    val lockTitleText = if (isDarkModeActive) Color.White else Color(0xFF1A1A1A)
    val lockSubText = if (isDarkModeActive) Color.LightGray.copy(alpha = 0.8f) else Color(0xFF555555)
    val lockAccent = Color(0xFFFF002C)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lockBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                    .background(lockIconBg),
                contentAlignment = Alignment.Center
            ) {
                IncognitoIcon(
                    tint = lockAccent,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.browser_incognito_locked_title),
                color = lockTitleText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.browser_incognito_locked_subtitle),
                color = lockSubText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(lockAccent)
                    .clickable(onClick = onBiometricUnlock),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.browser_unlock),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (showNoAuthBypassText) {
                Text(
                    text = stringResource(R.string.browser_no_auth_bypass),
                    color = lockSubText.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.browser_open_normal_tab),
                color = lockAccent,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(onClick = onOpenNormalTab)
            )
        }
    }
}
