package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableTagTreeItem(
    tagWithChildren: TagWithChildren,
    viewModel: MainViewModel,
    index: Int,
    isDragMode: Boolean,
    totalItemCount: Int,
    level: Int = 0,
    onMove: (Int, Int) -> Unit,
    useReferencedTagExpansion: Boolean = false,
    itemPositions: Map<Int, IntRange> = emptyMap()
) {
    val tag = tagWithChildren.tag
    val isExpanded = if (useReferencedTagExpansion) {
        viewModel.expandedReferencedTagIds.contains(tag.id)
    } else {
        viewModel.expandedTagIds.contains(tag.id)
    }
    val hasChildren = tagWithChildren.children.isNotEmpty() || tagWithChildren.referencedTags.isNotEmpty()
    
    // 调试信息
    println("DEBUG: DraggableTagTreeItem - 标签: ${tag.name}, 引用标签数量: ${tagWithChildren.referencedTags.size}")
    if (tagWithChildren.referencedTags.isNotEmpty()) {
        println("DEBUG: 引用标签列表: ${tagWithChildren.referencedTags.map { "${it.childTagId}" }}")
    }
    
    // 为“本体/引用”两类同 id 标签提供不同组合键，避免状态串联
    androidx.compose.runtime.key("${if (useReferencedTagExpansion) "ref" else "base"}-${tag.id}") {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        // 主标签项（可拖拽）
        DraggableTagItem(
            tagWithChildren = tagWithChildren,
            viewModel = viewModel,
            index = index,
            isDragMode = isDragMode,
            totalItemCount = totalItemCount,
            level = level,
            onMove = onMove,
            useReferencedTagExpansion = useReferencedTagExpansion,
            itemPositions = itemPositions
        )
        
        // 引用标签列表（支持展开/折叠，带动画）
        AnimatedVisibility(
            visible = isExpanded && hasChildren && !isDragMode,
            enter = expandVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(300))
        ) {
            // 排序模式下不显示引用标签树，只显示扁平的标签列表
            Column(modifier = Modifier.padding(start = 24.dp)) {
                // 正常模式：显示普通引用标签（按 sortOrder 排序）
                println("DEBUG: 显示正常模式引用标签，isDragMode = $isDragMode")
                tagWithChildren.referencedTags.sortedBy { it.sortOrder }.forEach { ref ->
                    ReferencedTagTreeItem(
                        parentTagId = tagWithChildren.tag.id,
                        childTagId = ref.childTagId,
                        viewModel = viewModel,
                        level = level + 1
                    )
                }
            }
        }
    }
    }
}

/**
 * 可拖拽的引用标签项组件（专门用于引用标签排序）
 */
@Composable
fun DraggableChildTagItem(
    parentTagId: Long,
    childTagId: Long,
    viewModel: MainViewModel,
    refIndex: Int,
    totalRefCount: Int,
    level: Int = 0,
    childItemPositions: Map<Int, IntRange> = emptyMap(),
    onLocalUpdate: ((List<TagWithChildren>) -> Unit)? = null,
    onNavigateToChild: ((TagWithChildren) -> Unit)? = null
) {
    println("DEBUG: DraggableChildTagItem 被调用 - 引用标签ID: $childTagId, 索引: $refIndex")
    
    var childWithChildren by remember(childTagId) { mutableStateOf<TagWithChildren?>(null) }
    // 监听标签引用刷新触发器，当添加或移除引用时重新加载数据
    LaunchedEffect(childTagId, viewModel.tagReferenceRefreshTrigger) {
        childWithChildren = viewModel.getTagWithChildrenForUi(childTagId)
    }

    if (childWithChildren == null) {
        // 回退：仅展示占位文本
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tag,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "#${childTagId}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontStyle = FontStyle.Italic
            )
        }
        return
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var initialGlobalY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    
    // 检查是否有子引用标签
    val hasReferencedTags = childWithChildren!!.referencedTags.isNotEmpty()
    
    // 拖拽状态管理
    val draggableState = rememberDraggableState { delta ->
        dragOffset += delta
    }
    
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (isDragging) {
            // 拖拽时使用更快的响应，减少滞后感
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        } else {
            // 停止拖拽时使用弹性动画
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "childDragOffset"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                // 记录初始全局Y坐标
                initialGlobalY = coordinates.positionInWindow().y
            }
            .zIndex(if (isDragging) 1f else 0f) // 拖动时提高z-index
            .offset { 
                androidx.compose.ui.unit.IntOffset(0, animatedDragOffset.roundToInt())
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStarted = { 
                    isDragging = true
                },
                onDragStopped = { 
                    isDragging = false
                    
                    // 计算拖拽距离，只有超过阈值才执行排序
                    val dragDistance = abs(dragOffset)
                    val threshold = with(density) { 10.dp.toPx() }
                    
                    if (dragDistance > threshold) {
                        var targetIdx = refIndex
                        
                        if (childItemPositions.isNotEmpty()) {
                            // 使用更精确的拖拽检测逻辑
                            val isDraggingUp = dragOffset < 0
                            val isDraggingDown = dragOffset > 0
                            
                            if (isDraggingUp) {
                                // 向上拖拽：找到当前位置上方的最近项目
                                var closestIndex = refIndex
                                var minDistance = Float.MAX_VALUE
                                
                                for ((idx, range) in childItemPositions) {
                                    if (idx >= refIndex) continue // 只考虑上方的项目
                                    
                                    val rangeCenter = (range.first + range.last) / 2f
                                    val currentCenter = (childItemPositions[refIndex]?.let { (it.first + it.last) / 2f } ?: initialGlobalY) + dragOffset
                                    val distance = abs(currentCenter - rangeCenter)
                                    
                                    if (distance < minDistance) {
                                        minDistance = distance
                                        closestIndex = idx
                                    }
                                }
                                targetIdx = closestIndex
                            } else if (isDraggingDown) {
                                // 向下拖拽：找到当前位置下方的最近项目
                                var closestIndex = refIndex
                                var minDistance = Float.MAX_VALUE
                                
                                for ((idx, range) in childItemPositions) {
                                    if (idx <= refIndex) continue // 只考虑下方的项目
                                    
                                    val rangeCenter = (range.first + range.last) / 2f
                                    val currentCenter = (childItemPositions[refIndex]?.let { (it.first + it.last) / 2f } ?: initialGlobalY) + dragOffset
                                    val distance = abs(currentCenter - rangeCenter)
                                    
                                    if (distance < minDistance) {
                                        minDistance = distance
                                        closestIndex = idx
                                    }
                                }
                                targetIdx = closestIndex
                            }
                        } else {
                            // 回退：使用简单的偏移计算（不推荐，但保留作为后备）
                            val baseItemHeight = with(density) { 40.dp.toPx() }
                            val itemsMoved = (dragOffset / baseItemHeight).roundToInt()
                            targetIdx = (refIndex + itemsMoved).coerceIn(0, totalRefCount - 1)
                        }
                        
                        // 边界处理
                        targetIdx = targetIdx.coerceIn(0, totalRefCount - 1)
                        
                        if (targetIdx != refIndex) {
                            println("DEBUG: 引用标签拖拽排序 - 从 $refIndex 到 $targetIdx (dragOffset=$dragOffset)")
                            
                            // 更新数据库
                            viewModel.moveChildTag(parentTagId, refIndex, targetIdx)
                        }
                    }
                    
                    // 平滑地重置拖拽偏移
                    dragOffset = 0f
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .background(
                    if (isDragging) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(4.dp)
                )
                .border(
                    width = if (isDragging) 2.dp else 0.dp,
                    color = if (isDragging) MaterialTheme.colorScheme.secondary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 层级缩进
                Spacer(modifier = Modifier.width((level * 20).dp))
                
                // 如果有子引用标签，显示展开按钮（移到左边）
                if (hasReferencedTags && onNavigateToChild != null) {
                    IconButton(
                        onClick = { 
                            onNavigateToChild(childWithChildren!!)
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "展开子引用标签",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }
                
                // 引用标签拖拽手柄图标（更小的图标）
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "拖拽引用标签排序",
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 6.dp),
                    tint = if (isDragging) MaterialTheme.colorScheme.secondary 
                           else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                
                // 引用标签图标
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                
                // 引用标签名称（更小的字体）
                Text(
                    text = childWithChildren!!.tag.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // 引用标签图片数量统计（更小的字体）
                val tagStats = remember(childWithChildren!!.tag.id) {
                    derivedStateOf {
                        viewModel.tagStatistics[childWithChildren!!.tag.id]
                    }
                }
                val imageCount = tagStats.value?.totalImageCount ?: 0
                if (imageCount > 0) {
                    Text(
                        text = "($imageCount)",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun DraggableTagItem(
    tagWithChildren: TagWithChildren,
    viewModel: MainViewModel,
    index: Int,
    isDragMode: Boolean,
    totalItemCount: Int,
    level: Int = 0,
    onMove: (Int, Int) -> Unit,
    useReferencedTagExpansion: Boolean = false,
    itemPositions: Map<Int, IntRange> = emptyMap()
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var initialGlobalY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    
    // 拖拽状态管理
    val draggableState = rememberDraggableState { delta ->
        dragOffset += delta
    }
    
    
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (isDragging) {
            // 拖拽时使用更快的响应，减少滞后感
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        } else {
            // 停止拖拽时使用弹性动画
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "dragOffset"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                // 记录初始全局Y坐标（组件顶部位置）
                initialGlobalY = coordinates.positionInWindow().y
            }
            .zIndex(if (isDragging) 1f else 0f) // 拖动时提高z-index
            .offset { 
                androidx.compose.ui.unit.IntOffset(0, animatedDragOffset.roundToInt())
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .background(
                    if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    else Color.Transparent,
                    RoundedCornerShape(4.dp)
                )
                .border(
                    width = if (isDragging) 2.dp else 0.dp,
                    color = if (isDragging) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 层级缩进
                Spacer(modifier = Modifier.width((level * 24).dp))
                
                // 引用标签排序按钮（排序模式下显示）
                val hasChildren = tagWithChildren.children.isNotEmpty() || tagWithChildren.referencedTags.isNotEmpty()
                val isExpanded = if (useReferencedTagExpansion) {
                    viewModel.expandedReferencedTagIds.contains(tagWithChildren.tag.id)
                } else {
                    viewModel.expandedTagIds.contains(tagWithChildren.tag.id)
                }
                
                if (hasChildren && isDragMode) {
                    IconButton(
                        onClick = { 
                            // 显示引用标签排序对话框
                            viewModel.showReferenceTagSortDialog(tagWithChildren)
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "引用标签排序",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (hasChildren && !isDragMode) {
                    // 普通模式：显示展开/折叠图标
                    IconButton(
                        onClick = { 
                            if (useReferencedTagExpansion) {
                                viewModel.toggleReferencedTagExpanded(tagWithChildren.tag.id)
                            } else {
                                viewModel.toggleTagExpanded(tagWithChildren.tag.id)
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "折叠" else "展开",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }
                
                // 拖拽手柄（仅手柄可拖拽）
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            enabled = isDragMode,
                            onDragStarted = { 
                                isDragging = true
                            },
                            onDragStopped = { 
                                isDragging = false
                                
                                // 计算拖拽距离，只有超过阈值才执行排序
                                val dragDistance = abs(dragOffset)
                                val threshold = with(density) { 15.dp.toPx() }
                                
                                if (dragDistance > threshold && itemPositions.isNotEmpty()) {
                                    // 使用更精确的拖拽检测逻辑
                                    var targetIndex = index
                                    
                                    // 计算拖拽方向
                                    val isDraggingUp = dragOffset < 0
                                    val isDraggingDown = dragOffset > 0
                                    
                                    if (isDraggingUp) {
                                        // 向上拖拽：找到当前位置上方的最近项目
                                        var closestIndex = index
                                        var minDistance = Float.MAX_VALUE
                                        
                                        for ((idx, range) in itemPositions) {
                                            if (idx >= index) continue // 只考虑上方的项目
                                            
                                            val rangeCenter = (range.first + range.last) / 2f
                                            val currentCenter = (itemPositions[index]?.let { (it.first + it.last) / 2f } ?: initialGlobalY) + dragOffset
                                            val distance = abs(currentCenter - rangeCenter)
                                            
                                            if (distance < minDistance) {
                                                minDistance = distance
                                                closestIndex = idx
                                            }
                                        }
                                        targetIndex = closestIndex
                                    } else if (isDraggingDown) {
                                        // 向下拖拽：找到当前位置下方的最近项目
                                        var closestIndex = index
                                        var minDistance = Float.MAX_VALUE
                                        
                                        for ((idx, range) in itemPositions) {
                                            if (idx <= index) continue // 只考虑下方的项目
                                            
                                            val rangeCenter = (range.first + range.last) / 2f
                                            val currentCenter = (itemPositions[index]?.let { (it.first + it.last) / 2f } ?: initialGlobalY) + dragOffset
                                            val distance = abs(currentCenter - rangeCenter)
                                            
                                            if (distance < minDistance) {
                                                minDistance = distance
                                                closestIndex = idx
                                            }
                                        }
                                        targetIndex = closestIndex
                                    }
                                    
                                    // 边界处理
                                    val finalTargetIndex = targetIndex.coerceIn(0, totalItemCount - 1)
                                    
                                    if (finalTargetIndex != index) {
                                        onMove(index, finalTargetIndex)
                                    }
                                }
                                
                                // 平滑地重置拖拽偏移
                                dragOffset = 0f
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "拖拽排序",
                        tint = if (isDragging) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                
                // 标签名称
                Text(
                    text = tagWithChildren.tag.name,
                    fontSize = if (level == 0) 16.sp else 14.sp,
                    fontWeight = if (level == 0) FontWeight.Normal else FontWeight.Light,
                    color = if (level == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // 图片数量统计
                val tagStats = remember(tagWithChildren.tag.id) {
                    derivedStateOf {
                        viewModel.tagStatistics[tagWithChildren.tag.id]
                    }
                }
                val imageCount = tagStats.value?.totalImageCount ?: 0
                if (imageCount > 0) {
                    Text(
                        text = "($imageCount)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

