package com.yue.browser.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.yue.browser.R

@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val moonColor = if (isDarkTheme) Color(0xFFDB2777) else Color(0xFFEC4899) // PinkDark / PinkPrimary
    
    val moonScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f)
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stylized "Yue" branding logo matching the homepage design
        Text(
            text = "Yue",
            fontSize = 80.sp,
            fontWeight = FontWeight.Light,
            color = moonColor,
            letterSpacing = (-1).sp,
            modifier = Modifier.graphicsLayer { 
                scaleX = moonScale 
                scaleY = moonScale 
            }
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        
        // Welcome text
        Text(
            text = stringResource(R.string.welcome_title),
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = textAlpha),
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
        
        // Tagline
        Text(
            text = stringResource(R.string.welcome_tagline),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f * textAlpha),
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(48.dp))
        
        // Start button
        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer { 
                    alpha = textAlpha 
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        ) {
            Text(
                text = stringResource(R.string.welcome_start_button),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
    }
}

// Preview for development
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun WelcomeScreenPreview() {
    com.yue.browser.presentation.theme.YueTheme(isDarkMode = false) {
        WelcomeScreen(onStartClick = {})
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "WelcomeScreen Dark")
fun WelcomeScreenDarkPreview() {
    com.yue.browser.presentation.theme.YueTheme(isDarkMode = true) {
        WelcomeScreen(onStartClick = {})
    }
}