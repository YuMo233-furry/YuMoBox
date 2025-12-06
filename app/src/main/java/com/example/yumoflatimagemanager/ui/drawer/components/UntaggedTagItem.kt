package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yumoflatimagemanager.MainViewModel

/**
 * 未分类虚拟标签项组件
 */
@Composable
fun UntaggedTagItem(
    viewModel: MainViewModel,
    lastClickTime: Long,
    onClickTimeUpdate: (Long) -> Unit
) {
    val isActive = viewModel.activeTagFilterIds.contains(-1L)
    val isExcluded = viewModel.excludedTagIds.contains(-1L)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val currentTime = System.currentTimeMillis()
                val isDoubleClick = currentTime - lastClickTime < 300
                onClickTimeUpdate(currentTime)
                
                if (viewModel.selectedImages.isEmpty()) {
                    val isExcluded = viewModel.excludedTagIds.contains(-1L)
                    val isActive = viewModel.activeTagFilterIds.contains(-1L)
                    
                    if (isDoubleClick) {
                        // 双击：切换排除模式
                        viewModel.toggleTagExclusion(-1L)
                    } else if (isExcluded || isActive) {
                        // 单击：如果已处于排除或激活状态，取消选择
                        viewModel.toggleTagFilter(-1L)
                    } else {
                        // 单击：未选择状态，进入激活模式
                        viewModel.toggleTagFilter(-1L)
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Spacer(modifier = Modifier.width(20.dp))
            
            // 标签名称
            Text(
                text = "未分类",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // 未分类图片数量统计
            val untaggedStats = remember {
                derivedStateOf {
                    viewModel.tagStatistics[-1L]
                }
            }
            val untaggedCount = untaggedStats.value?.totalImageCount ?: 0
            if (untaggedCount > 0) {
                Text(
                    text = "($untaggedCount)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        
        // 激活状态图标
        if (isExcluded) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "已排除",
                tint = Color(0xFFF44336),
                modifier = Modifier.size(16.dp)
            )
        } else if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已激活",
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

