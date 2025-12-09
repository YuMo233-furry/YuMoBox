package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.data.SortDirection
import com.example.yumoflatimagemanager.data.SortType
import com.example.yumoflatimagemanager.secure.SecureModeManager
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.example.yumoflatimagemanager.ui.screens.Screen

/**
 * AppBar菜单辅助工具类，提供统一的菜单实现，便于在不同组件中复用
 */
object AppBarMenuHelper {
    
    /**
     * 排序按钮及其下拉菜单组件
     * @param viewModel 主视图模型
     * @param contentColor 内容颜色
     */
    @Composable
    fun SortMenuButton(viewModel: MainViewModel, contentColor: Color) {
        var showSortSubMenu by remember { mutableStateOf(false) }
        val selectedAlbum = viewModel.selectedAlbum
        
        Box {
            IconButton(onClick = { showSortSubMenu = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "排序",
                    tint = contentColor
                )
            }
            
            // 排序子菜单
            DropdownMenu(
                expanded = showSortSubMenu,
                onDismissRequest = { showSortSubMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("按拍摄时间") },
                    onClick = {
                        selectedAlbum?.let {
                            val newSortConfig = SortConfig(SortType.CAPTURE_TIME, it.sortConfig.direction)
                            viewModel.updateAlbumSortConfig(it, newSortConfig)
                        }
                        showSortSubMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("按修改时间") },
                    onClick = {
                        selectedAlbum?.let {
                            val newSortConfig = SortConfig(SortType.MODIFY_TIME, it.sortConfig.direction)
                            viewModel.updateAlbumSortConfig(it, newSortConfig)
                        }
                        showSortSubMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("按创建时间") },
                    onClick = {
                        selectedAlbum?.let {
                            val newSortConfig = SortConfig(SortType.CREATE_TIME, it.sortConfig.direction)
                            viewModel.updateAlbumSortConfig(it, newSortConfig)
                        }
                        showSortSubMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("按名称") },
                    onClick = {
                        selectedAlbum?.let {
                            val newSortConfig = SortConfig(SortType.NAME, it.sortConfig.direction)
                            viewModel.updateAlbumSortConfig(it, newSortConfig)
                        }
                        showSortSubMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("按大小") },
                    onClick = {
                        selectedAlbum?.let {
                            val newSortConfig = SortConfig(SortType.SIZE, it.sortConfig.direction)
                            viewModel.updateAlbumSortConfig(it, newSortConfig)
                        }
                        showSortSubMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("排序方向") },
                    onClick = {
                        selectedAlbum?.let {
                            val newDirection = if (it.sortConfig.direction == SortDirection.ASCENDING) {
                                SortDirection.DESCENDING
                            } else {
                                SortDirection.ASCENDING
                            }
                            val newSortConfig = SortConfig(
                                it.sortConfig.type,
                                newDirection
                            )
                            viewModel.updateAlbumSortConfig(it, newSortConfig)
                        }
                        showSortSubMenu = false
                    }
                )
            }
        }
    }
    
    /**
     * 更多菜单按钮及其下拉菜单组件
     * @param viewModel 主视图模型
     * @param contentColor 内容颜色
     * @param customMenuItems 自定义菜单项列表
     */
    @Composable
    fun MoreMenuButton(
        viewModel: MainViewModel,
        contentColor: Color,
        vararg customMenuItems: MenuItem
    ) {
        var showMoreMenu by remember { mutableStateOf(false) }
        var showGridColumnsDialog by remember { mutableStateOf(false) }
        var currentGridColumns by remember { mutableStateOf(3) }
        var showSecureModeInfoDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val orientation = configuration.orientation
        // 通过当前屏幕判断是否在相册详情页，避免 selectedAlbum 残留导致主页面使用相册配置
        val isInAlbumDetail = viewModel.currentScreen is Screen.AlbumDetail
        val isSecureModeEnabled = remember {
            mutableStateOf(SecureModeManager.isSecureModeEnabled(context))
        }
        
        // 根据是否在相册详情页选择当前值
        LaunchedEffect(isInAlbumDetail, orientation) {
            currentGridColumns = if (isInAlbumDetail) {
                viewModel.gridColumnCount
            } else {
                viewModel.albumsGridColumnCount
            }
        }
        
        Box {
            IconButton(onClick = { showMoreMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = contentColor
                )
            }
            
            // 更多菜单
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                // 添加网格列数菜单项
                DropdownMenuItem(
                    text = { Text("网格列数") },
                    onClick = {
                        currentGridColumns = if (isInAlbumDetail) viewModel.gridColumnCount else viewModel.albumsGridColumnCount
                        showMoreMenu = false
                        showGridColumnsDialog = true
                    }
                )
                
                // 添加安全模式开关菜单项
                DropdownMenuItem(
                    text = { Text("安全模式") },
                    trailingIcon = {
                        Switch(
                            checked = isSecureModeEnabled.value,
                            onCheckedChange = {
                                val result = SecureModeManager.toggleSecureMode(context, it)
                                if (result) {
                                    isSecureModeEnabled.value = it
                                    // 刷新相册列表以显示变化
                                    viewModel.refreshMedia()
                                }
                            }
                        )
                    },
                    onClick = {
                        showMoreMenu = false
                        showSecureModeInfoDialog = true
                    }
                )
                
                // 添加自定义菜单项
                for (item in customMenuItems) {
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = {
                            item.onClick()
                            showMoreMenu = false
                        }
                    )
                }
            }
        }
        
        // 网格列数调整对话框（主页面和相册详情页都支持）
        if (showGridColumnsDialog) {
            val orientationText = when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> "横屏"
                else -> "竖屏"
            }
            val dialogTitle = if (isInAlbumDetail) {
                "调整网格列数（$orientationText）"
            } else {
                "调整相册列表网格列数（$orientationText）"
            }
            
            AlertDialog(
                onDismissRequest = { showGridColumnsDialog = false },
                title = { Text(dialogTitle) },
                text = {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "当前选择: $currentGridColumns 列",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 使用真正的Slider组件
                        Slider(
                            value = currentGridColumns.toFloat(),
                            onValueChange = { newValue ->
                                currentGridColumns = newValue.toInt().coerceIn(2, 8)
                            },
                            valueRange = 2f..8f,
                            steps = 5, // 2,3,4,5,6,7,8 共6个值，5个间隔
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        // 显示数值标签
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("2", style = MaterialTheme.typography.bodySmall)
                            Text("8", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (isInAlbumDetail) {
                                viewModel.selectedAlbum?.let { album ->
                                    viewModel.updateAlbumGridColumns(album, currentGridColumns)
                                }
                            } else {
                                viewModel.updateAlbumsGridColumns(currentGridColumns)
                            }
                            showGridColumnsDialog = false
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showGridColumnsDialog = false }
                    ) {
                        Text("取消")
                    }
                },
                properties = DialogProperties()
            )
        }

        if (showSecureModeInfoDialog) {
            AlertDialog(
                onDismissRequest = { showSecureModeInfoDialog = false },
                title = { Text("说明") },
                text = { Text("开启安全模式后隐藏设为私密的相册，并且使其他媒体也无法搜索到其中内容") },
                confirmButton = {
                    TextButton(onClick = { showSecureModeInfoDialog = false }) {
                        Text("我知道了")
                    }
                }
            )
        }
    }
    
    /**
     * 菜单项数据类，用于定义自定义菜单项
     * @param label 菜单项文本
     * @param onClick 点击事件处理函数
     */
    data class MenuItem(
        val label: String,
        val onClick: () -> Unit
    )
}