package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    
    // 单一纵向滚动容器，内含上下两个分区（有引用 / 无引用）
    ReorderableSectionedTagList(
        viewModel = viewModel,
        modifier = modifier,
        tagsWithReferences = tagsWithReferences,
        tagsWithoutReferences = tagsWithoutReferences
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReorderableSectionedTagList(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    tagsWithReferences: List<TagWithChildren>,
    tagsWithoutReferences: List<TagWithChildren>
) {
    // 独立的本地状态，避免拖拽时外部流更新导致抖动
    var localWithRefs by remember(tagsWithReferences) { mutableStateOf(tagsWithReferences) }
    var localWithoutRefs by remember(tagsWithoutReferences) { mutableStateOf(tagsWithoutReferences) }
    val coroutineScope = rememberCoroutineScope()

    val withRefChannel = remember { Channel<Unit>(Channel.CONFLATED) }
    val withoutRefChannel = remember { Channel<Unit>(Channel.CONFLATED) }

    val listState = rememberLazyListState()

    // 创建统一的 Reorderable 状态，按分区约束移动
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val flat = buildFlatList(localWithRefs, localWithoutRefs)
        val fromItem = flat.getOrNull(from.index) ?: return@rememberReorderableLazyListState
        val toItem = flat.getOrNull(to.index) ?: return@rememberReorderableLazyListState

        // 只允许在同一分区、非 Header、非 Divider 间移动
        if (fromItem.isHeader || toItem.isHeader || fromItem.isDivider || toItem.isDivider || fromItem.type != toItem.type) {
            return@rememberReorderableLazyListState
        }

        when (fromItem.type) {
            SectionType.WithRefs -> {
                val fromIdx = fromItem.indexInSection ?: return@rememberReorderableLazyListState
                val toIdx = toItem.indexInSection ?: return@rememberReorderableLazyListState
                // 保存拖拽前的可见标签列表和标签ID，用于计算排序值
                val originalVisibleTags = tagsWithReferences
                val movedTagId = localWithRefs[fromIdx].tag.id
                
                // 在原始列表中找到对应的索引
                val originalFromIdx = originalVisibleTags.indexOfFirst { it.tag.id == movedTagId }
                if (originalFromIdx == -1) return@rememberReorderableLazyListState
                
                // 计算目标位置在原始列表中的索引
                // 需要找到 toIdx 位置对应的标签ID，然后在原始列表中找到该标签的位置
                val originalToIdx = if (toIdx < localWithRefs.size) {
                    // 找到目标位置对应的标签ID，然后在原始列表中找到该标签
                    val targetTagId = localWithRefs[toIdx].tag.id
                    val idx = originalVisibleTags.indexOfFirst { it.tag.id == targetTagId }
                    if (idx == -1) return@rememberReorderableLazyListState
                    idx
                } else {
                    // 移动到末尾：使用列表大小作为索引（会在函数中处理）
                    originalVisibleTags.size
                }
                
                val updated = localWithRefs.toMutableList().apply {
                    add(toIdx, removeAt(fromIdx))
                }
                localWithRefs = updated
                withRefChannel.tryReceive()
                coroutineScope.launch {
                    // 传入原始可见标签列表和对应的索引，确保排序基于当前显示的标签
                    viewModel.moveTagInGroup(originalFromIdx, originalToIdx, true, originalVisibleTags)
                    withRefChannel.send(Unit)
                }
            }
            SectionType.WithoutRefs -> {
                val fromIdx = fromItem.indexInSection ?: return@rememberReorderableLazyListState
                val toIdx = toItem.indexInSection ?: return@rememberReorderableLazyListState
                // 保存拖拽前的可见标签列表和标签ID，用于计算排序值
                val originalVisibleTags = tagsWithoutReferences
                val movedTagId = localWithoutRefs[fromIdx].tag.id
                
                // 在原始列表中找到对应的索引
                val originalFromIdx = originalVisibleTags.indexOfFirst { it.tag.id == movedTagId }
                if (originalFromIdx == -1) return@rememberReorderableLazyListState
                
                // 计算目标位置在原始列表中的索引
                // 需要找到 toIdx 位置对应的标签ID，然后在原始列表中找到该标签的位置
                val originalToIdx = if (toIdx < localWithoutRefs.size) {
                    // 找到目标位置对应的标签ID，然后在原始列表中找到该标签
                    val targetTagId = localWithoutRefs[toIdx].tag.id
                    val idx = originalVisibleTags.indexOfFirst { it.tag.id == targetTagId }
                    if (idx == -1) return@rememberReorderableLazyListState
                    idx
                } else {
                    // 移动到末尾：使用列表大小作为索引（会在函数中处理）
                    originalVisibleTags.size
                }
                
                val updated = localWithoutRefs.toMutableList().apply {
                    add(toIdx, removeAt(fromIdx))
                }
                localWithoutRefs = updated
                withoutRefChannel.tryReceive()
                coroutineScope.launch {
                    // 传入原始可见标签列表和对应的索引，确保排序基于当前显示的标签
                    viewModel.moveTagInGroup(originalFromIdx, originalToIdx, false, originalVisibleTags)
                    withoutRefChannel.send(Unit)
                }
            }
        }
    }

    // 同步外部数据更新（有引用）
    LaunchedEffect(tagsWithReferences) {
        val localIds = localWithRefs.map { it.tag.id }
        val newIds = tagsWithReferences.map { it.tag.id }
        if (localIds != newIds) {
            withTimeoutOrNull(100) { withRefChannel.receive() }
            localWithRefs = tagsWithReferences
        }
    }

    // 同步外部数据更新（无引用）
    LaunchedEffect(tagsWithoutReferences) {
        val localIds = localWithoutRefs.map { it.tag.id }
        val newIds = tagsWithoutReferences.map { it.tag.id }
        if (localIds != newIds) {
            withTimeoutOrNull(100) { withoutRefChannel.receive() }
            localWithoutRefs = tagsWithoutReferences
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (localWithRefs.isNotEmpty()) {
            stickyHeader {
                SectionHeader(text = "有引用标签")
            }
            items(
                items = localWithRefs,
                key = { it.tag.id }
            ) { tagWithChildren ->
                ReorderableItem(
                    reorderableState,
                    key = tagWithChildren.tag.id
                ) { isDragging ->
                    SwipeToDeleteTagItem(
                        tagWithChildren = tagWithChildren,
                        viewModel = viewModel,
                        onDelete = { tag -> viewModel.deleteTagWithUndo(tag) },
                        useReferencedTagExpansion = false,
                        isDragging = isDragging,
                        reorderableScope = this
                    )
                }
            }
        }

        if (localWithRefs.isNotEmpty() && localWithoutRefs.isNotEmpty()) {
            item {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
            }
        }

        if (localWithoutRefs.isNotEmpty()) {
            stickyHeader {
                SectionHeader(text = "无引用标签")
            }
            items(
                items = localWithoutRefs,
                key = { it.tag.id }
            ) { tagWithChildren ->
                ReorderableItem(
                    reorderableState,
                    key = tagWithChildren.tag.id
                ) { isDragging ->
                    SwipeToDeleteTagItem(
                        tagWithChildren = tagWithChildren,
                        viewModel = viewModel,
                        onDelete = { tag -> viewModel.deleteTagWithUndo(tag) },
                        useReferencedTagExpansion = false,
                        isDragging = isDragging,
                        reorderableScope = this
                    )
                }
            }
        }

        if (localWithRefs.isEmpty() && localWithoutRefs.isEmpty()) {
            item {
                Text(
                    text = "暂无标签",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

private data class FlatItem(
    val type: SectionType,
    val isHeader: Boolean,
    val isDivider: Boolean = false, // 是否为分隔线
    val indexInSection: Int? // 在分区内的序号，Header 和 Divider 为 null
)

private enum class SectionType { WithRefs, WithoutRefs }

private fun buildFlatList(
    withRefs: List<TagWithChildren>,
    withoutRefs: List<TagWithChildren>
): List<FlatItem> {
    val result = mutableListOf<FlatItem>()
    if (withRefs.isNotEmpty()) {
        result.add(FlatItem(SectionType.WithRefs, isHeader = true, indexInSection = null))
        withRefs.forEachIndexed { idx, _ ->
            result.add(FlatItem(SectionType.WithRefs, isHeader = false, indexInSection = idx))
        }
    }
    // 当两个分区都存在时，添加 Divider 占位符以匹配 LazyColumn 的实际结构
    if (withRefs.isNotEmpty() && withoutRefs.isNotEmpty()) {
        result.add(FlatItem(SectionType.WithRefs, isHeader = false, isDivider = true, indexInSection = null))
    }
    if (withoutRefs.isNotEmpty()) {
        result.add(FlatItem(SectionType.WithoutRefs, isHeader = true, indexInSection = null))
        withoutRefs.forEachIndexed { idx, _ ->
            result.add(FlatItem(SectionType.WithoutRefs, isHeader = false, indexInSection = idx))
        }
    }
    return result
}

@Composable
private fun SectionHeader(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
