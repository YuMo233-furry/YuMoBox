package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 标签列表内容组件
 */
@Composable
fun TagListContent(
    viewModel: MainViewModel,
    filteredTags: List<TagWithChildren>,
    localTags: List<TagWithChildren>,
    isDragMode: Boolean,
    onLocalTagsChange: (List<TagWithChildren>) -> Unit,
    modifier: Modifier = Modifier
) {
    // 标签列表
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 位置追踪 - 记录每个标签项的全局Y坐标范围
    val itemPositions = remember { mutableStateMapOf<Int, IntRange>() }
    
    // 记录拖拽前的滚动位置，用于在拖拽排序后恢复
    var scrollIndexBeforeMove by remember { mutableStateOf(0) }
    var scrollOffsetBeforeMove by remember { mutableStateOf(0) }
    
    // 恢复滚动位置
    LaunchedEffect(Unit) {
        val savedScrollIndex = viewModel.restoreTagDrawerScrollPosition()
        if (savedScrollIndex > 0) {
            listState.scrollToItem(savedScrollIndex)
        }
    }
    
    // 保存滚动位置（在非拖拽模式下）
    LaunchedEffect(listState.firstVisibleItemIndex, isDragMode) {
        // 只在非拖拽模式下保存滚动位置，避免拖拽时的滚动跳动
        if (!isDragMode) {
            viewModel.saveTagDrawerScrollPosition(listState.firstVisibleItemIndex)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .pointerInput(isDragMode) {
                if (isDragMode) {
                    // 在排序模式下，使用pointerInput来检测触摸位置
                    // 只有在左半边才处理拖拽手势
                    var isDragging = false
                    var startPosition = Offset.Zero
                    
                    detectDragGestures(
                        onDragStart = { offset ->
                            val screenWidth = size.width
                            val rightHalf = screenWidth / 2
                            
                            // 如果触摸点在右半边，不处理拖拽，让LazyColumn处理滚动
                            if (offset.x > rightHalf) {
                                return@detectDragGestures
                            }
                            
                            // 在左半边，开始拖拽
                            isDragging = true
                            startPosition = offset
                        },
                        onDrag = { change, dragAmount ->
                            // 只在左半边处理拖拽
                            if (isDragging) {
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            // 拖拽结束处理
                            isDragging = false
                        }
                    )
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // 添加内容填充，优化滚动性能
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (filteredTags.isEmpty()) {
            item {
                Text(
                    text = "暂无标签",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else {
            if (isDragMode) {
                // 拖拽排序模式 - 分组显示有引用标签和无引用标签的标签
                val tagsWithReferences = localTags.filter { it.referencedTags.isNotEmpty() }
                val tagsWithoutReferences = localTags.filter { it.referencedTags.isEmpty() }
                
                // 显示有引用标签的标签
                itemsIndexed(
                    items = tagsWithReferences,
                    key = { _, tagWithChildren -> tagWithChildren.tag.id }
                ) { index, tagWithChildren ->
                    // 直接渲染，避免复杂动画导致滚动跳动
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            itemPositions[index] = (bounds.top.toInt()..bounds.bottom.toInt())
                        }
                    ) {
                        DraggableTagTreeItem(
                            tagWithChildren = tagWithChildren,
                            viewModel = viewModel,
                            index = index,
                            isDragMode = isDragMode,
                            totalItemCount = localTags.size,
                            level = 0,
                            useReferencedTagExpansion = true,  // 引用标签组使用引用展开状态
                            onMove = { fromIndex, toIndex ->
                                // 保存当前滚动位置
                                scrollIndexBeforeMove = listState.firstVisibleItemIndex
                                scrollOffsetBeforeMove = listState.firstVisibleItemScrollOffset
                                
                                // 只调用ViewModel更新数据库（使用分组排序）
                                // 不立即更新本地状态，避免拖拽过程中的页面重组
                                viewModel.moveTagInGroup(fromIndex, toIndex, true) // true表示有引用标签组
                                
                                // 延迟更新本地状态，确保UI立即反映变化
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50) // 短暂延迟，等待数据库更新
                                    
                                    // 从数据库重新获取最新数据
                                    val updatedTags = viewModel.tagsFlow.first()
                                    onLocalTagsChange(updatedTags)
                                }
                            },
                            itemPositions = itemPositions
                        )
                    }
                }
                
                // 如果有引用标签的本体标签和无引用标签的本体标签都存在，显示分隔线
                if (tagsWithReferences.isNotEmpty() && tagsWithoutReferences.isNotEmpty()) {
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
                
                // 显示无引用标签的标签
                itemsIndexed(
                    items = tagsWithoutReferences,
                    key = { _, tagWithChildren -> tagWithChildren.tag.id }
                ) { index, tagWithChildren ->
                    // 直接渲染，避免复杂动画导致滚动跳动
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            // 为第二组使用全局索引（加上第一组的数量）
                            val globalIndex = tagsWithReferences.size + index
                            itemPositions[globalIndex] = (bounds.top.toInt()..bounds.bottom.toInt())
                        }
                    ) {
                        // 计算全局索引，用于位置查找
                        val globalIndex = tagsWithReferences.size + index
                        
                        DraggableTagTreeItem(
                            tagWithChildren = tagWithChildren,
                            viewModel = viewModel,
                            index = globalIndex,  // 传递全局索引用于位置查找
                            isDragMode = isDragMode,
                            totalItemCount = localTags.size,  // 使用全局标签总数
                            level = 0,
                            useReferencedTagExpansion = false,  // 普通标签组使用本体展开状态
                            onMove = { fromGlobalIndex, toGlobalIndex ->
                                // 保存当前滚动位置
                                scrollIndexBeforeMove = listState.firstVisibleItemIndex
                                scrollOffsetBeforeMove = listState.firstVisibleItemScrollOffset
                                
                                // 边界检查：确保目标索引在第二组范围内
                                val firstGroupSize = tagsWithReferences.size
                                if (toGlobalIndex < firstGroupSize) {
                                    // 不允许拖拽到第一组，忽略此操作
                                    return@DraggableTagTreeItem
                                }
                                
                                // 转换为局部索引进行分组内排序
                                val fromLocalIndex = fromGlobalIndex - firstGroupSize
                                val toLocalIndex = toGlobalIndex - firstGroupSize
                                
                                // 确保索引在有效范围内
                                if (fromLocalIndex !in 0 until tagsWithoutReferences.size) return@DraggableTagTreeItem
                                
                                // 只调用ViewModel更新数据库（使用分组排序）
                                // 不立即更新本地状态，避免拖拽过程中的页面重组
                                viewModel.moveTagInGroup(fromLocalIndex, toLocalIndex, false) // false表示无引用标签组
                                
                                // 延迟更新本地状态，确保UI立即反映变化
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50) // 短暂延迟，等待数据库更新
                                    
                                    // 从数据库重新获取最新数据
                                    val updatedTags = viewModel.tagsFlow.first()
                                    onLocalTagsChange(updatedTags)
                                }
                            },
                            itemPositions = itemPositions
                        )
                    }
                }
            } else {
                // 普通模式 - 分组显示有引用标签和无引用标签的本体标签
                val tagsWithReferences = filteredTags.filter { it.referencedTags.isNotEmpty() }
                val tagsWithoutReferences = filteredTags.filter { it.referencedTags.isEmpty() }
                
                // 显示有引用标签的本体标签（这些标签本身引用了其他标签）
                items(
                    items = tagsWithReferences,
                    key = { tagWithChildren -> "tag_${tagWithChildren.tag.id}" }
                ) { tagWithChildren ->
                    SwipeToDeleteTagItem(
                        tagWithChildren = tagWithChildren,
                        viewModel = viewModel,
                        onDelete = { tag ->
                            viewModel.deleteTagWithUndo(tag)
                        },
                        useReferencedTagExpansion = false  // 本体标签使用本体展开状态
                    )
                }
                
                // 如果有引用标签的本体标签和无引用标签的本体标签都存在，显示分隔线
                if (tagsWithReferences.isNotEmpty() && tagsWithoutReferences.isNotEmpty()) {
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
                
                // 显示无引用标签的标签
                items(
                    items = tagsWithoutReferences,
                    key = { tagWithChildren -> "tag_${tagWithChildren.tag.id}" }
                ) { tagWithChildren ->
                    SwipeToDeleteTagItem(
                        tagWithChildren = tagWithChildren,
                        viewModel = viewModel,
                        onDelete = { tag ->
                            viewModel.deleteTagWithUndo(tag)
                        },
                        useReferencedTagExpansion = false  // 普通标签组使用本体展开状态
                    )
                }
            }
        }
    }
}

