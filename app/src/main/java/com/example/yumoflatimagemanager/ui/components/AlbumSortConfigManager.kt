package com.example.yumoflatimagemanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.data.SortDirection
import com.example.yumoflatimagemanager.data.SortType
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 相册排序配置管理组件
 */
@Composable
fun AlbumSortConfigManager(
    viewModel: MainViewModel,
    selectedAlbum: Album?
) {
    // 排序菜单状态
    var showSortMenu by remember { mutableStateOf(false) }

    // 显示排序菜单项
    DropdownMenuItem(
        text = { Text("排序") },
        onClick = { showSortMenu = true }
    )
    
    // 排序二级菜单
    DropdownMenu(
        expanded = showSortMenu,
        onDismissRequest = { showSortMenu = false },
        modifier = Modifier.width(200.dp)
    ) {
        // 排序类型选项
        SortTypeOption(
            sortType = SortType.CAPTURE_TIME,
            label = "按拍摄时间",
            currentSortConfig = selectedAlbum?.sortConfig,
            onSelect = {
                selectedAlbum?.let { currentAlbum ->
                    val newSortConfig = SortConfig(SortType.CAPTURE_TIME, currentAlbum.sortConfig.direction)
                    viewModel.updateAlbumSortConfig(currentAlbum, newSortConfig)
                }
            }
        )
        
        SortTypeOption(
            sortType = SortType.MODIFY_TIME,
            label = "按修改时间",
            currentSortConfig = selectedAlbum?.sortConfig,
            onSelect = {
                selectedAlbum?.let { currentAlbum ->
                    val newSortConfig = SortConfig(SortType.MODIFY_TIME, currentAlbum.sortConfig.direction)
                    viewModel.updateAlbumSortConfig(currentAlbum, newSortConfig)
                }
            }
        )
        
        SortTypeOption(
            sortType = SortType.CREATE_TIME,
            label = "按创建时间",
            currentSortConfig = selectedAlbum?.sortConfig,
            onSelect = {
                selectedAlbum?.let { currentAlbum ->
                    val newSortConfig = SortConfig(SortType.CREATE_TIME, currentAlbum.sortConfig.direction)
                    viewModel.updateAlbumSortConfig(currentAlbum, newSortConfig)
                }
            }
        )
        
        SortTypeOption(
            sortType = SortType.NAME,
            label = "按名称",
            currentSortConfig = selectedAlbum?.sortConfig,
            onSelect = {
                selectedAlbum?.let { currentAlbum ->
                    val newSortConfig = SortConfig(SortType.NAME, currentAlbum.sortConfig.direction)
                    viewModel.updateAlbumSortConfig(currentAlbum, newSortConfig)
                }
            }
        )
        
        SortTypeOption(
            sortType = SortType.SIZE,
            label = "按大小",
            currentSortConfig = selectedAlbum?.sortConfig,
            onSelect = {
                selectedAlbum?.let { currentAlbum ->
                    val newSortConfig = SortConfig(SortType.SIZE, currentAlbum.sortConfig.direction)
                    viewModel.updateAlbumSortConfig(currentAlbum, newSortConfig)
                }
            }
        )
        
        androidx.compose.material3.HorizontalDivider()
        
        // 排序方向切换按钮
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("排序方向")
                    Row {
                        Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "正序",
                                    tint = if (selectedAlbum?.sortConfig?.direction == SortDirection.ASCENDING) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "倒序",
                                    tint = if (selectedAlbum?.sortConfig?.direction == SortDirection.DESCENDING) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                    }
                }
            },
            onClick = {
                // 切换排序方向
                selectedAlbum?.let { currentAlbum ->
                    val newDirection = if (currentAlbum.sortConfig.direction == SortDirection.ASCENDING) {
                        SortDirection.DESCENDING
                    } else {
                        SortDirection.ASCENDING
                    }
                    val newSortConfig = SortConfig(
                        currentAlbum.sortConfig.type,
                        newDirection
                    )
                    viewModel.updateAlbumSortConfig(currentAlbum, newSortConfig)
                }
            }
        )
    }
}

/**
 * 排序类型选项组件
 */
@Composable
private fun SortTypeOption(
    sortType: SortType,
    label: String,
    currentSortConfig: SortConfig?,
    onSelect: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label)
                if (currentSortConfig?.type == sortType) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "当前排序",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        onClick = onSelect
    )
}