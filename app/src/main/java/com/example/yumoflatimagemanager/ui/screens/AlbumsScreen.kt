package com.example.yumoflatimagemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.AlbumType
import com.example.yumoflatimagemanager.ui.components.AlbumCard
import com.example.yumoflatimagemanager.ui.components.CreateAlbumCard
import com.example.yumoflatimagemanager.ui.components.RenameAlbumDialog
import com.example.yumoflatimagemanager.ui.components.SelectionBottomBar
import com.example.yumoflatimagemanager.ui.components.PrivacyButtonState
import com.example.yumoflatimagemanager.ui.components.SelectionPageType

/**
 * 相册列表屏幕组件
 */
@Composable
fun AlbumsScreen(
    mainAlbums: List<Album>,
    customAlbums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onCreateAlbumClick: () -> Unit,
    viewModel: MainViewModel,
    isSelectionMode: Boolean = false,
    onAlbumSelect: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Boolean,
    isAlbumSelected: (Album) -> Boolean,
    modifier: Modifier = Modifier
) {
    // 存储是否显示重命名对话框的状态
    val showRenameDialog = remember { mutableStateOf(false) }
    // 存储要重命名的相册
    val albumToRename = remember { mutableStateOf<Album?>(null) }
    val totalAlbumCount = mainAlbums.size + customAlbums.size
    
    // 创建LazyGridState并恢复滚动位置
    val gridState = rememberLazyGridState()
    
    // 恢复滚动位置
    LaunchedEffect(Unit) {
        val (firstIndex, firstOffset) = viewModel.loadGridScroll()
        if (firstIndex > 0 || firstOffset > 0) {
            gridState.scrollToItem(firstIndex, firstOffset)
        }
    }
    
    // 保存滚动位置
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        viewModel.saveGridScroll(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
    }
    
    // 计算隐私按钮状态
    val privacyButtonState = remember { derivedStateOf {
        val selectedAlbums = viewModel.getSelectedAlbumsList()
        
        // 如果没有选中相册或选中的包含主要相册，则禁用
        if (selectedAlbums.isEmpty() || selectedAlbums.any { it.type == AlbumType.MAIN } || selectedAlbums.any { it.type == AlbumType.SYSTEM }) {
            return@derivedStateOf PrivacyButtonState.DISABLED
        }
        
        // 检查是否全部为私密或全部为公开
        val allPrivate = selectedAlbums.all { it.isPrivate }
        val allPublic = selectedAlbums.all { !it.isPrivate }
        
        if (allPrivate) {
            PrivacyButtonState.REMOVE_PRIVATE
        } else if (allPublic) {
            PrivacyButtonState.SET_TO_PRIVATE
        } else {
            // 混合状态，禁用
            PrivacyButtonState.DISABLED
        }
    }}
    
    
    Scaffold(
        bottomBar = {
            // 使用AnimatedVisibility为底部栏添加过渡动画，与相册详情页保持一致
            AnimatedVisibility(
                visible = isSelectionMode,
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
                    onShare = { /* 主页面不需要分享功能 */ },
                    onAddTo = { /* 相册页面无添加到行为，留空 */ },
                    onDelete = { viewModel.deleteSelectedAlbums() },
                    onTag = { /* 相册页面无打标签行为，留空 */ },
                    onMore = { /* 实现更多操作 */ },
                    onRename = { 
                        // 获取选中的单个自定义相册
                        val album = viewModel.getSingleSelectedCustomAlbum()
                        if (album != null) {
                            albumToRename.value = album
                            showRenameDialog.value = true
                        }
                    },
                    canRename = viewModel.canRenameSelectedAlbum(),
                    onTogglePrivacy = { viewModel.toggleSelectedAlbumsPrivacy() },
                    privacyButtonState = privacyButtonState.value,
                    pageType = SelectionPageType.ALBUM_LIST
                )
            }
            
            // 显示重命名对话框
            if (showRenameDialog.value && albumToRename.value != null) {
                RenameAlbumDialog(
                    album = albumToRename.value!!,
                    onRename = { newName ->
                        viewModel.renameAlbum(albumToRename.value!!, newName)
                        showRenameDialog.value = false
                    },
                    onCancel = { showRenameDialog.value = false }
                )
            }
        },
        topBar = {
            // 标签管理功能已移至侧边栏菜单
        }
    ) { paddingValues ->
        // 使用单一的LazyVerticalGrid来避免滚动组件嵌套问题
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            content = {
            // 添加主要相册标题
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "主要相册",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                )
            }
            
            // 主要相册内容
            items(mainAlbums) {
                AlbumCard(
                    album = it,
                    onAlbumClick = onAlbumClick,
                    onAlbumSelect = onAlbumSelect,
                    onAlbumLongClick = onAlbumLongClick,
                    isSelectionMode = isSelectionMode,
                    isSelected = isAlbumSelected(it)
                )
            }
            
            // 添加我的相册标题
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "我的相册",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
                )
            }
            
            // 我的相册内容
            items(customAlbums) {
                AlbumCard(
                    album = it,
                    onAlbumClick = onAlbumClick,
                    onAlbumSelect = onAlbumSelect,
                    onAlbumLongClick = onAlbumLongClick,
                    isSelectionMode = isSelectionMode,
                    isSelected = isAlbumSelected(it)
                )
            }
            
            // 只在非选择模式下显示创建相册卡片
            if (!isSelectionMode) {
                item {
                    CreateAlbumCard {
                        onCreateAlbumClick()
                    }
                }
            }
        }
    )
}
}