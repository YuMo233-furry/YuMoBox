package com.example.yumoflatimagemanager.feature.tag.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import com.example.yumoflatimagemanager.feature.tag.model.DeletedTagCache
import com.example.yumoflatimagemanager.feature.tag.model.DeletedTagGroupCache

/**
 * 标签对话框状态管理类
 * 管理所有对话框的显示状态和相关数据
 */
class TagDialogState {
    
    // ==================== 对话框显示状态 ====================
    
    /** 创建标签对话框 */
    var showCreateTagDialog by mutableStateOf(false)
    
    /** 重命名标签对话框 */
    var showRenameTagDialog by mutableStateOf(false)
    
    /** 删除标签对话框 */
    var showDeleteTagDialog by mutableStateOf(false)
    
    /** 标签选择对话框 */
    var showTagSelectionDialog by mutableStateOf(false)
    
    /** 添加标签引用对话框 */
    var showAddTagReferenceDialog by mutableStateOf(false)
        private set
    
    /** 引用标签排序对话框 */
    var showReferenceTagSortDialog by mutableStateOf(false)
        private set
    
    // ==================== 对话框数据 ====================
    
    /** 待重命名的标签 */
    var tagToRename by mutableStateOf<TagEntity?>(null)
    
    /** 待删除的标签 */
    var tagToDelete by mutableStateOf<TagEntity?>(null)
    
    /** 选中用于添加引用的标签ID */
    var selectedTagForReference by mutableStateOf<Long?>(null)
        private set
    
    /** 可用于添加引用的标签列表 */
    var availableTagsForReference by mutableStateOf(listOf<TagEntity>())
        private set
    
    /** 待排序引用标签的父标签 */
    var parentTagForReferenceSort by mutableStateOf<TagWithChildren?>(null)
        private set
    
    // ==================== 撤回删除状态 ====================
    
    /** 最近删除的标签缓存 */
    var recentlyDeletedTag by mutableStateOf<DeletedTagCache?>(null)
        private set
    
    /** 显示撤回删除消息 */
    var showUndoDeleteMessage by mutableStateOf(false)
        private set
    
    /** 删除的标签名称 */
    var deletedTagName by mutableStateOf("")
        private set

    /** 最近删除的标签组缓存 */
    var recentlyDeletedTagGroup by mutableStateOf<DeletedTagGroupCache?>(null)
        private set

    /** 显示标签组撤回删除消息 */
    var showUndoDeleteTagGroupMessage by mutableStateOf(false)
        private set

    /** 删除的标签组名称 */
    var deletedTagGroupName by mutableStateOf("")
        private set
    
    // ==================== 更新方法 ====================
    
    fun showCreateDialog() {
        showCreateTagDialog = true
    }
    
    fun hideCreateDialog() {
        showCreateTagDialog = false
    }
    
    fun showRenameDialog(tag: TagEntity) {
        tagToRename = tag
        showRenameTagDialog = true
    }
    
    fun showRenameDialog() {
        showRenameTagDialog = true
    }
    
    fun hideRenameDialog() {
        showRenameTagDialog = false
        tagToRename = null
    }
    
    fun showDeleteDialog(tag: TagEntity) {
        tagToDelete = tag
        showDeleteTagDialog = true
    }
    
    fun showDeleteDialog() {
        showDeleteTagDialog = true
    }
    
    fun hideDeleteDialog() {
        showDeleteTagDialog = false
        tagToDelete = null
    }
    
    fun showTagSelection() {
        showTagSelectionDialog = true
    }
    
    fun hideTagSelection() {
        showTagSelectionDialog = false
    }
    
    fun showAddReferenceDialog(parentTagId: Long, availableTags: List<TagEntity>) {
        selectedTagForReference = parentTagId
        availableTagsForReference = availableTags
        showAddTagReferenceDialog = true
    }
    
    fun hideAddReferenceDialog() {
        showAddTagReferenceDialog = false
        selectedTagForReference = null
        availableTagsForReference = emptyList()
    }
    
    fun showReferenceTagSortDialog(parentTag: com.example.yumoflatimagemanager.data.local.TagWithChildren) {
        parentTagForReferenceSort = parentTag
        showReferenceTagSortDialog = true
    }
    
    fun hideReferenceTagSortDialog() {
        showReferenceTagSortDialog = false
        parentTagForReferenceSort = null
    }
    
    fun setDeletedTagCache(cache: DeletedTagCache) {
        recentlyDeletedTag = cache
        deletedTagName = cache.tag.name
        showUndoDeleteMessage = true
    }
    
    fun hideUndoDeleteMessage() {
        showUndoDeleteMessage = false
    }
    
    fun clearDeletedTagCache() {
        recentlyDeletedTag = null
        deletedTagName = ""
        showUndoDeleteMessage = false
    }

    fun setDeletedTagGroupCache(cache: DeletedTagGroupCache) {
        recentlyDeletedTagGroup = cache
        deletedTagGroupName = cache.tagGroupData.name
        showUndoDeleteTagGroupMessage = true
    }

    fun hideUndoDeleteTagGroupMessage() {
        showUndoDeleteTagGroupMessage = false
    }

    fun clearDeletedTagGroupCache() {
        recentlyDeletedTagGroup = null
        deletedTagGroupName = ""
        showUndoDeleteTagGroupMessage = false
    }
}

