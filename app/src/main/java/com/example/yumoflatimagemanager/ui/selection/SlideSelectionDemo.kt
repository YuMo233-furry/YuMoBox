/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.ui.SelectionManager
import com.example.yumoflatimagemanager.ui.createSelectionManager

/**
 * 滑动选择演示组件
 * 展示基于PictureSelector逻辑的滑动选择功能
 */
@Composable
fun SlideSelectionDemo(
    images: List<ImageItem>,
    modifier: Modifier = Modifier
) {
    val selectionManager = remember { createSelectionManager() }
    val selectionManagerFacade = remember { createSelectionManagerFacade(selectionManager) }
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current.density
    
    // 选择状态
    var isSelectionActive by remember { mutableStateOf(false) }
    var selectionRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部控制栏
        @OptIn(ExperimentalMaterial3Api::class)
        androidx.compose.material3.TopAppBar(
            title = { Text("滑动选择演示") },
            actions = {
                Button(
                    onClick = { selectionManagerFacade.toggleSelectionMode() }
                ) {
                    Text(if (selectionManagerFacade.isSelectionMode) "退出选择" else "进入选择")
                }
            }
        )
        
        // 选择信息栏
        if (selectionManagerFacade.isSelectionMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选择 ${selectionManagerFacade.selectedImages.size} 张图片",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Row {
                        Button(
                            onClick = { selectionManagerFacade.selectAllImages(images) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("全选")
                        }
                        
                        Button(
                            onClick = { selectionManagerFacade.clearSelection() }
                        ) {
                            Text("清空")
                        }
                    }
                }
            }
        }
        
        // 图片网格
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 滑动选择已移至 ImageCard 的长按事件中启动
                // 参考 AlbumDetailGrid 的实现
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(images.size) { index ->
                    val image = images[index]
                    val isSelected = selectionManagerFacade.isImageSelected(image)
                    
                    ImageItemCard(
                        image = image,
                        isSelected = isSelected,
                        onClick = {
                            if (selectionManagerFacade.isSelectionMode) {
                                selectionManagerFacade.selectImage(image)
                            } else {
                                // 处理图片点击
                            }
                        },
                        onLongClick = {
                            if (!selectionManagerFacade.isSelectionMode) {
                                selectionManagerFacade.toggleSelectionMode()
                                selectionManagerFacade.selectImage(image)
                            }
                        }
                    )
                }
            }
            
            // 滑动选择覆盖层
            SlideSelectionOverlay(
                selectionRect = selectionRect,
                isActive = isSelectionActive,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 图片项目卡片
 */
@Composable
private fun ImageItemCard(
    image: ImageItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
        Card(
            modifier = modifier
                .aspectRatio(1f)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else Color.Transparent
                )
                .clickable { onClick() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongClick() }
                    )
                }
        ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 这里应该显示实际的图片
            // 暂时用占位符代替
            Text(
                text = "图片 ${image.id}",
                style = MaterialTheme.typography.bodySmall
            )
            
            // 选择指示器
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * 根据位置查找项目索引
 */
private fun findItemAtPosition(
    position: Offset,
    spanCount: Int,
    images: List<ImageItem>,
    spacing: Float,
    topBarHeight: Float,
    gridState: LazyGridState
): Int? {
    // 这里需要根据实际的网格布局来计算
    // 暂时返回一个简单的实现
    val itemSize = 100f // 假设每个项目的大小
    val row = ((position.y + topBarHeight) / (itemSize + spacing)).toInt()
    val col = (position.x / (itemSize + spacing)).toInt()
    val index = row * spanCount + col
    
    return if (index >= 0 && index < images.size) index else null
}
