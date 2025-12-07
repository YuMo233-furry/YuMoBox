package com.example.yumoflatimagemanager

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.AlbumType
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.ConfigManager
import com.example.yumoflatimagemanager.data.ConfigMigration
import com.example.yumoflatimagemanager.data.TagConfig
import com.example.yumoflatimagemanager.data.AlbumConfig
import com.example.yumoflatimagemanager.data.ScrollPosition
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.data.SortType
import com.example.yumoflatimagemanager.data.SortDirection
import com.example.yumoflatimagemanager.media.AlbumPathManager
import com.example.yumoflatimagemanager.media.FileRenameManager
import com.example.yumoflatimagemanager.media.SimplifiedImageLoaderHelper
import com.example.yumoflatimagemanager.media.MediaContentManager
import com.example.yumoflatimagemanager.permissions.PermissionsManager
import com.example.yumoflatimagemanager.ui.screens.Screen
import com.example.yumoflatimagemanager.ui.AlbumManagerImpl
import com.example.yumoflatimagemanager.ui.SelectionManagerImpl
import com.example.yumoflatimagemanager.ui.ScreenManagerImpl
import com.example.yumoflatimagemanager.ui.UiStateManagerImpl
import com.example.yumoflatimagemanager.ui.RefreshManager
import com.example.yumoflatimagemanager.ui.RefreshManagerImpl
import com.example.yumoflatimagemanager.ui.createRefreshManager
import com.example.yumoflatimagemanager.secure.SecureModeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import com.example.yumoflatimagemanager.data.local.AppDatabase
import com.example.yumoflatimagemanager.data.repo.FileTagRepositoryImpl
import com.example.yumoflatimagemanager.domain.usecase.ObserveTagsUseCase
import com.example.yumoflatimagemanager.sync.OneDriveSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.example.yumoflatimagemanager.media.TimeMetadataUpdater
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagReferenceEntity
import com.example.yumoflatimagemanager.data.local.MediaTagCrossRef
import kotlinx.coroutines.Job
import android.util.Log
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

// 缓存被删除标签的数据类
data class DeletedTagCache(
	val tag: TagEntity,
	val associatedMediaPaths: List<String>,
	val childReferences: List<TagReferenceEntity>,
	val parentReferences: List<TagReferenceEntity>,
	val childTags: List<TagEntity>
)

/**
 * MainViewModel 用于管理应用的主要状态和逻辑
 */
class MainViewModel(private val context: Context) : ViewModel() {
    // 权限启动器
    lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    
    // 媒体内容管理器
    val mediaContentManager = MediaContentManager(context)
    
    // 相册管理器
    private val albumManager = AlbumManagerImpl()
    
    // 选择管理器
    private val selectionManager = SelectionManagerImpl()
    
    // 屏幕管理器
    private val screenManager = ScreenManagerImpl()
    
    // UI状态管理器
    private val uiStateManager = UiStateManagerImpl()
    
    // 配置管理器 - 替换原有的 preferencesManager
    // ConfigManager 是一个单例对象，直接使用即可
    
    // ============ 新架构：标签功能代理 ============
    // 使用新的 TagViewModel 来管理所有标签功能
    // 注意：使用 lazy 初始化以避免循环依赖，但确保在需要时已初始化
    private val _tagViewModel by lazy { 
        com.example.yumoflatimagemanager.feature.tag.TagViewModelNew(context, mediaContentManager)
    }
    // ==============================================
    
    // 排序模式状态
    var isInDragMode by mutableStateOf(false)
        private set
    
    // 安全模式管理器
    private val secureModeManager = SecureModeManager
    
    // 刷新管理器
    private val refreshManager: RefreshManager = createRefreshManager(selectionManager, screenManager)

    // 标签与同步最小整合
    private val db by lazy { AppDatabase.get(context) }
    private val tagRepo by lazy { FileTagRepositoryImpl(db.tagDao()) }
    val tagsFlow: Flow<List<com.example.yumoflatimagemanager.data.local.TagWithChildren>> =
        ObserveTagsUseCase(tagRepo).invoke()

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
    var tagStatistics by mutableStateOf<Map<Long, com.example.yumoflatimagemanager.data.local.TagStatistics>>(emptyMap())
        private set
    
    // 标签引用相关状态
    var showAddTagReferenceDialog by mutableStateOf(false)
        private set
    
    var selectedTagForReference by mutableStateOf<Long?>(null)
        private set
    
    var availableTagsForReference by mutableStateOf(listOf<com.example.yumoflatimagemanager.data.local.TagEntity>())
        private set

    // 引用标签排序相关状态
    var showReferenceTagSortDialog by mutableStateOf(false)
        private set
    
    var parentTagForReferenceSort by mutableStateOf<com.example.yumoflatimagemanager.data.local.TagWithChildren?>(null)
        private set

    // 标签管理对话框状态
    var showCreateTagDialog by mutableStateOf(false)
    
    var showRenameTagDialog by mutableStateOf(false)
    
    var showDeleteTagDialog by mutableStateOf(false)
    
    var showTagSelectionDialog by mutableStateOf(false)
    
    // 多选标签状态
    var selectedTagIdsForOperation by mutableStateOf<Set<Long>>(emptySet())
        private set
    
    var tagToRename by mutableStateOf<com.example.yumoflatimagemanager.data.local.TagEntity?>(null)
    
    var tagToDelete by mutableStateOf<com.example.yumoflatimagemanager.data.local.TagEntity?>(null)
    
    // 标签删除撤回相关状态
    var recentlyDeletedTag by mutableStateOf<DeletedTagCache?>(null)
        private set
    
    var showUndoDeleteMessage by mutableStateOf(false)
        private set
    
    // 重置标签状态确认对话框
    var showResetConfirmationDialog by mutableStateOf(false)
        private set
    
    var deletedTagName by mutableStateOf("")
        private set
    
    private var undoDeleteJob: kotlinx.coroutines.Job? = null
    
    // 标签引用刷新触发器
    var tagReferenceRefreshTrigger by mutableStateOf(0L)
        private set
    
    // 文件操作相关状态
    var showAlbumSelectionScreen by mutableStateOf(false)
        private set
    
    // 相册选择屏幕的退出动画类型
    var albumSelectionExitAnimation by mutableStateOf(AlbumSelectionExitAnimation.SLIDE_OUT)
        private set
    
    var showMoveCopyDialog by mutableStateOf(false)
        private set
    
    var selectedTargetAlbum by mutableStateOf<Album?>(null)
        private set
    
    var showOperationProgressDialog by mutableStateOf(false)
        private set
    
    var operationProgress by mutableStateOf(0)
        private set
    
    var operationTotal by mutableStateOf(0)
        private set
    
    var currentOperationFileName by mutableStateOf("")
        private set
    
    // 删除确认对话框
    var showDeleteConfirmDialog by mutableStateOf(false)
        private set

    // 水印相关状态
    var showWatermarkPresetDialog by mutableStateOf(false)
    var showWatermarkEditor by mutableStateOf(false)
    var showWatermarkPreview by mutableStateOf(false)
    var showWatermarkSaveOption by mutableStateOf(false)
    var watermarkPreviewTrigger by mutableStateOf(0) // 用于触发LaunchedEffect
    var watermarkPresets by mutableStateOf<List<com.example.yumoflatimagemanager.data.WatermarkPreset>>(emptyList())
        private set
    var editingPreset by mutableStateOf<com.example.yumoflatimagemanager.data.WatermarkPreset?>(null)
        private set
    var currentWatermarkState by mutableStateOf<com.example.yumoflatimagemanager.data.WatermarkState?>(null)
    var imageWatermarkParamsList by mutableStateOf<List<com.example.yumoflatimagemanager.data.ImageWatermarkParams>?>(null)
        private set
    var selectedWatermarkSaveOption by mutableStateOf<com.example.yumoflatimagemanager.data.WatermarkSaveOption?>(null)
        private set
    
    var deleteConfirmMessage by mutableStateOf("")
        private set
    
    
    private var currentOperationJob: kotlinx.coroutines.Job? = null

    // 标签展开状态与滚动位置持久化key
    private val PREF_ACTIVE_TAGS = "active_tags"
    private val PREF_EXCLUDED_TAGS = "excluded_tags"
    private val PREF_EXPANDED_TAGS = "expanded_tags"
    private val PREF_EXPANDED_REFERENCED_TAGS = "expanded_referenced_tags"
    private val PREF_TAG_DRAWER_SCROLL_INDEX = "tag_drawer_scroll_index"
    private val PREF_GRID_SCROLL_INDEX = "grid_scroll_index"
    private val PREF_GRID_SCROLL_OFFSET = "grid_scroll_offset"

    // 过滤结果 StateFlow - 非阻塞式响应式数据流
    private val _filteredImagesFlow = MutableStateFlow<List<ImageItem>>(emptyList())
    val filteredImagesFlow: StateFlow<List<ImageItem>> = _filteredImagesFlow
    
    // 过滤状态：是否正在过滤
    private val _isFiltering = MutableStateFlow(false)
    val isFiltering: StateFlow<Boolean> = _isFiltering
    
    // 过滤缓存键，避免重复计算
    private var lastFilterState: String = ""
    
    // 兼容性：保留旧的 getter 用于向后兼容，但现在它只是返回 Flow 的当前值
    @Deprecated("Use filteredImagesFlow.collectAsState() instead", ReplaceWith("filteredImagesFlow.value"))
    val filteredImages: List<ImageItem>
        get() = _filteredImagesFlow.value

    fun addTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tagId = tagRepo.createTag(name.trim(), parentId = null)
                
                // 新标签默认插入到无引用标签的本体标签组的最上面
                // 使用新的两套独立排序值系统
                val allTags = tagsFlow.first()
                val tagsWithoutReferences = allTags.filter { it.referencedTags.isEmpty() }
                
                val newSortOrder = if (tagsWithoutReferences.isEmpty()) {
                    // 如果没有普通标签，使用基础排序值
                    1000
                } else {
                    // 获取无引用标签的本体标签组中的最小排序值，新标签插入到最上面
                    val minSortOrder = tagsWithoutReferences.minOf { it.tag.normalGroupSortOrder }
                    if (minSortOrder > 0) {
                        minSortOrder - 1000  // 比最小排序值小1000，确保在最上面
                    } else {
                        1000  // 如果排序值为0，使用基础值
                    }
                }
                
                // 使用新的无引用标签的本体标签组排序方法
                tagRepo.updateNormalGroupSortOrder(tagId, newSortOrder)
                println("DEBUG: 成功创建标签: $name, ID: $tagId, 无引用组排序值: $newSortOrder (插入到无引用标签的本体标签组最上面)")
                
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

    fun addTagToSelected(tagId: Long) {
        val targets = selectedImages
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
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        message,
                        if (result.failureCount > 0) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun removeTagFromSelected(tagId: Long) {
        val targets = selectedImages
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
    
    // 批量添加标签到媒体（分批处理）
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
    
    // 批量移除标签（分批处理）
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
    
    // 批量操作结果
    data class BatchResult(
        val successCount: Int,
        val failureCount: Int
    )

    // 过滤：切换标签
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
        saveCurrentTagState()
        
        // 清理过滤缓存
        clearFilterCache()
        
        // 更新未分类标签统计
        if (tagId == -1L) {
            updateTagStatistics(-1L)
        }
    }
    
    // 切换标签排除模式
    fun toggleTagExclusion(tagId: Long) {
        excludedTagIds = if (excludedTagIds.contains(tagId)) {
            excludedTagIds - tagId
        } else {
            excludedTagIds + tagId
        }
        
        // 确保一个标签不能同时处于激活和排除状态
        if (excludedTagIds.contains(tagId)) {
            activeTagFilterIds = activeTagFilterIds - tagId
            saveCurrentTagState()
        }
        
        // 持久化排除状态
        saveCurrentTagState()
        // 清理过滤缓存
        clearFilterCache()
    }
    
    // 清理过滤缓存并触发重新过滤
    private fun clearFilterCache() {
        lastFilterState = ""
        // 异步触发过滤更新
        triggerFilterUpdate()
    }
    
    /**
     * 异步触发过滤更新 - 在后台线程执行，不阻塞主线程
     */
    private fun triggerFilterUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = images
                val filters = activeTagFilterIds
                val excludes = excludedTagIds
                
                // 构建当前过滤状态键
                val currentFilterState = "${filters.sorted()}_${excludes.sorted()}_${current.size}"
                
                // 如果状态未变化且已有缓存，直接返回
                if (currentFilterState == lastFilterState && _filteredImagesFlow.value.isNotEmpty()) {
                    return@launch
                }
                
                // 标记正在过滤
                _isFiltering.value = true
                
                // 如果没有激活的过滤和排除，返回全部图片
                val result = if (filters.isEmpty() && excludes.isEmpty()) {
                    current
                } else {
                    // 执行过滤计算
                    computeFilteredImages(current, filters, excludes)
                }
                
                // 更新过滤结果
                withContext(Dispatchers.Main) {
                    _filteredImagesFlow.value = result
                    // 过滤结果更新后：保留当前可见项选择，清除不可见项选择
                    if (selectionManager.isSelectionMode) {
                        val visibleIds = result.map { it.id }.toHashSet()
                        val pruned = selectionManager.selectedImages.filter { visibleIds.contains(it.id) }
                        if (pruned.size != selectionManager.selectedImages.size) {
                            selectionManager.selectedImages = pruned
                        }
                    }
                    lastFilterState = currentFilterState
                    _isFiltering.value = false
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "过滤失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isFiltering.value = false
                    // 发生错误时，至少保持当前图片列表可访问
                    if (_filteredImagesFlow.value.isEmpty()) {
                        _filteredImagesFlow.value = images
                    }
                }
            }
        }
    }
    
    /**
     * 计算过滤后的图片列表（避免主线程阻塞）
     */
    private suspend fun computeFilteredImages(
        current: List<ImageItem>,
        filters: Set<Long>,
        excludes: Set<Long>
    ): List<ImageItem> {
        var result = current
        
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
                            intersection = if (intersection == null) pathSet else intersection!!.intersect(pathSet)
                            if (intersection!!.isEmpty()) break
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
                            intersection = if (intersection == null) pathSet else intersection!!.intersect(pathSet)
                            if (intersection!!.isEmpty()) break
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
    
    // 清除所有排除模式
    fun clearTagExclusions() {
        excludedTagIds = emptySet()
    }
    
    // 切换标签展开状态
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
            saveCurrentTagState()
            println("DEBUG: 切换本体标签 ${tagId} 的展开状态为 ${!isExpanded}, 当前本体展开状态: $expandedTagIds")
        }
    }
    
    // 切换引用标签展开状态（独立于本体标签）
    fun toggleReferencedTagExpanded(tagId: Long) {
        viewModelScope.launch {
            val isExpanded = expandedReferencedTagIds.contains(tagId)
            expandedReferencedTagIds = if (isExpanded) {
                expandedReferencedTagIds - tagId
            } else {
                expandedReferencedTagIds + tagId
            }
            // 引用标签的展开状态不保存到数据库，只保存在内存中
            println("DEBUG: 切换引用标签 ${tagId} 的展开状态为 ${!isExpanded}, 当前引用展开状态: $expandedReferencedTagIds")
            
            // 持久化引用标签展开状态
            saveCurrentTagState()
        }
    }
    
    // 添加标签引用（多对多关系）
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
                
                // 检查父标签是否需要切换到有引用组
                val allTags = tagsFlow.first()
                val parentTag = allTags.find { it.tag.id == parentTagId }
                if (parentTag != null && parentTag.referencedTags.isEmpty()) {
                    // 父标签之前没有引用，现在有了，需要切换到有引用组
                    handleTagGroupSwitch(parentTagId, true)
                }
            }
        }
    }
    
    // 移除标签引用
    fun removeTagReference(parentTagId: Long, childTagId: Long) {
        viewModelScope.launch {
            tagRepo.removeTagReference(parentTagId, childTagId)
            // 触发引用刷新
            triggerTagReferenceRefresh()
            // 更新相关标签的统计信息
            clearTagStatisticsCacheForTag(parentTagId)
            clearTagStatisticsCacheForTag(childTagId)
            clearTagStatisticsCacheForTag(-1L) // 更新未分类标签统计
            
            // 检查父标签是否需要切换到普通组
            val allTags = tagsFlow.first()
            val parentTag = allTags.find { it.tag.id == parentTagId }
            if (parentTag != null && parentTag.referencedTags.isEmpty()) {
                // 父标签现在没有引用了，需要切换到普通组
                handleTagGroupSwitch(parentTagId, false)
            }
        }
    }
    
    // 显示创建标签对话框
    fun showCreateTagDialog() {
        showCreateTagDialog = true
    }
    
    // 隐藏创建标签对话框
    fun hideCreateTagDialog() {
        showCreateTagDialog = false
    }
    
    // 显示重命名标签对话框
    fun showRenameTagDialog(tag: com.example.yumoflatimagemanager.data.local.TagEntity) {
        tagToRename = tag
        showRenameTagDialog = true
    }
    
    // 显示重命名标签对话框（通过ID）
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
    
    // 隐藏重命名标签对话框
    fun hideRenameTagDialog() {
        showRenameTagDialog = false
        tagToRename = null
    }
    
    // 重命名标签
    fun renameTag(newName: String) {
        tagToRename?.let { tag ->
            viewModelScope.launch {
                tagRepo.renameTag(tag.id, newName, tag.parentId)
            }
        }
        hideRenameTagDialog()
    }
    
    // 显示删除标签对话框
    fun showDeleteTagDialog(tag: com.example.yumoflatimagemanager.data.local.TagEntity) {
        tagToDelete = tag
        showDeleteTagDialog = true
    }
    
    // 隐藏删除标签对话框
    fun hideDeleteTagDialog() {
        showDeleteTagDialog = false
        tagToDelete = null
    }
    
    // 删除标签
    fun deleteTag() {
        tagToDelete?.let { tag ->
            viewModelScope.launch {
                // 从激活的标签过滤中移除该标签
                if (activeTagFilterIds.contains(tag.id)) {
                    activeTagFilterIds = activeTagFilterIds - tag.id
                    // 持久化更新
                    saveCurrentTagState()
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
    
    // 左滑删除标签（带撤回功能）
    fun deleteTagWithUndo(tag: com.example.yumoflatimagemanager.data.local.TagEntity) {
        // 取消之前的撤回任务
        undoDeleteJob?.cancel()
        
        // 从激活的标签过滤中移除该标签
        if (activeTagFilterIds.contains(tag.id)) {
            activeTagFilterIds = activeTagFilterIds - tag.id
            // 持久化更新
            saveCurrentTagState()
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
            kotlinx.coroutines.delay(5000)
            showUndoDeleteMessage = false
            recentlyDeletedTag = null
        }
    }
    
    // 撤回删除操作
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
                            // 单个图片恢复失败不影响其他恢复
                        }
                    }
                    
                    // 恢复引用标签的父级关系
                    cache.childTags.forEach { childTag ->
                        try {
                            db.tagDao().updateParent(childTag.id, newTagId)
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
                    
                    // 恢复被引用关系
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
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 如果恢复失败，显示错误提示
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "标签恢复失败：${cache.tag.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        
        showUndoDeleteMessage = false
        recentlyDeletedTag = null
        deletedTagName = ""
    }
    
    // 隐藏撤回消息
    fun hideUndoDeleteMessage() {
        undoDeleteJob?.cancel()
        showUndoDeleteMessage = false
        recentlyDeletedTag = null
        deletedTagName = ""
    }
    
    // 清理删除缓存（在应用退出时调用）
    fun clearDeletedTagCache() {
        recentlyDeletedTag = null
        deletedTagName = ""
        showUndoDeleteMessage = false
        undoDeleteJob?.cancel()
    }
    
    // 标签统计信息缓存
    private val tagStatisticsCache = mutableMapOf<Long, com.example.yumoflatimagemanager.data.local.TagStatistics>()
    private val statisticsUpdateJobs = mutableMapOf<Long, Job>()
    
    // 更新标签统计信息 - 优化版本（支持懒加载）
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
                    com.example.yumoflatimagemanager.data.local.TagStatistics(
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
                Log.e("MainViewModel", "更新标签统计信息失败: $tagId", e)
            } finally {
                statisticsUpdateJobs.remove(tagId)
            }
        }
        
        statisticsUpdateJobs[tagId] = job
    }
    
    // 批量更新标签统计信息 - 优化版本
    fun updateTagStatisticsBatch(tagIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            // 分批处理，避免一次性处理大量标签
            val batchSize = 5
            tagIds.chunked(batchSize).forEach { batch ->
                batch.forEach { tagId ->
                    updateTagStatistics(tagId)
                }
                // 批次间延迟，避免过度占用资源
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    // 智能批量更新标签统计信息 - 只更新未缓存的
    fun updateTagStatisticsBatchIfNeeded(tagIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            // 过滤出未缓存的标签ID
            val uncachedTagIds = tagIds.filter { tagId ->
                !tagStatisticsCache.containsKey(tagId) && !statisticsUpdateJobs.containsKey(tagId)
            }
            
            if (uncachedTagIds.isNotEmpty()) {
                Log.d("MainViewModel", "需要更新 ${uncachedTagIds.size} 个标签的统计信息")
                updateTagStatisticsBatch(uncachedTagIds)
            } else {
                Log.d("MainViewModel", "所有标签统计信息已缓存，跳过更新")
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
    suspend fun getAllTagsWithChildren(): List<com.example.yumoflatimagemanager.data.local.TagWithChildren> {
        return try {
            tagRepo.observeRootTags().first()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    // 清理统计信息缓存
    fun clearTagStatisticsCache() {
        tagStatisticsCache.clear()
        statisticsUpdateJobs.values.forEach { it.cancel() }
        statisticsUpdateJobs.clear()
    }
    
    // 清理特定标签的统计信息缓存
    private fun clearTagStatisticsCacheForTag(tagId: Long) {
        tagStatisticsCache.remove(tagId)
        statisticsUpdateJobs[tagId]?.cancel()
        statisticsUpdateJobs.remove(tagId)
        
        // 立即重新计算该标签的统计信息
        updateTagStatistics(tagId)
        
        // 异步更新所有父标签的统计信息（包括引用父标签）
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 获取所有父标签ID（包括直接父标签和引用父标签）
                val parentTagIds = tagRepo.getParentTagIds(tagId)
                
                // 递归更新所有父标签的统计信息
                parentTagIds.forEach { parentTagId ->
                    // 清理父标签的缓存
                    tagStatisticsCache.remove(parentTagId)
                    statisticsUpdateJobs[parentTagId]?.cancel()
                    statisticsUpdateJobs.remove(parentTagId)
                    
                    // 重新计算父标签的统计信息
                    updateTagStatistics(parentTagId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // 显示添加标签引用对话框
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
    
    // 隐藏添加标签引用对话框
    fun hideAddTagReferenceDialog() {
        showAddTagReferenceDialog = false
        selectedTagForReference = null
        availableTagsForReference = emptyList()
    }

    // 显示引用标签排序对话框
    fun showReferenceTagSortDialog(parentTag: com.example.yumoflatimagemanager.data.local.TagWithChildren) {
        showReferenceTagSortDialog = true
        parentTagForReferenceSort = parentTag
    }
    
    // 隐藏引用标签排序对话框
    fun hideReferenceTagSortDialog() {
        showReferenceTagSortDialog = false
        parentTagForReferenceSort = null
    }
    
    // 添加标签引用
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
                        clearTagStatisticsCacheForTag(parentTagId)
                        clearTagStatisticsCacheForTag(childTagId)
                        clearTagStatisticsCacheForTag(-1L) // 更新未分类标签统计
                    }
                } catch (e: Exception) {
                    // 处理循环引用等错误
                    e.printStackTrace()
                }
            }
        }
        hideAddTagReferenceDialog()
    }

    // 触发标签引用刷新
    private fun triggerTagReferenceRefresh() {
        tagReferenceRefreshTrigger = System.currentTimeMillis()
    }

    // 为 UI 加载任意标签的 TagWithChildren（含直接引用标签与直接引用）
    suspend fun getTagWithChildrenForUi(tagId: Long): com.example.yumoflatimagemanager.data.local.TagWithChildren? {
        // 使用 tagRepo 获取标签数据，支持数据库和文件系统
        val tag = tagRepo.getTagById(tagId) ?: return null
        val children = tagRepo.getTagsByParentId(tagId)
        val tagReferences = tagRepo.getTagReferences(tagId)
        val tagReferenceEntities = tagReferences.map { ref -> 
            com.example.yumoflatimagemanager.data.local.TagReferenceEntity(parentTagId = tagId, childTagId = ref.tag.id, sortOrder = ref.referenceSortOrder)
        }
        return com.example.yumoflatimagemanager.data.local.TagWithChildren(tag, children, tagReferenceEntities)
    }
    
    // 显示标签选择对话框
    fun showTagSelectionDialog() {
        clearTagSelection()
        showTagSelectionDialog = true
    }
    
    // 隐藏标签选择对话框
    fun hideTagSelectionDialog() {
        clearTagSelection()
        showTagSelectionDialog = false
    }
    
    // 切换标签选择状态
    fun toggleTagSelection(tagId: Long) {
        selectedTagIdsForOperation = if (tagId in selectedTagIdsForOperation) {
            selectedTagIdsForOperation - tagId
        } else {
            selectedTagIdsForOperation + tagId
        }
    }
    
    // 清空标签选择
    fun clearTagSelection() {
        selectedTagIdsForOperation = emptySet()
    }
    
    // 批量添加选中的标签到图片
    fun addSelectedTagsToImages() {
        if (selectedTagIdsForOperation.isEmpty() || selectedImages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableListOf<Pair<String, Int>>() // 标签名和实际添加数量
            var totalAdded = 0
            var totalAlreadyTagged = 0
            
            selectedTagIdsForOperation.forEach { tagId ->
                if (tagId == -1L) return@forEach // 跳过未分类标签
                
                val tagName = try {
                    tagRepo.getTagById(tagId)?.name ?: "未知标签"
                } catch (e: Exception) {
                    "标签"
                }
                
                var actualAddedCount = 0
                var alreadyTaggedCount = 0
                
                selectedImages.forEach { image ->
                    try {
                        val alreadyTagged = tagRepo.isMediaTagged(image.uri.toString(), tagId)
                        if (!alreadyTagged) {
                            tagRepo.addTagToMedia(image.uri.toString(), tagId)
                            actualAddedCount++
                        } else {
                            alreadyTaggedCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                if (actualAddedCount > 0) {
                    results.add(Pair(tagName, actualAddedCount))
                    totalAdded += actualAddedCount
                }
                if (alreadyTaggedCount > 0) {
                    totalAlreadyTagged += alreadyTaggedCount
                }
                
                // 清理缓存
                if (actualAddedCount > 0) {
                    clearTagStatisticsCacheForTag(tagId)
                    clearTagStatisticsCacheForTag(-1L)
                    clearFilterCache()
                }
            }
            
            withContext(Dispatchers.Main) {
                // 显示汇总消息
                val message = when {
                    results.isEmpty() -> "所选项已包含在所有选中标签中"
                    results.size == 1 -> {
                        val (tagName, count) = results[0]
                        "已添加${count}张图片到${tagName}"
                    }
                    else -> {
                        val tagDetails = results.joinToString("，") { "${it.first}(${it.second}张)" }
                        "已添加${totalAdded}张图片到${results.size}个标签：${tagDetails}"
                    }
                }
                
                android.widget.Toast.makeText(
                    context,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
                clearTagSelection()
                hideTagSelectionDialog()
            }
        }
    }
    
    // 批量从图片移除选中的标签
    fun removeSelectedTagsFromImages() {
        if (selectedTagIdsForOperation.isEmpty() || selectedImages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableListOf<Pair<String, Int>>() // 标签名和实际移除数量
            var totalRemoved = 0
            
            selectedTagIdsForOperation.forEach { tagId ->
                if (tagId == -1L) return@forEach // 跳过未分类标签
                
                val tagName = try {
                    tagRepo.getTagById(tagId)?.name ?: "未知标签"
                } catch (e: Exception) {
                    "标签"
                }
                
                var actualRemovedCount = 0
                
                selectedImages.forEach { image ->
                    try {
                        val hasTag = tagRepo.isMediaTagged(image.uri.toString(), tagId)
                        if (hasTag) {
                            tagRepo.removeTagFromMedia(image.uri.toString(), tagId)
                            actualRemovedCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                if (actualRemovedCount > 0) {
                    results.add(Pair(tagName, actualRemovedCount))
                    totalRemoved += actualRemovedCount
                }
                
                // 清理缓存
                if (actualRemovedCount > 0) {
                    clearTagStatisticsCacheForTag(tagId)
                    clearTagStatisticsCacheForTag(-1L)
                    clearFilterCache()
                }
            }
            
            withContext(Dispatchers.Main) {
                // 显示汇总消息
                val message = when {
                    results.isEmpty() -> "所选项不包含任何选中标签"
                    results.size == 1 -> {
                        val (tagName, count) = results[0]
                        "已从${count}张图片中移除${tagName}标签"
                    }
                    else -> {
                        val tagDetails = results.joinToString("，") { "${it.first}(${it.second}张)" }
                        "已从${totalRemoved}张图片中移除${results.size}个标签：${tagDetails}"
                    }
                }
                
                android.widget.Toast.makeText(
                    context,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
                clearTagSelection()
                hideTagSelectionDialog()
            }
        }
    }
    
    // 为选中的图片添加标签
    fun addTagToSelectedImages(tagId: Long) {
        if (tagId == -1L) {
            // 处理"未分类"标签，这里可以添加特殊逻辑
            // 目前"未分类"只是作为一个虚拟标签显示，不执行实际操作
            return
        }
        
        val targets = selectedImages
        if (targets.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            // 获取标签名称用于反馈
            val tagName = try {
                tagRepo.getTagById(tagId)?.name ?: "未知标签"
            } catch (e: Exception) {
                "标签"
            }
            
            var actualAddedCount = 0
            var failureCount = 0
            
            targets.forEach { image ->
                try {
                    // 先检查是否已有该标签
                    val alreadyTagged = tagRepo.isMediaTagged(image.uri.toString(), tagId)
                    if (!alreadyTagged) {
                        tagRepo.addTagToMedia(image.uri.toString(), tagId)
                        actualAddedCount++
                    }
                } catch (e: Exception) {
                    failureCount++
                    e.printStackTrace()
                }
            }
            
            // 清理相关标签的统计信息缓存，强制重新计算
            if (actualAddedCount > 0) {
                clearTagStatisticsCacheForTag(tagId)
                // 同时清理未分类标签的缓存
                clearTagStatisticsCacheForTag(-1L)
                // 清理过滤缓存，确保过滤结果更新
                clearFilterCache()
            }
            
            // 在主线程显示反馈
            withContext(Dispatchers.Main) {
                val message = when {
                    failureCount > 0 && actualAddedCount > 0 -> 
                        "已添加${actualAddedCount}张图片到${tagName}，${failureCount}张失败"
                    failureCount > 0 && actualAddedCount == 0 -> 
                        "添加失败：${tagName}标签添加失败"
                    actualAddedCount > 0 -> 
                        "已添加${actualAddedCount}张图片到${tagName}"
                    else -> 
                        "所选项已包含在${tagName}中"
                }
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        message,
                        if (failureCount > 0) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        hideTagSelectionDialog()
    }
    
    // 从选中的图片中移除标签
    fun removeTagFromSelectedImages(tagId: Long) {
        if (tagId == -1L) {
            // 处理"未分类"标签，这里可以添加特殊逻辑
            // 目前"未分类"只是作为一个虚拟标签显示，不执行实际操作
            return
        }
        
        val targets = selectedImages
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
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        message,
                        if (failureCount > 0) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        hideTagSelectionDialog()
    }
    
    // 获取标签的完整图片路径（包含所有引用标签和引用标签）
    fun getTagMediaPaths(tagId: Long): List<String> {
        return runBlocking {
            val descendantIds = tagRepo.getDescendantTagIds(tagId)
            val allTagIds = listOf(tagId) + descendantIds
            tagRepo.getMediaPathsByAnyTag(allTagIds)
        }
    }

    fun clearTagFilters() { 
        activeTagFilterIds = emptySet()
        excludedTagIds = emptySet()
    }
    
    // 保存标签抽屉滚动位置
    fun saveTagDrawerScrollPosition(index: Int) {
        tagDrawerScrollIndex = index
        // 更新标签配置并保存到文件
        saveCurrentTagState()
    }
    
    // 恢复标签抽屉滚动位置
    fun restoreTagDrawerScrollPosition(): Int {
        // 从配置文件读取标签配置
        val tagConfig = ConfigManager.readTagConfig()
        tagDrawerScrollIndex = tagConfig.tagDrawerScrollIndex
        return tagDrawerScrollIndex
    }
    
    // 保存当前标签状态到配置文件
    private fun saveCurrentTagState() {
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.activeTagFilterIds = activeTagFilterIds
        tagConfig.excludedTagIds = excludedTagIds
        tagConfig.expandedTagIds = expandedTagIds
        tagConfig.expandedReferencedTagIds = expandedReferencedTagIds
        tagConfig.tagDrawerScrollIndex = tagDrawerScrollIndex
        ConfigManager.writeTagConfig(tagConfig)
    }

    fun restoreTagFilters() {
        // 从配置文件读取标签配置
        val tagConfig = ConfigManager.readTagConfig()
        // 使用配置文件中的标签状态
        activeTagFilterIds = tagConfig.activeTagFilterIds
    }
    
    // 恢复所有标签状态
    fun restoreAllTagStates() {
        // 从配置文件读取标签配置
        val tagConfig = ConfigManager.readTagConfig()
        
        // 恢复激活的标签过滤
        activeTagFilterIds = tagConfig.activeTagFilterIds
        
        // 恢复排除的标签
        excludedTagIds = tagConfig.excludedTagIds
        
        // 恢复展开的标签
        expandedTagIds = tagConfig.expandedTagIds
        
        // 恢复展开的引用标签
        expandedReferencedTagIds = tagConfig.expandedReferencedTagIds
        
        // 恢复标签抽屉滚动位置
        tagDrawerScrollIndex = tagConfig.tagDrawerScrollIndex
    }
    
    // 重置所有标签状态
    fun resetAllTagStates() {
        activeTagFilterIds = emptySet<Long>()
        excludedTagIds = emptySet<Long>()
        expandedTagIds = emptySet<Long>()
        expandedReferencedTagIds = emptySet<Long>()
        tagDrawerScrollIndex = 0
        
        // 清除持久化数据 - 保存到配置文件
        val tagConfig = TagConfig(
            expandedTagIds = emptySet<Long>(),
            activeTagFilterIds = emptySet<Long>(),
            excludedTagIds = emptySet<Long>(),
            expandedReferencedTagIds = emptySet<Long>(),
            tagDrawerScrollIndex = 0
        )
        ConfigManager.writeTagConfig(tagConfig)
    }

    // 保存/恢复网格滚动位置
    fun saveGridScroll(firstIndex: Int, firstOffset: Int) {
        // 从配置文件读取当前相册配置
        val albumConfig = ConfigManager.readAlbumConfig()
        // 更新默认相册的滚动位置
        albumConfig.gridScrollPositions["default"] = ScrollPosition(firstIndex, firstOffset)
        // 保存回配置文件
        ConfigManager.writeAlbumConfig(albumConfig)
    }
    
    fun loadGridScroll(): Pair<Int, Int> {
        // 从配置文件读取当前相册配置
        val albumConfig = ConfigManager.readAlbumConfig()
        // 获取默认相册的滚动位置
        val scrollPosition = albumConfig.gridScrollPositions["default"] ?: ScrollPosition()
        return scrollPosition.index to scrollPosition.offset
    }
    
    /**
     * 清除已保存的滚动位置数据
     */
    fun clearSavedScrollPosition() {
        // 从配置文件读取当前相册配置
        val albumConfig = ConfigManager.readAlbumConfig()
        // 清除默认相册的滚动位置
        albumConfig.gridScrollPositions.remove("default")
        // 保存回配置文件
        ConfigManager.writeAlbumConfig(albumConfig)
    }
    
    /**
     * 应用重启时清除滚动位置（在Application中调用）
     */
    fun clearScrollPositionOnAppRestart() {
        clearSavedScrollPosition()
    }
    
    /**
     * 检查标签是否已应用到选中的图片上
     * 在选择模式下，只有当所有选中的图片都包含该标签时才返回true
     */
    fun isTagAppliedToSelected(tagId: Long): Boolean {
        if (selectedImages.isEmpty()) return false
        
        // 使用tagRepo来检查所有选中的图片是否都包含该标签
        // 由于这是在UI线程上调用的，我们需要在协程中执行异步操作
        var result = false
        val job = viewModelScope.launch(Dispatchers.IO) {
            val mediaPaths = selectedImages.map { it.uri.toString() }
            result = tagRepo.areAllMediaTagged(mediaPaths, tagId)
        }
        // 等待协程完成
        runBlocking { job.join() }
        
        return result
    }
    
    // 当前选中的相册
    val selectedAlbum: Album?
        get() = albumManager.selectedAlbum
    
    // 是否处于选择模式
    val isSelectionMode: Boolean
        get() = selectionManager.isSelectionMode
    
    // 当前选中的图片列表
    val selectedImages: List<ImageItem>
        get() = selectionManager.selectedImages
    
    // 当前显示的屏幕
    val currentScreen: Screen
        get() = screenManager.currentScreen
    
    // 获取刷新key，用于强制组件重新创建
    val refreshKey: Int
        get() = refreshManager.refreshKey
    
    // 获取相册列表 - 使用可观察的状态变量
    var mainAlbums: List<Album> by mutableStateOf(emptyList())
        private set
    
    var customAlbums: List<Album> by mutableStateOf(emptyList())
        private set
    
    val mergedAlbums: List<Album>
        get() = albumManager.mergedAlbums
    
    // 所有相册列表
    val albums: List<Album>
        get() = mergedAlbums
    
    // 导航到相册列表
    fun navigateToAlbums() {
        // 只刷新选择模式状态，不刷新所有UI状态
        refreshManager.refreshSelectionMode()
        screenManager.navigateToAlbums()
    }
    
    // 更新滚动位置
    fun updateScrollPosition(isNearTop: Boolean) {
        uiStateManager.updateScrollPosition(isNearTop)
    }
    
    // 刷新媒体内容
    fun refreshMedia() {
        // 先异步加载所有媒体内容，然后再加载相册数据
        viewModelScope.launch {
            // 在IO线程中异步加载媒体内容
            withContext(Dispatchers.IO) {
                mediaContentManager.loadAllMedia()
            }

            // 在主线程中更新UI数据
            withContext(Dispatchers.Main) {
                loadAlbumData()
                // 刷新标签统计信息
                updateAllTagStatistics()
            }
        }
    }
    
    /**
     * 刷新UI状态
     */
    fun refreshUI() {
        refreshManager.refreshAll()
    }
    
    // 当前相册的网格列数
    var gridColumnCount by mutableStateOf(3)
    
    // 相册中的图片列表
    var images: List<ImageItem> by mutableStateOf(emptyList())
        private set
    
    // 滚动位置状态
    val isAlbumDetailNearTop: Boolean
        get() = uiStateManager.isNearTop
    
    // 移动对话框是否显示
    val showMoveDialog: Boolean
        get() = uiStateManager.isMoveDialogVisible
    
    // 创建相册对话框是否显示
    val showCreateAlbumDialog: Boolean
        get() = uiStateManager.isCreateAlbumDialogVisible
    
    // 初始化
    init {
        com.example.yumoflatimagemanager.startup.StartupPerformanceMonitor.startStage("MainViewModel初始化")
        
        // 初始化启动缓存管理器
        com.example.yumoflatimagemanager.startup.StartupCacheManager.initialize(context)
        
        // 只在应用重启时清除滚动位置，应用内导航时保持滚动位置
        // clearSavedScrollPosition() // 注释掉这行，保持滚动位置
        
        // 恢复所有标签状态
        restoreAllTagStates()
        
        // 初始化水印功能
        loadWatermarkPresets()
        
        // 设置媒体变化监听器
        mediaContentManager.setMediaChangeListener(object : MediaContentManager.MediaChangeListener {
            override fun onMediaChanged() {
                // 媒体内容变化时，重新加载相册数据
                viewModelScope.launch(Dispatchers.Main) {
                    loadAlbumData()
                }
            }
        })
        
        // 先尝试快速加载缓存
        loadCacheDataSync()
        
        // 然后异步初始化媒体管理器
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 确保媒体管理器完全初始化
                mediaContentManager.initialize()
                
                // 初始化完成后，检查是否需要更新数据
                withContext(Dispatchers.Main) {
                    val hasValidCache = mainAlbums.isNotEmpty() || customAlbums.isNotEmpty()
                    if (!hasValidCache) {
                        // 没有有效缓存，立即加载数据
                        android.util.Log.d("MainViewModel", "没有有效缓存，立即加载数据")
                        loadAlbumData()
                    } else {
                        // 有缓存，异步更新最新数据
                        android.util.Log.d("MainViewModel", "有有效缓存，异步更新最新数据")
                        loadLatestDataAsyncWithoutUIUpdate()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "媒体管理器初始化失败: ${e.message}")
                // 初始化失败，回退到正常加载
                withContext(Dispatchers.Main) {
                    loadAlbumData()
                }
            }
        }
        
        com.example.yumoflatimagemanager.startup.StartupPerformanceMonitor.endStage("MainViewModel初始化")
    }

    /**
     * 快速加载缓存数据，立即更新UI
     */
    private fun loadCacheDataSync() {
        try {
            // 初始化缓存管理器
            com.example.yumoflatimagemanager.startup.StartupCacheManager.initialize(context)
            
            // 使用协程快速加载缓存数据，但立即返回
            viewModelScope.launch(Dispatchers.IO) {
                val cachedAlbums = com.example.yumoflatimagemanager.startup.StartupCacheManager.loadAlbumsCache()
                
                if (cachedAlbums != null && cachedAlbums.isNotEmpty()) {
                    // 立即更新UI显示缓存数据
                    withContext(Dispatchers.Main) {
                        updateAlbumsFromCache(cachedAlbums)
                    }
                    android.util.Log.d("MainViewModel", "快速加载缓存数据成功，共${cachedAlbums.size}个相册")
                } else {
                    android.util.Log.d("MainViewModel", "没有可用的缓存数据")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "快速加载缓存数据失败: ${e.message}")
        }
    }
    
    
    /**
     * 从缓存更新相册数据到UI
     */
    private fun updateAlbumsFromCache(cachedAlbums: List<Album>) {
        // 分离主要相册和自定义相册
        val mainAlbumsList = cachedAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.MAIN }
        val customAlbumsList = cachedAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.CUSTOM }

        // 更新相册管理器中的数据
        albumManager.mainAlbums = mainAlbumsList
        albumManager.customAlbums = customAlbumsList
        albumManager.mergedAlbums = cachedAlbums
        
        // 更新可观察的状态变量，触发UI重新渲染
        mainAlbums = mainAlbumsList
        customAlbums = customAlbumsList
        
        android.util.Log.d("MainViewModel", "相册数据已更新到UI，共${cachedAlbums.size}个相册，主要相册${mainAlbumsList.size}个，自定义相册${customAlbumsList.size}个")
        android.util.Log.d("MainViewModel", "状态变量更新 - mainAlbums: ${mainAlbums.size}, customAlbums: ${customAlbums.size}")
    }
    
    /**
     * 异步加载最新数据（不更新UI，用于有缓存的情况）
     */
    private suspend fun loadLatestDataAsyncWithoutUIUpdate() {
        try {
            // 在后台加载最新数据
            val allAlbums = mediaContentManager.albums
            val allImages = mediaContentManager.allImages
            val allVideos = mediaContentManager.allVideos
            
            // 分离主要相册和自定义相册
            val mainAlbumsList = allAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.MAIN }
            val customAlbumsList = allAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.CUSTOM }

            // 主要相册保持默认排序（按名称正序）
            val sortedMainAlbums = mainAlbumsList.sortedWith(Comparator<Album> { album1, album2 ->
                val name1 = album1.name ?: ""
                val name2 = album2.name ?: ""
                name1.compareTo(name2, ignoreCase = true)
            })

            // 自定义相册应用用户选择的排序配置
            val sortConfig = ConfigManager.readAlbumConfig().albumsSortConfig
            val sortedCustomAlbums = sortAlbums(customAlbumsList, sortConfig)

            // 合并排序后的相册列表
            val sortedAlbums = sortedMainAlbums + sortedCustomAlbums
            
            // 只更新内部数据，不更新UI状态
            albumManager.mainAlbums = sortedMainAlbums
            albumManager.customAlbums = sortedCustomAlbums
            albumManager.mergedAlbums = sortedAlbums
            
            // 保存到缓存
            com.example.yumoflatimagemanager.startup.StartupCacheManager.saveAlbumsCache(sortedAlbums)
            com.example.yumoflatimagemanager.startup.StartupCacheManager.saveImagesCache(allImages + allVideos)
            
            // 启动预热渲染
            startWarmupRendering()
            
            // 更新标签统计
            updateAllTagStatistics()
            
            android.util.Log.d("MainViewModel", "最新数据加载完成（无UI更新）")
            
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "加载最新数据失败: ${e.message}")
        }
    }

    /**
     * 异步加载最新数据
     */
    private suspend fun loadLatestDataAsync() {
        try {
            // 在后台加载最新数据
            val allAlbums = mediaContentManager.albums
            val allImages = mediaContentManager.allImages
            val allVideos = mediaContentManager.allVideos
            
            // 分离主要相册和自定义相册
            val mainAlbumsList = allAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.MAIN }
            val customAlbumsList = allAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.CUSTOM }

            // 主要相册保持默认排序（按名称正序）
            val sortedMainAlbums = mainAlbumsList.sortedWith(Comparator<Album> { album1, album2 ->
                val name1 = album1.name ?: ""
                val name2 = album2.name ?: ""
                name1.compareTo(name2, ignoreCase = true)
            })

            // 自定义相册应用用户选择的排序配置
            val sortConfig = ConfigManager.readAlbumConfig().albumsSortConfig
            val sortedCustomAlbums = sortAlbums(customAlbumsList, sortConfig)

            // 合并排序后的相册列表
            val sortedAlbums = sortedMainAlbums + sortedCustomAlbums
            
            // 更新UI
            withContext(Dispatchers.Main) {
                updateAlbumsFromCache(sortedAlbums)
            }
            
            // 保存到缓存
            com.example.yumoflatimagemanager.startup.StartupCacheManager.saveAlbumsCache(sortedAlbums)
            com.example.yumoflatimagemanager.startup.StartupCacheManager.saveImagesCache(allImages + allVideos)
            
            // 启动预热渲染
            startWarmupRendering()
            
            // 更新标签统计
            updateAllTagStatistics()
            
            android.util.Log.d("MainViewModel", "最新数据加载完成")
            
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "加载最新数据失败: ${e.message}")
        }
    }

    /**
     * 对相册列表进行排序
     */
    private fun sortAlbums(albums: List<Album>, sortConfig: SortConfig): List<Album> {
        return albums.sortedWith(Comparator<Album> { album1, album2 ->
            val comparison = when (sortConfig.type) {
                SortType.NAME -> {
                    val name1 = album1.name ?: ""
                    val name2 = album2.name ?: ""
                    name1.compareTo(name2, ignoreCase = true)
                }
                SortType.MODIFY_TIME -> {
                    // 对于相册，我们使用相册的修改时间（这里简化为按名称排序）
                    val name1 = album1.name ?: ""
                    val name2 = album2.name ?: ""
                    name1.compareTo(name2, ignoreCase = true)
                }
                SortType.IMAGE_COUNT -> {
                    album1.count.compareTo(album2.count)
                }
                else -> {
                    val name1 = album1.name ?: ""
                    val name2 = album2.name ?: ""
                    name1.compareTo(name2, ignoreCase = true)
                }
            }
            
            // 根据排序方向调整比较结果
            if (sortConfig.direction == SortDirection.DESCENDING) -comparison else comparison
        })
    }

    /**
     * 启动预热渲染 - 异步加载超低质量缩略图
     */
    private fun startWarmupRendering() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 获取前20张图片进行预热渲染
                val allImages = mediaContentManager.allImages
                val allVideos = mediaContentManager.allVideos
                val allMedia = (allImages + allVideos).take(20)
                
                // 并行预热渲染超低质量缩略图
                allMedia.forEach { mediaItem ->
                    try {
                        SimplifiedImageLoaderHelper.getUltraLowQualityThumbnail(context, mediaItem.uri)
                    } catch (e: Exception) {
                        // 忽略单个图片的预热失败
                    }
                }
                
                android.util.Log.d("MainViewModel", "预热渲染完成，已缓存${allMedia.size}张超低质量缩略图")
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "预热渲染失败: ${e.message}")
            }
        }
    }
    
    /**
     * 加载相册数据 - 同步版本
     */
    private fun loadAlbumData() {
        // 获取所有相册
        val allAlbums = mediaContentManager.albums

        // 分离主要相册和自定义相册
        val mainAlbumsList = allAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.MAIN }
        val customAlbumsList = allAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.CUSTOM }

        // 主要相册保持默认排序（按名称正序）
        val sortedMainAlbums = mainAlbumsList.sortedWith(Comparator<Album> { album1, album2 ->
            val name1 = album1.name ?: ""
            val name2 = album2.name ?: ""
            name1.compareTo(name2, ignoreCase = true)
        })

        // 自定义相册应用用户选择的排序配置
        val sortConfig = ConfigManager.readAlbumConfig().albumsSortConfig
        val sortedCustomAlbums = sortAlbums(customAlbumsList, sortConfig)

        // 合并排序后的相册列表
        val sortedAlbums = sortedMainAlbums + sortedCustomAlbums

        // 更新相册管理器中的数据
        albumManager.mainAlbums = sortedMainAlbums
        albumManager.customAlbums = sortedCustomAlbums
        albumManager.mergedAlbums = sortedAlbums

        // 更新UI状态变量，触发UI重新渲染
        mainAlbums = sortedMainAlbums
        customAlbums = sortedCustomAlbums
        
        android.util.Log.d("MainViewModel", "loadAlbumData完成 - mainAlbums: ${mainAlbums.size}, customAlbums: ${customAlbums.size}")
        android.util.Log.d("MainViewModel", "主要相册保持默认排序，自定义相册应用用户排序配置")

        // 只在首次加载或媒体内容变化时更新全部相册封面缓存
        // 避免每次返回主页面都刷新封面
        if (!SimplifiedImageLoaderHelper.hasAllAlbumCoverCache()) {
            SimplifiedImageLoaderHelper.forceUpdateAllAlbumCoverCache(context)
        }

        // 如果当前有选中的相册，重新加载该相册的图片
        selectedAlbum?.let {
            loadAlbumImages(it)
        }
    }

    /**
     * 异步加载所有媒体内容和相册数据
     */
    fun loadAllDataAsync() {
        viewModelScope.launch {
            // 在IO线程中异步加载媒体内容
            withContext(Dispatchers.IO) {
                mediaContentManager.loadAllMedia()
            }

            // 在主线程中更新UI数据
            withContext(Dispatchers.Main) {
                loadAlbumData()
            }
        }
    }
    
    /**
     * 加载指定相册的图片
     */
    private fun loadAlbumImages(album: Album) {
        // 尝试从偏好设置中获取该相册的排序配置
        val savedSortConfig = ConfigManager.readAlbumConfig().sortConfigs[album.id] ?: SortConfig()
        val sortConfig = savedSortConfig ?: album.sortConfig
        
        // 尝试从偏好设置中获取该相册的网格列数配置
        gridColumnCount = ConfigManager.readAlbumConfig().gridColumns[album.id] ?: 3
        
        // 使用媒体内容管理器获取相册中的图片
        images = mediaContentManager.getImagesByAlbumId(album.id, sortConfig)
        
        // 触发过滤更新，确保标签过滤状态应用到新相册的图片上
        triggerFilterUpdate()
    }
    
    /**
     * 预热加载相册图片
     */
    private fun preloadAlbumImages(album: Album) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 获取相册中的所有图片
                val albumImages = mediaContentManager.getImagesByAlbumId(album.id, album.sortConfig)
                if (albumImages.isNotEmpty()) {
                    android.util.Log.d("MainViewModel", "开始预热加载相册 ${album.name} 的 ${albumImages.size} 张图片")
                    
                    // 立即预加载可见区域的图片（前100张）
                    val visibleRange = 100 // 增加预加载数量
                    val visibleImages = albumImages.take(visibleRange)
                    if (visibleImages.isNotEmpty()) {
                        val uris = visibleImages.map { it.uri }
                        SimplifiedImageLoaderHelper.preloadLowQualityImages(context, uris)
                        android.util.Log.d("MainViewModel", "立即预加载可见区域 ${visibleImages.size} 张图片")
                    }
                    
                    // 然后异步预加载整个相册
                    SimplifiedImageLoaderHelper.preloadAlbumLowQualityImages(context, albumImages)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "预热加载相册图片失败: ${e.message}")
            }
        }
    }
    
    /**
     * 检查是否拥有所有必要的权限
     */
    fun hasRequiredPermissions(): Boolean {
        return PermissionsManager.hasRequiredPermissions(context)
    }
    
    /**
     * 请求媒体权限
     */
    fun requestPermission() {
        val permissions = PermissionsManager.getPermissionsToRequest(context)
        permissionLauncher.launch(permissions)
    }
    
    /**
     * 加载媒体数据
     */
    fun loadMediaData() {
        // 使用异步方式重新加载所有媒体内容，确保权限变更后能获取最新数据
        refreshMedia()
    }
    
    /**
     * 处理相册点击
     */
    fun onAlbumClick(album: Album) {
        // 只刷新选择模式状态，不刷新所有UI状态
        refreshManager.refreshSelectionMode()
        
        // 清理图片加载缓存，避免相册切换时的加载错误
        SimplifiedImageLoaderHelper.clearAllCacheAndState()
        
        // 切换到相册详情屏幕
        screenManager.navigateToAlbumDetail()
        
        // 选中该相册
        albumManager.switchToAlbum(album)
        
        // 加载该相册的图片
        loadAlbumImages(album)
        
        // 预热加载整个相册的最低质量缩略图
        preloadAlbumImages(album)
    }
    
    /**
     * 处理图片点击
     */
    fun onImageClick(image: ImageItem) {
        if (isSelectionMode) {
            // 选择模式下，点击图片切换选择状态
            selectionManager.selectImage(image)
        } else {
            // 非选择模式下，图片查看功能已移至UI层处理
        }
    }
    
    /**
     * 处理图片长按
     */
    fun onImageLongClick(image: ImageItem): Boolean {
        if (!isSelectionMode) {
            // 非选择模式下，长按图片进入选择模式并选中当前项
            selectionManager.toggleSelectionMode()
            // 立即选中当前项，作为锚点
            selectionManager.selectImage(image)
        }
        return true
    }
    
    /**
     * 处理图片滑动选择
     */
    fun onImageDragSelect(imagesToSelect: List<ImageItem>) {
        // 直接同步选中列表到外部的SelectionManager，避免调用selectImages的复杂逻辑
        // 这样可以确保单击操作的结果正确同步，不会被selectImages的条件判断干扰
        if (isSelectionMode) {
            // 直接设置选中列表，不通过selectImages方法
            val currentSelected = selectionManager.selectedImages.toMutableList()
            currentSelected.clear()
            currentSelected.addAll(imagesToSelect)
            selectionManager.selectedImages = currentSelected
        }
    }
    
    /**
     * 切换选择模式
     */
    fun toggleSelectionMode() {
        selectionManager.toggleSelectionMode()
    }
    
    /**
     * 全选或取消全选图片
     */
    fun selectAllImages() {
        selectionManager.selectAllImages(filteredImages)
    }
    
    /**
     * 处理相册点击
     */
    fun onAlbumSelect(album: Album) {
        if (isSelectionMode) {
            // 选择模式下，点击相册切换选择状态
            selectionManager.selectAlbum(album)
        }
    }
    
    /**
     * 处理相册长按
     */
    fun onAlbumLongClick(album: Album): Boolean {
        if (!isSelectionMode) {
            // 非选择模式下，长按相册进入选择模式
            selectionManager.toggleSelectionMode()
            selectionManager.selectAlbum(album)
        }
        return true
    }
    
    /**
     * 全选或取消全选相册
     */
    fun selectAllAlbums() {
        val allAlbums = mergedAlbums
        selectionManager.selectAllAlbums(allAlbums)
    }
    
    /**
     * 检查相册是否被选中
     */
    fun isAlbumSelected(album: Album): Boolean {
        return selectionManager.isAlbumSelected(album)
    }
    
    /**
     * 获取选中的相册列表
     */
    val selectedAlbums: List<Album>
        get() = selectionManager.selectedAlbums
        
    /**
     * 获取选中的相册列表（用于AlbumsScreen）
     */
    fun getSelectedAlbumsList(): List<Album> {
        return selectedAlbums
    }
    
    /**
     * 切换选中相册的隐私状态
     */
    fun toggleSelectedAlbumsPrivacy() {
        viewModelScope.launch {
            val selectedAlbums = selectionManager.selectedAlbums
            
            // 过滤掉系统相册和主要相册，只处理自定义相册
            val nonSystemAlbums = selectedAlbums.filter { 
                it.type != AlbumType.MAIN && it.type != AlbumType.SYSTEM
            }
            
            if (nonSystemAlbums.isEmpty()) return@launch
            
            // 检查所有选中相册的隐私状态
            val allPrivate = nonSystemAlbums.all { album -> 
                secureModeManager.isAlbumPrivate(context, album.id)
            }
            
            // 检查安全模式是否启用
            val isSecureModeEnabled = secureModeManager.isSecureModeEnabled(context)
            
            try {
                var successCount = 0
                var failureCount = 0
                
                withContext(Dispatchers.IO) {
                    // 根据当前状态决定是设为私密还是取消私密
                    if (allPrivate) {
                        // 取消私密
                        nonSystemAlbums.forEach { album -> 
                            val albumPath = getAlbumRealPath(album.id)
                            if (albumPath != null) {
                                // 只有在安全模式启用时才执行实际的公开操作
                                var operationSuccess = true
                                if (isSecureModeEnabled) {
                                    operationSuccess = secureModeManager.makeAlbumPublic(context, albumPath)
                                }
                                
                                // 根据操作结果更新私密相册列表
                                if (operationSuccess) {
                                    secureModeManager.removePrivateAlbum(context, album.id)
                                    successCount++
                                } else {
                                    failureCount++
                                }
                            } else {
                                failureCount++
                            }
                        }
                    } else {
                        // 设为私密前检查是否包含子文件夹
                        val albumsWithSubfolders = nonSystemAlbums.filter { album ->
                            val albumPath = getAlbumRealPath(album.id)
                            albumPath != null && secureModeManager.containsSubFolders(albumPath)
                        }
                        
                        // 如果有相册包含子文件夹，标记操作失败并显示警告
                        if (albumsWithSubfolders.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "包含子文件夹的相册无法设为私密",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            return@withContext
                        } else {
                            // 所有相册都不包含子文件夹，执行设为私密操作
                            nonSystemAlbums.forEach { album -> 
                                val albumPath = getAlbumRealPath(album.id)
                                if (albumPath != null) {
                                    // 只有在安全模式启用时才执行实际的私密操作
                                    var operationSuccess = true
                                    if (isSecureModeEnabled) {
                                        operationSuccess = secureModeManager.makeAlbumPrivate(context, albumPath)
                                    }
                                    
                                    // 根据操作结果更新私密相册列表
                                    if (operationSuccess) {
                                        secureModeManager.addPrivateAlbum(context, album.id)
                                        successCount++
                                    } else {
                                        failureCount++
                                    }
                                } else {
                                    failureCount++
                                }
                            }
                        }
                    }
                }
                
                // 在主线程更新UI和显示结果
                withContext(Dispatchers.Main) {
                    // 刷新相册数据，更新UI显示
                    refreshMedia()
                    
                    // 清除选择并退出选择模式
                    selectionManager.clearSelection()
                    selectionManager.toggleSelectionMode()
                    
                    // 显示操作结果
                    if (failureCount > 0) {
                        android.widget.Toast.makeText(
                            context,
                            "操作部分失败：成功${successCount}个，失败${failureCount}个",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else if (successCount > 0) {
                        // 显示成功消息
                        val message = if (allPrivate) {
                            "成功公开${successCount}个相册"
                        } else {
                            "成功设置${successCount}个相册为私密"
                        }
                        android.widget.Toast.makeText(
                            context,
                            message,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                // 处理异常情况
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "设置失败：${e.message ?: "未知错误"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 移动选中的图片
     */
    fun moveSelectedImages(targetAlbum: Album) {
        val selectedImages = selectionManager.selectedImages
        if (selectedImages.isEmpty()) {
            uiStateManager.hideMoveDialog()
            return
        }
        
        // 隐藏对话框
        uiStateManager.hideMoveDialog()
        
        // 在后台执行移动操作
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 根据相册类型确定目标路径
                val targetPath = when (targetAlbum.type) {
                    com.example.yumoflatimagemanager.data.AlbumType.MAIN -> {
                        when (targetAlbum.id) {
                            "all" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
                            "video" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath
                            "camera" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Camera"
                            "screenshot" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
                            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
                        }
                    }
                    com.example.yumoflatimagemanager.data.AlbumType.CUSTOM -> {
                        // 自定义相册路径
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/" + targetAlbum.name
                    }
                    com.example.yumoflatimagemanager.data.AlbumType.SYSTEM -> {
                        // 系统相册路径
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
                    }
                }
                
                com.example.yumoflatimagemanager.media.FileOperationManager.moveFilesToAlbum(
                    context = context,
                    images = selectedImages,
                    targetAlbumPath = targetPath,
                    callback = object : com.example.yumoflatimagemanager.media.FileOperationManager.OperationProgressCallback {
                        override fun onProgress(current: Int, total: Int, fileName: String) {
                            // 静默执行，不更新UI
                        }
                        
                        override fun onCompleted(result: com.example.yumoflatimagemanager.media.FileOperationManager.OperationResult) {
                            if (result.success) {
                                // 同步更新标签中存储的图片位置信息
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        selectedImages.forEach { image ->
                                            // 更新该图片在标签中的路径信息
                                            val newPath = targetPath + "/" + (image.name ?: "unknown")
                                            tagRepo.updateMediaPath(image.uri.toString(), newPath)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                
                                // 刷新媒体内容
                                refreshMedia()
                                
                                // 刷新标签统计信息
                                updateAllTagStatistics()
                                
                                // 清除选中状态并退出选择模式
        selectionManager.clearSelection()
        selectionManager.toggleSelectionMode()
                                
                                // 显示成功提示
                                viewModelScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "已移动${selectedImages.size}张图片到${targetAlbum.name}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                // 显示失败提示
                                viewModelScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "移动操作失败: ${result.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                // 显示异常提示
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "移动操作失败: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 显示删除确认对话框
     */
    fun showDeleteConfirmDialog() {
        val selectedCount = selectionManager.selectedImages.size
        deleteConfirmMessage = "确定要删除选中的 $selectedCount 个文件吗？\n\n删除的文件将移动到回收站，可以在系统相册的\"最近删除\"中恢复。"
        showDeleteConfirmDialog = true
    }
    
    /**
     * 隐藏删除确认对话框
     */
    fun hideDeleteConfirmDialog() {
        showDeleteConfirmDialog = false
        deleteConfirmMessage = ""
    }
    
    /**
     * 确认删除选中的图片
     */
    fun confirmDeleteSelectedImages() {
        val selectedImages = selectionManager.selectedImages
        if (selectedImages.isEmpty()) {
            hideDeleteConfirmDialog()
            return
        }
        
        hideDeleteConfirmDialog()
        
        // 立即退出多选模式并隐藏要删除的图片
        val imagesToDelete = selectedImages.toList() // 保存要删除的图片列表
        
        // 清理 Coil 缓存，避免删除后显示错误的缓存图片
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val imageLoader = coil.ImageLoader(context)
                imagesToDelete.forEach { image ->
                    // 清理内存缓存
                    imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(image.uri.toString()))
                    // 清理磁盘缓存（通过多种可能的缓存键）
                    val sizes = listOf(150, 160, 180, 200, 250, 300, 400)
                    sizes.forEach { size ->
                        imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key("${image.uri}_${size}"))
                        imageLoader.diskCache?.remove("${image.uri}_${size}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        selectionManager.clearSelection() // 清除选中状态
        selectionManager.toggleSelectionMode() // 退出选择模式
        
        // 从当前图片列表中移除要删除的图片，实现立即隐藏效果
        val currentImages = images.toMutableList()
        currentImages.removeAll { image -> 
            imagesToDelete.any { toDelete -> toDelete.uri == image.uri }
        }
        images = currentImages
        
        // 触发过滤更新，确保删除后的图片列表正确应用标签过滤
        triggerFilterUpdate()
        
        // 在后台默默执行删除操作
        viewModelScope.launch(Dispatchers.IO) {
            try {
                    com.example.yumoflatimagemanager.media.FileOperationManager.deleteFiles(
                        context = context,
                    images = imagesToDelete,
                        callback = object : com.example.yumoflatimagemanager.media.FileOperationManager.OperationProgressCallback {
                            override fun onProgress(current: Int, total: Int, fileName: String) {
                            // 静默执行，不更新UI
                            }
                            
                            override fun onCompleted(result: com.example.yumoflatimagemanager.media.FileOperationManager.OperationResult) {
                            // 删除完成后刷新媒体内容和标签统计
                                if (result.success) {
                                // 同步删除标签中存储的图片信息
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        imagesToDelete.forEach { image ->
                                            // 删除该图片的所有标签关联
                                            tagRepo.removeAllTagsFromMedia(image.uri.toString())
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                
                                // 刷新媒体内容（包括标签同步）
                                refreshMedia()
                                
                                // 刷新标签统计信息
                                updateAllTagStatistics()
                                
                                // 在主线程显示删除成功提示
                                viewModelScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "已删除${imagesToDelete.size}张图片",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                // 删除失败，重新加载媒体内容恢复图片显示
                                refreshMedia()
                                
                                // 在主线程显示删除失败提示
                                viewModelScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "删除操作失败: ${result.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                // 删除异常，重新加载媒体内容恢复图片显示
                refreshMedia()
                
                // 在主线程显示异常提示
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "删除操作失败: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 删除选中的图片（旧方法，保留兼容性）
     */
    fun deleteSelectedImages() {
        showDeleteConfirmDialog()
    }

    // 相册详情：显示移动/复制选择对话框
    fun showMoveDialog() {
        uiStateManager.showMoveDialog()
    }
    
    /**
     * 删除选中的相册
     */
    fun deleteSelectedAlbums() {
        // 获取选中的相册
        val selectedAlbums = selectionManager.selectedAlbums
        
        // 删除每个选中的相册
        selectedAlbums.forEach {
            albumManager.deleteAlbum(it)
        }
        
        // 清除选中状态
        selectionManager.clearSelection()
        selectionManager.toggleSelectionMode()
        
        // 刷新相册列表
        loadAlbumData()
    }
    
    /**
     * 创建新相册
     */
    fun createNewAlbum(name: String) {
        albumManager.createAlbum(name)
        uiStateManager.hideCreateAlbumDialog()
    }
    
    /**
     * 检查是否可以重命名选中的相册
     * 条件：只选中了一个相册，且不是主要相册
     */
    fun canRenameSelectedAlbum(): Boolean {
        val selectedAlbums = selectionManager.selectedAlbums
        // 只选中了一个相册，且不是主要相册
        return selectedAlbums.size == 1 && selectedAlbums[0].type == com.example.yumoflatimagemanager.data.AlbumType.CUSTOM
    }
    
    /**
     * 获取选中的自定义相册（如果有且只有一个）
     */
    fun getSingleSelectedCustomAlbum(): Album? {
        val selectedAlbums = selectionManager.selectedAlbums
        if (selectedAlbums.size == 1 && selectedAlbums[0].type == com.example.yumoflatimagemanager.data.AlbumType.CUSTOM) {
            return selectedAlbums[0]
        }
        return null
    }

    /**
     * 获取相册在系统中的实际详细路径
     * @param albumId 相册ID，通常是文件夹名称或路径
     * @return 相册的实际系统路径，如果无法获取则返回null
     */
    fun getAlbumRealPath(albumId: String): String? {
        return AlbumPathManager.getAlbumRealPath(context, albumId)
    }

    /**
     * 验证相册路径是否有效
     * @param path 要验证的路径
     * @return 路径是否有效
     */
    fun isValidAlbumPath(path: String?): Boolean {
        return AlbumPathManager.isValidAlbumPath(path)
    }

    /**
     * 格式化相册路径，确保路径格式一致
     * @param path 要格式化的路径
     * @return 格式化后的路径
     */
    fun formatAlbumPath(path: String?): String? {
        return AlbumPathManager.formatAlbumPath(path)
    }

    /**
     * 从路径中提取相册的显示名称
     * @param path 相册路径
     * @return 相册的显示名称
     */
    fun getAlbumDisplayNameFromPath(path: String?): String {
        return AlbumPathManager.getAlbumDisplayNameFromPath(path)
    }
    
    /**
     * 重命名相册
     * @param album 要重命名的相册
     * @param newName 新的相册名称
     * @return 是否重命名成功
     */
    fun renameAlbum(album: Album, newName: String): Boolean {
        if (album.type != com.example.yumoflatimagemanager.data.AlbumType.CUSTOM) {
            return false
        }

        // 获取相册的实际文件系统路径
        val albumPath = getAlbumRealPath(album.id)

        // 如果无法获取相册路径，无法执行实际重命名
        if (albumPath == null) {
            return false
        }

        // 保存原始相册名称，用于可能的恢复操作
        val originalName = album.name
        val originalId = album.id

        // 立即更新UI - 先显示新名称，提高用户体验
        val appRenamed = albumManager.renameAlbum(album, newName)

        if (appRenamed) {
            // 清除选中状态
            selectionManager.clearSelection()
            selectionManager.toggleSelectionMode()

            // 在后台线程执行文件系统重命名和媒体库扫描
            viewModelScope.launch {
                try {
                    // 执行实际的文件系统重命名操作
                    val fileSystemRenamed = FileRenameManager.renameFolder(context, albumPath, newName)

                    if (!fileSystemRenamed) {
                        // 文件系统重命名失败，恢复原来的相册名称
                        withContext(Dispatchers.Main) {
                            // 创建一个临时相册对象用于恢复名称
                            val tempAlbum = Album(
                                id = originalId,
                                name = originalName,
                                coverImage = album.coverImage,
                                coverUri = album.coverUri,
                                count = album.count,
                                type = album.type,
                                sortConfig = album.sortConfig
                            )
                            albumManager.renameAlbum(tempAlbum, originalName) // 恢复原来的名称
                            loadAlbumData() // 重新加载相册数据
                        }
                    } else {
                        // 文件系统重命名成功，使用防抖异步更新媒体内容
                        withContext(Dispatchers.Main) {
                            mediaContentManager.loadAllMediaAsync()
                        }
                    }
                } catch (e: Exception) {
                    // 处理可能的异常，恢复原来的相册名称
                    withContext(Dispatchers.Main) {
                        // 创建一个临时相册对象用于恢复名称
                        val tempAlbum = Album(
                            id = originalId,
                            name = originalName,
                            coverImage = album.coverImage,
                            coverUri = album.coverUri,
                            count = album.count,
                            type = album.type,
                            sortConfig = album.sortConfig
                        )
                        albumManager.renameAlbum(tempAlbum, originalName) // 恢复原来的名称
                        loadAlbumData() // 重新加载相册数据
                    }
                }
            }
        }

        return appRenamed
    }
    
    /**
     * 更新相册排序配置
     */
    fun updateAlbumSortConfig(album: Album, sortConfig: SortConfig) {
        // 更新相册的排序配置
        val updatedAlbum = albumManager.updateAlbumSortConfig(album, sortConfig)
        
        // 从配置文件读取当前相册配置
        val albumConfig = ConfigManager.readAlbumConfig()
        // 更新相册排序配置
        albumConfig.sortConfigs[album.id] = sortConfig
        // 保存回配置文件
        ConfigManager.writeAlbumConfig(albumConfig)
        
        // 重新加载相册图片
        loadAlbumImages(updatedAlbum)
    }

    /**
     * 设置相册列表的排序配置
     */
    fun setAlbumsSortConfig(sortConfig: SortConfig) {
        // 从配置文件读取当前相册配置
        val albumConfig = ConfigManager.readAlbumConfig()
        // 更新相册列表排序配置
        albumConfig.albumsSortConfig = sortConfig
        // 保存回配置文件
        ConfigManager.writeAlbumConfig(albumConfig)
        
        // 重新加载相册数据以应用排序
        loadAlbumData()
    }
    
    /**
     * 更新相册网格列数配置
     */
    fun updateAlbumGridColumns(album: Album, columns: Int) {
        // 确保列数在有效范围内
        val validColumns = columns.coerceIn(2, 8)
        
        // 更新当前网格列数
        gridColumnCount = validColumns
        
        // 从配置文件读取当前相册配置
        val albumConfig = ConfigManager.readAlbumConfig()
        // 更新相册网格列数配置
        albumConfig.gridColumns[album.id] = validColumns
        // 保存回配置文件
        ConfigManager.writeAlbumConfig(albumConfig)
        
        // 强制重新加载相册图片，以适应新的网格列数
        loadAlbumImages(album)
    }
    
    /**
     * 处理返回按钮点击
     */
    fun handleBackPress() {
        if (isSelectionMode) {
            // 如果处于选择模式，清除选择并退出选择模式
            selectionManager.clearSelection()
            selectionManager.toggleSelectionMode()
        } else if (showMoveDialog) {
            // 如果移动对话框显示，关闭对话框
            uiStateManager.hideMoveDialog()
        } else if (showCreateAlbumDialog) {
            // 如果创建相册对话框显示，关闭对话框
            uiStateManager.hideCreateAlbumDialog()
        } else {
            // 其他情况，执行屏幕返回
            val wasInAlbumDetail = currentScreen is Screen.AlbumDetail
            
            // 在返回操作之前只刷新选择模式状态，避免不必要的UI刷新
            if (wasInAlbumDetail) {
                refreshManager.refreshSelectionMode()
            }
            
            screenManager.handleBackPress()
        }
    }
    
    // 此处的对话框显示/隐藏方法已在文件上方提供，不再重复定义
    
    /**
     * 检查图片是否被选中
     */
    fun isImageSelected(image: ImageItem): Boolean {
        return selectionManager.isImageSelected(image)
    }
    
    /**
     * 释放资源
     */
    override fun onCleared() {
        super.onCleared()
        mediaContentManager.release()
    }

    // 时间属性更新入口：对选中图片批量更新拍摄或修改时间
    fun updateSelectedTakenTime(takenTimeMillis: Long) {
        val targets = selectedImages
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            targets.forEach { item ->
                TimeMetadataUpdater.updateTakenTimeWithExif(context, item.uri, takenTimeMillis)
            }
        }
    }

    fun updateSelectedModifyTime(modifyTimeMillis: Long) {
        val targets = selectedImages
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            targets.forEach { item ->
                TimeMetadataUpdater.updateModifyTime(context, item.uri, modifyTimeMillis)
            }
        }
    }
    
    // ==================== 过滤管理功能 ====================
    
    /**
     * 显示重置标签状态确认对话框
     */
    fun showResetConfirmationDialog() {
        showResetConfirmationDialog = true
    }
    
    /**
     * 隐藏重置标签状态确认对话框
     */
    fun hideResetConfirmationDialog() {
        showResetConfirmationDialog = false
    }
    
    /**
     * 清除所有标签过滤（模块化功能，供多个地方复用）
     */
    fun clearAllTagFilters() {
        // 清除激活的标签过滤
        activeTagFilterIds = emptySet()
        // 清除排除的标签
        excludedTagIds = emptySet()
        // 清除展开的标签
        expandedTagIds = emptySet()
        // 清除展开的引用标签
        expandedReferencedTagIds = emptySet()
        
        // 持久化清除的状态
        // 重置标签配置并保存到文件
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.activeTagFilterIds = emptySet()
        tagConfig.excludedTagIds = emptySet()
        tagConfig.expandedTagIds = emptySet()
        tagConfig.expandedReferencedTagIds = emptySet()
        ConfigManager.writeTagConfig(tagConfig)
        
        // 清理过滤缓存，触发重新过滤
        clearFilterCache()
        
        android.util.Log.d("MainViewModel", "已清除所有标签过滤和展开状态")
    }
    
    /**
     * 分享选中的图片
     */
    fun shareSelectedImages() {
        val selectedImages = selectionManager.selectedImages
        if (selectedImages.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                "请先选择要分享的图片",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        try {
            val uris = mutableListOf<Uri>()
            
            selectedImages.forEach { imageItem ->
                // 直接使用ImageItem的uri属性
                uris.add(imageItem.uri)
            }
            
            if (uris.isNotEmpty()) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooserIntent = Intent.createChooser(shareIntent, "分享图片")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)
                
                android.util.Log.d("MainViewModel", "分享 ${uris.size} 张图片")
            } else {
                android.widget.Toast.makeText(
                    context,
                    "没有找到可分享的图片",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "分享图片失败: ${e.message}", e)
            android.widget.Toast.makeText(
                context,
                "分享失败: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * 移动标签到新位置（拖拽排序）
     * @param fromIndex 源位置索引
     * @param toIndex 目标位置索引
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
     * @param fromIndex 源位置索引
     * @param toIndex 目标位置索引
     * @param isWithReferences 是否是有引用标签的本体标签组
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
        groupTags: List<com.example.yumoflatimagemanager.data.local.TagWithChildren>,
        fromIndex: Int,
        toIndex: Int,
        isWithReferences: Boolean
    ) {
        if (fromIndex == toIndex) return
        
        val movedTag = groupTags[fromIndex]
        
        // 计算新的排序值（使用新的分组排序字段）
        val newSortOrder = 1000 + toIndex * 1000  // 从1000开始，间隔1000
        
        // 更新被移动标签的排序值（使用对应的分组排序字段）
        if (isWithReferences) {
            tagRepo.updateReferencedGroupSortOrder(movedTag.tag.id, newSortOrder)
        } else {
            tagRepo.updateNormalGroupSortOrder(movedTag.tag.id, newSortOrder)
        }
        println("DEBUG: 分组内更新标签 ${movedTag.tag.name} 的${if (isWithReferences) "引用组" else "普通组"}排序值为 $newSortOrder")
        
        // 更新组内其他标签的排序值
        groupTags.forEachIndexed { index, tagWithChildren ->
            if (index != fromIndex) {
                val adjustedIndex = if (index > fromIndex && index <= toIndex) index - 1
                else if (index < fromIndex && index >= toIndex) index + 1
                else index
                
                val adjustedSortOrder = 1000 + adjustedIndex * 1000
                if (isWithReferences) {
                    tagRepo.updateReferencedGroupSortOrder(tagWithChildren.tag.id, adjustedSortOrder)
                } else {
                    tagRepo.updateNormalGroupSortOrder(tagWithChildren.tag.id, adjustedSortOrder)
                }
                println("DEBUG: 分组内调整标签 ${tagWithChildren.tag.name} 的${if (isWithReferences) "引用组" else "普通组"}排序值为 $adjustedSortOrder")
            }
        }
    }
    
    /**
     * 实现插入逻辑：将标签插入到目标位置，使用新的分组排序字段
     */
    private suspend fun insertTagAtPosition(
        tags: List<com.example.yumoflatimagemanager.data.local.TagWithChildren>,
        fromIndex: Int,
        toIndex: Int
    ) {
        if (fromIndex == toIndex) return
        
        val movedTag = tags[fromIndex]
        val targetTag = tags[toIndex]
        
        // 判断移动的标签和目标位置的标签属于哪个组
        val movedTagHasReferences = movedTag.referencedTags.isNotEmpty()
        val targetTagHasReferences = targetTag.referencedTags.isNotEmpty()
        
        // 如果两个标签属于不同的组，需要先处理组切换
        if (movedTagHasReferences != targetTagHasReferences) {
            // 标签需要切换组
            handleTagGroupSwitch(movedTag.tag.id, targetTagHasReferences)
        }
        
        // 现在两个标签在同一个组内，使用分组内排序
        val isWithReferences = targetTagHasReferences
        val groupTags = if (isWithReferences) {
            tags.filter { it.referencedTags.isNotEmpty() }
        } else {
            tags.filter { it.referencedTags.isEmpty() }
        }
        
        // 找到在组内的索引
        val fromGroupIndex = groupTags.indexOf(movedTag)
        val toGroupIndex = groupTags.indexOf(targetTag)
        
        if (fromGroupIndex >= 0 && toGroupIndex >= 0) {
            // 在组内进行排序
            insertTagAtPositionInGroup(groupTags, fromGroupIndex, toGroupIndex, isWithReferences)
        }
    }
    
    // 注意：以下方法已被新的分组排序逻辑替代，不再需要
    // updateAffectedTagsSortOrderForInsertion, updateAffectedTagsSortOrder, reindexAllTags
    // 新的排序逻辑在 insertTagAtPositionInGroup 中处理
    
    
    /**
     * 设置排序模式状态
     */
    fun setDragMode(isDragMode: Boolean) {
        isInDragMode = isDragMode
        println("DEBUG: 设置排序模式状态: $isDragMode")
    }
    
    /**
     * 刷新标签列表
     */
    fun refreshTags() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 强制刷新标签数据
                tagRepo.refreshTags()
                println("DEBUG: 标签列表已刷新")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 移动子标签排序（引用标签就是子标签）
     * @param parentTagId 父标签ID
     * @param fromIndex 源位置索引
     * @param toIndex 目标位置索引
     */
    fun moveChildTag(parentTagId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 获取父标签的引用标签列表
                val parentTag = tagRepo.getTagById(parentTagId)
                if (parentTag == null) {
                    println("DEBUG: 父标签不存在，ID=$parentTagId")
                    return@launch
                }
                
                // 获取所有引用标签并按引用关系的sortOrder排序
                val referencedTags = tagRepo.getTagReferences(parentTagId)
                    .sortedBy { it.referenceSortOrder }  // 使用引用关系的sortOrder
                
                println("DEBUG: 子标签排序 - 父标签ID: $parentTagId, 从 $fromIndex 到 $toIndex")
                println("DEBUG: 引用标签数量: ${referencedTags.size}")
                println("DEBUG: 引用标签列表: ${referencedTags.map { "${it.tag.name}(refSortOrder=${it.referenceSortOrder})" }}")
                
                if (fromIndex < 0 || fromIndex >= referencedTags.size || 
                    toIndex < 0 || toIndex >= referencedTags.size || 
                    fromIndex == toIndex) {
                    println("DEBUG: 索引无效或相同 - fromIndex=$fromIndex, toIndex=$toIndex, size=${referencedTags.size}")
                    return@launch
                }
                
                // 实现子标签插入逻辑
                insertChildTagAtPosition(parentTagId, referencedTags, fromIndex, toIndex)
                
                // 强制刷新标签列表以确保UI更新
                println("DEBUG: 排序完成，刷新标签列表")
                tagRepo.refreshTags()
                
                // 触发标签引用刷新，更新UI
                withContext(Dispatchers.Main) {
                    triggerTagReferenceRefresh()
                }
                println("DEBUG: 已触发标签引用刷新")
                
            } catch (e: Exception) {
                println("DEBUG: 移动子标签时发生错误: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 实现子标签插入逻辑
     */
    private suspend fun insertChildTagAtPosition(
        parentTagId: Long,
        referencedTags: List<com.example.yumoflatimagemanager.data.repo.TagWithReferenceOrder>,  // 修改类型
        fromIndex: Int,
        toIndex: Int
    ) {
        if (fromIndex == toIndex) return
        
        val movedTag = referencedTags[fromIndex]
        
        // 使用引用关系的sortOrder计算新值
        val newSortOrder = calculateNewReferenceSortOrder(referencedTags, fromIndex, toIndex)
        
        // 更新被移动标签的引用关系排序值
        tagRepo.updateTagReferenceSort(parentTagId, movedTag.tag.id, newSortOrder)
        println("DEBUG: 更新引用标签 ${movedTag.tag.name} 的排序值为 $newSortOrder")
        
        // 更新受影响的其他引用标签排序值
        updateAffectedChildTagsSortOrder(parentTagId, referencedTags, fromIndex, toIndex)
        
        // 检查并修复可能的排序值重复
        tagRepo.checkAndFixSortOrderDuplicates(parentTagId)
    }
    
    /**
     * 计算新的引用关系排序值，确保唯一性
     */
    private fun calculateNewReferenceSortOrder(
        referencedTags: List<com.example.yumoflatimagemanager.data.repo.TagWithReferenceOrder>,
        fromIndex: Int,
        toIndex: Int
    ): Int {
        return when {
            toIndex == 0 -> {
                // 插入到第一个位置：使用第一个标签的引用排序值减1000
                val firstTag = referencedTags[0]
                firstTag.referenceSortOrder - 1000
            }
            toIndex >= referencedTags.size - 1 -> {
                // 插入到最后一个位置：使用最后一个标签的引用排序值加1000
                val lastTag = referencedTags[referencedTags.size - 1]
                lastTag.referenceSortOrder + 1000
            }
            toIndex < fromIndex -> {
                // 向前插入：使用目标位置和前一位置引用排序值的中间值
                val targetTag = referencedTags[toIndex]
                val prevTag = if (toIndex > 0) referencedTags[toIndex - 1] else null
                if (prevTag != null) {
                    (prevTag.referenceSortOrder + targetTag.referenceSortOrder) / 2
                } else {
                    targetTag.referenceSortOrder - 500
                }
            }
            else -> {
                // 向后插入：使用目标位置和后一位置引用排序值的中间值
                val targetTag = referencedTags[toIndex]
                val nextTag = if (toIndex < referencedTags.size - 1) referencedTags[toIndex + 1] else null
                if (nextTag != null) {
                    (targetTag.referenceSortOrder + nextTag.referenceSortOrder) / 2
                } else {
                    targetTag.referenceSortOrder + 500
                }
            }
        }
    }
    
    /**
     * 更新受影响的子标签排序值
     */
    private suspend fun updateAffectedChildTagsSortOrder(
        parentTagId: Long,
        referencedTags: List<com.example.yumoflatimagemanager.data.repo.TagWithReferenceOrder>,
        fromIndex: Int,
        toIndex: Int
    ) {
        // 由于使用了中间值计算，大多数情况下不需要调整其他标签的排序值
        // 只有在排序值过于接近时才需要重新分配
        if (needsReferenceSortOrderRedistribution(referencedTags)) {
            redistributeReferenceSortOrders(parentTagId, referencedTags)
        }
    }
    
    /**
     * 检查是否需要重新分配引用关系排序值
     */
    private fun needsReferenceSortOrderRedistribution(
        referencedTags: List<com.example.yumoflatimagemanager.data.repo.TagWithReferenceOrder>
    ): Boolean {
        // 检查是否有引用排序值过于接近（差值小于10）
        for (i in 0 until referencedTags.size - 1) {
            val current = referencedTags[i].referenceSortOrder
            val next = referencedTags[i + 1].referenceSortOrder
            if (next - current < 10) {
                return true
            }
        }
        return false
    }
    
    /**
     * 重新分配引用关系排序值，确保唯一性和足够的间隔
     */
    private suspend fun redistributeReferenceSortOrders(
        parentTagId: Long,
        referencedTags: List<com.example.yumoflatimagemanager.data.repo.TagWithReferenceOrder>
    ) {
        println("DEBUG: 重新分配引用关系排序值以确保唯一性")
        
        // 使用批量更新避免并发冲突
        val updates = referencedTags.mapIndexed { index, tagWithOrder ->
            val newSortOrder = (index + 1) * 1000  // 使用1000的间隔
            Triple(parentTagId, tagWithOrder.tag.id, newSortOrder)
        }
        
        // 批量执行更新
        updates.forEach { (parentId, childId, sortOrder) ->
            tagRepo.updateTagReferenceSort(parentId, childId, sortOrder)
            println("DEBUG: 重新分配引用标签排序值为 $sortOrder")
        }
    }
    
    
    /**
     * 重置标签排序（按名称排序）
     */
    fun resetTagSort() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTags = tagsFlow.first()
                currentTags.sortedBy { it.tag.name }.forEachIndexed { index, tagWithChildren ->
                    val newSortOrder = (index + 1) * 1000  // 使用1000的间隔确保唯一性
                    tagRepo.updateTagSort(tagWithChildren.tag.id, newSortOrder)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 重置引用标签排序（按名称排序）
     */
    fun resetReferencedTagSort(parentTagId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val referencedTags = tagRepo.getTagReferences(parentTagId).sortedBy { it.tag.name }
                referencedTags.forEachIndexed { index, refTag ->
                    val newSortOrder = (index + 1) * 1000  // 使用1000的间隔确保唯一性
                    tagRepo.updateTagReferenceSort(parentTagId, refTag.tag.id, newSortOrder)
                    println("DEBUG: 重置引用标签 ${refTag.tag.name} 的排序值为 $newSortOrder")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 检查并修复所有标签的排序值唯一性
     */
    fun checkAndFixAllSortOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTags = tagsFlow.first()
                
                // 重新分配主标签的排序值
                currentTags.forEachIndexed { index, tagWithChildren ->
                    val newSortOrder = (index + 1) * 1000
                    tagRepo.updateTagSort(tagWithChildren.tag.id, newSortOrder)
                    println("DEBUG: 重新分配主标签 ${tagWithChildren.tag.name} 的排序值为 $newSortOrder")
                }
                
                // 检查并修复引用标签的排序值
                currentTags.forEach { tagWithChildren ->
                    tagRepo.checkAndFixSortOrderDuplicates(tagWithChildren.tag.id)
                }
                
                println("DEBUG: 完成所有标签排序值检查和修复")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 重新分配所有标签的排序值，确保唯一性和连续性
     */
    private suspend fun redistributeAllTagSortOrders() {
        try {
            val currentTags = tagsFlow.first()
            
            // 分别处理两个组的排序值重新分配
            val tagsWithReferences = currentTags.filter { it.referencedTags.isNotEmpty() }
            val tagsWithoutReferences = currentTags.filter { it.referencedTags.isEmpty() }
            
            // 重新分配有引用标签的本体标签组的排序值
            tagsWithReferences.forEachIndexed { index, tagWithChildren ->
                val newSortOrder = (index + 1) * 1000  // 从1000开始，间隔1000
                tagRepo.updateReferencedGroupSortOrder(tagWithChildren.tag.id, newSortOrder)
                println("DEBUG: 重新分配有引用标签的本体标签 ${tagWithChildren.tag.name} 的排序值为 $newSortOrder")
            }
            
            // 重新分配无引用标签的本体标签组的排序值
            tagsWithoutReferences.forEachIndexed { index, tagWithChildren ->
                val newSortOrder = (index + 1) * 1000  // 从1000开始，间隔1000
                tagRepo.updateNormalGroupSortOrder(tagWithChildren.tag.id, newSortOrder)
                println("DEBUG: 重新分配无引用标签的本体标签 ${tagWithChildren.tag.name} 的排序值为 $newSortOrder")
            }
            
            println("DEBUG: 完成所有标签排序值重新分配")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 当标签引用状态发生变化时，移动到正确的组
     * @param tagId 标签ID
     * @param hasReferences 是否有引用
     */
    fun moveTagToCorrectGroup(tagId: Long, hasReferences: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTags = tagsFlow.first()
                val tag = currentTags.find { it.tag.id == tagId }
                if (tag == null) return@launch
                
                val baseSortOrder = if (hasReferences) 1000 else 100000
                val newSortOrder = baseSortOrder  // 移动到组的最上面
                
                tagRepo.updateTagSort(tagId, newSortOrder)
                println("DEBUG: 移动标签 ${tag.tag.name} 到${if (hasReferences) "有引用" else "无引用"}组，排序值为 $newSortOrder")
                
                // 重新分配排序值
                redistributeAllTagSortOrders()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 处理标签在两组之间切换时的排序值分配
     * @param tagId 标签ID
     * @param hasReferences 是否有引用
     */
    fun handleTagGroupSwitch(tagId: Long, hasReferences: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allTags = tagsFlow.first()
                val tag = allTags.find { it.tag.id == tagId }
                if (tag == null) return@launch
                
                if (hasReferences) {
                    // 标签从普通组切换到有引用组
                    // 清空普通组排序值，设置引用组排序值
                    tagRepo.updateNormalGroupSortOrder(tagId, 0)
                    
                    val tagsWithReferences = allTags.filter { it.referencedTags.isNotEmpty() }
                    val newSortOrder = if (tagsWithReferences.isEmpty()) {
                        1000
                    } else {
                        val minSortOrder = tagsWithReferences.minOf { it.tag.referencedGroupSortOrder }
                        if (minSortOrder > 0) minSortOrder - 1000 else 1000
                    }
                    tagRepo.updateReferencedGroupSortOrder(tagId, newSortOrder)
                    println("DEBUG: 标签 ${tag.tag.name} 切换到有引用组，排序值: $newSortOrder")
                    
                } else {
                    // 标签从有引用组切换到普通组
                    // 清空引用组排序值，设置普通组排序值
                    tagRepo.updateReferencedGroupSortOrder(tagId, 0)
                    
                    val tagsWithoutReferences = allTags.filter { it.referencedTags.isEmpty() }
                    val newSortOrder = if (tagsWithoutReferences.isEmpty()) {
                        1000
                    } else {
                        val minSortOrder = tagsWithoutReferences.minOf { it.tag.normalGroupSortOrder }
                        if (minSortOrder > 0) minSortOrder - 1000 else 1000
                    }
                    tagRepo.updateNormalGroupSortOrder(tagId, newSortOrder)
                    println("DEBUG: 标签 ${tag.tag.name} 切换到普通组，排序值: $newSortOrder")
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 处理新创建标签的默认位置
     * @param tagId 新标签ID
     */
    fun setNewTagDefaultPosition(tagId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 新标签默认在无引用组的最上面
                val baseSortOrder = 1000
                tagRepo.updateNormalGroupSortOrder(tagId, baseSortOrder)
                println("DEBUG: 新标签默认位置设置为无引用组最上面，排序值为 $baseSortOrder")
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 选择标签
     */
    fun selectTag(tagId: Long) {
        // 这里可以添加选择标签的逻辑，比如更新UI状态
        println("DEBUG: 选择标签 $tagId")
    }
    
    // ==================== 文件操作相关方法 ====================
    
    /**
     * 显示相册选择屏幕
     */
    fun showAlbumSelection() {
        showAlbumSelectionScreen = true
    }
    
    /**
     * 隐藏相册选择屏幕
     */
    fun hideAlbumSelection() {
        albumSelectionExitAnimation = AlbumSelectionExitAnimation.SLIDE_OUT
        showAlbumSelectionScreen = false
        selectedTargetAlbum = null
    }
    
    /**
     * 选择目标相册
     */
    fun selectTargetAlbum(album: Album) {
        selectedTargetAlbum = album
        albumSelectionExitAnimation = AlbumSelectionExitAnimation.FADE_OUT
        showAlbumSelectionScreen = false
        showMoveCopyDialog = true
    }
    
    /**
     * 隐藏移动/复制对话框
     */
    fun hideMoveCopyDialog() {
        showMoveCopyDialog = false
        selectedTargetAlbum = null
    }
    
    /**
     * 执行移动操作
     */
    fun performMoveOperation() {
        val targetAlbum = selectedTargetAlbum ?: return
        val selectedImages = selectedImages
        
        if (selectedImages.isEmpty()) {
            return
        }
        
        showMoveCopyDialog = false
        showOperationProgressDialog = true
        operationProgress = 0
        operationTotal = selectedImages.size
        currentOperationFileName = ""
        
        // 取消之前的操作
        currentOperationJob?.cancel()
        
        currentOperationJob = viewModelScope.launch {
            try {
                val targetPath = com.example.yumoflatimagemanager.media.FileOperationManager.getAlbumPhysicalPath(targetAlbum.name)
                
                val result = com.example.yumoflatimagemanager.media.FileOperationManager.moveFilesToAlbum(
                    context = context,
                    images = selectedImages,
                    targetAlbumPath = targetPath,
                    callback = object : com.example.yumoflatimagemanager.media.FileOperationManager.OperationProgressCallback {
                        override fun onProgress(current: Int, total: Int, fileName: String) {
                            operationProgress = current
                            operationTotal = total
                            currentOperationFileName = fileName
                        }
                        
                        override fun onCompleted(result: com.example.yumoflatimagemanager.media.FileOperationManager.OperationResult) {
                            showOperationProgressDialog = false
                            
                            // 如果操作成功，刷新媒体内容并退出选择模式
                            if (result.success) {
                                refreshMedia()
                                selectionManager.toggleSelectionMode()
                                
                                // 显示成功提示
                                val message = "成功移动${selectedImages.size}张图片到${targetAlbum.name}"
                                viewModelScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        message,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                // 显示失败提示
                                viewModelScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "移动操作失败: ${result.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                showOperationProgressDialog = false
                
                // 显示失败提示
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "移动操作失败: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 执行复制操作
     */
    fun performCopyOperation() {
        val targetAlbum = selectedTargetAlbum ?: return
        val selectedImages = selectedImages
        
        if (selectedImages.isEmpty()) {
            return
        }
        
        showMoveCopyDialog = false
        showOperationProgressDialog = true
        operationProgress = 0
        operationTotal = selectedImages.size
        currentOperationFileName = ""
        
        // 取消之前的操作
        currentOperationJob?.cancel()
        
        currentOperationJob = viewModelScope.launch {
            try {
                val targetPath = com.example.yumoflatimagemanager.media.FileOperationManager.getAlbumPhysicalPath(targetAlbum.name)
                
                val result = com.example.yumoflatimagemanager.media.FileOperationManager.copyFilesToAlbum(
                    context = context,
                    images = selectedImages,
                    targetAlbumPath = targetPath,
                    callback = object : com.example.yumoflatimagemanager.media.FileOperationManager.OperationProgressCallback {
                        override fun onProgress(current: Int, total: Int, fileName: String) {
                            operationProgress = current
                            operationTotal = total
                            currentOperationFileName = fileName
                        }
                        
                        override fun onCompleted(result: com.example.yumoflatimagemanager.media.FileOperationManager.OperationResult) {
                            showOperationProgressDialog = false
                            
                            // 如果操作成功，刷新媒体内容并退出选择模式
                            if (result.success) {
                                refreshMedia()
                                selectionManager.toggleSelectionMode()
                                
                                // 显示成功提示
                                val message = "成功复制${selectedImages.size}张图片到${targetAlbum.name}"
                                viewModelScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        message,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                // 显示失败提示
                                viewModelScope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "复制操作失败: ${result.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                showOperationProgressDialog = false
                
                // 显示失败提示
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "复制操作失败: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 取消当前操作
     */
    fun cancelCurrentOperation() {
        currentOperationJob?.cancel()
        showOperationProgressDialog = false
        operationProgress = 0
        operationTotal = 0
        currentOperationFileName = ""
    }
    
    // ==================== 水印功能 ====================
    
    // 加载水印预设
    private fun loadWatermarkPresets() {
        viewModelScope.launch {
            db.watermarkDao().getAllPresets().collect { presets ->
                watermarkPresets = presets
            }
        }
    }
    
    
    // 显示水印预设对话框（从"更多"菜单调用）
    fun showWatermarkDialog() {
        if (selectedImages.isEmpty()) {
            android.widget.Toast.makeText(context, "请先选择图片", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        showWatermarkPresetDialog = true
    }
    
    // 选择预设后显示预览
    fun selectWatermarkPreset(preset: com.example.yumoflatimagemanager.data.WatermarkPreset) {
        showWatermarkPresetDialog = false
        
        val density = context.resources.displayMetrics.density
        val firstImage = selectedImages.firstOrNull() ?: return
        
        // 计算初始位置
        val (x, y) = com.example.yumoflatimagemanager.media.WatermarkUtils.calculateAnchorPosition(
            imageWidth = 1000, // 临时值，实际会在预览时根据图片调整
            imageHeight = 1000,
            anchor = preset.anchor,
            offsetXDp = preset.offsetX,
            offsetYDp = preset.offsetY,
            density = density
        )
        
        currentWatermarkState = com.example.yumoflatimagemanager.data.WatermarkState(
            preset = preset,
            currentX = x / 1000f,
            currentY = y / 1000f
        )
        
        showWatermarkPreview = true
        watermarkPreviewTrigger++ // 增加计数器以触发LaunchedEffect
    }
    
    // 确认水印位置后选择保存方式
    fun confirmWatermarkPosition(state: com.example.yumoflatimagemanager.data.WatermarkState) {
        currentWatermarkState = state
        showWatermarkPreview = false
        showWatermarkSaveOption = true
    }
    
    // 确认水印位置后选择保存方式（带参数列表）
    fun confirmWatermarkPositionWithParams(
        paramsList: List<com.example.yumoflatimagemanager.data.ImageWatermarkParams>,
        preset: com.example.yumoflatimagemanager.data.WatermarkPreset
    ) {
        imageWatermarkParamsList = paramsList
        
        // 添加调试日志
        android.util.Log.d("MainViewModel", "收到 ${paramsList.size} 个参数")
        paramsList.forEachIndexed { index, params ->
            android.util.Log.d("MainViewModel",
                "参数$index: uri=${params.imageUri}, " +
                "x=${params.watermarkX}, y=${params.watermarkY}, " +
                "rotation=${params.watermarkRotation}, scale=${params.watermarkScale}"
            )
        }
        
        // 使用第一张图片的参数更新 currentWatermarkState（用于UI显示）
        val firstParam = paramsList.firstOrNull()
        if (firstParam != null) {
            currentWatermarkState = com.example.yumoflatimagemanager.data.WatermarkState(
                preset = preset,
                currentX = firstParam.watermarkX,
                currentY = firstParam.watermarkY,
                currentRotation = firstParam.watermarkRotation,
                currentAlpha = firstParam.watermarkAlpha,
                currentScale = firstParam.watermarkScale
            )
        }
        showWatermarkPreview = false
        showWatermarkSaveOption = true
    }
    
    // 选择保存方式后开始应用
    fun confirmWatermarkSaveOption(option: com.example.yumoflatimagemanager.data.WatermarkSaveOption) {
        selectedWatermarkSaveOption = option
        showWatermarkSaveOption = false
        applyWatermarkToSelected()
    }
    
    // 应用水印到选中图片
    private fun applyWatermarkToSelected() {
        val paramsList = imageWatermarkParamsList
        val state = currentWatermarkState ?: return
        val option = selectedWatermarkSaveOption ?: return
        val images = selectedImages.toList()
        
        if (images.isEmpty()) return
        
        showOperationProgressDialog = true
        operationProgress = 0
        operationTotal = images.size
        
        viewModelScope.launch(Dispatchers.IO) {
            val result = if (paramsList != null && paramsList.isNotEmpty()) {
                // 使用每张图片的独立参数
                com.example.yumoflatimagemanager.media.WatermarkUtils.applyWatermarkBatchWithParams(
                    imageParams = paramsList,
                    preset = state.preset,
                    saveOption = option,
                    context = context,
                    onProgress = { current, total, fileName ->
                        operationProgress = current
                        currentOperationFileName = fileName
                    }
                )
            } else {
                // 使用统一的 state（兼容旧逻辑）
                com.example.yumoflatimagemanager.media.WatermarkUtils.applyWatermarkBatch(
                    images = images,
                    state = state,
                    saveOption = option,
                    context = context,
                    onProgress = { current, total, fileName ->
                        operationProgress = current
                        currentOperationFileName = fileName
                    }
                )
            }
            
            withContext(Dispatchers.Main) {
                showOperationProgressDialog = false
                selectionManager.clearSelection()
                selectionManager.toggleSelectionMode()
                
                result.onSuccess { (successCount, newFiles) ->
                    val message = when (option) {
                        com.example.yumoflatimagemanager.data.WatermarkSaveOption.OVERWRITE -> 
                            "成功为 $successCount 张图片添加水印"
                        com.example.yumoflatimagemanager.data.WatermarkSaveOption.CREATE_NEW -> 
                            "成功创建 $successCount 张带水印的新图片"
                    }
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    
                    // 刷新媒体库，让新图片能够显示
                    refreshMedia()
                    
                    // 如果创建了新文件，需要扫描媒体库
                    if (option == com.example.yumoflatimagemanager.data.WatermarkSaveOption.CREATE_NEW && newFiles.isNotEmpty()) {
                        scanNewFiles(newFiles)
                    }
                }
                
                currentWatermarkState = null
                selectedWatermarkSaveOption = null
            }
        }
    }
    
    // 扫描新文件到媒体库
    private fun scanNewFiles(fileUris: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 将URI转换为文件路径
                val filePaths = fileUris.mapNotNull { uriString ->
                    try {
                        val uri = android.net.Uri.parse(uriString)
                        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Images.Media.DATA), null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val pathIndex = it.getColumnIndex(android.provider.MediaStore.Images.Media.DATA)
                                if (pathIndex >= 0) {
                                    it.getString(pathIndex)
                                } else null
                            } else null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("WatermarkUtils", "Failed to get file path for URI: $uriString", e)
                        null
                    }
                }
                
                if (filePaths.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        filePaths.toTypedArray(),
                        null
                    ) { path, uri ->
                        android.util.Log.d("WatermarkUtils", "Media scan completed for: $path")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WatermarkUtils", "Failed to scan new files", e)
            }
        }
    }
    
    // 创建新预设
    fun createWatermarkPreset() {
        editingPreset = null
        showWatermarkEditor = true
    }
    
    // 编辑预设
    fun editWatermarkPreset(preset: com.example.yumoflatimagemanager.data.WatermarkPreset) {
        editingPreset = preset
        showWatermarkPresetDialog = false
        showWatermarkEditor = true
    }
    
    // 保存预设
    fun saveWatermarkPreset(preset: com.example.yumoflatimagemanager.data.WatermarkPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            if (preset.id == 0L) {
                db.watermarkDao().insertPreset(preset)
            } else {
                db.watermarkDao().updatePreset(preset)
            }
            // 重新加载预设列表
            loadWatermarkPresets()
        }
        // 保存后返回预设选择界面，而不是完全退出
        showWatermarkEditor = false
        showWatermarkPresetDialog = true
    }
    
    // 删除预设
    fun deleteWatermarkPreset(preset: com.example.yumoflatimagemanager.data.WatermarkPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            db.watermarkDao().deletePreset(preset)
        }
    }
    
    // 关闭对话框
    fun dismissWatermarkDialogs() {
        showWatermarkPresetDialog = false
        showWatermarkEditor = false
        showWatermarkPreview = false
        showWatermarkSaveOption = false
    }
    
}

/**
 * 相册选择屏幕的退出动画类型
 */
enum class AlbumSelectionExitAnimation {
    SLIDE_OUT,  // 从左边滑出
    FADE_OUT    // 淡出
}