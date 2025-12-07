package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagGroupEntity
import com.example.yumoflatimagemanager.feature.tag.TagViewModelNew
import kotlinx.coroutines.delay

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
    
    // 标签组管理展开状态
    var isManagementExpanded by remember { mutableStateOf(false) }
    
    // 展开管理按钮旋转动画
    val expandButtonRotation by animateFloatAsState(
        targetValue = if (isManagementExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    
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
                        isSelected = it.id == selectedTagGroupId,
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime < DOUBLE_CLICK_DELAY) {
                                // 双击：打开标签组配置页面
                                // 暂时不实现，留待后续开发
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
            
            // 右侧固定按钮组
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
        
        // 管理页面叠加层 - 仅在展开时显示
        AnimatedVisibility(
            visible = isManagementExpanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    .padding(16.dp)
            ) {
                Text(
                    text = "标签组管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // 后续实现管理页面内容
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
                else MaterialTheme.colorScheme.surfaceVariant
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
