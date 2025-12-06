package com.example.yumoflatimagemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.ui.components.ScrollProgressBar
import com.example.yumoflatimagemanager.ui.components.SelectionBottomBar
import com.example.yumoflatimagemanager.ui.components.PrivacyButtonState
import com.example.yumoflatimagemanager.ui.components.SelectionPageType
import com.example.yumoflatimagemanager.ui.selection.SelectionManagerFacade
import com.example.yumoflatimagemanager.ui.createSelectionManager
import com.example.yumoflatimagemanager.ui.selection.createSelectionManagerFacade
import com.example.yumoflatimagemanager.ui.drawer.DrawerController
import kotlinx.coroutines.launch

/**
 * 相册详情屏幕组件
 */
@Composable
fun AlbumDetailScreen(
    viewModel: MainViewModel,
    images: List<ImageItem>,
    onImageClick: (ImageItem) -> Unit,
    onImageLongClick: (ImageItem) -> Unit,
    onImageDragSelect: (List<ImageItem>) -> Unit,
    isSelectionMode: Boolean,
    selectedImages: List<ImageItem>,
    drawerController: DrawerController,
    modifier: Modifier = Modifier,
    onScrollPositionChange: (Boolean) -> Unit = {}
) {
    // 初始化选择管理器
    val baseSelectionManager = remember { createSelectionManager() }
    val selectionManager = remember { createSelectionManagerFacade(baseSelectionManager) }
    
    // 同步外部状态到内部状态
    LaunchedEffect(isSelectionMode, selectedImages) {
        // 同步选择模式状态
        if (selectionManager.isSelectionMode != isSelectionMode) {
            selectionManager.toggleSelectionMode()
        }
        // 同步选中的图片
        val currentSelected = selectionManager.selectedImages
        if (currentSelected != selectedImages) {
            selectionManager.clearSelection()
            if (selectedImages.isNotEmpty()) {
                selectionManager.selectImages(selectedImages)
            }
        }
    }
    
    // 新的滑动选择系统已集成到 SelectionManagerFacade 中
    
    // 处理系统返回键
    BackHandler(enabled = selectionManager.isSelectionMode) {
        // 调用外部ViewModel的toggleSelectionMode，确保状态同步
        viewModel.toggleSelectionMode()
        // 清空选中的图片
        selectionManager.clearSelection()
        // 同步状态到外部，确保上端菜单正确更新
        onImageDragSelect(selectionManager.selectedImages)
    }
    
    // 内部状态管理
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacingPx = with(density) { 2.dp.toPx() }
    val topBarHeightPx = with(density) { 120.dp.toPx() }
    
    // 滚动状态
    val scrollProgress = remember { mutableStateOf(0f) }
    val lazyGridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        // 不再恢复滚动位置，每次都从顶部开始
        // 恢复已激活标签
        viewModel.restoreTagFilters()
    }
    val isNearTop = remember { mutableStateOf(true) }
    
    // 计算隐私按钮状态
    val privacyButtonState = remember { derivedStateOf {
        if (selectionManager.selectedImages.isEmpty()) {
            PrivacyButtonState.HIDDEN
        } else {
            PrivacyButtonState.SET_TO_PRIVATE
        }
    }}
    
    // 监听滚动位置变化
    LaunchedEffect(lazyGridState.firstVisibleItemIndex, lazyGridState.firstVisibleItemScrollOffset) {
        val newIsNearTop = lazyGridState.firstVisibleItemIndex == 0 && 
                          lazyGridState.firstVisibleItemScrollOffset < 100
        // 只在状态真正改变时触发回调，避免频繁更新
        if (newIsNearTop != isNearTop.value) {
            isNearTop.value = newIsNearTop
            onScrollPositionChange(newIsNearTop)
        }
        
        // 更新滚动进度
        val totalItems = images.size
        val visibleItems = lazyGridState.layoutInfo.visibleItemsInfo.size
        if (totalItems > 0) {
            scrollProgress.value = (lazyGridState.firstVisibleItemIndex + visibleItems / 2f) / totalItems
        }
        // 不再持久化滚动位置
    }
    
    // 页面加载时发送初始状态
    LaunchedEffect(Unit) {
        onScrollPositionChange(isNearTop.value)
    }
    
    // 监听选择模式变化，重置拖拽状态
    LaunchedEffect(selectionManager.isSelectionMode) {
        if (!selectionManager.isSelectionMode) {
            onImageDragSelect(emptyList())
        }
    }
    
    Scaffold(
        bottomBar = {
            // 使用AnimatedVisibility为底部栏添加过渡动画，与主页面保持一致
            AnimatedVisibility(
                visible = selectionManager.isSelectionMode,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 200)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 150)
                )
            ) {
                SelectionBottomBar(
                    onShare = { viewModel.shareSelectedImages() },
                    onAddTo = { viewModel.showAlbumSelection() },
                    onDelete = { viewModel.deleteSelectedImages() },
                    onTag = { viewModel.showTagSelectionDialog() },
                    onMore = { /* 更多功能 */ },
                    privacyButtonState = privacyButtonState.value,
                    pageType = SelectionPageType.ALBUM_DETAIL,
                    viewModel = viewModel
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = modifier.fillMaxSize()) {
                // 滑动进度条
                ScrollProgressBar(
                    scrollProgress = scrollProgress.value,
                    enabled = images.isNotEmpty()
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 标签管理功能已移至侧边栏菜单

                    // 图片网格
                    AlbumDetailGrid(
                        images = images,
                        selectionManager = selectionManager,
                        lazyGridState = lazyGridState,
                        gridColumnCount = viewModel.gridColumnCount,
                        spacingPx = spacingPx,
                        topBarHeightPx = topBarHeightPx,
                        onImageClick = onImageClick,
                        onImageLongClick = onImageLongClick,
                        onImageDragSelect = { selectedImages ->
                            onImageDragSelect(selectedImages)
                        },
                        onShowResetConfirmation = { 
                            drawerController.openTagManager()
                            viewModel.showResetConfirmationDialog()
                        }
                    )
                }
            }
            
            // 左下角的标签抽屉按钮 - 在非选择模式下显示
            if (!selectionManager.isSelectionMode) {
                FloatingActionButton(
                    onClick = { drawerController.openTagManager() },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp), // 方形圆角背景
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), // 透明背景
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "标签管理",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}