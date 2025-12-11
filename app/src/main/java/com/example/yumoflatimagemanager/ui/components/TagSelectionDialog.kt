package com.example.yumoflatimagemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import kotlinx.coroutines.flow.Flow

/**
 * 标签选择对话框
 * 用于为多选图片添加标签
 */
@Composable
fun TagSelectionDialog(
    tagsFlow: Flow<List<TagWithChildren>>,
    onTagSelected: (Long) -> Unit,
    onTagRemoved: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel? = null
) {
    var tags by remember { mutableStateOf<List<TagWithChildren>>(emptyList()) }
    var selectedTagId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    // 移除本地 expandedTagIds，直接使用全局状态
    
    // 收集标签数据
    LaunchedEffect(tagsFlow) {
        try {
            tagsFlow.collect { tagList ->
                tags = tagList
                // 更新标签统计信息
                tagList.forEach { tagWithChildren ->
                    // 这里需要访问viewModel，但当前组件没有viewModel参数
                    // 需要在调用处处理统计信息更新
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "选择标签",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = { Text("搜索标签…") },
                    singleLine = true
                )

                if (tags.isEmpty()) {
                    // 显示空状态
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
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
                        // 对分组内的标签进行排序，与标签抽屉保持一致
                        a.sortWith(compareBy({ it.tag.sortOrder }, { it.tag.name }))
                        b.sortWith(compareBy({ it.tag.sortOrder }, { it.tag.name }))
                        Pair(a, b)
                    }

                    // 过滤（支持匹配任意子孙）
                    val filteredWithRefs = remember(withRefs, searchQuery) {
                        if (searchQuery.isBlank()) withRefs else withRefs.filter { matchesTree(it, searchQuery) }
                    }
                    val filteredWithoutRefs = remember(withoutRefs, searchQuery) {
                        if (searchQuery.isBlank()) withoutRefs else withoutRefs.filter { matchesTree(it, searchQuery) }
                    }

                    // 自动展开包含匹配子项的父节点（仅对本地expandedTagIds生效）
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
                        // 搜索时临时展开匹配的标签，但不影响全局状态
                        // 这里可以添加临时展开逻辑，但为了保持一致性，暂时不实现
                    }

                    // 绑定到全局 ViewModel 的展开集合，确保与标签抽屉保持一致且可持久化
                    val baseExpandedTagIds = viewModel?.expandedTagIds ?: emptySet()
                    val refExpandedTagIds = viewModel?.expandedReferencedTagIds ?: emptySet()
                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = listState
                    ) {
                        // 上半部分：有引用的标签
                        item {
                            if (filteredWithRefs.isNotEmpty()) {
                                Text(
                                    text = "含引用的标签",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                        items(count = filteredWithRefs.size) { index ->
                            // 引用区使用引用展开集合（与抽屉一致）
                            TagSelectionTreeItem(
                                tagWithChildren = filteredWithRefs[index],
                                isSelected = selectedTagId == filteredWithRefs[index].tag.id,
                                onSelect = { selectedTagId = it },
                                expandedTagIds = refExpandedTagIds,
                                onToggleExpand = { tagId ->
                                    viewModel?.toggleReferencedTagExpanded(tagId)
                                },
                                level = 0,
                                viewModel = viewModel,
                                selectedTagId = selectedTagId
                            )
                        }

                        // 分隔线
                        item {
                            if (filteredWithRefs.isNotEmpty() && filteredWithoutRefs.isNotEmpty()) {
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }

                        // 下半部分：无引用的标签
                        item {
                            if (filteredWithoutRefs.isNotEmpty()) {
                                Text(
                                    text = "普通标签",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                        items(count = filteredWithoutRefs.size) { index ->
                            TagSelectionTreeItem(
                                tagWithChildren = filteredWithoutRefs[index],
                                isSelected = selectedTagId == filteredWithoutRefs[index].tag.id,
                                onSelect = { selectedTagId = it },
                                expandedTagIds = baseExpandedTagIds,
                                onToggleExpand = { tagId ->
                                    viewModel?.toggleTagExpanded(tagId)
                                },
                                level = 0,
                                viewModel = viewModel,
                                selectedTagId = selectedTagId
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                // 移除按钮（左下角）
                TextButton(
                    onClick = {
                        selectedTagId?.let { tagId ->
                            onTagRemoved(tagId)
                        }
                    },
                    enabled = selectedTagId != null
                ) {
                    Text("移除")
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 取消按钮
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                
                // 确定按钮
                TextButton(
                    onClick = {
                        selectedTagId?.let { tagId ->
                            onTagSelected(tagId)
                        }
                    },
                    enabled = selectedTagId != null
                ) {
                    Text("确定")
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

/**
 * 标签选择项组件
 */
@Composable
private fun TagSelectionItem(
    tag: TagEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    level: Int,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp),
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标签图标
            Icon(
                imageVector = Icons.Default.Tag,
                contentDescription = "标签",
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 标签名称
            Text(
                text = tag.name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            
            // 图片数量
            val imageCount = if (viewModel != null) {
                val tagStats = remember(tag.id) {
                    derivedStateOf {
                        viewModel.tagStatistics[tag.id]
                    }
                }
                tagStats.value?.totalImageCount ?: 0
            } else {
                tag.imageCount
            }
            
            if (imageCount > 0) {
                Text(
                    text = "($imageCount)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 选中状态指示器
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 递归树形标签项（用于选择对话框）
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
    selectedTagId: Long? = null  // 添加参数以支持递归选中状态判断
) {
    val tag = tagWithChildren.tag
    val hasChildren = tagWithChildren.children.isNotEmpty() || tagWithChildren.referencedTags.isNotEmpty()
    val isExpanded = expandedTagIds.contains(tag.id)
    // 保持与标签抽屉一致的排序：子标签按 sortOrder/name，引用标签按 sortOrder
    val sortedChildren = remember(tagWithChildren.children) {
        tagWithChildren.children.sortedWith(compareBy<TagEntity>({ it.sortOrder }, { it.name.lowercase() }))
    }
    val sortedReferencedTags = remember(tagWithChildren.referencedTags) {
        tagWithChildren.referencedTags.sortedBy { it.sortOrder }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        onClick = { onSelect(tag.id) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 展开/折叠
            if (hasChildren) {
                IconButton(onClick = { onToggleExpand(tag.id) }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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

            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // 子项
    if (isExpanded && hasChildren) {
        // 子标签（仅一层）
        sortedChildren.forEach { childTag: TagEntity ->
            TagSelectionLeafItem(
                tag = childTag,
                isSelected = isSelected && childTag.id == tag.id,
                onSelect = onSelect,
                level = level + 1,
                viewModel = viewModel
            )
        }

        // 引用标签（递归展开）
        sortedReferencedTags.forEach { ref: com.example.yumoflatimagemanager.data.local.TagReferenceEntity ->
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
                // 引用标签使用引用展开状态，避免与本体标签联动
                TagSelectionTreeItem(
                    tagWithChildren = referencedTagWithChildren!!,
                    isSelected = selectedTagId == referencedTagWithChildren!!.tag.id,  // 正确传递选中状态
                    onSelect = onSelect,
                    expandedTagIds = if (level == 0) (viewModel?.expandedReferencedTagIds ?: emptySet()) else expandedTagIds,  // 顶层引用标签使用引用展开状态
                    onToggleExpand = if (level == 0) { tagId -> viewModel?.toggleReferencedTagExpanded(tagId) ?: Unit } else onToggleExpand,  // 顶层引用标签使用引用切换
                    level = level + 1,
                    viewModel = viewModel,
                    selectedTagId = selectedTagId
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
                
                TagSelectionLeafItem(
                    tag = TagEntity(id = ref.childTagId, name = tagName),
                    isSelected = false,  // 选中状态由Surface的onClick处理
                    onSelect = onSelect,
                    level = level + 1,
                    viewModel = viewModel
                )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        onClick = { onSelect(tag.id) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
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
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
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