package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 标签列表内容组件
 */
@Composable
fun TagListContent(
    viewModel: MainViewModel,
    filteredTags: List<TagWithChildren>,
    modifier: Modifier = Modifier
) {
    // 分组显示有引用标签和无引用标签的标签
    val tagsWithReferences = remember(filteredTags) {
        filteredTags.filter { it.referencedTags.isNotEmpty() }
    }
    val tagsWithoutReferences = remember(filteredTags) {
        filteredTags.filter { it.referencedTags.isEmpty() }
    }
    
    Column(modifier = modifier) {
        if (filteredTags.isEmpty()) {
            Text(
                text = "暂无标签",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            // 有引用标签组
            if (tagsWithReferences.isNotEmpty()) {
                ReorderableTagGroup(
                    tags = tagsWithReferences,
                    viewModel = viewModel,
                    isWithReferences = true,
                    onMove = { fromIndex, toIndex ->
                        viewModel.moveTagInGroup(fromIndex, toIndex, true)
                    }
                )
            }
            
            // 分隔线
            if (tagsWithReferences.isNotEmpty() && tagsWithoutReferences.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
            }
            
            // 无引用标签组
            if (tagsWithoutReferences.isNotEmpty()) {
                ReorderableTagGroup(
                    tags = tagsWithoutReferences,
                    viewModel = viewModel,
                    isWithReferences = false,
                    onMove = { fromIndex, toIndex ->
                        viewModel.moveTagInGroup(fromIndex, toIndex, false)
                    }
                )
            }
        }
    }
}

/**
 * 可拖拽排序的标签组
 */
@Composable
private fun ReorderableTagGroup(
    tags: List<TagWithChildren>,
    viewModel: MainViewModel,
    isWithReferences: Boolean,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 恢复滚动位置（仅第一个组）
    LaunchedEffect(Unit) {
        if (isWithReferences) {
            val savedScrollIndex = viewModel.restoreTagDrawerScrollPosition()
            if (savedScrollIndex > 0 && savedScrollIndex < tags.size) {
                listState.scrollToItem(savedScrollIndex)
            }
        }
    }
    
    // 保存滚动位置（仅第一个组）
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (isWithReferences) {
            viewModel.saveTagDrawerScrollPosition(listState.firstVisibleItemIndex)
        }
    }
    
    // 创建 Reorderable 状态
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        // 确保索引在有效范围内
        if (from.index in tags.indices && to.index in tags.indices) {
            onMove(from.index, to.index)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            items = tags,
            key = { tagWithChildren -> tagWithChildren.tag.id }
        ) { tagWithChildren ->
            ReorderableItem(
                reorderableState,
                key = tagWithChildren.tag.id
            ) { isDragging ->
                SwipeToDeleteTagItem(
                    tagWithChildren = tagWithChildren,
                    viewModel = viewModel,
                    onDelete = { tag ->
                        viewModel.deleteTagWithUndo(tag)
                    },
                    useReferencedTagExpansion = false,
                    isDragging = isDragging,
                    reorderableScope = this
                )
            }
        }
    }
}
