package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight

/**
 * 标签管理抽屉的标题栏组件
 */
@Composable
fun TagManagerHeader(
    isDragMode: Boolean,
    onDragModeToggle: (Boolean) -> Unit,
    onResetClick: () -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 可展开搜索按钮，包含标题文字
        ExpandableSearchButton(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            title = "标签管理"
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 拖拽模式切换按钮
            IconButton(
                onClick = { onDragModeToggle(!isDragMode) }
            ) {
                Icon(
                    imageVector = if (isDragMode) Icons.Default.Check else Icons.Default.DragIndicator,
                    contentDescription = if (isDragMode) "退出拖拽模式并保存排序" else "拖拽排序",
                    tint = if (isDragMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 重置状态按钮
            IconButton(onClick = onResetClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "重置标签状态"
                )
            }
        }
    }
}

