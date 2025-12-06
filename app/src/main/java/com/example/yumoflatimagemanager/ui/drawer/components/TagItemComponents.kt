package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 标签项组件
 */
@Composable
fun TagItem(
    tagWithChildren: TagWithChildren,
    viewModel: MainViewModel,
    level: Int = 0,
    onClickOutside: (() -> Unit)? = null,
    useReferencedTagExpansion: Boolean = false
) {
    println("DEBUG: TagItem 被调用 - 标签: ${tagWithChildren.tag.name}, 级别: $level, 排序模式: ${viewModel.isInDragMode}, useReferencedTagExpansion: $useReferencedTagExpansion")
    
    val tag = tagWithChildren.tag
    val isExpanded = if (useReferencedTagExpansion) {
        viewModel.expandedReferencedTagIds.contains(tag.id)
    } else {
        viewModel.expandedTagIds.contains(tag.id)
    }
    val hasChildren = tagWithChildren.children.isNotEmpty() || tagWithChildren.referencedTags.isNotEmpty()
    
    // 双击检测状态
    var lastClickTime by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    
    // 检查当前标签是否已应用到选中的图片上
    val isTagApplied = remember {
        derivedStateOf {
            viewModel.selectedImages.isNotEmpty() && viewModel.isTagAppliedToSelected(tag.id)
        }
    }
    
    // 获取标签统计信息
    val tagStats = remember(tag.id) {
        derivedStateOf {
            viewModel.tagStatistics[tag.id]
        }
    }
    
    // 关键：为"本体/引用"两类同 id 标签提供不同的组合键，防止 Compose 复用状态导致两者联动
    androidx.compose.runtime.key("${if (useReferencedTagExpansion) "ref" else "base"}-${tag.id}") {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onClickOutside?.invoke() // 先处理外部点击事件
                    
                    val currentTime = System.currentTimeMillis()
                    val isDoubleClick = currentTime - lastClickTime < 300 // 300ms内为双击
                    lastClickTime = currentTime
                    
                    if (viewModel.selectedImages.isNotEmpty()) {
                        // 如果有选中的图片，点击标签可以添加或移除标签
                        if (isTagApplied.value) {
                            viewModel.removeTagFromSelected(tag.id)
                        } else {
                            viewModel.addTagToSelected(tag.id)
                        }
                    } else {
                        // 如果没有选中的图片，处理标签过滤/排除逻辑
                        val isExcluded = viewModel.excludedTagIds.contains(tag.id)
                        val isActive = viewModel.activeTagFilterIds.contains(tag.id)
                        
                        if (isDoubleClick) {
                            // 双击：切换排除模式
                            viewModel.toggleTagExclusion(tag.id)
                        } else if (isExcluded || isActive) {
                            // 单击：如果已处于排除或激活状态，取消选择
                            viewModel.toggleTagFilter(tag.id)
                        } else {
                            // 单击：未选择状态，进入激活模式
                            viewModel.toggleTagFilter(tag.id)
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
                // 展开/折叠图标或引用标签排序按钮
                if (hasChildren) {
                    // 排序模式下显示引用标签排序按钮，普通模式下显示展开/折叠按钮
                    if (viewModel.isInDragMode) {
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
                    } else {
                        IconButton(
                            onClick = { 
                                // 阻止点击事件冒泡到父容器
                                if (useReferencedTagExpansion) {
                                    viewModel.toggleReferencedTagExpanded(tag.id)
                                } else {
                                    viewModel.toggleTagExpanded(tag.id)
                                }
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            val rotation by animateFloatAsState(
                                targetValue = if (isExpanded) 90f else 0f,
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                label = "arrow_rotation"
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (isExpanded) "折叠" else "展开",
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(rotation)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }
                
                // 标签名称
                Text(
                    text = tag.name,
                    fontSize = if (level == 0) 16.sp else 14.sp,
                    fontWeight = if (level == 0) FontWeight.Normal else FontWeight.Light,
                    color = if (level == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // 图片数量统计
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
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 重命名按钮
                if (viewModel.selectedImages.isEmpty()) {
                    IconButton(
                        onClick = { viewModel.showRenameTagDialog(tag.id) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "重命名",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 添加引用按钮（+号）
                if (viewModel.selectedImages.isEmpty()) {
                    IconButton(
                        onClick = { viewModel.showAddTagReferenceDialog(tag.id) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加引用",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // 激活状态图标
                val isActive = viewModel.activeTagFilterIds.contains(tag.id)
                val isExcluded = viewModel.excludedTagIds.contains(tag.id)
                
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
                
                // 选中状态复选框
                if (viewModel.selectedImages.isNotEmpty()) {
                    Checkbox(
                        checked = isTagApplied.value,
                        onCheckedChange = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // 引用标签列表（带动画）
        AnimatedVisibility(
            visible = isExpanded && hasChildren && !viewModel.isInDragMode,
            enter = expandVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(300))
        ) {
            // 排序模式下不显示引用标签树
            Column(modifier = Modifier.padding(start = 24.dp)) {
                // 正常模式：显示普通引用标签（按 sortOrder 排序）
                tagWithChildren.referencedTags.sortedBy { it.sortOrder }.forEach { ref ->
                    ReferencedTagTreeItem(
                        parentTagId = tagWithChildren.tag.id,
                        childTagId = ref.childTagId,
                        viewModel = viewModel,
                        level = level + 1,
                        useReferencedTagExpansion = true  // 引用标签始终使用引用展开状态
                    )
                }
            }
        }
    }
    }
}

@Composable
fun ReferencedTagTreeItem(
    parentTagId: Long,
    childTagId: Long,
    viewModel: MainViewModel,
    level: Int,
    useReferencedTagExpansion: Boolean = false
) {
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

    SwipeToRemoveReferenceItem(
        parentTagId = parentTagId,
        tagWithChildren = childWithChildren!!,
        viewModel = viewModel,
        useReferencedTagExpansion = useReferencedTagExpansion
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToRemoveReferenceItem(
    parentTagId: Long,
    tagWithChildren: TagWithChildren,
    viewModel: MainViewModel,
    useReferencedTagExpansion: Boolean = false
) {
    var offsetX by remember { mutableStateOf(0f) }
    var isReveal by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val btnWidth = with(density) { 80.dp.toPx() }

    fun reset() {
        offsetX = 0f
        isReveal = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -btnWidth / 2) {
                            offsetX = -btnWidth
                            isReveal = true
                        } else {
                            reset()
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = offsetX + dragAmount
                        offsetX = newOffset.coerceIn(-btnWidth, 0f)
                    }
                )
            }
    ) {
        if (isReveal || offsetX < -10) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFA000))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(onClick = {
                    viewModel.removeTagReference(parentTagId, tagWithChildren.tag.id)
                    reset()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "移除引用",
                        tint = Color.White
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            TagItem(
                tagWithChildren = tagWithChildren,
                viewModel = viewModel,
                level = 1,
                useReferencedTagExpansion = useReferencedTagExpansion
            )
        }
    }
}


