package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.yumoflatimagemanager.R
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.media.SimplifiedImageLoaderHelper
import com.example.yumoflatimagemanager.media.SimpleImageEngine
import com.example.yumoflatimagemanager.media.MediaManager
import com.example.yumoflatimagemanager.media.VideoThumbnailHelper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CheckCircle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * 相册卡片组件
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    album: Album,
    onAlbumClick: (Album) -> Unit,
    onAlbumSelect: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    val context = LocalContext.current
    
    // 图片加载状态
    var imageLoadError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var fallbackThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var imageRequestCompleted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    
    // 处理点击事件
    val handleClick = {
        if (isSelectionMode) {
            onAlbumSelect(album)
        } else {
            onAlbumClick(album)
        }
    }
    
    // 处理长按事件
    val handleLongPress = {
        val consumed = onAlbumLongClick(album)
        if (consumed) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        // 忽略返回值，因为combinedClickable不使用它
    }
    
    // 为私密相册添加清晰的红色边框
    Box(
        modifier = Modifier
            .padding(if (album.isPrivate) 2.dp else 0.dp)
            .background(
                brush = if (album.isPrivate) {
                    // 使用明确的水平和垂直渐变来创建清晰的边框效果
                    Brush.sweepGradient(
                        colors = listOf(
                            Color.Red.copy(alpha = 0.4f),
                            Color.Red.copy(alpha = 0.6f),
                            Color.Red.copy(alpha = 0.4f)
                        )
                    )
                } else {
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Transparent))
                },
                shape = RoundedCornerShape(5.dp)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .combinedClickable(
                    onClick = handleClick,
                    onLongClick = handleLongPress
                ),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = if (isSelected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            } else {
                CardDefaults.cardColors()
            }
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageLoadError || (album.coverUri == null)) {
                // 使用资源图片作为默认和错误占位符
                Image(
                    painter = painterResource(id = album.coverImage),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (album.id == "all") {
                // 对于"全部图片"相册，显示前四张图片作为封面 - 使用稳定的缓存机制
                var allImagesPreview by remember { mutableStateOf<List<ImageItem>?>(null) }
                
                // 使用稳定的key，避免因refreshKey变化导致重新加载
                LaunchedEffect(Unit) {
                    // 首先尝试从缓存获取
                    val cachedImages = SimplifiedImageLoaderHelper.getAllAlbumCoverCache(context)
                    if (cachedImages != null) {
                        allImagesPreview = cachedImages
                    } else {
                        // 缓存不存在时异步加载
                        allImagesPreview = withContext(Dispatchers.IO) {
                            MediaManager.loadImagesFromDevice(context)
                        }
                    }
                }
                
                // 创建网格布局显示前四张图片
                val images = allImagesPreview
                if (images != null && images.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            // 第一张图片
                            if (images.isNotEmpty()) {
                                Box(modifier = Modifier.weight(1f).padding(1.dp)) {
                                    val painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(context)
                                            .data(images[0].uri)
                                            .build()
                                    )
                                    Image(
                                        painter = painter,
                                        contentDescription = images[0].name ?: "图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).background(Color.Gray))
                            }
                            
                            // 第二张图片
                            if (images.size > 1) {
                                Box(modifier = Modifier.weight(1f).padding(1.dp)) {
                                    val painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(context)
                                            .data(images[1].uri)
                                            .build()
                                    )
                                    Image(
                                        painter = painter,
                                        contentDescription = images[1].name ?: "图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).background(Color.Gray))
                            }
                        }
                        
                        // 第二行：第三和第四张图片
                        Row(modifier = Modifier.weight(1f)) {
                            // 第三张图片
                            if (images.size > 2) {
                                Box(modifier = Modifier.weight(1f).padding(1.dp)) {
                                    val painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(context)
                                            .data(images[2].uri)
                                            .build()
                                    )
                                    Image(
                                        painter = painter,
                                        contentDescription = images[2].name ?: "图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).background(Color.Gray))
                            }
                            
                            // 第四张图片
                            if (images.size > 3) {
                                Box(modifier = Modifier.weight(1f).padding(1.dp)) {
                                    val painter = rememberAsyncImagePainter(
                                        ImageRequest.Builder(context)
                                            .data(images[3].uri)
                                            .build()
                                    )
                                    Image(
                                        painter = painter,
                                        contentDescription = images[3].name ?: "图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).background(Color.Gray))
                            }
                        }
                    }
                } else {
                    // 加载中或加载失败时显示默认图片
                    Image(
                        painter = painterResource(id = album.coverImage),
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // 使用实际图片URI，添加错误处理和视频缩略图特殊处理
                if (album.name == "视频") {
                    // 对于视频专辑，添加异步加载备用缩略图的逻辑
                    LaunchedEffect(album.coverUri) {
                        coroutineScope.launch(Dispatchers.IO) {
                            val bitmap = VideoThumbnailHelper.getVideoThumbnail(context, album.coverUri!!)
                            if (bitmap != null) {
                                withContext(Dispatchers.Main) {
                                    fallbackThumbnail = bitmap
                                    imageLoadError = false
                                    isLoading = false
                                }
                            } else {
                                if (isLoading) {
                                    withContext(Dispatchers.Main) {
                                        imageLoadError = true
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 对于普通图片专辑，添加加载超时处理
                    LaunchedEffect(album.coverUri) {
                        delay(5000) // 5秒后如果仍在加载，则视为加载失败
                        if (isLoading && !imageRequestCompleted) {
                            imageLoadError = true
                            isLoading = false
                        }
                    }
                }
                
                val imageRequest = remember(album.coverUri) {
                    if (album.name == "视频") {
                        // 对于视频专辑，使用视频缩略图专用处理
                        album.coverUri?.let {
                            VideoThumbnailHelper.createVideoImageRequest(context, it)
                        }
                    } else {
                        // 普通图片专辑
                        album.coverUri?.let {
                            SimpleImageEngine.createOptimizedImageRequest(
                                context = context,
                                uri = it,
                                isThumbnail = true
                            )
                        }
                    }
                }
                
                imageRequest?.let {
                    val painter = rememberAsyncImagePainter(
                        it,
                        onError = {
                            // 只有当没有备用缩略图时才显示错误
                            if (fallbackThumbnail == null) {
                                imageLoadError = true
                            }
                            isLoading = false
                            imageRequestCompleted = true
                        },
                        onSuccess = {
                            isLoading = false
                            imageRequestCompleted = true
                        }
                    )
                    
                    // 优先使用Coil加载，如果有备用缩略图且Coil加载失败则使用备用缩略图
                    if (fallbackThumbnail != null && album.name == "视频") {
                        // 对于视频，如果有备用缩略图则使用它
                        Image(
                            bitmap = fallbackThumbnail!!.asImageBitmap(),
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!imageLoadError) {
                        // 显示Coil加载的图片
                        Image(
                            painter = painter,
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // 如果图片加载失败或仍在加载中，显示加载指示器或错误占位图
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = album.name, 
                        color = Color.White, 
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.count} 个项目", 
                        color = Color.White, 
                        fontSize = 12.sp
                    )
                }
            }
            if (album.name == "视频") {
                Icon(
                    Icons.Default.Menu, // 使用默认图标表示视频
                    contentDescription = "视频",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(4.dp)
                        .size(32.dp)
                )
            }
            
            // 显示选中状态
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.White.copy(alpha = 0.8f), shape = RoundedCornerShape(50))
                        .padding(2.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "已选中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }    
        }    
    }
}
    }
}