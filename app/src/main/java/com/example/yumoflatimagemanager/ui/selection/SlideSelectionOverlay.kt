/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 滑动选择覆盖层
 * 提供视觉反馈，显示选择区域
 */
@Composable
fun SlideSelectionOverlay(
    selectionRect: Rect?,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive || selectionRect == null) return
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        drawSelectionOverlay(selectionRect)
    }
}

/**
 * 绘制选择覆盖层
 */
private fun DrawScope.drawSelectionOverlay(rect: Rect) {
    // 绘制半透明背景
    drawRect(
        color = Color.Blue.copy(alpha = 0.1f),
        topLeft = rect.topLeft,
        size = rect.size
    )
    
    // 绘制边框
    drawRect(
        color = Color.Blue,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 2.dp.toPx())
    )
    
    // 绘制角落指示器
    drawCornerIndicators(rect)
}

/**
 * 绘制角落指示器
 */
private fun DrawScope.drawCornerIndicators(rect: Rect) {
    val cornerSize = 8.dp.toPx()
    val strokeWidth = 3.dp.toPx()
    
    // 左上角
    drawLine(
        color = Color.Blue,
        start = Offset(rect.left, rect.top + cornerSize),
        end = Offset(rect.left, rect.top),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = Color.Blue,
        start = Offset(rect.left, rect.top),
        end = Offset(rect.left + cornerSize, rect.top),
        strokeWidth = strokeWidth
    )
    
    // 右上角
    drawLine(
        color = Color.Blue,
        start = Offset(rect.right - cornerSize, rect.top),
        end = Offset(rect.right, rect.top),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = Color.Blue,
        start = Offset(rect.right, rect.top),
        end = Offset(rect.right, rect.top + cornerSize),
        strokeWidth = strokeWidth
    )
    
    // 左下角
    drawLine(
        color = Color.Blue,
        start = Offset(rect.left, rect.bottom - cornerSize),
        end = Offset(rect.left, rect.bottom),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = Color.Blue,
        start = Offset(rect.left, rect.bottom),
        end = Offset(rect.left + cornerSize, rect.bottom),
        strokeWidth = strokeWidth
    )
    
    // 右下角
    drawLine(
        color = Color.Blue,
        start = Offset(rect.right - cornerSize, rect.bottom),
        end = Offset(rect.right, rect.bottom),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = Color.Blue,
        start = Offset(rect.right, rect.bottom),
        end = Offset(rect.right, rect.bottom - cornerSize),
        strokeWidth = strokeWidth
    )
}

/**
 * 计算选择矩形
 */
fun calculateSelectionRect(
    startPosition: Offset,
    endPosition: Offset
): Rect {
    val left = minOf(startPosition.x, endPosition.x)
    val top = minOf(startPosition.y, endPosition.y)
    val right = maxOf(startPosition.x, endPosition.x)
    val bottom = maxOf(startPosition.y, endPosition.y)
    
    return Rect(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}

/**
 * 计算项目在屏幕上的矩形区域
 */
fun calculateItemRect(
    itemIndex: Int,
    gridColumnCount: Int,
    itemSize: Float,
    spacing: Float,
    topBarHeight: Float,
    gridOffset: Offset = Offset.Zero
): Rect {
    val row = itemIndex / gridColumnCount
    val col = itemIndex % gridColumnCount
    
    val x = col * (itemSize + spacing) + gridOffset.x
    val y = row * (itemSize + spacing) + gridOffset.y - topBarHeight
    
    return Rect(
        left = x,
        top = y,
        right = x + itemSize,
        bottom = y + itemSize
    )
}
