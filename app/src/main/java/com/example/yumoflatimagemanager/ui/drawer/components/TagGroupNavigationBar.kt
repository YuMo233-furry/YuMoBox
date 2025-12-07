package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagGroupEntity
import com.example.yumoflatimagemanager.feature.tag.TagViewModelNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    
    // 双击检测状态
    var lastClickTime by remember { mutableStateOf(0L) }
    val DOUBLE_CLICK_DELAY = 300L
    
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
    
    // 标签组拖拽排序状态
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var tagGroupsState by remember { mutableStateOf(tagGroups) }
    
    // 协程作用域
    val coroutineScope = rememberCoroutineScope()
    
    // 展开管理按钮旋转动画
    val expandButtonRotation by animateFloatAsState(
        targetValue = if (isManagementExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    
    // 监听标签组变化，更新tagGroupsState
    LaunchedEffect(tagGroups) {
        tagGroupsState = tagGroups
    }
    
    Box(modifier = modifier) {
        Column {
            // 标签组导航栏主内容
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 横向滚动的标签组列表 - 占据大部分宽度
                LazyRow(
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
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastClickTime < DOUBLE_CLICK_DELAY) {
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
                                } else {
                                    // 单击：切换标签组
                                    // 实现未选中功能：重复点击已选中标签组进入未选择状态
                                    tagViewModel.selectTagGroup(it.id)
                                }
                                lastClickTime = currentTime
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
                    .zIndex(1f)
            ) {
                Column {
                    // 标题
                    Text(
                        text = "标签组管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // 平铺显示所有标签组，每行3个标签，支持拖拽排序
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        itemsIndexed(tagGroupsState) { index, tagGroup ->
                            val isDragging = draggedItemIndex == index
                            
                            Box(
                                modifier = Modifier
                                    .background(if (isDragging) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .border(
                                        width = if (isDragging) 2.dp else 0.dp,
                                        color = if (isDragging) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .padding(8.dp)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .pointerInput(tagGroup) {
                                        // 长按1.5秒后直接拖动排序
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggedItemIndex = index },
                                            onDrag = { change, offset ->
                                                change.consume()
                                                // 拖拽过程中更新位置
                                                if (draggedItemIndex != null) {
                                                    val newIndex = (draggedItemIndex!! + (offset.y / 100).toInt()).coerceIn(0, tagGroupsState.size - 1)
                                                    if (newIndex != draggedItemIndex) {
                                                        // 更新标签组顺序
                                                        val reorderedGroups = tagGroupsState.toMutableList()
                                                        val movedGroup = reorderedGroups.removeAt(draggedItemIndex!!)
                                                        reorderedGroups.add(newIndex, movedGroup)
                                                        
                                                        // 更新本地状态，立即反映拖拽效果
                                                        tagGroupsState = reorderedGroups
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                // 拖拽结束，更新数据库中的排序
                                                tagViewModel.reorderTagGroups(tagGroupsState)
                                                draggedItemIndex = null
                                            }
                                        )
                                    }
                            ) {
                                TagGroupItem(
                                    tagGroup = tagGroup,
                                    isSelected = tagGroup.id == selectedTagGroupId,
                                    onClick = {
                                        // 单击：切换标签组，但不关闭管理视图
                                        tagViewModel.selectTagGroup(tagGroup.id)
                                    },
                                    onDoubleClick = {
                                        // 双击：打开标签组编辑对话框
                                        // 检查是否为默认标签组，如果是则不允许编辑
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
                                    }
                                )
                            }
                        }
                    }
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
 * 单个标签组项组件
 */
@Composable
private fun TagGroupItem(
    tagGroup: TagGroupEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null
) {
    // 双击检测状态
    var lastClickTime by remember { mutableStateOf(0L) }
    val DOUBLE_CLICK_DELAY = 300L
    
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
