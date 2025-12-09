package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
        
        // 重置状态按钮
        IconButton(onClick = onResetClick) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "重置标签状态"
            )
        }
    }
}

