/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.R
import android.net.Uri
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.example.yumoflatimagemanager.media.SimpleImageEngine
import com.example.yumoflatimagemanager.media.VideoThumbnailHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import com.example.yumoflatimagemanager.utils.VibrationHelper
import coil.size.Precision
import coil.size.Scale

/**
 * 计算两点之间的距离
 */
private fun Offset.getDistance(): Float {
    return sqrt(x * x + y * y)
}

/**
 * 简化的图片卡片组件
 * 保持原有UI和动画效果，但简化图片加载逻辑
 * 参考PictureSelector的简洁实现
 */
@Composable
fun SimplifiedImageCard(
    image: ImageItem,
    onImageClick: (ImageItem) -> Unit,
    onImageLongClick: (ImageItem) -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    columnCount: Int = 4, // 支持动态列数
    shouldLoadImage: Boolean = true,
    onPreviewClick: ((ImageItem) -> Unit)? = null, // 预览按钮点击回调
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 性能优化：缓存计算结果，减少重组
    val imageSize = remember(columnCount) {
        SimpleImageEngine.getGridImageSize(columnCount)
    }
    val coroutineScope = rememberCoroutineScope()
    
    // 简化的状态管理 - 只保留必要的状态
    var imageLoadError by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableStateOf("0:00") }
    
    // 预览按钮点击标志，用于防止事件冒泡到父级
    var isPreviewButtonClicked by remember { mutableStateOf(false) }
    
    // 自定义手势检测，支持长按后立即拖动
    val pointerModifier = modifier
        .fillMaxWidth()
        .forceSquare() // 使用强制正方形 Modifier，确保容器在测量时就是 1:1
        .pointerInput(isSelectionMode) { // 根据选择模式改变行为
            awaitPointerEventScope {
                while (true) {
                    // 等待按下事件（不消费事件，让其他处理器也能接收）
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = System.currentTimeMillis()
                    var isLongPressTriggered = false
                    var wasMoved = false
                    
                    // 启动长按检测协程
                    val longPressJob = coroutineScope.launch {
                        delay(400L) // 长按阈值400ms，更快响应
                        if (!wasMoved) {
                            isLongPressTriggered = true
                            println("🔔 SimplifiedImageCard - 长按触发，触发震动反馈")
                            // 立即触发强烈的震动反馈，确保用户能感知到进入多选模式
                            VibrationHelper.performLongPressVibration(context)
                            // 长按触发 - 在这里触发长按回调
                            onImageLongClick(image)
                        }
                    }
                    
                    // 监听后续事件
                    var isUp = false
                    while (!isUp) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        
                        if (change.pressed) {
                            // 检测是否移动
                            val moveDistance = (change.position - down.position).getDistance()
                            if (moveDistance > 10f) { // 移动超过10px
                                wasMoved = true
                                if (isLongPressTriggered) {
                                    // 已经触发了长按，进入了拖动选择模式
                                    // 不消费事件，让 detectSlideSelection 处理
                                    println("🎯 SimplifiedImageCard - 长按后移动，不消费事件")
                                } else {
                                    // 移动太快，取消长按
                                    longPressJob.cancel()
                                }
                            }
                        } else {
                            // 手指抬起
                            isUp = true
                            longPressJob.cancel()
                            
                            // 如果没有移动且没有触发长按，则是点击
                            val pressDuration = System.currentTimeMillis() - downTime
                            if (!wasMoved && !isLongPressTriggered && pressDuration < 400) {
                                // 检查是否是预览按钮点击，避免误触发图片选中
                                if (!isPreviewButtonClicked) {
                                    // 只有点击才消费事件
                                    change.consume()
                                    onImageClick(image)
                                }
                            }
                        }
                    }
                }
            }
        }
    
    // 选中状态的动画效果 - 保持原有动画
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.25f else 0f, 
        label = "selAlpha"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f, 
        label = "selScale"
    )

    Box(modifier = pointerModifier.scale(animatedScale)) {
        // 简化的图片加载逻辑
        if (image.isVideo) {
            // 视频处理 - 保持原有功能
            VideoContent(
                image = image,
                shouldLoadImage = shouldLoadImage,
                videoDuration = videoDuration,
                onDurationUpdate = { videoDuration = it },
                onError = { imageLoadError = true }
            )
        } else {
            // 图片处理 - 使用简化的加载逻辑
            ImageContent(
                image = image,
                shouldLoadImage = shouldLoadImage,
                columnCount = columnCount,
                onError = { imageLoadError = true }
            )
        }
        
        // 选中高亮遮罩 - 保持原有UI效果
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = animatedAlpha))
        )
        
        // 选中状态指示器 - 保持原有UI
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选择",
                tint = Color.Blue,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.White, shape = CircleShape)
            )
        }
        
        // 视频时长显示 - 保持原有功能
        if (image.isVideo && !imageLoadError) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(2.dp)
            ) {
                Text(
                    text = videoDuration,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
        
        // 视频图标 - 保持原有UI
        if (image.isVideo) {
            Icon(
                Icons.Filled.Menu, // 使用临时图标替代视频图标
                contentDescription = "视频",
                tint = Color.Red,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
            )
        }
        
        // 多选模式下的预览按钮 - 显示在右下角，使用方块形状
        if (isSelectionMode && onPreviewClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp) // 方块形状，圆角4dp
                    )
                    .pointerInput(Unit) {
                        // 使用 detectTapGestures 来完全拦截点击事件
                        detectTapGestures(
                            onPress = {
                                // 立即设置标志，防止父级响应点击
                                isPreviewButtonClicked = true
                                
                                // 尝试等待释放或取消
                                val success = tryAwaitRelease()
                                if (success) {
                                    // 点击成功，触发预览
                                    onPreviewClick(image)
                                }
                                
                                // 延迟重置标志，确保父级已完成事件处理
                                coroutineScope.launch {
                                    delay(100L)
                                    isPreviewButtonClicked = false
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CustomIcons.Preview,
                    contentDescription = "预览",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 图片内容组件
 * 使用简化的图片加载逻辑
 */
@Composable
private fun ImageContent(
    image: ImageItem,
    shouldLoadImage: Boolean,
    columnCount: Int,
    onError: () -> Unit
) {
    if (!shouldLoadImage) {
        // 占位符
        PlaceholderContent()
    } else {
        // 使用缓存的图片尺寸，减少计算
        val imageSize = remember(columnCount) {
            SimpleImageEngine.getGridImageSize(columnCount)
        }
        
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image.uri)
                .size(imageSize, imageSize)
                .precision(Precision.EXACT) // 确保精确尺寸，避免模糊
                .scale(Scale.FILL) // 填充模式
                // 移除 allowHardware(false)，使用硬件加速提升性能
                .memoryCacheKey("${image.uri}_${imageSize}") // 明确缓存键，避免错误缓存
                .diskCacheKey("${image.uri}_${imageSize}") // 明确磁盘缓存键
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .crossfade(false) // 移除淡入动画，减少重组
                .memoryCachePolicy(CachePolicy.ENABLED) // 启用内存缓存
                .diskCachePolicy(CachePolicy.ENABLED) // 启用磁盘缓存
                .build()
        )
        
        Image(
            painter = painter,
            contentDescription = image.name ?: "图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * 视频内容组件
 * 保持原有的视频处理功能
 */
@Composable
private fun VideoContent(
    image: ImageItem,
    shouldLoadImage: Boolean,
    videoDuration: String,
    onDurationUpdate: (String) -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fallbackThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(shouldLoadImage) }
    
    // 视频缩略图加载
    LaunchedEffect(image.uri, shouldLoadImage) {
        if (!shouldLoadImage) {
            isLoading = false
            return@LaunchedEffect
        }
        
        if (fallbackThumbnail != null) {
            isLoading = false
            return@LaunchedEffect
        }
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val durationMs = VideoThumbnailHelper.getVideoDuration(context, image.uri)
                val bitmap = VideoThumbnailHelper.getVideoThumbnail(context, image.uri)
                
                withContext(Dispatchers.Main) {
                    onDurationUpdate(VideoThumbnailHelper.formatDuration(durationMs))
                    if (bitmap != null && !bitmap.isRecycled) {
                        fallbackThumbnail = bitmap
                        isLoading = false
                    } else {
                        onError()
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError()
                    isLoading = false
                }
            }
        }
    }
    
    when {
        fallbackThumbnail != null && !fallbackThumbnail!!.isRecycled -> {
            Image(
                bitmap = fallbackThumbnail!!.asImageBitmap(),
                contentDescription = image.name ?: "视频",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        shouldLoadImage && isLoading -> {
            LoadingPlaceholder()
        }
        else -> {
            PlaceholderContent()
        }
    }
}

/**
 * 占位符内容
 */
@Composable
private fun PlaceholderContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "占位符",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

/**
 * 加载中占位符
 */
@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.VideoFile,
            contentDescription = "加载中",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

