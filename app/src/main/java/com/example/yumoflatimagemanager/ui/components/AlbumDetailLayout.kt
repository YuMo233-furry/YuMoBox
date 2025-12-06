package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.ui.screens.AlbumDetailScreen
import com.example.yumoflatimagemanager.ui.components.PhotoViewerActivity
import com.example.yumoflatimagemanager.ui.drawer.DrawerController

/**
 * 相册详情页布局组件
 */
@Composable
fun AlbumDetailLayout(
    viewModel: MainViewModel,
    isAlbumDetailNearTop: Boolean,
    drawerController: DrawerController
) {
    // 获取当前Activity实例
    val activity = LocalContext.current as Activity
    
    // 收集过滤后的图片 StateFlow
    val filteredImages by viewModel.filteredImagesFlow.collectAsState()
    val isFiltering by viewModel.isFiltering.collectAsState()
    
    // 判断是否使用过滤
    val hasActiveFilters = viewModel.activeTagFilterIds.isNotEmpty() || viewModel.excludedTagIds.isNotEmpty()
    
    // 选择要显示的图片列表
    val displayImages = if (hasActiveFilters) filteredImages else viewModel.images
    
    // 使用Box容器嵌套AlbumDetailScreen和渐变背景层
    Box(modifier = Modifier.fillMaxSize()) {
        // 相册详情屏幕
        if (viewModel.selectedAlbum != null) {
                AlbumDetailScreen(
                viewModel = viewModel,
                images = displayImages,
                onImageClick = { image -> 
                    // 在非选择模式下，使用PhotoViewerActivity查看图片
                    if (!viewModel.isSelectionMode) {
                        // 获取当前相册中的所有图片
                        val allImages = displayImages
                        // 查找被点击图片的位置
                        val position = allImages.indexOf(image)
                        if (position >= 0) {
                            // 转换为Uri列表
                            val uris = allImages.map { it.uri }
                            // 启动我们自定义的PhotoViewerActivity
                            PhotoViewerActivity.start(activity, uris, position)
                        }
                    } else {
                        viewModel.onImageClick(image)
                    }
                },
                onImageLongClick = { image -> viewModel.onImageLongClick(image) },
                onImageDragSelect = { range -> viewModel.onImageDragSelect(range) },
                isSelectionMode = viewModel.isSelectionMode,
                selectedImages = viewModel.selectedImages,
                drawerController = drawerController,
                onScrollPositionChange = { viewModel.updateScrollPosition(it) }
            )
        }
        
        // 添加调试日志，跟踪isAlbumDetailNearTop状态
        LaunchedEffect(isAlbumDetailNearTop) {
            println("渐变层调试 - isAlbumDetailNearTop: $isAlbumDetailNearTop, 渐变层应显示: ${!isAlbumDetailNearTop}")
        }
        
        // 首次加载标志位
        val isFirstLoad = remember { mutableStateOf(true) }
        
        // 添加渐变背景层，并使用AnimatedVisibility为其添加过渡动画
        // 调整渐变层的位置和属性，确保它能正确显示
        AnimatedVisibility(
            visible = !isAlbumDetailNearTop, // 当不在顶部时显示渐变层
            enter = if (isFirstLoad.value) {
                // 首次加载时，不使用动画
                fadeIn(animationSpec = tween(durationMillis = 0))
            } else {
                // 后续状态变化时，使用动画
                fadeIn(animationSpec = tween(durationMillis = 300))
            },
            exit = fadeOut(animationSpec = tween(durationMillis = 300)),
            modifier = Modifier
        ) {
            // 动画完成后，标记为非首次加载
            LaunchedEffect(Unit) {
                isFirstLoad.value = false
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),  // 降低透明度使渐变更明显
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 800f  // 增加渐变范围
                        )
                    )
            ) {}
        }
    }
}