package com.example.yumoflatimagemanager.feature.tag.manager

import android.util.Log
import com.example.yumoflatimagemanager.data.local.TagStatistics
import com.example.yumoflatimagemanager.data.repo.TagRepository
import com.example.yumoflatimagemanager.feature.tag.state.TagState
import com.example.yumoflatimagemanager.media.MediaContentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 标签统计管理器
 * 负责标签统计信息的计算、缓存和批量更新
 */
class TagStatisticsManager(
    private val tagRepo: TagRepository,
    private val mediaContentManager: MediaContentManager,
    private val tagState: TagState,
    private val scope: CoroutineScope
) {
    // 标签统计信息缓存
    private val tagStatisticsCache = mutableMapOf<Long, TagStatistics>()
    private val statisticsUpdateJobs = mutableMapOf<Long, Job>()
    
    // ==================== 统计信息更新 ====================
    
    /**
     * 更新标签统计信息 - 优化版本（支持懒加载）
     */
    fun updateTagStatistics(tagId: Long) {
        // 取消之前的更新任务，避免重复计算
        statisticsUpdateJobs[tagId]?.cancel()
        
        // 检查缓存，如果缓存存在，直接使用缓存
        val cachedStats = tagStatisticsCache[tagId]
        if (cachedStats != null) {
            tagState.updateTagStatistic(tagId, cachedStats)
            return
        }
        
        // 对于未分类标签，确保媒体内容已加载
        if (tagId == -1L) {
            mediaContentManager.loadAllMedia()
        }
        
        // 异步计算统计信息
        val job = scope.launch(Dispatchers.IO) {
            try {
                val statistics = if (tagId == -1L) {
                    // 为"未分类"标签创建虚拟统计
                    calculateUntaggedStatistics()
                } else {
                    tagRepo.getTagStatistics(tagId)
                }
                
                // 更新缓存
                tagStatisticsCache[tagId] = statistics
                
                // 在主线程更新UI
                withContext(Dispatchers.Main) {
                    tagState.updateTagStatistic(tagId, statistics)
                }
            } catch (e: Exception) {
                Log.e("TagStatisticsManager", "更新标签统计信息失败: $tagId", e)
            } finally {
                statisticsUpdateJobs.remove(tagId)
            }
        }
        
        statisticsUpdateJobs[tagId] = job
    }
    
    /**
     * 计算未分类标签的统计信息
     */
    private suspend fun calculateUntaggedStatistics(): TagStatistics {
        val taggedPaths = tagRepo.getAllTaggedMediaPaths()
        // 获取所有媒体内容，而不仅仅是当前相册的图片
        val allMediaImages = mediaContentManager.allImages + mediaContentManager.allVideos
        val allImagePaths = allMediaImages.map { it.uri.toString() }
        val untaggedPaths = allImagePaths.filter { !taggedPaths.contains(it) }
        return TagStatistics(
            tagId = -1L,
            directImageCount = untaggedPaths.size,
            totalImageCount = untaggedPaths.size,
            referencedCount = 0
        )
    }
    
    /**
     * 批量更新标签统计信息 - 优化版本
     */
    fun updateTagStatisticsBatch(tagIds: List<Long>) {
        scope.launch(Dispatchers.IO) {
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
        scope.launch(Dispatchers.IO) {
            // 过滤出未缓存的标签ID
            val uncachedTagIds = tagIds.filter { tagId ->
                !tagStatisticsCache.containsKey(tagId) && !statisticsUpdateJobs.containsKey(tagId)
            }
            
            if (uncachedTagIds.isNotEmpty()) {
                Log.d("TagStatisticsManager", "需要更新 ${uncachedTagIds.size} 个标签的统计信息")
                updateTagStatisticsBatch(uncachedTagIds)
            } else {
                Log.d("TagStatisticsManager", "所有标签统计信息已缓存，跳过更新")
            }
        }
    }
    
    /**
     * 更新所有标签的统计信息
     */
    fun updateAllTagStatistics() {
        scope.launch(Dispatchers.IO) {
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
    fun clearTagStatisticsCacheForTag(tagId: Long) {
        tagStatisticsCache.remove(tagId)
        statisticsUpdateJobs[tagId]?.cancel()
        statisticsUpdateJobs.remove(tagId)
        
        // 立即重新计算该标签的统计信息
        updateTagStatistics(tagId)
        
        // 异步更新所有父标签的统计信息（包括引用父标签）
        scope.launch(Dispatchers.IO) {
            try {
                // 获取所有父标签ID（包括直接父标签和引用父标签）
                val parentTagIds = tagRepo.getParentTagIds(tagId)
                
                // 递归更新所有父标签的统计信息
                parentTagIds.forEach { parentTagId ->
                    // 清除父标签的缓存
                    tagStatisticsCache.remove(parentTagId)
                    statisticsUpdateJobs[parentTagId]?.cancel()
                    statisticsUpdateJobs.remove(parentTagId)
                    
                    // 重新计算父标签的统计信息
                    updateTagStatistics(parentTagId)
                }
            } catch (e: Exception) {
                Log.e("TagStatisticsManager", "更新父标签统计信息失败", e)
            }
        }
    }
}

