package com.example.yumoflatimagemanager.ui.drawer.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.ui.components.CreateTagDialog
import com.example.yumoflatimagemanager.ui.components.DeleteTagDialog
import com.example.yumoflatimagemanager.ui.components.RenameTagDialog
import com.example.yumoflatimagemanager.ui.dialog.ReferenceTagSortDialog

/**
 * 标签管理相关对话框容器组件
 */
@Composable
fun TagDialogsContainer(
    viewModel: MainViewModel,
    showResetDialog: Boolean,
    onResetDismiss: () -> Unit,
    onResetConfirm: () -> Unit,
    onClose: () -> Unit,
    showAlbumResetDialog: Boolean = false, // 新增：是否显示相册详情页的重置对话框
    onAlbumResetDismiss: () -> Unit = {} // 新增：相册详情页重置对话框的关闭回调
) {
    // 创建标签对话框
    if (viewModel.showCreateTagDialog) {
        CreateTagDialog(
            onDismiss = { viewModel.showCreateTagDialog = false },
            onConfirm = { tagName ->
                viewModel.addTag(tagName)
                viewModel.showCreateTagDialog = false
            }
        )
    }
    
    // 重命名标签对话框
    if (viewModel.showRenameTagDialog && viewModel.tagToRename != null) {
        RenameTagDialog(
            tag = viewModel.tagToRename!!,
            onDismiss = { viewModel.showRenameTagDialog = false },
            onConfirm = { newName ->
                viewModel.renameTag(newName)
                viewModel.showRenameTagDialog = false
            }
        )
    }
    
    // 删除标签确认对话框
    if (viewModel.showDeleteTagDialog && viewModel.tagToDelete != null) {
        DeleteTagDialog(
            tag = viewModel.tagToDelete!!,
            onDismiss = { viewModel.showDeleteTagDialog = false },
            onConfirm = {
                viewModel.deleteTag()
                viewModel.showDeleteTagDialog = false
            },
            viewModel = viewModel
        )
    }

    // 添加引用对话框
    if (viewModel.showAddTagReferenceDialog) {
        AddTagReferenceDialog(
            available = viewModel.availableTagsForReference,
            onDismiss = { viewModel.hideAddTagReferenceDialog() },
            onSelect = { tag -> viewModel.addTagReferenceFromDialog(tag.id) }
        )
    }

    // 引用标签排序对话框
    if (viewModel.showReferenceTagSortDialog && viewModel.parentTagForReferenceSort != null) {
        ReferenceTagSortDialog(
            parentTag = viewModel.parentTagForReferenceSort!!,
            viewModel = viewModel,
            onDismiss = { viewModel.hideReferenceTagSortDialog() }
        )
    }
    
    // 重置确认对话框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = onResetDismiss,
            title = { Text("重置标签状态") },
            text = { Text("要重置你的标签树展开和选择吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllTagFilters()
                        onResetDismiss()
                        onClose()
                    }
                ) {
                    Text("重置")
                }
            },
            dismissButton = {
                TextButton(onClick = onResetDismiss) {
                    Text("取消")
                }
            }
        )
    }
    
    // 相册详情页的重置确认对话框
    if (showAlbumResetDialog) {
        AlertDialog(
            onDismissRequest = onAlbumResetDismiss,
            title = { Text("重置标签状态") },
            text = { Text("要重置你的标签树展开和选择吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllTagFilters()
                        onAlbumResetDismiss()
                    }
                ) {
                    Text("重置")
                }
            },
            dismissButton = {
                TextButton(onClick = onAlbumResetDismiss) {
                    Text("取消")
                }
            }
        )
    }
    
    // 来自相册详情页的重置确认对话框
    if (viewModel.showResetConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideResetConfirmationDialog() },
            title = { Text("重置标签状态") },
            text = { Text("要重置你的标签树展开和选择吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllTagFilters()
                        viewModel.hideResetConfirmationDialog()
                        onClose()
                    }
                ) {
                    Text("重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideResetConfirmationDialog() }) {
                    Text("取消")
                }
            }
        )
    }
    
}

