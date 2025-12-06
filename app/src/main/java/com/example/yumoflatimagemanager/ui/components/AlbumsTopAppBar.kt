package com.example.yumoflatimagemanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalAnimationApi

import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.data.SortType
import com.example.yumoflatimagemanager.data.SortDirection

/**
 * 相册列表屏幕的顶部应用栏组件
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AlbumsTopAppBar(
    viewModel: MainViewModel
) {
    // 排序菜单状态
    var showSortMenu by remember { mutableStateOf(false) }
    val isSelectionMode = viewModel.isSelectionMode
    val animatedContentColor by animateColorAsState(
        targetValue = if (isSelectionMode) Color.White else MaterialTheme.colorScheme.onPrimary,
        animationSpec = tween(durationMillis = 220),
        label = "albumsTopBarContentColor"
    )

    TopAppBar(
        navigationIcon = {
            if (isSelectionMode) {
                // 多选模式下显示取消按钮
                TextButton(onClick = { viewModel.toggleSelectionMode() }) {
                    Text(
                        "取消",
                        color = animatedContentColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        title = {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = { fadeIn(tween(200)) with fadeOut(tween(150)) },
                label = "albumsTopBarTitle"
            ) { selection ->
                if (selection) {
                    Text(
                        text = "已选择 ${viewModel.selectedAlbums.size} 项",
                        color = animatedContentColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        "图片管理",
                        color = animatedContentColor
                    )
                }
            }
        },
        actions = {
            if (isSelectionMode) {
                // 多选模式下显示全选文本按钮
                TextButton(onClick = { viewModel.selectAllAlbums() }) {
                    Text(
                        "全选",
                        color = animatedContentColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                // 正常模式下显示选择按钮和更多菜单
                // 添加选择按钮
                IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "选择",
                        tint = animatedContentColor
                    )
                }
                
                // 排序按钮
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序", tint = animatedContentColor)
                }
                
                // 排序菜单
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("按名称排序") },
                        onClick = { 
                            viewModel.setAlbumsSortConfig(SortConfig(SortType.NAME, SortDirection.ASCENDING))
                            showSortMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("按修改时间排序") },
                        onClick = { 
                            viewModel.setAlbumsSortConfig(SortConfig(SortType.MODIFY_TIME, SortDirection.DESCENDING))
                            showSortMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("按图片数量排序") },
                        onClick = { 
                            viewModel.setAlbumsSortConfig(SortConfig(SortType.IMAGE_COUNT, SortDirection.DESCENDING))
                            showSortMenu = false 
                        }
                    )
                }
                
                // 使用AppBarMenuHelper的更多菜单按钮，这样会包含安全模式开关
                AppBarMenuHelper.MoreMenuButton(
                    viewModel = viewModel,
                    contentColor = animatedContentColor,
                    // 添加自定义菜单项
                    AppBarMenuHelper.MenuItem(
                        label = "刷新",
                        onClick = { viewModel.refreshMedia() }
                    ),
                    AppBarMenuHelper.MenuItem(
                        label = "关于",
                        onClick = { /* 实现关于页面逻辑 */ }
                    )
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = animatedContentColor,
            actionIconContentColor = animatedContentColor,
            navigationIconContentColor = animatedContentColor
        )
    )
}