package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagGroupEntity
import kotlinx.coroutines.delay

/**
 * 标签组导航栏组件
 * - 可左右滑动的标签组列表
 * - 支持单击切换标签组、双击配置标签组
 * - 右侧包含"新建标签组"和"展开管理"按钮
 */
@Composable
fun TagGroupNavigationBar(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // 简化版本：使用硬编码的标签组，为后续集成做准备
    val tagGroups = remember { listOf(
        TagGroupEntity(id = 1, name = "未分组", sortOrder = 0, isDefault = true),
        TagGroupEntity(id = 2, name = "工作", sortOrder = 1000, isDefault = false),
        TagGroupEntity(id = 3, name = "生活", sortOrder = 2000, isDefault = false),
        TagGroupEntity(id = 4, name = "旅行", sortOrder = 3000, isDefault = false),
        TagGroupEntity(id = 5, name = "美食", sortOrder = 4000, isDefault = false)
    ) }
    
    var selectedGroupId by remember { mutableStateOf(1L) } // 默认选中"未分组"标签组
    
    // 双击检测状态
    var lastClickTime by remember { mutableStateOf(0L) }
    val DOUBLE_CLICK_DELAY = 300L
    
    // 新建标签组对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    
    // 标签组重命名对话框状态
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedTagGroup by remember { mutableStateOf<TagGroupEntity?>(null) }
    var renameGroupName by remember { mutableStateOf("") }
    
    Column(modifier = modifier) {
        // 标签组导航栏主内容
        Box(modifier = Modifier.fillMaxWidth()) {
            // 横向滚动的标签组列表
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(tagGroups, key = { it.id }) {
                    TagGroupItem(
                        tagGroup = it,
                        isSelected = it.id == selectedGroupId,
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime < DOUBLE_CLICK_DELAY) {
                                // 双击：打开标签组配置页面
                                selectedTagGroup = it
                                renameGroupName = it.name
                                showRenameDialog = true
                            } else {
                                // 单击：切换标签组
                                selectedGroupId = it.id
                            }
                            lastClickTime = currentTime
                        }
                    )
                }
            }
            
            // 右侧按钮组
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 16.dp),
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
                
                // 展开标签组管理页面的"V"型按钮
                IconButton(
                    onClick = { /* 暂时不实现展开管理功能 */ },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "展开标签组管理",
                        tint = MaterialTheme.colorScheme.primary
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
                                // 暂时不实现创建标签组功能，只关闭对话框
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
        
        // 标签组重命名对话框
        if (showRenameDialog && selectedTagGroup != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text(text = "重命名标签组") },
                text = {
                    OutlinedTextField(
                        value = renameGroupName,
                        onValueChange = { renameGroupName = it },
                        label = { Text(text = "新名称") },
                        placeholder = { Text(text = "输入标签组新名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameGroupName.isNotBlank()) {
                                // 暂时不实现重命名标签组功能，只关闭对话框
                                renameGroupName = ""
                                showRenameDialog = false
                            }
                        },
                        enabled = renameGroupName.isNotBlank()
                    ) {
                        Text(text = "确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
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
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
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
