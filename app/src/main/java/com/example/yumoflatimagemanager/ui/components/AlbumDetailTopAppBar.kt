package com.example.yumoflatimagemanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.view.WindowManager
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.data.SortDirection
import com.example.yumoflatimagemanager.data.SortType
import com.example.yumoflatimagemanager.ui.drawer.DrawerController

/**
 * 相册详情屏幕的顶部应用栏组件
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AlbumDetailTopAppBar(
    viewModel: MainViewModel,
    isAlbumDetailNearTop: Boolean,
    drawerController: DrawerController
) {
    // 获取选中的相册
    val selectedAlbum = viewModel.selectedAlbum
    // 是否处于选择模式
    val isSelectionMode = viewModel.isSelectionMode

    // 为文本和图标的颜色添加过渡动画，与主页面保持一致
    val contentColor by animateColorAsState(
        targetValue = if (isAlbumDetailNearTop) Color.Black else Color.White,
        animationSpec = tween(durationMillis = 220),
        label = "contentColor"
    )

    // 使用remember保存动画状态，避免组件重建导致动画重置
    val titleAnimationState = remember { mutableStateOf(isSelectionMode) }
    
    // 只在选择模式真正改变时更新动画状态
    LaunchedEffect(isSelectionMode) {
        titleAnimationState.value = isSelectionMode
    }

    TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = contentColor,
                actionIconContentColor = contentColor,
                navigationIconContentColor = contentColor
            ),
            title = {
                AnimatedContent(
                    targetState = titleAnimationState.value,
                    transitionSpec = { fadeIn(tween(200)) with fadeOut(tween(150)) },
                    label = "albumDetailTopBarTitle"
                ) { selection ->
                    if (selection) {
                        // 多选模式下显示选择数量，居中显示
                        Text(
                            text = "已选择 ${viewModel.selectedImages.size} 项", 
                            color = contentColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // 正常模式下显示相册名称
                        Text(
                            text = selectedAlbum?.name ?: "",
                            color = contentColor
                        )
                    }
                }
            },
            navigationIcon = {
                if (isSelectionMode) {
                    // 多选模式下显示取消按钮，字体放大
                    TextButton(onClick = { viewModel.toggleSelectionMode() }) {
                        Text(
                            "取消", 
                            color = contentColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    // 正常模式下显示返回按钮
                    IconButton(onClick = { viewModel.navigateToAlbums() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = contentColor
                        )
                    }
                }
            },
            actions = {
                if (isSelectionMode) {
                    // 多选模式下显示全选文本按钮
                    TextButton(onClick = { viewModel.selectAllImages() }) {
                        Text(
                            "全选", 
                            color = contentColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    // 正常模式下显示选择按钮和更多菜单
                    // 修改为方块勾图标表示多选功能
                    IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "选择",
                            tint = contentColor
                        )
                    }
                    
                    // 使用排序菜单辅助工具
                    AppBarMenuHelper.SortMenuButton(viewModel, contentColor)
                    
                    // 使用更多菜单辅助工具（移除标签管理菜单项）
                    AppBarMenuHelper.MoreMenuButton(
                        viewModel,
                        contentColor
                    )
                }
            }
        )
}