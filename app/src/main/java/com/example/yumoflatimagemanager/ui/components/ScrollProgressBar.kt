package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue

/**
 * 滚动进度条组件
 * 显示当前滚动位置的进度
 */
@Composable
fun ScrollProgressBar(
    scrollProgress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 3.dp,
    horizontalPadding: Dp = 16.dp,
    enabled: Boolean = true
) {
    // 只有当进度不为0或1时才显示动画，提高性能
    val animatedProgress by animateFloatAsState(
        targetValue = scrollProgress,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (scrollProgress == 0f || scrollProgress == 1f) 100 else 50
        ),
        label = "scrollProgressAnimation"
    )
    
    if (enabled) {
        Box(modifier = modifier.fillMaxWidth()) {
            // 背景进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(horizontal = horizontalPadding)
                    .background(backgroundColor)
            )
            
            // 前景进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(height)
                    .padding(start = horizontalPadding)
                    .background(color)
            )
        }
    }
}