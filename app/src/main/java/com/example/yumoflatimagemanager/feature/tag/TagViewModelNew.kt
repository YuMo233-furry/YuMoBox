package com.example.yumoflatimagemanager.feature.tag

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagGroupEntity
import com.example.yumoflatimagemanager.data.local.TagStatistics
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import com.example.yumoflatimagemanager.data.model.TagGroupData
import com.example.yumoflatimagemanager.data.model.TagGroupFileManager
import com.example.yumoflatimagemanager.data.repo.FileTagRepositoryImpl
import com.example.yumoflatimagemanager.domain.usecase.ObserveTagsUseCase
import com.example.yumoflatimagemanager.feature.tag.manager.TagCrudManager
import com.example.yumoflatimagemanager.feature.tag.manager.TagFilterManager
import com.example.yumoflatimagemanager.feature.tag.manager.TagPersistenceManager
import com.example.yumoflatimagemanager.feature.tag.manager.TagSortManager
import com.example.yumoflatimagemanager.feature.tag.manager.TagStatisticsManager
import com.example.yumoflatimagemanager.feature.tag.model.BatchResult
import com.example.yumoflatimagemanager.feature.tag.state.TagDialogState
import com.example.yumoflatimagemanager.feature.tag.state.TagState
import com.example.yumoflatimagemanager.media.MediaContentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * 标签 ViewModel - 协调器模式
 * 组合各个功能管理器，提供统一的API给UI层
 */
class TagViewModelNew(
    private val context: Context,
    private val mediaContentManager: MediaContentManager
) : ViewModel() {
    
    // 数据库和仓库
    private val db by lazy { com.example.yumoflatimagemanager.data.local.AppDatabase.get(context) }
    private val tagRepo by lazy { FileTagRepositoryImpl(db.tagDao()) }
    
    // 状态管理
    val tagState = TagState()
    val dialogState = TagDialogState()
    
    // 功能管理器
    private val crudManager = TagCrudManager(context, tagRepo, tagState, dialogState, viewModelScope)
    private val filterManager = TagFilterManager(tagRepo, tagState)
    private val statisticsManager = TagStatisticsManager(tagRepo, mediaContentManager, tagState, viewModelScope)
    private val sortManager = TagSortManager(tagRepo, viewModelScope)
    private val persistenceManager = TagPersistenceManager(tagState)
    
    // 标签流
    val tagsFlow: Flow<List<com.example.yumoflatimagemanager.data.local.TagWithChildren>> = ObserveTagsUseCase(tagRepo).invoke()
    
    // 标签组流 - 从文件系统获取
    val tagGroupsFlow: Flow<List<com.example.yumoflatimagemanager.data.local.TagGroupEntity>> = flow {
        // 初始加载
        emit(getAllTagGroupsFromFiles())
        
        // 监听标签组变化
        TagGroupFileManager.tagGroupChanges.collect {
            emit(getAllTagGroupsFromFiles())
        }
    }
    
    // 用于生成标签组ID的原子计数器
    private val tagGroupIdCounter = AtomicLong(1000L)
    
    // 初始化标签组ID计数器
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val allTagGroups = TagGroupFileManager.getAllTagGroups()
            if (allTagGroups.isNotEmpty()) {
                val maxId = allTagGroups.maxByOrNull { it.id }?.id ?: 1000L
                tagGroupIdCounter.set(maxId + 1)
            }
            
            // 恢复上次选中的标签组：若不存在则回退到默认或第一个
            val savedGroupId = persistenceManager.getSavedTagGroupId()
            val targetGroupId = when {
                savedGroupId != null && allTagGroups.any { it.id == savedGroupId } -> savedGroupId
                allTagGroups.any { it.isDefault } -> allTagGroups.first { it.isDefault }.id
                else -> allTagGroups.firstOrNull()?.id
            }
            withContext(Dispatchers.Main) {
                tagState.setTagGroupSelection(targetGroupId)
            }
        }
    }
    
    // ==================== 标签组状态访问器（代理到 tagState） ====================
    
    val selectedTagGroupId: Long? get() = tagState.selectedTagGroupId
    val isTagGroupDragMode: Boolean get() = tagState.isTagGroupDragMode
    val isTagGroupManagementVisible: Boolean get() = tagState.isTagGroupManagementVisible
    
    // ==================== 状态访问器（代理到 tagState） ====================
    
    val activeTagFilterIds: Set<Long> get() = tagState.activeTagFilterIds
    val excludedTagIds: Set<Long> get() = tagState.excludedTagIds
    val expandedTagIds: Set<Long> get() = tagState.expandedTagIds
    val expandedReferencedTagIds: Set<Long> get() = tagState.expandedReferencedTagIds
    val tagDrawerScrollIndex: Int get() = tagState.tagDrawerScrollIndex
    val tagStatistics: Map<Long, TagStatistics> get() = tagState.tagStatistics
    val tagReferenceRefreshTrigger: Long get() = tagState.tagReferenceRefreshTrigger
    
    // ==================== 对话框状态访问器（代理到 dialogState） ====================
    
    val showCreateTagDialog: Boolean get() = dialogState.showCreateTagDialog
    val showRenameTagDialog: Boolean get() = dialogState.showRenameTagDialog
    val showDeleteTagDialog: Boolean get() = dialogState.showDeleteTagDialog
    val showTagSelectionDialog: Boolean get() = dialogState.showTagSelectionDialog
    val showAddTagReferenceDialog: Boolean get() = dialogState.showAddTagReferenceDialog
    val tagToRename: TagEntity? get() = dialogState.tagToRename
    val tagToDelete: TagEntity? get() = dialogState.tagToDelete
    val selectedTagForReference: Long? get() = dialogState.selectedTagForReference
    val availableTagsForReference: List<TagEntity> get() = dialogState.availableTagsForReference
    val recentlyDeletedTag get() = dialogState.recentlyDeletedTag
    val showUndoDeleteMessage: Boolean get() = dialogState.showUndoDeleteMessage
    val deletedTagName: String get() = dialogState.deletedTagName
    
    // ==================== CRUD 操作（代理到 crudManager） ====================
    
    fun addTag(name: String) = crudManager.addTag(name)
    
    fun showCreateTagDialog() = dialogState.showCreateDialog()
    fun hideCreateTagDialog() = dialogState.hideCreateDialog()
    
    fun showRenameTagDialog(tag: TagEntity) = dialogState.showRenameDialog(tag)
    fun showRenameTagDialog() = dialogState.showRenameDialog()
    fun showRenameTagDialog(tagId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val tag = tagRepo.getTagById(tagId)
            if (tag != null) {
                withContext(Dispatchers.Main) {
                    dialogState.showRenameDialog(tag)
                }
            }
        }
    }
    fun hideRenameTagDialog() = dialogState.hideRenameDialog()
    
    fun renameTag(newName: String) {
        dialogState.tagToRename?.let { tag ->
            crudManager.renameTag(tag, newName)
        }
        hideRenameTagDialog()
    }
    
    fun showDeleteTagDialog(tag: TagEntity) = dialogState.showDeleteDialog(tag)
    fun showDeleteTagDialog() = dialogState.showDeleteDialog()
    fun hideDeleteTagDialog() = dialogState.hideDeleteDialog()
    
    fun deleteTag() {
        dialogState.tagToDelete?.let { tag ->
            crudManager.deleteTag(tag) { tagId ->
                statisticsManager.updateTagStatistics(tagId)
            }
        }
        hideDeleteTagDialog()
    }
    
    fun deleteTagWithUndo(tag: TagEntity) {
        crudManager.deleteTagWithUndo(tag) { tagId ->
            statisticsManager.updateTagStatistics(tagId)
        }
    }
    
    fun undoDeleteTag() {
        crudManager.undoDeleteTag { tagId ->
            statisticsManager.updateTagStatistics(tagId)
        }
    }
    
    fun hideUndoDeleteMessage() = dialogState.hideUndoDeleteMessage()
    fun clearDeletedTagCache() = crudManager.clearDeletedTagCache()
    
    // ==================== 过滤操作（代理到 filterManager） ====================
    
    fun toggleTagFilter(tagId: Long) {
        filterManager.toggleTagFilter(tagId)
        persistenceManager.saveActiveTagFilters()
        // 更新未分类标签统计
        if (tagId == -1L) {
            statisticsManager.updateTagStatistics(-1L)
        }
    }
    
    fun toggleTagExclusion(tagId: Long) {
        filterManager.toggleTagExclusion(tagId)
        persistenceManager.saveExcludedTags()
    }
    
    fun clearTagExclusions() = filterManager.clearTagExclusions()
    fun clearTagFilters() = filterManager.clearTagFilters()
    fun clearFilterCache() = filterManager.clearFilterCache()
    
    suspend fun computeFilteredImages(
        currentImages: List<ImageItem>,
        filters: Set<Long> = activeTagFilterIds,
        excludes: Set<Long> = excludedTagIds
    ): List<ImageItem> = filterManager.computeFilteredImages(currentImages, filters, excludes)
    
    fun getFilteredImages(currentImages: List<ImageItem>): List<ImageItem> {
        return kotlinx.coroutines.runBlocking {
            filterManager.getFilteredImages(currentImages)
        }
    }
    
    fun getTagMediaPaths(tagId: Long): List<String> {
        return kotlinx.coroutines.runBlocking {
            filterManager.getTagMediaPaths(tagId)
        }
    }
    
    // ==================== 展开状态（代理到 tagState 和 persistenceManager） ====================
    
    fun toggleTagExpanded(tagId: Long) {
        viewModelScope.launch {
            val isExpanded = tagState.expandedTagIds.contains(tagId)
            tagState.toggleExpandedTagId(tagId)
            tagRepo.toggleTagExpanded(tagId, !isExpanded)
            persistenceManager.saveExpandedTags()
        }
    }
    
    fun toggleReferencedTagExpanded(tagId: Long) {
        viewModelScope.launch {
            tagState.toggleExpandedReferencedTagId(tagId)
            persistenceManager.saveExpandedReferencedTags()
        }
    }
    
    // ==================== 标签引用管理（代理到 crudManager） ====================
    
    fun addTagReference(parentTagId: Long, childTagId: Long) {
        viewModelScope.launch {
            val success = crudManager.addTagReference(parentTagId, childTagId)
            if (success) {
                statisticsManager.updateTagStatistics(parentTagId)
                statisticsManager.updateTagStatistics(childTagId)
            }
        }
    }
    
    fun removeTagReference(parentTagId: Long, childTagId: Long) {
        viewModelScope.launch {
            crudManager.removeTagReference(parentTagId, childTagId)
            statisticsManager.updateTagStatistics(parentTagId)
            statisticsManager.updateTagStatistics(childTagId)
        }
    }
    
    fun showAddTagReferenceDialog(parentTagId: Long) {
        viewModelScope.launch {
            val availableTags = crudManager.getAvailableTagsForReference(parentTagId)
            withContext(Dispatchers.Main) {
                dialogState.showAddReferenceDialog(parentTagId, availableTags)
            }
        }
    }
    
    fun hideAddTagReferenceDialog() = dialogState.hideAddReferenceDialog()
    
    fun addTagReferenceFromDialog(childTagId: Long) {
        dialogState.selectedTagForReference?.let { parentTagId ->
            addTagReference(parentTagId, childTagId)
        }
        hideAddTagReferenceDialog()
    }
    
    // ==================== 标签与图片关联 ====================
    
    fun addTagToSelected(targets: List<ImageItem>, tagId: Long) {
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val tagName = try {
                tagRepo.getTagById(tagId)?.name ?: "未知标签"
            } catch (e: Exception) {
                "标签"
            }
            
            val result = batchAddTagToMedia(targets, tagId)
            
            if (result.successCount > 0) {
                statisticsManager.clearTagStatisticsCacheForTag(tagId)
                statisticsManager.clearTagStatisticsCacheForTag(-1L)
            }
            
            withContext(Dispatchers.Main) {
                val message = if (result.failureCount > 0) {
                    if (result.successCount > 0) {
                        "已添加${result.successCount}张图片到${tagName}，${result.failureCount}张失败"
                    } else {
                        "添加失败：${tagName}标签添加失败"
                    }
                } else {
                    "已添加${result.successCount}张图片到${tagName}"
                }
                
                Toast.makeText(
                    context,
                    message,
                    if (result.failureCount > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    fun removeTagFromSelected(targets: List<ImageItem>, tagId: Long) {
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = batchRemoveTagFromMedia(targets, tagId)
            
            if (result.successCount > 0) {
                statisticsManager.clearTagStatisticsCacheForTag(tagId)
                statisticsManager.clearTagStatisticsCacheForTag(-1L)
            }
        }
    }
    
    private suspend fun batchAddTagToMedia(images: List<ImageItem>, tagId: Long): BatchResult {
        var successCount = 0
        var failureCount = 0
        val batchSize = 20
        val batches = images.chunked(batchSize)
        
        for (batch in batches) {
            for (image in batch) {
                try {
                    tagRepo.addTagToMedia(image.uri.toString(), tagId)
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    println("ERROR: 添加标签失败 - 图片: ${image.uri}, 标签ID: $tagId, 错误: ${e.message}")
                }
            }
        }
        
        return BatchResult(successCount, failureCount)
    }
    
    private suspend fun batchRemoveTagFromMedia(images: List<ImageItem>, tagId: Long): BatchResult {
        var successCount = 0
        var failureCount = 0
        val batchSize = 20
        val batches = images.chunked(batchSize)
        
        for (batch in batches) {
            for (image in batch) {
                try {
                    tagRepo.removeTagFromMedia(image.uri.toString(), tagId)
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    println("ERROR: 移除标签失败 - 图片: ${image.uri}, 标签ID: $tagId, 错误: ${e.message}")
                }
            }
        }
        
        return BatchResult(successCount, failureCount)
    }
    
    fun showTagSelectionDialog() = dialogState.showTagSelection()
    fun hideTagSelectionDialog() = dialogState.hideTagSelection()
    
    fun addTagToSelectedImages(targets: List<ImageItem>, tagId: Long) {
        addTagToSelected(targets, tagId)
        hideTagSelectionDialog()
    }
    
    fun removeTagFromSelectedImages(targets: List<ImageItem>, tagId: Long) {
        removeTagFromSelected(targets, tagId)
        hideTagSelectionDialog()
    }
    
    // ==================== 统计信息（代理到 statisticsManager） ====================
    
    fun updateTagStatistics(tagId: Long) = statisticsManager.updateTagStatistics(tagId)
    fun updateTagStatisticsBatch(tagIds: List<Long>) = statisticsManager.updateTagStatisticsBatch(tagIds)
    fun updateTagStatisticsBatchIfNeeded(tagIds: List<Long>) = statisticsManager.updateTagStatisticsBatchIfNeeded(tagIds)
    fun updateAllTagStatistics() = statisticsManager.updateAllTagStatistics()
    fun clearTagStatisticsCache() = statisticsManager.clearTagStatisticsCache()
    
    // ==================== 排序管理（代理到 sortManager） ====================
    
    fun moveTag(fromIndex: Int, toIndex: Int, tags: List<TagWithChildren>) {
        sortManager.moveTag(fromIndex, toIndex, tags)
    }
    
    fun moveTagInGroup(fromIndex: Int, toIndex: Int, groupTags: List<TagWithChildren>, isWithReferences: Boolean) {
        sortManager.moveTagInGroup(fromIndex, toIndex, groupTags, isWithReferences)
    }
    
    fun moveChildTag(parentTagId: Long, fromIndex: Int, toIndex: Int) {
        sortManager.moveChildTag(parentTagId, fromIndex, toIndex)
    }
    
    fun checkAndFixAllSortOrders() {
        sortManager.checkAndFixAllSortOrders()
    }
    
    // ==================== 标签组管理 ====================
    
    // 从TagGroupData转换为TagGroupEntity
    private fun TagGroupData.toTagGroupEntity() = com.example.yumoflatimagemanager.data.local.TagGroupEntity(
        id = id,
        name = name,
        sortOrder = sortOrder,
        isDefault = isDefault
    )
    
    // 从TagGroupEntity转换为TagGroupData
    private fun com.example.yumoflatimagemanager.data.local.TagGroupEntity.toTagGroupData(tagIds: List<Long> = emptyList()) = TagGroupData(
        id = id,
        name = name,
        sortOrder = sortOrder,
        isDefault = isDefault,
        tagIds = tagIds
    )
    
    // 从文件系统获取所有标签组
    private fun getAllTagGroupsFromFiles(): List<com.example.yumoflatimagemanager.data.local.TagGroupEntity> {
        return TagGroupFileManager.getAllTagGroups().map { it.toTagGroupEntity() }
    }
    
    // 标签组状态操作
    fun selectTagGroup(groupId: Long) {
        tagState.selectTagGroup(groupId)
        persistenceManager.saveTagGroupSelection(tagState.selectedTagGroupId)
    }
    
    fun toggleTagGroupDragMode() {
        tagState.isTagGroupDragMode = !tagState.isTagGroupDragMode
    }
    
    fun toggleTagGroupManagement() {
        tagState.toggleTagGroupManagement()
    }
    
    fun setTagGroupManagementVisible(visible: Boolean) {
        tagState.isTagGroupManagementVisible = visible
    }
    
    // 标签组CRUD操作
    fun createTagGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allTagGroups = TagGroupFileManager.getAllTagGroups()
                val maxSortOrder = allTagGroups.maxByOrNull { it.sortOrder }?.sortOrder ?: 0
                val newTagGroup = TagGroupData(
                    id = tagGroupIdCounter.getAndIncrement(),
                    name = name,
                    sortOrder = maxSortOrder + 1000,
                    isDefault = false,
                    tagIds = emptyList()
                )
                TagGroupFileManager.writeTagGroup(newTagGroup)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "创建标签组失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun renameTagGroup(tagGroup: com.example.yumoflatimagemanager.data.local.TagGroupEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tagGroupData = TagGroupFileManager.readTagGroup(tagGroup.id)
                if (tagGroupData != null) {
                    val updatedTagGroupData = tagGroupData.copy(name = newName)
                    TagGroupFileManager.writeTagGroup(updatedTagGroupData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "重命名标签组失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun deleteTagGroup(tagGroup: com.example.yumoflatimagemanager.data.local.TagGroupEntity) {
        if (tagGroup.isDefault) return // 不可删除默认组
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                TagGroupFileManager.deleteTagGroup(tagGroup.id)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "删除标签组失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // 标签组与标签关联管理
    fun addTagToTagGroup(tagId: Long, tagGroupId: Long) {
        viewModelScope.launch {
            try {
                TagGroupFileManager.addTagToTagGroup(tagId, tagGroupId)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "添加标签到标签组失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun removeTagFromTagGroup(tagId: Long, tagGroupId: Long) {
        viewModelScope.launch {
            try {
                TagGroupFileManager.removeTagFromTagGroup(tagId, tagGroupId)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "从标签组移除标签失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // 标签组排序
    fun updateTagGroupSortOrder(tagGroupId: Long, sortOrder: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tagGroupData = TagGroupFileManager.readTagGroup(tagGroupId)
                if (tagGroupData != null) {
                    val updatedTagGroupData = tagGroupData.copy(sortOrder = sortOrder)
                    TagGroupFileManager.writeTagGroup(updatedTagGroupData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "更新标签组排序失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // 标签组拖拽排序
    fun reorderTagGroups(tagGroups: List<com.example.yumoflatimagemanager.data.local.TagGroupEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                for ((index, tagGroup) in tagGroups.withIndex()) {
                    val tagGroupData = TagGroupFileManager.readTagGroup(tagGroup.id)
                    if (tagGroupData != null) {
                        val updatedTagGroupData = tagGroupData.copy(sortOrder = index * 1000)
                        TagGroupFileManager.writeTagGroup(updatedTagGroupData)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "重新排序标签组失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // 获取标签组下的标签
    suspend fun getTagsByTagGroupId(tagGroupId: Long): List<com.example.yumoflatimagemanager.data.local.TagEntity> {
        return try {
            // 从文件系统获取标签组数据
            val tagGroupData = TagGroupFileManager.readTagGroup(tagGroupId)
            if (tagGroupData == null) {
                return emptyList()
            }
            
            // 从标签文件管理器获取所有标签
            val allTags = com.example.yumoflatimagemanager.data.model.TagFileManager.getAllTags()
            
            // 根据 tagIds 过滤并转换为 TagEntity
            return allTags
                .filter { tagGroupData.tagIds.contains(it.id) }
                .map { it.toTagEntity() }
        } catch (e: Exception) {
            e.printStackTrace()
            println("ERROR: 获取标签组下的标签失败 - 标签组ID: $tagGroupId, 错误: ${e.message}")
            emptyList()
        }
    }
    
    // 获取标签所属的标签组
    suspend fun getTagGroupsByTagId(tagId: Long): List<com.example.yumoflatimagemanager.data.local.TagGroupEntity> {
        return try {
            val tagGroupsData = TagGroupFileManager.getTagGroupsByTagId(tagId)
            tagGroupsData.map { it.toTagGroupEntity() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    // ==================== 持久化（代理到 persistenceManager） ====================
    
    fun saveTagDrawerScrollPosition(index: Int) = persistenceManager.saveTagDrawerScrollPosition(index)
    fun restoreTagDrawerScrollPosition(): Int = persistenceManager.restoreTagDrawerScrollPosition()
    fun restoreTagFilters() = persistenceManager.restoreActiveTagFilters()
    fun restoreAllTagStates() = persistenceManager.restoreAllTagStates()
    fun resetAllTagStates() {
        persistenceManager.resetAllTagStates()
        filterManager.clearFilterCache()
    }
    
    // ==================== 辅助方法 ====================
    
    suspend fun getTagNameById(tagId: Long): String? {
        return try {
            tagRepo.getTagById(tagId)?.name
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun getAllTagsWithChildren(): List<TagWithChildren> {
        return try {
            tagRepo.observeRootTags().first()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getTagWithChildrenForUi(tagId: Long): com.example.yumoflatimagemanager.data.local.TagWithChildren? {
        return try {
            tagRepo.getTagById(tagId)?.let { tag ->
                val children = tagRepo.getTagsByParentId(tagId)
                val tagReferences = tagRepo.getTagReferences(tagId)
                val tagReferenceEntities = tagReferences.map { ref -> 
                    com.example.yumoflatimagemanager.data.local.TagReferenceEntity(
                        parentTagId = tagId, 
                        childTagId = ref.tag.id, 
                        sortOrder = ref.referenceSortOrder
                    )
                }
                com.example.yumoflatimagemanager.data.local.TagWithChildren(tag, children, tagReferenceEntities)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ==================== 生命周期 ====================
    
    override fun onCleared() {
        super.onCleared()
        statisticsManager.clearTagStatisticsCache()
        crudManager.clearDeletedTagCache()
    }
}

