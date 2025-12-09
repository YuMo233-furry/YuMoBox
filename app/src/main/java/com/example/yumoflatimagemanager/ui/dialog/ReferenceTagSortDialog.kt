package com.example.yumoflatimagemanager.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import com.example.yumoflatimagemanager.ui.drawer.components.ReferencedTagTreeItem
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import kotlinx.coroutines.runBlocking

/**
 * 层级数据类，用于存储每一层的信息
 */
private data class SortLayer(
    val parentTag: TagWithChildren,
    val referencedTags: List<TagWithChildren>,
    // 本地状态，用于拖拽时实时更新
    var localReferencedTags: List<TagWithChildren>
)

/**
 * 引用标签排序对话框
 * 支持递归展开多层引用标签进行排序
 */
@Composable
fun ReferenceTagSortDialog(
    parentTag: TagWithChildren,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    // 初始化第一层 - 按 sortOrder 排序
    val initialReferencedTags = remember(parentTag.referencedTags) {
        parentTag.referencedTags
            .sortedBy { it.sortOrder }  // 按 sortOrder 排序
            .mapNotNull { ref ->
                runBlocking {
                    viewModel.getTagWithChildrenForUi(ref.childTagId)
                }
            }
    }
    
    // 层级栈，管理多层嵌套
    var layerStack by remember {
        mutableStateOf(
            listOf(
                SortLayer(
                    parentTag = parentTag,
                    referencedTags = initialReferencedTags,
                    localReferencedTags = initialReferencedTags
                )
            )
        )
    }
    
    // 当前层
    val currentLayer = layerStack.last()
    
    // 监听刷新触发器，当引用标签排序发生变化时重新加载
    LaunchedEffect(viewModel.tagReferenceRefreshTrigger) {
        // 从数据库重新获取当前父标签的最新数据
        val refreshedParentTag = viewModel.getTagWithChildrenForUi(currentLayer.parentTag.tag.id)
        
        if (refreshedParentTag != null) {
            // 重新加载当前层的引用标签，按 sortOrder 排序
            val updatedReferencedTags = refreshedParentTag.referencedTags
                .sortedBy { it.sortOrder }  // 按 sortOrder 排序
                .mapNotNull { ref ->
                    viewModel.getTagWithChildrenForUi(ref.childTagId)
                }
            
            println("DEBUG: 刷新引用标签列表 - 父标签: ${refreshedParentTag.tag.name}, 引用数量: ${updatedReferencedTags.size}")
            println("DEBUG: 引用标签排序值: ${refreshedParentTag.referencedTags.sortedBy { it.sortOrder }.map { "ID=${it.childTagId}, sortOrder=${it.sortOrder}" }}")
            
            // 更新当前层的本地状态
            layerStack = layerStack.dropLast(1) + listOf(
                currentLayer.copy(
                    parentTag = refreshedParentTag,
                    referencedTags = updatedReferencedTags,
                    localReferencedTags = updatedReferencedTags
                )
            )
        }
    }
    
    // 进入下一层（子引用标签的排序）
    val navigateToChild: (TagWithChildren) -> Unit = { childTag ->
        val childReferencedTags = childTag.referencedTags
            .sortedBy { it.sortOrder }  // 按 sortOrder 排序
            .mapNotNull { ref ->
                runBlocking {
                    viewModel.getTagWithChildrenForUi(ref.childTagId)
                }
            }
        
        layerStack = layerStack + SortLayer(
            parentTag = childTag,
            referencedTags = childReferencedTags,
            localReferencedTags = childReferencedTags
        )
    }
    
    // 返回上一层
    val navigateBack: () -> Unit = {
        if (layerStack.size > 1) {
            layerStack = layerStack.dropLast(1)
        } else {
            // 第一层，关闭对话框
            onDismiss()
        }
    }
    
    // 更新本地排序状态
    val updateLocalTags: (List<TagWithChildren>) -> Unit = { newTags ->
        layerStack = layerStack.dropLast(1) + listOf(
            currentLayer.copy(localReferencedTags = newTags)
        )
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "引用标签排序",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        
                        // 面包屑导航
                        Text(
                            text = layerStack.joinToString(" > ") { it.parentTag.tag.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider()
                
                // 引用标签列表
                if (currentLayer.localReferencedTags.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "暂无引用标签",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    ReorderableColumn(
                        list = currentLayer.localReferencedTags,
                        onSettle = { fromIndex, toIndex ->
                            // 更新引用标签的排序
                            viewModel.moveChildTag(currentLayer.parentTag.tag.id, fromIndex, toIndex)
                            // 更新本地状态
                            val newTags = currentLayer.localReferencedTags.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                            updateLocalTags(newTags)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) { index, tagWithChildren, isDragging ->
                        key(tagWithChildren.tag.id) {
                            ReorderableItem {
                                ReferencedTagTreeItem(
                                    parentTagId = currentLayer.parentTag.tag.id,
                                    childTagId = tagWithChildren.tag.id,
                                    viewModel = viewModel,
                                    level = layerStack.size,
                                    useReferencedTagExpansion = true,
                                    reorderableScope = this@ReorderableItem
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider()
                
                // 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：返回按钮
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (layerStack.size > 1) "返回上一层" else "关闭"
                        )
                    }
                    
                    // 右侧：保存所有按钮
                    Button(
                        onClick = {
                            // 保存所有层级的排序结果并关闭
                            onDismiss()
                        }
                    ) {
                        Text("保存所有")
                    }
                }
            }
        }
    }
}