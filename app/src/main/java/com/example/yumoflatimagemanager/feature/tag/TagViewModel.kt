package com.example.yumoflatimagemanager.feature.tag

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.PreferencesManager
import com.example.yumoflatimagemanager.data.local.AppDatabase
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagReferenceEntity
import com.example.yumoflatimagemanager.data.local.TagStatistics
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import com.example.yumoflatimagemanager.data.repo.FileTagRepositoryImpl
import com.example.yumoflatimagemanager.domain.usecase.ObserveTagsUseCase
import com.example.yumoflatimagemanager.media.MediaContentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 缓存被删除标签的数据类
data class DeletedTagCache(
    val tag: TagEntity,
    val associatedMediaPaths: List<String>,
    val childReferences: List<TagReferenceEntity>,
    val parentReferences: List<TagReferenceEntity>,
    val childTags: List<TagEntity>
)

// 批量操作结果
data class BatchResult(
    val successCount: Int,
    val failureCount: Int
)

/**
 * TagViewModel - 负责标签管理的所有功能
 * 从MainViewModel中提取标签相关逻辑，实现职责单一化
 */
class TagViewModel(
    private val context: Context,
    private val mediaContentManager: MediaContentManager
) : ViewModel() {
    
    // 数据库和仓库
    private val db by lazy { AppDatabase.get(context) }
    private val tagRepo by lazy { FileTagRepositoryImpl(db.tagDao()) }
    private val preferencesManager = PreferencesManager.getInstance(context)
    
    // 标签展开状态与滚动位置持久化key
    private val PREF_ACTIVE_TAGS = "active_tags"
    private val PREF_EXCLUDED_TAGS = "excluded_tags"
    private val PREF_EXPANDED_TAGS = "expanded_tags"
    private val PREF_EXPANDED_REFERENCED_TAGS = "expanded_referenced_tags"
    private val PREF_TAG_DRAWER_SCROLL_INDEX = "tag_drawer_scroll_index"
    
    // ==================== 状态变量 ====================
    
    // 标签流
    val tagsFlow: Flow<List<TagWithChildren>> = ObserveTagsUseCase(tagRepo).invoke()
    
    // 激活的标签过滤（并集）
    var activeTagFilterIds by mutableStateOf<Set<Long>>(emptySet())
        private set
        
    // 排除模式的标签ID集合
    var excludedTagIds by mutableStateOf<Set<Long>>(emptySet())
        private set
        
    // 标签展开状态
    var expandedTagIds by mutableStateOf<Set<Long>>(emptySet())
        private set
        
    // 引用标签展开状态（独立于本体标签）
    var expandedReferencedTagIds by mutableStateOf<Set<Long>>(emptySet())
        private set
        
    // 标签抽屉滚动位置
    var tagDrawerScrollIndex by mutableStateOf(0)
        private set
        
    // 标签统计信息
    var tagStatistics by mutableStateOf<Map<Long, TagStatistics>>(emptyMap())
        private set
    
    // 标签引用相关状态
    var showAddTagReferenceDialog by mutableStateOf(false)
        private set
    
    var selectedTagForReference by mutableStateOf<Long?>(null)
        private set
    
    var availableTagsForReference by mutableStateOf(listOf<TagEntity>())
        private set

    // 标签管理对话框状态
    var showCreateTagDialog by mutableStateOf(false)
    
    var showRenameTagDialog by mutableStateOf(false)
    
    var showDeleteTagDialog by mutableStateOf(false)
    
    var showTagSelectionDialog by mutableStateOf(false)
    
    var tagToRename by mutableStateOf<TagEntity?>(null)
    
    var tagToDelete by mutableStateOf<TagEntity?>(null)
    
    // 标签删除撤回相关状态
    var recentlyDeletedTag by mutableStateOf<DeletedTagCache?>(null)
        private set
    
    var showUndoDeleteMessage by mutableStateOf(false)
        private set
    
    var deletedTagName by mutableStateOf("")
        private set
    
    private var undoDeleteJob: Job? = null
    
    // 标签引用刷新触发器
    var tagReferenceRefreshTrigger by mutableStateOf(0L)
        private set
    
    // 标签统计信息缓存
    private val tagStatisticsCache = mutableMapOf<Long, TagStatistics>()
    private val statisticsUpdateJobs = mutableMapOf<Long, Job>()
    
    // 过滤结果缓存
    private var filteredImagesCache: List<ImageItem>? = null
    private var lastFilterState: String = ""
    
    // ==================== 标签CRUD操作 ====================
    
    /**
     * 添加新标签
     */
    fun addTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tagId = tagRepo.createTag(name.trim(), parentId = null)
                println("DEBUG: 成功创建标签: $name, ID: $tagId")
            } catch (e: Exception) {
                println("ERROR: 创建标签失败: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "创建标签失败: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 显示创建标签对话框
     */
    fun showCreateTagDialog() {
        showCreateTagDialog = true
    }
    
    /**
     * 隐藏创建标签对话框
     */
    fun hideCreateTagDialog() {
        showCreateTagDialog = false
    }
    
    /**
     * 显示重命名标签对话框
     */
    fun showRenameTagDialog(tag: TagEntity) {
        tagToRename = tag
        showRenameTagDialog = true
    }
    
    /**
     * 显示重命名标签对话框（通过ID）
     */
    fun showRenameTagDialog(tagId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val tag = tagRepo.getTagById(tagId)
            if (tag != null) {
                withContext(Dispatchers.Main) {
                    tagToRename = tag
                    showRenameTagDialog = true
                }
            }
        }
    }
    
    /**
     * 隐藏重命名标签对话框
     */
    fun hideRenameTagDialog() {
        showRenameTagDialog = false
        tagToRename = null
    }
    
    /**
     * 重命名标签
     */
    fun renameTag(newName: String) {
        tagToRename?.let { tag ->
            viewModelScope.launch {
                tagRepo.renameTag(tag.id, newName, tag.parentId)
            }
        }
        hideRenameTagDialog()
    }
    
    /**
     * 显示删除标签对话框
     */
    fun showDeleteTagDialog(tag: TagEntity) {
        tagToDelete = tag
        showDeleteTagDialog = true
    }
    
    /**
     * 隐藏删除标签对话框
     */
    fun hideDeleteTagDialog() {
        showDeleteTagDialog = false
        tagToDelete = null
    }
    
    /**
     * 删除标签
     */
    fun deleteTag() {
        tagToDelete?.let { tag ->
            viewModelScope.launch {
                // 从激活的标签过滤中移除该标签
                if (activeTagFilterIds.contains(tag.id)) {
                    activeTagFilterIds = activeTagFilterIds - tag.id
                    // 持久化更新
                    preferencesManager.putString(PREF_ACTIVE_TAGS, activeTagFilterIds.joinToString(","))
                }
                
                // 删除标签（包含清理所有关联数据）
                tagRepo.deleteTag(tag.id)
                
                // 更新相关标签的统计信息
                updateTagStatistics(tag.id)
                if (tag.parentId != null) {
                    updateTagStatistics(tag.parentId)
                }
            }
        }
        hideDeleteTagDialog()
    }
    
    /**
     * 左滑删除标签（带撤回功能）
     */
    fun deleteTagWithUndo(tag: TagEntity) {
        // 取消之前的撤回任务
        undoDeleteJob?.cancel()
        
        // 从激活的标签过滤中移除该标签
        if (activeTagFilterIds.contains(tag.id)) {
            activeTagFilterIds = activeTagFilterIds - tag.id
            // 持久化更新
            preferencesManager.putString(PREF_ACTIVE_TAGS, activeTagFilterIds.joinToString(","))
        }
        
        // 先缓存标签的所有关联数据
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 获取标签关联的所有图片路径
                val mediaPaths = tagRepo.getMediaPathsByAnyTag(listOf(tag.id))
                
                // 获取标签的引用关系
                val childReferences = tagRepo.getTagReferences(tag.id)
                    .map { ref -> TagReferenceEntity(tag.id, ref.tag.id) }
                val parentReferences = db.tagDao().getTagReferencesByChildId(tag.id)
                
                // 获取引用标签
                val childTags = db.tagDao().getTagsByParentId(tag.id)
                
                // 创建缓存对象
                val deletedTagCache = DeletedTagCache(
                    tag = TagEntity(
                        id = tag.id, 
                        parentId = tag.parentId, 
                        name = tag.name, 
                        sortOrder = tag.sortOrder, 
                        isExpanded = tag.isExpanded, 
                        imageCount = tag.imageCount
                    ),
                    associatedMediaPaths = mediaPaths,
                    childReferences = childReferences,
                    parentReferences = parentReferences,
                    childTags = childTags
                )
                
                // 立即删除标签
                tagRepo.deleteTag(tag.id)
                
                // 更新相关标签的统计信息
                updateTagStatistics(tag.id)
                if (tag.parentId != null) {
                    updateTagStatistics(tag.parentId)
                }
                
                // 保存删除的标签缓存信息（切换到主线程）
                withContext(Dispatchers.Main) {
                    recentlyDeletedTag = deletedTagCache
                    deletedTagName = tag.name
                    showUndoDeleteMessage = true
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果缓存失败，仍然删除标签但不提供撤回功能
                tagRepo.deleteTag(tag.id)
                updateTagStatistics(tag.id)
                if (tag.parentId != null) {
                    updateTagStatistics(tag.parentId)
                }
                
                withContext(Dispatchers.Main) {
                    recentlyDeletedTag = null
                    deletedTagName = ""
                    showUndoDeleteMessage = false
                }
            }
        }
        
        // 设置5秒后自动隐藏撤回消息
        undoDeleteJob = viewModelScope.launch {
            delay(5000)
            showUndoDeleteMessage = false
            recentlyDeletedTag = null
        }
    }
    
    /**
     * 撤回删除操作
     */
    fun undoDeleteTag() {
        undoDeleteJob?.cancel()
        
        recentlyDeletedTag?.let { cache ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // 重新创建标签
                    val newTagId = tagRepo.createTag(cache.tag.name, cache.tag.parentId)
                    
                    // 恢复标签关联的图片
                    cache.associatedMediaPaths.forEach { mediaPath ->
                        try {
                            tagRepo.addTagToMedia(mediaPath, newTagId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    // 恢复标签引用关系
                    cache.childReferences.forEach { ref ->
                        try {
                            tagRepo.addTagReference(newTagId, ref.childTagId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    cache.parentReferences.forEach { ref ->
                        try {
                            tagRepo.addTagReference(ref.parentTagId, newTagId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    // 更新相关标签的统计信息
                    updateTagStatistics(newTagId)
                    if (cache.tag.parentId != null) {
                        updateTagStatistics(cache.tag.parentId)
                    }
                    
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "已恢复标签 ${cache.tag.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "恢复标签失败: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        
        showUndoDeleteMessage = false
        recentlyDeletedTag = null
        deletedTagName = ""
    }
    
    /**
     * 隐藏撤回消息
     */
    fun hideUndoDeleteMessage() {
        undoDeleteJob?.cancel()
        showUndoDeleteMessage = false
        recentlyDeletedTag = null
        deletedTagName = ""
    }
    
    /**
     * 清理删除缓存（在应用退出时调用）
     */
    fun clearDeletedTagCache() {
        recentlyDeletedTag = null
        deletedTagName = ""
        showUndoDeleteMessage = false
        undoDeleteJob?.cancel()
    }
    
    // ==================== 标签过滤功能 ====================
    
    /**
     * 切换标签过滤
     */
    fun toggleTagFilter(tagId: Long) {
        // 如果标签在排除模式中，先移除排除状态
        if (excludedTagIds.contains(tagId)) {
            excludedTagIds = excludedTagIds - tagId
            clearFilterCache() // 清理缓存
            return
        }
        
        activeTagFilterIds = if (activeTagFilterIds.contains(tagId)) {
            activeTagFilterIds - tagId
        } else {
            activeTagFilterIds + tagId
        }
        // 持久化
        preferencesManager.putString(PREF_ACTIVE_TAGS, activeTagFilterIds.joinToString(","))
        
        // 清理过滤缓存
        clearFilterCache()
        
        // 更新未分类标签统计
        if (tagId == -1L) {
            updateTagStatistics(-1L)
        }
    }
    
    /**
     * 切换标签排除模式
     */
    fun toggleTagExclusion(tagId: Long) {
        excludedTagIds = if (excludedTagIds.contains(tagId)) {
            excludedTagIds - tagId
        } else {
            excludedTagIds + tagId
        }
        
        // 确保一个标签不能同时处于激活和排除状态
        if (excludedTagIds.contains(tagId)) {
            activeTagFilterIds = activeTagFilterIds - tagId
            preferencesManager.putString(PREF_ACTIVE_TAGS, activeTagFilterIds.joinToString(","))
        }
        
        // 持久化排除状态
        preferencesManager.putString(PREF_EXCLUDED_TAGS, excludedTagIds.joinToString(","))
        // 清理过滤缓存
        clearFilterCache()
    }
    
    /**
     * 清除所有排除模式
     */
    fun clearTagExclusions() {
        excludedTagIds = emptySet()
    }
    
    /**
     * 清除所有标签过滤
     */
    fun clearTagFilters() { 
        activeTagFilterIds = emptySet()
        excludedTagIds = emptySet()
    }
    
    /**
     * 清理过滤缓存
     */
    fun clearFilterCache() {
        filteredImagesCache = null
        lastFilterState = ""
    }
    
    /**
     * 计算过滤后的图片列表
     */
    suspend fun computeFilteredImages(
        currentImages: List<ImageItem>,
        filters: Set<Long> = activeTagFilterIds,
        excludes: Set<Long> = excludedTagIds
    ): List<ImageItem> {
        var result = currentImages
        
        // 处理标签过滤逻辑
        if (filters.isNotEmpty() || excludes.isNotEmpty()) {
            // 情况1：只有激活过滤（正常交集过滤）
            if (filters.isNotEmpty() && excludes.isEmpty()) {
                // 未分类过滤：显示未被任何标签打上的图片
                if (filters.contains(-1L) && filters.size == 1) {
                    val taggedPaths = tagRepo.getAllTaggedMediaPaths()
                    val taggedPathSet = taggedPaths.toHashSet()
                    result = result.filter { !taggedPathSet.contains(it.uri.toString()) }
                } else {
                    // 对每个选中标签，取 该标签 ∪ 所有子孙（含引用） 的媒体路径集合
                    val effectiveTagIdsPerFilter: List<Set<Long>> = filters
                        .filter { it != -1L }
                        .map { tagId ->
                            val descendants = tagRepo.getDescendantTagIds(tagId)
                            (descendants + tagId).toSet()
                        }

                    if (effectiveTagIdsPerFilter.isNotEmpty()) {
                        var intersection: Set<String>? = null
                        for (tagIdSet in effectiveTagIdsPerFilter) {
                            val paths = tagRepo.getMediaPathsByAnyTag(tagIdSet.toList())
                            val pathSet = paths.toHashSet()
                            intersection = if (intersection == null) pathSet else intersection.intersect(pathSet)
                            if (intersection.isEmpty()) break
                        }

                        val finalSet = intersection ?: emptySet()
                        result = result.filter { finalSet.contains(it.uri.toString()) }
                    }
                }
            }
            // 情况2：只有排除过滤（所有选中标签都是排除模式）
            else if (filters.isEmpty() && excludes.isNotEmpty()) {
                // 获取所有需要排除的媒体路径（包含子孙标签）
                val excludedPaths = mutableSetOf<String>()
                
                for (excludeTagId in excludes) {
                    val descendants = tagRepo.getDescendantTagIds(excludeTagId)
                    val allExcludeTagIds = (descendants + excludeTagId).toSet()
                    val paths = tagRepo.getMediaPathsByAnyTag(allExcludeTagIds.toList())
                    excludedPaths.addAll(paths)
                }
                
                // 从所有图片中排除这些路径
                result = result.filter { !excludedPaths.contains(it.uri.toString()) }
            }
            // 情况3：同时有激活过滤和排除过滤
            else if (filters.isNotEmpty() && excludes.isNotEmpty()) {
                // 先处理激活过滤
                if (filters.contains(-1L) && filters.size == 1) {
                    val taggedPaths = tagRepo.getAllTaggedMediaPaths()
                    val taggedPathSet = taggedPaths.toHashSet()
                    result = result.filter { !taggedPathSet.contains(it.uri.toString()) }
                } else {
                    val effectiveTagIdsPerFilter: List<Set<Long>> = filters
                        .filter { it != -1L }
                        .map { tagId ->
                            val descendants = tagRepo.getDescendantTagIds(tagId)
                            (descendants + tagId).toSet()
                        }

                    if (effectiveTagIdsPerFilter.isNotEmpty()) {
                        var intersection: Set<String>? = null
                        for (tagIdSet in effectiveTagIdsPerFilter) {
                            val paths = tagRepo.getMediaPathsByAnyTag(tagIdSet.toList())
                            val pathSet = paths.toHashSet()
                            intersection = if (intersection == null) pathSet else intersection.intersect(pathSet)
                            if (intersection.isEmpty()) break
                        }

                        val finalSet = intersection ?: emptySet()
                        result = result.filter { finalSet.contains(it.uri.toString()) }
                    }
                }
                
                // 再处理排除模式
                val excludedPaths = mutableSetOf<String>()
                for (excludeTagId in excludes) {
                    val descendants = tagRepo.getDescendantTagIds(excludeTagId)
                    val allExcludeTagIds = (descendants + excludeTagId).toSet()
                    val paths = tagRepo.getMediaPathsByAnyTag(allExcludeTagIds.toList())
                    excludedPaths.addAll(paths)
                }
                
                // 从当前结果中排除这些路径
                result = result.filter { !excludedPaths.contains(it.uri.toString()) }
            }
        }
        
        return result
    }
    
    /**
     * 获取过滤后的图片列表（带缓存）
     */
    fun getFilteredImages(currentImages: List<ImageItem>): List<ImageItem> {
        val filters = activeTagFilterIds
        val excludes = excludedTagIds
        
        // 如果没有激活的过滤和排除，返回全部图片
        if (filters.isEmpty() && excludes.isEmpty()) {
            filteredImagesCache = currentImages
            return currentImages
        }
        
        // 检查缓存是否有效
        val currentFilterState = "${filters.sorted()}_${excludes.sorted()}_${currentImages.size}"
        if (filteredImagesCache != null && lastFilterState == currentFilterState) {
            return filteredImagesCache!!
        }
        
        // 同步计算过滤结果（避免主线程阻塞）
        val result = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            computeFilteredImages(currentImages, filters, excludes)
        }
        
        // 更新缓存
        filteredImagesCache = result
        lastFilterState = currentFilterState
        
        return result
    }
    
    // ==================== 标签展开状态管理 ====================
    
    /**
     * 切换标签展开状态
     */
    fun toggleTagExpanded(tagId: Long) {
        viewModelScope.launch {
            val isExpanded = expandedTagIds.contains(tagId)
            expandedTagIds = if (isExpanded) {
                expandedTagIds - tagId
            } else {
                expandedTagIds + tagId
            }
            tagRepo.toggleTagExpanded(tagId, !isExpanded)
            
            // 持久化展开状态
            preferencesManager.putString(PREF_EXPANDED_TAGS, expandedTagIds.joinToString(","))
        }
    }
    
    /**
     * 切换引用标签展开状态（独立于本体标签）
     */
    fun toggleReferencedTagExpanded(tagId: Long) {
        viewModelScope.launch {
            val isExpanded = expandedReferencedTagIds.contains(tagId)
            expandedReferencedTagIds = if (isExpanded) {
                expandedReferencedTagIds - tagId
            } else {
                expandedReferencedTagIds + tagId
            }
            // 引用标签的展开状态不保存到数据库，只保存在内存中
            println("DEBUG: 切换引用标签 ${tagId} 的展开状态为 ${!isExpanded}")
            
            // 持久化引用标签展开状态
            preferencesManager.putString(PREF_EXPANDED_REFERENCED_TAGS, expandedReferencedTagIds.joinToString(","))
        }
    }
    
    // ==================== 标签引用管理 ====================
    
    /**
     * 添加标签引用（多对多关系）
     */
    fun addTagReference(parentTagId: Long, childTagId: Long) {
        viewModelScope.launch {
            val success = tagRepo.addTagReference(parentTagId, childTagId)
            if (!success) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "添加引用失败：检测到自循环/祖先-子孙循环",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // 触发引用刷新
                triggerTagReferenceRefresh()
                // 更新相关标签的统计信息
                updateTagStatistics(parentTagId)
                updateTagStatistics(childTagId)
            }
        }
    }
    
    /**
     * 移除标签引用
     */
    fun removeTagReference(parentTagId: Long, childTagId: Long) {
        viewModelScope.launch {
            tagRepo.removeTagReference(parentTagId, childTagId)
            // 触发引用刷新
            triggerTagReferenceRefresh()
            // 更新相关标签的统计信息
            updateTagStatistics(parentTagId)
            updateTagStatistics(childTagId)
        }
    }
    
    /**
     * 显示添加标签引用对话框
     */
    fun showAddTagReferenceDialog(parentTagId: Long) {
        selectedTagForReference = parentTagId
        viewModelScope.launch {
            // 获取所有可用标签（排除自身、已引用的标签、父标签和祖宗标签）
            val allTags = tagRepo.getAllTags().first() // 只获取当前数据，不持续监听
            val existingReferences = tagRepo.getTagReferences(parentTagId)
            val parentTagIds = tagRepo.getParentTagIds(parentTagId) // 获取所有父标签和祖宗标签
            val availableTags = allTags.filter { tag ->
                tag.id != parentTagId && 
                existingReferences.none { refTag -> refTag.tag.id == tag.id } &&
                tag.id !in parentTagIds // 排除父标签和祖宗标签
            }
            availableTagsForReference = availableTags
            showAddTagReferenceDialog = true
        }
    }
    
    /**
     * 隐藏添加标签引用对话框
     */
    fun hideAddTagReferenceDialog() {
        showAddTagReferenceDialog = false
        selectedTagForReference = null
        availableTagsForReference = emptyList()
    }
    
    /**
     * 添加标签引用
     */
    fun addTagReferenceFromDialog(childTagId: Long) {
        selectedTagForReference?.let { parentTagId ->
            viewModelScope.launch {
                try {
                    val success = tagRepo.addTagReference(parentTagId, childTagId)
                    if (!success) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "添加引用失败：不允许自引用或形成递归",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        // 触发引用刷新
                        triggerTagReferenceRefresh()
                        // 更新相关标签的统计信息
                        updateTagStatistics(parentTagId)
                        updateTagStatistics(childTagId)
                    }
                } catch (e: Exception) {
                    // 处理循环引用等错误
                    e.printStackTrace()
                }
            }
        }
        hideAddTagReferenceDialog()
    }

    /**
     * 触发标签引用刷新
     */
    private fun triggerTagReferenceRefresh() {
        tagReferenceRefreshTrigger = System.currentTimeMillis()
    }
    
    // ==================== 标签与图片关联 ====================
    
    /**
     * 为选中的图片添加标签
     */
    fun addTagToSelected(targets: List<ImageItem>, tagId: Long) {
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // 获取标签名称用于反馈
            val tagName = try {
                tagRepo.getTagById(tagId)?.name ?: "未知标签"
            } catch (e: Exception) {
                "标签"
            }
            
            // 使用批量处理优化性能
            val result = batchAddTagToMedia(targets, tagId)
            
            // 清理相关标签的统计信息缓存，强制重新计算
            if (result.successCount > 0) {
                clearTagStatisticsCacheForTag(tagId)
                // 同时清理未分类标签的缓存
                clearTagStatisticsCacheForTag(-1L)
            }
            
            // 在主线程显示反馈
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
                
                android.widget.Toast.makeText(
                    context,
                    message,
                    if (result.failureCount > 0) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * 从选中的图片中移除标签
     */
    fun removeTagFromSelected(targets: List<ImageItem>, tagId: Long) {
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            // 使用批量处理优化性能
            val result = batchRemoveTagFromMedia(targets, tagId)
            
            // 清理相关标签的统计信息缓存，强制重新计算
            if (result.successCount > 0) {
                clearTagStatisticsCacheForTag(tagId)
                // 同时清理未分类标签的缓存
                clearTagStatisticsCacheForTag(-1L)
            }
        }
    }
    
    /**
     * 批量添加标签到媒体（分批处理）
     */
    private suspend fun batchAddTagToMedia(images: List<ImageItem>, tagId: Long): BatchResult {
        var successCount = 0
        var failureCount = 0
        
        // 分批处理，避免一次性处理太多数据
        val batchSize = 20 // 每批处理20张图片
        val batches = images.chunked(batchSize)
        
        for (batch in batches) {
            // 顺序处理每个批次，避免并发问题
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
    
    /**
     * 批量移除标签（分批处理）
     */
    private suspend fun batchRemoveTagFromMedia(images: List<ImageItem>, tagId: Long): BatchResult {
        var successCount = 0
        var failureCount = 0
        val batchSize = 20 // 每批处理20张图片
        val batches = images.chunked(batchSize)
        
        for (batch in batches) {
            // 顺序处理每个批次
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
    
    /**
     * 显示标签选择对话框
     */
    fun showTagSelectionDialog() {
        showTagSelectionDialog = true
    }
    
    /**
     * 隐藏标签选择对话框
     */
    fun hideTagSelectionDialog() {
        showTagSelectionDialog = false
    }
    
    /**
     * 为选中的图片添加标签（从对话框）
     */
    fun addTagToSelectedImages(targets: List<ImageItem>, tagId: Long) {
        if (tagId == -1L) {
            // 处理"未分类"标签，这里可以添加特殊逻辑
            // 目前"未分类"只是作为一个虚拟标签显示，不执行实际操作
            return
        }
        
        if (targets.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            // 获取标签名称用于反馈
            val tagName = try {
                tagRepo.getTagById(tagId)?.name ?: "未知标签"
            } catch (e: Exception) {
                "标签"
            }
            
            var successCount = 0
            var failureCount = 0
            
            targets.forEach { image ->
                try {
                    tagRepo.addTagToMedia(image.uri.toString(), tagId)
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    e.printStackTrace()
                }
            }
            
            // 清理相关标签的统计信息缓存，强制重新计算
            if (successCount > 0) {
                clearTagStatisticsCacheForTag(tagId)
                // 同时清理未分类标签的缓存
                clearTagStatisticsCacheForTag(-1L)
                // 清理过滤缓存，确保过滤结果更新
                clearFilterCache()
            }
            
            // 在主线程显示反馈
            withContext(Dispatchers.Main) {
                val message = if (failureCount > 0) {
                    if (successCount > 0) {
                        "已添加${successCount}张图片到${tagName}，${failureCount}张失败"
                    } else {
                        "添加失败：${tagName}标签添加失败"
                    }
                } else {
                    "已添加${successCount}张图片到${tagName}"
                }
                
                android.widget.Toast.makeText(
                    context,
                    message,
                    if (failureCount > 0) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        hideTagSelectionDialog()
    }
    
    /**
     * 从选中的图片中移除标签（从对话框）
     */
    fun removeTagFromSelectedImages(targets: List<ImageItem>, tagId: Long) {
        if (tagId == -1L) {
            // 处理"未分类"标签，这里可以添加特殊逻辑
            // 目前"未分类"只是作为一个虚拟标签显示，不执行实际操作
            return
        }
        
        if (targets.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            // 获取标签名称用于反馈
            val tagName = try {
                tagRepo.getTagById(tagId)?.name ?: "未知标签"
            } catch (e: Exception) {
                "标签"
            }
            
            var successCount = 0
            var failureCount = 0
            
            targets.forEach { image ->
                try {
                    tagRepo.removeTagFromMedia(image.uri.toString(), tagId)
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    e.printStackTrace()
                }
            }
            
            // 清理相关标签的统计信息缓存，强制重新计算
            if (successCount > 0) {
                clearTagStatisticsCacheForTag(tagId)
                // 同时清理未分类标签的缓存
                clearTagStatisticsCacheForTag(-1L)
                // 清理过滤缓存，确保过滤结果更新
                clearFilterCache()
            }
            
            // 在主线程显示反馈
            withContext(Dispatchers.Main) {
                val message = if (failureCount > 0) {
                    if (successCount > 0) {
                        "已从${successCount}张图片中移除${tagName}标签，${failureCount}张失败"
                    } else {
                        "移除失败：${tagName}标签移除失败"
                    }
                } else {
                    "已从${successCount}张图片中移除${tagName}标签"
                }
                
                android.widget.Toast.makeText(
                    context,
                    message,
                    if (failureCount > 0) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        hideTagSelectionDialog()
    }
    
    /**
     * 获取标签的完整图片路径（包含所有引用标签和引用标签）
     */
    fun getTagMediaPaths(tagId: Long): List<String> {
        return kotlinx.coroutines.runBlocking {
            val descendantIds = tagRepo.getDescendantTagIds(tagId)
            val allTagIds = listOf(tagId) + descendantIds
            tagRepo.getMediaPathsByAnyTag(allTagIds)
        }
    }
    
    // ==================== 标签统计信息 ====================
    
    /**
     * 更新标签统计信息 - 优化版本（支持懒加载）
     */
    fun updateTagStatistics(tagId: Long) {
        // 取消之前的更新任务，避免重复计算
        statisticsUpdateJobs[tagId]?.cancel()
        
        // 检查缓存，如果缓存存在且时间不超过5分钟，直接使用缓存
        val cachedStats = tagStatisticsCache[tagId]
        if (cachedStats != null) {
            tagStatistics = tagStatistics.toMutableMap().apply {
                put(tagId, cachedStats)
            }
            return
        }
        
        // 对于未分类标签，确保媒体内容已加载
        if (tagId == -1L) {
            // 确保媒体内容已加载
            mediaContentManager.loadAllMedia()
        }
        
        // 异步计算统计信息
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val statistics = if (tagId == -1L) {
                    // 为"未分类"标签创建虚拟统计
                    val taggedPaths = tagRepo.getAllTaggedMediaPaths()
                    // 获取所有媒体内容，而不仅仅是当前相册的图片
                    val allMediaImages = mediaContentManager.allImages + mediaContentManager.allVideos
                    val allImagePaths = allMediaImages.map { it.uri.toString() }
                    val untaggedPaths = allImagePaths.filter { !taggedPaths.contains(it) }
                    TagStatistics(
                        tagId = -1L,
                        directImageCount = untaggedPaths.size,
                        totalImageCount = untaggedPaths.size,
                        referencedCount = 0
                    )
                } else {
                    tagRepo.getTagStatistics(tagId)
                }
                
                // 更新缓存
                tagStatisticsCache[tagId] = statistics
                
                // 在主线程更新UI
                withContext(Dispatchers.Main) {
                    tagStatistics = tagStatistics.toMutableMap().apply {
                        put(tagId, statistics)
                    }
                }
            } catch (e: Exception) {
                Log.e("TagViewModel", "更新标签统计信息失败: $tagId", e)
            } finally {
                statisticsUpdateJobs.remove(tagId)
            }
        }
        
        statisticsUpdateJobs[tagId] = job
    }
    
    /**
     * 批量更新标签统计信息 - 优化版本
     */
    fun updateTagStatisticsBatch(tagIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            // 分批处理，避免一次性处理大量标签
            val batchSize = 5
            tagIds.chunked(batchSize).forEach { batch ->
                batch.forEach { tagId ->
                    updateTagStatistics(tagId)
                }
                // 批次间延迟，避免过度占用资源
                delay(100)
            }
        }
    }
    
    /**
     * 智能批量更新标签统计信息 - 只更新未缓存的
     */
    fun updateTagStatisticsBatchIfNeeded(tagIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            // 过滤出未缓存的标签ID
            val uncachedTagIds = tagIds.filter { tagId ->
                !tagStatisticsCache.containsKey(tagId) && !statisticsUpdateJobs.containsKey(tagId)
            }
            
            if (uncachedTagIds.isNotEmpty()) {
                Log.d("TagViewModel", "需要更新 ${uncachedTagIds.size} 个标签的统计信息")
                updateTagStatisticsBatch(uncachedTagIds)
            } else {
                Log.d("TagViewModel", "所有标签统计信息已缓存，跳过更新")
            }
        }
    }
    
    /**
     * 更新所有标签的统计信息
     */
    fun updateAllTagStatistics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 获取所有标签ID
                val allTags = tagRepo.getAllTags().first()
                val allTagIds = allTags.map { it.id } + listOf(-1L) // 包括未分类标签
                
                // 批量更新统计信息
                updateTagStatisticsBatch(allTagIds)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 清理统计信息缓存
     */
    fun clearTagStatisticsCache() {
        tagStatisticsCache.clear()
        statisticsUpdateJobs.values.forEach { it.cancel() }
        statisticsUpdateJobs.clear()
    }
    
    /**
     * 清理特定标签的统计信息缓存
     */
    private fun clearTagStatisticsCacheForTag(tagId: Long) {
        tagStatisticsCache.remove(tagId)
        statisticsUpdateJobs[tagId]?.cancel()
        statisticsUpdateJobs.remove(tagId)
        
        // 立即重新计算该标签的统计信息
        updateTagStatistics(tagId)
    }
    
    // ==================== 标签拖拽排序 ====================
    
    /**
     * 移动标签到新位置（拖拽排序）
     */
    fun moveTag(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 获取当前标签列表
                val currentTags = tagsFlow.first()
                if (fromIndex < 0 || fromIndex >= currentTags.size || 
                    toIndex < 0 || toIndex >= currentTags.size || 
                    fromIndex == toIndex) {
                    return@launch
                }
                
                // 实现插入逻辑：将标签插入到目标位置，后面的标签排序值+1
                insertTagAtPosition(currentTags, fromIndex, toIndex)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 分组内移动标签排序
     */
    fun moveTagInGroup(fromIndex: Int, toIndex: Int, isWithReferences: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 获取当前标签列表
                val currentTags = tagsFlow.first()
                val groupTags = if (isWithReferences) {
                    currentTags.filter { it.referencedTags.isNotEmpty() }
                } else {
                    currentTags.filter { it.referencedTags.isEmpty() }
                }
                
                if (fromIndex < 0 || fromIndex >= groupTags.size || 
                    toIndex < 0 || toIndex >= groupTags.size || 
                    fromIndex == toIndex) {
                    return@launch
                }
                
                // 在组内实现插入逻辑
                insertTagAtPositionInGroup(groupTags, fromIndex, toIndex, isWithReferences)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 分组内实现插入逻辑：将标签插入到目标位置，使用分组排序值
     */
    private suspend fun insertTagAtPositionInGroup(
        groupTags: List<TagWithChildren>,
        fromIndex: Int,
        toIndex: Int,
        isWithReferences: Boolean
    ) {
        if (fromIndex == toIndex) return
        
        val movedTag = groupTags[fromIndex]
        
        // 计算新的排序值（使用分组排序）
        val baseSortOrder = if (isWithReferences) 1000 else 100000  // 有引用标签的本体标签组使用1000-99999，无引用标签的本体标签组使用100000+
        val newSortOrder = baseSortOrder + toIndex * 1000
        
        // 更新被移动标签的排序值
        tagRepo.updateTagSort(movedTag.tag.id, newSortOrder)
        println("DEBUG: 分组内更新标签 ${movedTag.tag.name} 的排序值为 $newSortOrder")
        
        // 更新组内其他标签的排序值
        groupTags.forEachIndexed { index, tagWithChildren ->
            if (index != fromIndex) {
                val adjustedIndex = if (index > fromIndex && index <= toIndex) index - 1
                else if (index < fromIndex && index >= toIndex) index + 1
                else index
                
                val adjustedSortOrder = baseSortOrder + adjustedIndex * 1000
                tagRepo.updateTagSort(tagWithChildren.tag.id, adjustedSortOrder)
                println("DEBUG: 分组内调整标签 ${tagWithChildren.tag.name} 的排序值为 $adjustedSortOrder")
            }
        }
    }
    
    /**
     * 实现插入逻辑：将标签插入到目标位置，后面的标签排序值整体下移
     */
    private suspend fun insertTagAtPosition(
        tags: List<TagWithChildren>,
        fromIndex: Int,
        toIndex: Int
    ) {
        if (fromIndex == toIndex) return
        
        val movedTag = tags[fromIndex]
        
        // 计算新的排序值
        val newSortOrder = when {
            toIndex == 0 -> {
                // 插入到第一个位置
                val firstTag = tags[0]
                firstTag.tag.sortOrder - 1000  // 使用更大的间隔避免冲突
            }
            toIndex >= tags.size - 1 -> {
                // 插入到最后一个位置
                val lastTag = tags[tags.size - 1]
                lastTag.tag.sortOrder + 1000  // 使用更大的间隔避免冲突
            }
            toIndex < fromIndex -> {
                // 向前插入：使用目标位置标签的排序值减500，避免重复
                val targetTag = tags[toIndex]
                targetTag.tag.sortOrder - 500
            }
            else -> {
                // 向后插入：使用目标位置标签的排序值减500，避免重复
                val targetTag = tags[toIndex]
                targetTag.tag.sortOrder - 500
            }
        }
        
        // 更新被移动标签的排序值
        tagRepo.updateTagSort(movedTag.tag.id, newSortOrder)
        println("DEBUG: 更新标签 ${movedTag.tag.name} 的排序值为 $newSortOrder")
        
        // 更新受影响的其他标签排序值
        updateAffectedTagsSortOrderForInsertion(tags, fromIndex, toIndex)
        
        // 重新分配排序值以确保唯一性和连续性
        redistributeAllTagSortOrders()
    }
    
    /**
     * 更新受影响的标签排序值（插入排序专用）
     */
    private suspend fun updateAffectedTagsSortOrderForInsertion(
        tags: List<TagWithChildren>,
        fromIndex: Int,
        toIndex: Int
    ) {
        when {
            toIndex == 0 -> {
                // 插入到第一个位置：所有其他标签排序值+1000
                tags.forEachIndexed { i, tagWithChildren ->
                    if (i != fromIndex) {
                        val newSortOrder = tagWithChildren.tag.sortOrder + 1000
                        tagRepo.updateTagSort(tagWithChildren.tag.id, newSortOrder)
                        println("DEBUG: 插入到第一个位置 - 更新标签 ${tagWithChildren.tag.name} 的排序值为 $newSortOrder")
                    }
                }
            }
            toIndex >= tags.size - 1 -> {
                // 插入到最后一个位置：所有其他标签保持不变
                println("DEBUG: 插入到最后一个位置 - 其他标签排序值保持不变")
            }
            toIndex < fromIndex -> {
                // 向前插入：目标位置及之后到源位置之前的标签排序值+1000
                tags.forEachIndexed { i, tagWithChildren ->
                    if (i >= toIndex && i < fromIndex) {
                        val newSortOrder = tagWithChildren.tag.sortOrder + 1000
                        tagRepo.updateTagSort(tagWithChildren.tag.id, newSortOrder)
                        println("DEBUG: 向前插入 - 更新标签 ${tagWithChildren.tag.name} 的排序值为 $newSortOrder")
                    }
                }
            }
            else -> {
                // 向后插入：源位置之后到目标位置之前的标签排序值-1000
                tags.forEachIndexed { i, tagWithChildren ->
                    if (i > fromIndex && i <= toIndex) {
                        val newSortOrder = tagWithChildren.tag.sortOrder - 1000
                        tagRepo.updateTagSort(tagWithChildren.tag.id, newSortOrder)
                        println("DEBUG: 向后插入 - 更新标签 ${tagWithChildren.tag.name} 的排序值为 $newSortOrder")
                    }
                }
            }
        }
    }
    
    /**
     * 重新分配所有标签的排序值，确保唯一性和连续性
     */
    private suspend fun redistributeAllTagSortOrders() {
        try {
            val allTags = tagsFlow.first()
            // 按当前排序值排序
            val sortedTags = allTags.sortedBy { it.tag.sortOrder }
            
            // 重新分配排序值，每个标签间隔10000，确保足够空间
            sortedTags.forEachIndexed { index, tagWithChildren ->
                val newSortOrder = index * 10000
                tagRepo.updateTagSort(tagWithChildren.tag.id, newSortOrder)
                println("DEBUG: 重新分配标签 ${tagWithChildren.tag.name} 的排序值为 $newSortOrder")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ==================== 状态持久化 ====================
    
    /**
     * 保存标签抽屉滚动位置
     */
    fun saveTagDrawerScrollPosition(index: Int) {
        tagDrawerScrollIndex = index
        preferencesManager.putString(PREF_TAG_DRAWER_SCROLL_INDEX, index.toString())
    }
    
    /**
     * 恢复标签抽屉滚动位置
     */
    fun restoreTagDrawerScrollPosition(): Int {
        val saved = preferencesManager.getString(PREF_TAG_DRAWER_SCROLL_INDEX, "0")
        tagDrawerScrollIndex = saved.toIntOrNull() ?: 0
        return tagDrawerScrollIndex
    }

    /**
     * 恢复标签过滤状态
     */
    fun restoreTagFilters() {
        val saved = preferencesManager.getString(PREF_ACTIVE_TAGS, "")
        if (saved.isNotBlank()) {
            activeTagFilterIds = saved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        }
    }
    
    /**
     * 恢复所有标签状态
     */
    fun restoreAllTagStates() {
        // 恢复激活的标签过滤
        val activeSaved = preferencesManager.getString(PREF_ACTIVE_TAGS, "")
        if (activeSaved.isNotBlank()) {
            activeTagFilterIds = activeSaved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        }
        
        // 恢复排除的标签
        val excludedSaved = preferencesManager.getString(PREF_EXCLUDED_TAGS, "")
        if (excludedSaved.isNotBlank()) {
            excludedTagIds = excludedSaved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        }
        
        // 恢复展开的标签
        val expandedSaved = preferencesManager.getString(PREF_EXPANDED_TAGS, "")
        if (expandedSaved.isNotBlank()) {
            expandedTagIds = expandedSaved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        }
        
        // 恢复展开的引用标签
        val expandedReferencedSaved = preferencesManager.getString(PREF_EXPANDED_REFERENCED_TAGS, "")
        if (expandedReferencedSaved.isNotBlank()) {
            expandedReferencedTagIds = expandedReferencedSaved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        }
        
        // 恢复标签抽屉滚动位置
        restoreTagDrawerScrollPosition()
    }
    
    /**
     * 重置所有标签状态
     */
    fun resetAllTagStates() {
        activeTagFilterIds = emptySet()
        excludedTagIds = emptySet()
        expandedTagIds = emptySet()
        expandedReferencedTagIds = emptySet()
        tagDrawerScrollIndex = 0
        
        // 清理持久化数据
        preferencesManager.putString(PREF_ACTIVE_TAGS, "")
        preferencesManager.putString(PREF_EXCLUDED_TAGS, "")
        preferencesManager.putString(PREF_EXPANDED_TAGS, "")
        preferencesManager.putString(PREF_EXPANDED_REFERENCED_TAGS, "")
        preferencesManager.putString(PREF_TAG_DRAWER_SCROLL_INDEX, "0")
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 根据标签ID获取标签名称
     */
    suspend fun getTagNameById(tagId: Long): String? {
        return try {
            tagRepo.getTagById(tagId)?.name
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取所有标签及其子标签信息
     */
    suspend fun getAllTagsWithChildren(): List<TagWithChildren> {
        return try {
            tagRepo.observeRootTags().first()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 为 UI 加载任意标签的 TagWithChildren（含直接引用标签与直接引用）
     */
    suspend fun getTagWithChildrenForUi(tagId: Long): TagWithChildren? {
        return db.tagDao().getTagWithChildren(tagId)
    }
    
    /**
     * 释放资源
     */
    override fun onCleared() {
        super.onCleared()
        clearTagStatisticsCache()
        undoDeleteJob?.cancel()
    }
}

