package com.example.yumoflatimagemanager.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontStyle
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * 全屏标签选择页面
 * 用于为多选图片添加/移除标签
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelectionScreen(
    tagsFlow: Flow<List<TagWithChildren>>,
    selectedTagIds: Set<Long>,
    onToggleTag: (Long) -> Unit,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onCreateTag: () -> Unit,
    viewModel: MainViewModel?,
    modifier: Modifier = Modifier
) {
    var tags by remember { mutableStateOf<List<TagWithChildren>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 处理系统返回键
    BackHandler {
        onCancel()
    }
    
    // 收集标签数据
    LaunchedEffect(tagsFlow) {
        try {
            tagsFlow.collect { tagList ->
                tags = tagList
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // 使用Box确保层级正确，避免被其他UI元素挡住
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)) // 浅灰色背景
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部应用栏
            TopAppBar(
                title = {
                    Text(
                        text = "选择标签",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCreateTag) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建标签"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
            
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索标签…") },
                singleLine = true
            )
            
            // 标签列表
            if (tags.isEmpty()) {
                // 显示空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无标签，请先创建标签",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                // 按是否有引用分组（与标签抽屉一致的分隔线分组）
                val (withRefs, withoutRefs) = remember(tags) {
                    val a = mutableListOf<TagWithChildren>()
                    val b = mutableListOf<TagWithChildren>()
                    tags.forEach { twc ->
                        if (twc.referencedTags.isNotEmpty()) a.add(twc) else b.add(twc)
                    }
                    // 对分组内的标签进行排序，使用新的分组排序字段
                    a.sortWith(compareBy({ it.tag.referencedGroupSortOrder }, { it.tag.name }))
                    b.sortWith(compareBy({ it.tag.normalGroupSortOrder }, { it.tag.name }))
                    Pair(a, b)
                }

                // 过滤（支持匹配任意子孙）
                val filteredWithRefs = remember(withRefs, searchQuery) {
                    if (searchQuery.isBlank()) withRefs else withRefs.filter { matchesTree(it, searchQuery) }
                }
                val filteredWithoutRefs = remember(withoutRefs, searchQuery) {
                    if (searchQuery.isBlank()) withoutRefs else withoutRefs.filter { matchesTree(it, searchQuery) }
                }

                // 自动展开包含匹配子项的父节点
                LaunchedEffect(searchQuery, tags) {
                    if (searchQuery.isBlank()) return@LaunchedEffect
                    val toExpand = mutableSetOf<Long>()
                    fun collectParents(twc: TagWithChildren, query: String, path: MutableList<Long>) {
                        val selfMatch = twc.tag.name.contains(query, ignoreCase = true)
                        var childMatched = false
                        // 检查子标签（TagEntity）
                        twc.children.forEach { child ->
                            val childPath = (path + twc.tag.id).toMutableList()
                            if (child.name.contains(query, ignoreCase = true)) {
                                toExpand.addAll(childPath)
                                childMatched = true
                            }
                        }
                        // 检查引用标签
                        twc.referencedTags.forEach { ref ->
                            val childPath = (path + twc.tag.id).toMutableList()
                            if (ref.childTagId.toString().contains(query, ignoreCase = true)) {
                                toExpand.addAll(childPath)
                                childMatched = true
                            }
                        }
                        if (selfMatch || childMatched) {
                            toExpand.addAll(path)
                        }
                    }
                    tags.forEach { collectParents(it, searchQuery, mutableListOf()) }
                }

                // 绑定到全局 ViewModel 的展开集合，确保与标签抽屉保持一致且可持久化
                val baseExpandedTagIds = viewModel?.expandedTagIds ?: emptySet()
                val refExpandedTagIds = viewModel?.expandedReferencedTagIds ?: emptySet()
                val listState = rememberLazyListState()
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 上半部分：有引用标签的标签（这些标签本身引用了其他标签）
                    // 例如：标签B引用了标签C，那么标签B作为本体标签出现在这里
                    if (filteredWithRefs.isNotEmpty()) {
                        item {
                            Text(
                                text = "有引用标签的本体标签",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            )
                        }
                        
                        items(filteredWithRefs) { tagWithChildren ->
                            TagSelectionTreeItem(
                                tagWithChildren = tagWithChildren,
                                isSelected = tagWithChildren.tag.id in selectedTagIds,
                                onSelect = onToggleTag,
                                // 本体标签使用本体展开状态，与标签抽屉保持一致
                                expandedTagIds = baseExpandedTagIds,
                                onToggleExpand = { tagId ->
                                    viewModel?.toggleTagExpanded(tagId)
                                },
                                level = 0,
                                viewModel = viewModel,
                                selectedTagIds = selectedTagIds
                            )
                        }
                    }

                    // 分隔线
                    if (filteredWithRefs.isNotEmpty() && filteredWithoutRefs.isNotEmpty()) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // 下半部分：没有引用标签的标签（这些标签没有引用任何其他标签）
                    // 例如：标签C没有引用其他标签，那么标签C作为本体标签出现在这里
                    if (filteredWithoutRefs.isNotEmpty()) {
                        item {
                            Text(
                                text = "无引用标签的本体标签",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            )
                        }
                        
                        items(filteredWithoutRefs) { tagWithChildren ->
                            TagSelectionTreeItem(
                                tagWithChildren = tagWithChildren,
                                isSelected = tagWithChildren.tag.id in selectedTagIds,
                                onSelect = onToggleTag,
                                // 本体标签使用本体展开状态，与标签抽屉保持一致
                                expandedTagIds = baseExpandedTagIds,
                                onToggleExpand = { tagId ->
                                    viewModel?.toggleTagExpanded(tagId)
                                },
                                level = 0,
                                viewModel = viewModel,
                                selectedTagIds = selectedTagIds
                            )
                        }
                    }
                }
            }
            
            // 底部操作栏
            BottomAppBar(
                modifier = Modifier.height(72.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 移除按钮（左下角）
                    TextButton(
                        onClick = onRemove,
                        enabled = selectedTagIds.isNotEmpty()
                    ) {
                        Text("移除")
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // 取消按钮
                    TextButton(onClick = onCancel) {
                        Text("取消")
                    }
                    
                    // 确定按钮
                    TextButton(
                        onClick = onConfirm,
                        enabled = selectedTagIds.isNotEmpty()
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/**
 * 递归树形标签项（用于选择页面）
 */
@Composable
private fun TagSelectionTreeItem(
    tagWithChildren: TagWithChildren,
    isSelected: Boolean,
    onSelect: (Long) -> Unit,
    expandedTagIds: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    level: Int,
    viewModel: MainViewModel?,
    selectedTagIds: Set<Long>
) {
    val tag = tagWithChildren.tag
    val hasChildren = tagWithChildren.children.isNotEmpty() || tagWithChildren.referencedTags.isNotEmpty()
    val isExpanded = expandedTagIds.contains(tag.id)

    // 白色圆角背景容器 - 包裹整个标签树
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp, bottom = 4.dp)
            .background(
                Color.White,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            // 主标签项
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(tag.id) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                // 展开/折叠（带动画）
                if (hasChildren) {
                    IconButton(onClick = { onToggleExpand(tag.id) }, modifier = Modifier.size(20.dp)) {
                        val rotation by animateFloatAsState(
                            targetValue = if (isExpanded) 90f else 0f,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "arrow_rotation"
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "折叠" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }

                    // 标签图标与名称
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tag.name,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // 数量
                    val imageCount = if (viewModel != null) {
                        val tagStats = remember(tag.id) {
                            derivedStateOf { viewModel.tagStatistics[tag.id] }
                        }
                        tagStats.value?.totalImageCount ?: 0
                    } else tag.imageCount
                    if (imageCount > 0) {
                        Text(
                            text = "($imageCount)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 多选状态指示器（Checkbox）
                    Spacer(modifier = Modifier.width(6.dp))
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelect(tag.id) },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 子项 - 在同一个白色背景内（带动画）
            AnimatedVisibility(
                visible = isExpanded && hasChildren,
                enter = expandVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(300))
            ) {
                Column {
                    // 子标签（仅一层）
                    tagWithChildren.children.forEach { childTag: TagEntity ->
                        TagSelectionLeafItem(
                            tag = childTag,
                            isSelected = childTag.id in selectedTagIds,
                            onSelect = onSelect,
                            level = 0, // 在白色背景内，不需要额外缩进
                            viewModel = viewModel
                        )
                    }

                    // 引用标签（递归展开）
                    tagWithChildren.referencedTags.forEach { ref: com.example.yumoflatimagemanager.data.local.TagReferenceEntity ->
                    var referencedTagWithChildren by remember(ref.childTagId) { mutableStateOf<TagWithChildren?>(null) }
                    
                    // 异步获取完整的引用标签信息
                    LaunchedEffect(ref.childTagId, viewModel) {
                        if (viewModel != null) {
                            try {
                                // 获取完整的TagWithChildren信息
                                val allTags: List<com.example.yumoflatimagemanager.data.local.TagWithChildren> = viewModel.getAllTagsWithChildren()
                                val foundTag = allTags.find { it.tag.id == ref.childTagId }
                                if (foundTag != null) {
                                    referencedTagWithChildren = foundTag
                                }
                            } catch (e: Exception) {
                                // 保持null状态，避免闪退
                                e.printStackTrace()
                            }
                        }
                    }
                    
                    if (referencedTagWithChildren != null) {
                        // 递归展开引用标签，传递onSelect让用户可以选择引用标签
                        // 引用标签总是使用引用展开状态，避免与本体标签联动
                        // 引用标签使用灰色文字以区分本体标签
                        TagSelectionTreeItemWithGrayText(
                            tagWithChildren = referencedTagWithChildren!!,
                            isSelected = referencedTagWithChildren!!.tag.id in selectedTagIds,
                            onSelect = onSelect,
                            // 引用标签使用引用展开状态，与本体标签独立
                            expandedTagIds = viewModel?.expandedReferencedTagIds ?: emptySet(),
                            onToggleExpand = { tagId -> viewModel?.toggleReferencedTagExpanded(tagId) ?: Unit },
                            level = 0, // 在白色背景内，不需要额外缩进
                            viewModel = viewModel,
                            selectedTagIds = selectedTagIds
                        )
                    } else {
                        // 如果无法获取完整信息，显示为叶子项
                        var tagName by remember(ref.childTagId) { mutableStateOf("标签#${ref.childTagId}") }
                        
                        LaunchedEffect(ref.childTagId, viewModel) {
                            if (viewModel != null) {
                                try {
                                    val name = viewModel.getTagNameById(ref.childTagId)
                                    if (name != null) {
                                        tagName = name
                                    }
                                } catch (e: Exception) {
                                    // 保持默认名称，避免闪退
                                    e.printStackTrace()
                                }
                            }
                        }
                        
                        TagSelectionLeafItemWithGrayText(
                            tag = TagEntity(id = ref.childTagId, name = tagName),
                            isSelected = ref.childTagId in selectedTagIds,
                            onSelect = onSelect,
                            level = 0, // 在白色背景内，不需要额外缩进
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TagSelectionLeafItem(
    tag: TagEntity,
    isSelected: Boolean,
    onSelect: (Long) -> Unit,
    level: Int,
    viewModel: MainViewModel?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp, bottom = 4.dp)
            .clickable { onSelect(tag.id) }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(20.dp))
        Icon(
            imageVector = Icons.Default.Tag,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tag.name,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val imageCount = if (viewModel != null) {
            val tagStats = remember(tag.id) { derivedStateOf { viewModel.tagStatistics[tag.id] } }
            tagStats.value?.totalImageCount ?: 0
        } else tag.imageCount
        if (imageCount > 0) {
            Text(text = "($imageCount)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onSelect(tag.id) },
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 引用标签的树形标签项（用于选择页面，使用灰色文字）
 */
@Composable
private fun TagSelectionTreeItemWithGrayText(
    tagWithChildren: TagWithChildren,
    isSelected: Boolean,
    onSelect: (Long) -> Unit,
    expandedTagIds: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    level: Int,
    viewModel: MainViewModel?,
    selectedTagIds: Set<Long>
) {
    val tag = tagWithChildren.tag
    val hasChildren = tagWithChildren.children.isNotEmpty() || tagWithChildren.referencedTags.isNotEmpty()
    val isExpanded = expandedTagIds.contains(tag.id)

    // 白色圆角背景容器
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp, bottom = 4.dp)
            .background(
                Color.White,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(tag.id) }
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 展开/折叠（带动画）
                if (hasChildren) {
                    IconButton(onClick = { onToggleExpand(tag.id) }, modifier = Modifier.size(20.dp)) {
                        val rotation by animateFloatAsState(
                            targetValue = if (isExpanded) 90f else 0f,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            label = "arrow_rotation"
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "折叠" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), // 灰色
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }

                // 标签图标与名称（灰色）
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // 灰色
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tag.name,
                    fontSize = 14.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), // 灰色
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // 数量（灰色）
                val imageCount = if (viewModel != null) {
                    val tagStats = remember(tag.id) {
                        derivedStateOf { viewModel.tagStatistics[tag.id] }
                    }
                    tagStats.value?.totalImageCount ?: 0
                } else tag.imageCount
                if (imageCount > 0) {
                    Text(
                        text = "($imageCount)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // 更浅的灰色
                    )
                }

                // 多选状态指示器（Checkbox）
                Spacer(modifier = Modifier.width(6.dp))
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect(tag.id) },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // 子项（引用标签的子项也使用灰色，带动画）
    AnimatedVisibility(
        visible = isExpanded && hasChildren,
        enter = expandVertically(
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Column {
            // 子标签（仅一层）
            tagWithChildren.children.forEach { childTag: TagEntity ->
                TagSelectionLeafItemWithGrayText(
                    tag = childTag,
                    isSelected = childTag.id in selectedTagIds,
                    onSelect = onSelect,
                    level = level + 1,
                    viewModel = viewModel
                )
            }

            // 引用标签（递归展开）
            tagWithChildren.referencedTags.forEach { ref: com.example.yumoflatimagemanager.data.local.TagReferenceEntity ->
            var referencedTagWithChildren by remember(ref.childTagId) { mutableStateOf<TagWithChildren?>(null) }
            
            // 异步获取完整的引用标签信息
            LaunchedEffect(ref.childTagId, viewModel) {
                if (viewModel != null) {
                    try {
                        val allTags: List<com.example.yumoflatimagemanager.data.local.TagWithChildren> = viewModel.getAllTagsWithChildren()
                        val foundTag = allTags.find { it.tag.id == ref.childTagId }
                        if (foundTag != null) {
                            referencedTagWithChildren = foundTag
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            if (referencedTagWithChildren != null) {
                TagSelectionTreeItemWithGrayText(
                    tagWithChildren = referencedTagWithChildren!!,
                    isSelected = referencedTagWithChildren!!.tag.id in selectedTagIds,
                    onSelect = onSelect,
                    expandedTagIds = viewModel?.expandedReferencedTagIds ?: emptySet(),
                    onToggleExpand = { tagId -> viewModel?.toggleReferencedTagExpanded(tagId) ?: Unit },
                    level = level + 1,
                    viewModel = viewModel,
                    selectedTagIds = selectedTagIds
                )
            } else {
                var tagName by remember(ref.childTagId) { mutableStateOf("标签#${ref.childTagId}") }
                
                LaunchedEffect(ref.childTagId, viewModel) {
                    if (viewModel != null) {
                        try {
                            val name = viewModel.getTagNameById(ref.childTagId)
                            if (name != null) {
                                tagName = name
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                TagSelectionLeafItemWithGrayText(
                    tag = TagEntity(id = ref.childTagId, name = tagName),
                    isSelected = ref.childTagId in selectedTagIds,
                    onSelect = onSelect,
                    level = level + 1,
                    viewModel = viewModel
                )
            }
        }
        }
    }
}

/**
 * 引用标签的叶子项（使用灰色文字）
 */
@Composable
private fun TagSelectionLeafItemWithGrayText(
    tag: TagEntity,
    isSelected: Boolean,
    onSelect: (Long) -> Unit,
    level: Int,
    viewModel: MainViewModel?
) {
    // 白色圆角背景容器
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp, bottom = 4.dp)
            .background(
                Color.White,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(tag.id) }
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(20.dp))
            Icon(
                imageVector = Icons.Default.Tag,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // 灰色
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tag.name,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), // 灰色
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val imageCount = if (viewModel != null) {
                val tagStats = remember(tag.id) { derivedStateOf { viewModel.tagStatistics[tag.id] } }
                tagStats.value?.totalImageCount ?: 0
            } else tag.imageCount
            if (imageCount > 0) {
                Text(text = "($imageCount)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) // 更浅的灰色
            }
            Spacer(modifier = Modifier.width(6.dp))
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect(tag.id) },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// 工具：树匹配（名称包含任一层）
private fun matchesTree(node: TagWithChildren, query: String): Boolean {
    if (node.tag.name.contains(query, ignoreCase = true)) return true
    if (node.children.any { it.name.contains(query, ignoreCase = true) }) return true
    // 引用标签的匹配（这里简化处理，只检查ID）
    if (node.referencedTags.any { it.childTagId.toString().contains(query, ignoreCase = true) }) return true
    return false
}

