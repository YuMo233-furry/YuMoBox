package com.example.yumoflatimagemanager.feature.tag.manager

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagReferenceEntity
import com.example.yumoflatimagemanager.data.repo.TagRepository
import com.example.yumoflatimagemanager.feature.tag.model.DeletedTagCache
import com.example.yumoflatimagemanager.feature.tag.state.TagDialogState
import com.example.yumoflatimagemanager.feature.tag.state.TagState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 标签 CRUD 操作管理器
 * 负责标签的创建、重命名、删除、撤回删除、引用管理等操作
 */
class TagCrudManager(
    private val context: Context,
    private val tagRepo: TagRepository,
    private val tagState: TagState,
    private val dialogState: TagDialogState,
    private val scope: CoroutineScope
) {
    private var undoDeleteJob: Job? = null
    
    // ==================== 标签 CRUD ====================
    
    /**
     * 添加新标签
     */
    fun addTag(name: String) {
        if (name.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val tagId = tagRepo.createTag(name.trim(), parentId = null)
                println("DEBUG: 成功创建标签: $name, ID: $tagId")
            } catch (e: Exception) {
                println("ERROR: 创建标签失败: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "创建标签失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 重命名标签
     */
    fun renameTag(tag: TagEntity, newName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                tagRepo.renameTag(tag.id, newName, tag.parentId)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "重命名失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * 删除标签
     */
    fun deleteTag(tag: TagEntity, onStatisticsUpdate: (Long) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                // 从激活的标签过滤中移除该标签
                if (tagState.activeTagFilterIds.contains(tag.id)) {
                    tagState.removeActiveTagFilterId(tag.id)
                }
                
                // 删除标签（包含清理所有关联数据）
                tagRepo.deleteTag(tag.id)
                
                // 更新相关标签的统计信息
                onStatisticsUpdate(tag.id)
                if (tag.parentId != null) {
                    onStatisticsUpdate(tag.parentId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "删除失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * 左滑删除标签（带撤回功能）
     */
    fun deleteTagWithUndo(
        tag: TagEntity,
        onStatisticsUpdate: (Long) -> Unit
    ) {
        // 取消之前的撤回任务
        undoDeleteJob?.cancel()
        
        // 从激活的标签过滤中移除该标签
        if (tagState.activeTagFilterIds.contains(tag.id)) {
            tagState.removeActiveTagFilterId(tag.id)
        }
        
        // 先缓存标签的所有关联数据
        scope.launch(Dispatchers.IO) {
            try {
                // 获取标签关联的所有图片路径
                val mediaPaths = tagRepo.getMediaPathsByAnyTag(listOf(tag.id))
                
                // 获取标签的引用关系
                val childReferences = tagRepo.getTagReferences(tag.id)
                    .map { ref -> TagReferenceEntity(tag.id, ref.tag.id) }
                val parentReferences = tagRepo.getTagReferencesByChildId(tag.id)
                
                // 获取引用标签
                val childTags = tagRepo.getTagsByParentId(tag.id)
                
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
                onStatisticsUpdate(tag.id)
                if (tag.parentId != null) {
                    onStatisticsUpdate(tag.parentId)
                }
                
                // 保存删除的标签缓存信息（切换到主线程）
                withContext(Dispatchers.Main) {
                    dialogState.setDeletedTagCache(deletedTagCache)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果缓存失败，仍然删除标签但不提供撤回功能
                tagRepo.deleteTag(tag.id)
                onStatisticsUpdate(tag.id)
                if (tag.parentId != null) {
                    onStatisticsUpdate(tag.parentId)
                }
                
                withContext(Dispatchers.Main) {
                    dialogState.clearDeletedTagCache()
                }
            }
        }
        
        // 设置5秒后自动隐藏撤回消息
        undoDeleteJob = scope.launch {
            delay(5000)
            dialogState.hideUndoDeleteMessage()
            dialogState.clearDeletedTagCache()
        }
    }
    
    /**
     * 撤回删除操作
     */
    fun undoDeleteTag(onStatisticsUpdate: (Long) -> Unit) {
        undoDeleteJob?.cancel()
        
        val cache = dialogState.recentlyDeletedTag ?: return
        
        scope.launch(Dispatchers.IO) {
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
                onStatisticsUpdate(newTagId)
                if (cache.tag.parentId != null) {
                    onStatisticsUpdate(cache.tag.parentId)
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "已恢复标签 ${cache.tag.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "恢复标签失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        
        dialogState.clearDeletedTagCache()
    }
    
    /**
     * 清理删除缓存（在应用退出时调用）
     */
    fun clearDeletedTagCache() {
        dialogState.clearDeletedTagCache()
        undoDeleteJob?.cancel()
    }
    
    // ==================== 标签引用管理 ====================
    
    /**
     * 添加标签引用
     */
    suspend fun addTagReference(parentTagId: Long, childTagId: Long): Boolean {
        return try {
            val success = tagRepo.addTagReference(parentTagId, childTagId)
            if (!success) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "添加引用失败：检测到自循环/祖先-子孙循环",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                tagState.triggerTagReferenceRefresh()
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 移除标签引用
     */
    suspend fun removeTagReference(parentTagId: Long, childTagId: Long) {
        try {
            tagRepo.removeTagReference(parentTagId, childTagId)
            tagState.triggerTagReferenceRefresh()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 获取可用于添加引用的标签列表
     */
    suspend fun getAvailableTagsForReference(parentTagId: Long): List<TagEntity> {
        return try {
            val allTags = tagRepo.getAllTagsList()
            val existingReferences = tagRepo.getTagReferences(parentTagId)
            val parentTagIds = tagRepo.getParentTagIds(parentTagId)
            allTags.filter { tag ->
                tag.id != parentTagId &&
                existingReferences.none { refTag -> refTag.tag.id == tag.id } &&
                tag.id !in parentTagIds
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

