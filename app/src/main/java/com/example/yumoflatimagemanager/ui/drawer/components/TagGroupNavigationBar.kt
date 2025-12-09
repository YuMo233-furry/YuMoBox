package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagGroupEntity
import com.example.yumoflatimagemanager.feature.tag.TagViewModelNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * 标签组导航栏组件
 * - 可左右滑动的标签组列表
 * - 支持单击切换标签组、双击配置标签组
 * - 右侧包含"新建标签组"和"展开管理"按钮
 * - 实现设计文档要求的所有功能
 */
@Composable
fun TagGroupNavigationBar(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // 从ViewModel获取标签组数据
    val tagGroups by viewModel.tagGroupsFlow.collectAsState(initial = emptyList())
    val tagViewModel = viewModel.tagViewModel
    
    // 获取当前选中的标签组ID（可能为null，表示未选择任何标签组）
    val selectedTagGroupId by remember {
        derivedStateOf { tagViewModel.selectedTagGroupId }
    }
    
    // 新建标签组对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    
    // 标签组编辑对话框状态
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedTagGroup by remember { mutableStateOf<TagGroupEntity?>(null) }
    var renameGroupName by remember { mutableStateOf("") }
    
    // 标签选择相关状态
    var allTags by remember { mutableStateOf<List<TagEntity>>(emptyList()) }
    var selectedTags by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var tagSearchQuery by remember { mutableStateOf("") }
    
    // 标签组管理展开状态
    var isManagementExpanded by remember { mutableStateOf(false) }
    
    // 协程作用域
    val coroutineScope = rememberCoroutineScope()
    
    // 展开管理按钮旋转动画
    val expandButtonRotation by animateFloatAsState(
        targetValue = if (isManagementExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    
    Box(modifier = modifier) {
        Column {
            // 标签组导航栏主内容
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 横向滚动的标签组列表 - 占据大部分宽度
                val rowState = rememberLazyListState()
                // 跟踪是否已执行过初始定位
                var hasInitialized by remember { mutableStateOf(false) }
                // 自动定位到选中项 - 仅在首次加载时执行一次
                LaunchedEffect(selectedTagGroupId, tagGroups) {
                    if (!hasInitialized && selectedTagGroupId != null && tagGroups.isNotEmpty()) {
                        val targetIndex = tagGroups.indexOfFirst { it.id == selectedTagGroupId }
                        if (targetIndex >= 0) {
                            rowState.animateScrollToItem(targetIndex)
                            hasInitialized = true
                        }
                    }
                }
                LazyRow(
                    state = rowState,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    items(tagGroups, key = { it.id }) {
                        TagGroupItem(
                            tagGroup = it,
                            isSelected = it.id == selectedTagGroupId,
                            onClick = {
                                // 单击：切换标签组
                                tagViewModel.selectTagGroup(it.id)
                            },
                            onDoubleClick = {
                                // 双击：打开标签组编辑对话框
                                // 检查是否为默认标签组，如果是则不允许编辑
                                if (it.isDefault) {
                                    // 不允许编辑默认标签组
                                    return@TagGroupItem
                                }
                                
                                selectedTagGroup = it
                                renameGroupName = it.name
                                
                                // 加载所有标签和当前标签组中的标签
                                coroutineScope.launch {
                                    try {
                                        // 加载所有标签
                                        val tagsWithChildren = viewModel.tagsFlow.first()
                                        val tagEntities = mutableListOf<TagEntity>()
                                        
                                        // 收集所有本体标签
                                        tagsWithChildren.forEach { tagWithChild ->
                                            tagEntities.add(tagWithChild.tag)
                                        }
                                        
                                        // 去重并排序
                                        allTags = tagEntities.distinctBy { it.id }.sortedBy { it.name }
                                        
                                        // 加载当前标签组中的标签
                                        val tagsInGroup = tagViewModel.getTagsByTagGroupId(it.id)
                                        val tagIdsInGroup = tagsInGroup.map { it.id }.toSet()
                                        selectedTags = tagIdsInGroup
                                        println("DEBUG: 加载标签组 ${it.id} 中的标签: $tagIdsInGroup")
                                    } catch (e: Exception) {
                                        // 处理异常
                                        println("ERROR: 加载标签组标签失败 - 标签组ID: ${it.id}, 错误: ${e.message}")
                                        allTags = emptyList()
                                        selectedTags = emptySet()
                                    }
                                }
                                showRenameDialog = true
                            }
                        )
                    }
                }
                
                // 右侧固定按钮组 - 固定宽度，不影响LazyRow
                Row(
                    modifier = Modifier.padding(end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 新建标签组按钮
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建标签组",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 展开标签组管理页面的按钮（带旋转动画）
                    IconButton(
                        onClick = { isManagementExpanded = !isManagementExpanded },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (isManagementExpanded) "收起管理" else "展开管理",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.rotate(expandButtonRotation)
                        )
                    }
                }
            }
        }
        
        // 管理页面叠加层 - 仅在展开时显示，绝对定位覆盖在原UI上
        AnimatedVisibility(
            visible = isManagementExpanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    .padding(16.dp)
            ) {
                Column {
                    // 标题
                    Text(
                        text = "标签组管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // 平铺显示所有标签组，支持拖拽排序
                    ReorderableTagGroupGrid(
                        tagGroups = tagGroups,
                        tagViewModel = tagViewModel,
                        selectedTagGroupId = selectedTagGroupId,
                        onTagGroupClick = { tagGroup ->
                            tagViewModel.selectTagGroup(tagGroup.id)
                        },
                        onTagGroupDoubleClick = { tagGroup ->
                            if (!tagGroup.isDefault) {
                                selectedTagGroup = tagGroup
                                renameGroupName = tagGroup.name
                                
                                // 加载所有标签和当前标签组中的标签
                                coroutineScope.launch {
                                    try {
                                        // 加载所有标签
                                        val tagsWithChildren = viewModel.tagsFlow.first()
                                        val tagEntities = mutableListOf<TagEntity>()
                                        
                                        // 收集所有本体标签
                                        tagsWithChildren.forEach { tagWithChild ->
                                            tagEntities.add(tagWithChild.tag)
                                        }
                                        
                                        // 去重并排序
                                        allTags = tagEntities.distinctBy { it.id }.sortedBy { it.name }
                                        
                                        // 加载当前标签组中的标签
                                        val tagsInGroup = tagViewModel.getTagsByTagGroupId(tagGroup.id)
                                        val tagIdsInGroup = tagsInGroup.map { it.id }.toSet()
                                        selectedTags = tagIdsInGroup
                                        println("DEBUG: 加载标签组 ${tagGroup.id} 中的标签: $tagIdsInGroup")
                                    } catch (e: Exception) {
                                        // 处理异常
                                        println("ERROR: 加载标签组标签失败 - 标签组ID: ${tagGroup.id}, 错误: ${e.message}")
                                        allTags = emptyList()
                                        selectedTags = emptySet()
                                    }
                                }
                                showRenameDialog = true
                            }
                        },
                        modifier = Modifier.height(300.dp)
                    )
                }
            }
        }
        
        // 新建标签组对话框
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text(text = "新建标签组") },
                text = {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text(text = "标签组名称") },
                        placeholder = { Text(text = "输入标签组名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newGroupName.isNotBlank()) {
                                tagViewModel.createTagGroup(newGroupName)
                                newGroupName = ""
                                showCreateDialog = false
                            }
                        },
                        enabled = newGroupName.isNotBlank()
                    ) {
                        Text(text = "创建")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(text = "取消")
                    }
                }
            )
        }
        
        // 标签组编辑对话框
        if (showRenameDialog && selectedTagGroup != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text(text = "编辑标签组") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 标签组名称输入
                        OutlinedTextField(
                            value = renameGroupName,
                            onValueChange = { renameGroupName = it },
                            label = { Text(text = "标签组名称") },
                            placeholder = { Text(text = "输入标签组新名称") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 标签选择标题
                        Text(
                            text = "标签选择",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // 标签搜索框
                        OutlinedTextField(
                            value = tagSearchQuery,
                            onValueChange = { tagSearchQuery = it },
                            label = { Text(text = "搜索标签") },
                            placeholder = { Text(text = "输入标签名称") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 标签选择列表
                        Box(modifier = Modifier.height(200.dp)) {
                            LazyColumn {
                                items(allTags) { tag ->
                                    // 过滤标签
                                    if (tagSearchQuery.isBlank() || tag.name.contains(tagSearchQuery, ignoreCase = true)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // 切换标签选择状态
                                                    selectedTags = if (selectedTags.contains(tag.id)) {
                                                        selectedTags - tag.id
                                                    } else {
                                                        selectedTags + tag.id
                                                    }
                                                }
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Checkbox(
                                                checked = selectedTags.contains(tag.id),
                                                onCheckedChange = {
                                                    selectedTags = if (it) {
                                                        selectedTags + tag.id
                                                    } else {
                                                        selectedTags - tag.id
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = tag.name)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 删除标签组按钮 - 左下角
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    selectedTagGroup?.let { group ->
                                        if (!group.isDefault) {
                                            tagViewModel.deleteTagGroupWithUndo(group)
                                            renameGroupName = ""
                                            selectedTags = emptySet()
                                            tagSearchQuery = ""
                                            showRenameDialog = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(text = "删除标签组")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameGroupName.isNotBlank()) {
                                selectedTagGroup?.let {
                                    val tagGroupId = it.id
                                    
                                    coroutineScope.launch {
                                        try {
                                            // 重命名标签组
                                            tagViewModel.renameTagGroup(it, renameGroupName)
                                            
                                            // 获取当前标签组中的所有标签
                                            val currentTagsInGroup = tagViewModel.getTagsByTagGroupId(tagGroupId)
                                            val currentTagIds = currentTagsInGroup.map { it.id }.toSet()
                                            
                                            // 需要添加的标签
                                            val tagsToAdd = selectedTags - currentTagIds
                                            // 需要移除的标签
                                            val tagsToRemove = currentTagIds - selectedTags
                                            
                                            // 添加标签到标签组
                                            tagsToAdd.forEach { tagId ->
                                                tagViewModel.addTagToTagGroup(tagId, tagGroupId)
                                            }
                                            
                                            // 从标签组移除标签
                                            tagsToRemove.forEach { tagId ->
                                                tagViewModel.removeTagFromTagGroup(tagId, tagGroupId)
                                            }
                                            
                                            // 重置状态
                                            withContext(Dispatchers.Main) {
                                                renameGroupName = ""
                                                selectedTags = emptySet()
                                                tagSearchQuery = ""
                                                showRenameDialog = false
                                            }
                                        } catch (e: Exception) {
                                            // 处理异常
                                            withContext(Dispatchers.Main) {
                                                // 显示错误提示
                                                androidx.compose.material3.SnackbarHostState().showSnackbar(
                                                    message = "保存标签组失败: ${e.message}",
                                                    actionLabel = "关闭"
                                                )
                                            }
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = renameGroupName.isNotBlank()
                    ) {
                        Text(text = "确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        // 重置状态
                        renameGroupName = ""
                        selectedTags = emptySet()
                        tagSearchQuery = ""
                        showRenameDialog = false
                    }) {
                        Text(text = "取消")
                    }
                }
            )
        }
    }
}

/**
 * 可拖拽排序的标签组网格
 */
@Composable
private fun ReorderableTagGroupGrid(
    tagGroups: List<TagGroupEntity>,
    tagViewModel: TagViewModelNew,
    selectedTagGroupId: Long?,
    onTagGroupClick: (TagGroupEntity) -> Unit,
    onTagGroupDoubleClick: (TagGroupEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    
    // 本地状态管理标签组列表，避免拖拽时的数据流更新导致抖动
    var localTagGroups by remember(tagGroups) { mutableStateOf(tagGroups) }
    
    // Channel 用于同步数据库更新
    val listUpdatedChannel = remember { Channel<Unit>(Channel.CONFLATED) }
    
    // 创建 Reorderable 状态
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        // 确保索引在有效范围内
        if (from.index in localTagGroups.indices && to.index in localTagGroups.indices) {
            // 立即更新本地状态（同步操作，避免抖动）
            val newTagGroups = localTagGroups.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            localTagGroups = newTagGroups
            
            // 异步更新数据库
            coroutineScope.launch {
                tagViewModel.reorderTagGroups(localTagGroups)
                
                // 通知数据库更新完成
                listUpdatedChannel.send(Unit)
            }
        }
    }
    
    // 监听数据流更新，使用 Channel 同步
    LaunchedEffect(tagGroups) {
        // 如果本地状态的标签组ID顺序和 tagGroups 不同，说明有外部更新
        val localTagGroupIds = localTagGroups.map { it.id }
        val newTagGroupIds = tagGroups.map { it.id }
        
        if (localTagGroupIds != newTagGroupIds) {
            // 等待数据库更新完成（如果有正在进行的更新）
            // 使用 withTimeoutOrNull 避免无限等待
            withTimeoutOrNull(100) {
                listUpdatedChannel.receive()
            }
            
            // 更新本地状态
            localTagGroups = tagGroups
        }
    }
    
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(
            items = localTagGroups,
            key = { tagGroup -> tagGroup.id }
        ) { tagGroup ->
            ReorderableItem(
                reorderableState,
                key = tagGroup.id
            ) { isDragging ->
                TagGroupItem(
                    tagGroup = tagGroup,
                    isSelected = tagGroup.id == selectedTagGroupId,
                    onClick = { onTagGroupClick(tagGroup) },
                    onDoubleClick = { onTagGroupDoubleClick(tagGroup) },
                    isDragging = isDragging,
                    reorderableScope = this
                )
            }
        }
    }
}

/**
 * 单个标签组项组件
 */
@Composable
private fun TagGroupItem(
    tagGroup: TagGroupEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    isDragging: Boolean = false,
    reorderableScope: ReorderableCollectionItemScope? = null
) {
    // 双击检测状态
    var lastClickTime by remember { mutableStateOf(0L) }
    val DOUBLE_CLICK_DELAY = 300L
    
    val hapticFeedback = LocalHapticFeedback.current
    
    Box(
        modifier = Modifier
            // 基础样式
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            // 适当的内边距
            .padding(horizontal = 16.dp, vertical = 10.dp)
            // 长按拖拽手柄（仅在管理页面启用）
            .then(
                if (reorderableScope != null) {
                    with(reorderableScope) {
                        Modifier.longPressDraggableHandle()
                    }
                } else {
                    Modifier
                }
            )
            // 点击处理
            .clickable {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < DOUBLE_CLICK_DELAY && onDoubleClick != null) {
                    // 双击
                    onDoubleClick()
                } else {
                    // 单击
                    onClick()
                }
                lastClickTime = currentTime
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标签组名称
            Text(
                text = tagGroup.name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
