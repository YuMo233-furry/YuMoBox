package com.example.yumoflatimagemanager.feature.tag.manager

import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.repo.TagRepository
import com.example.yumoflatimagemanager.feature.tag.state.TagState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.util.Log

/**
 * 标签过滤管理器
 * 负责标签的过滤、排除逻辑和过滤缓存管理
 */
class TagFilterManager(
    private val tagRepo: TagRepository,
    private val tagState: TagState
) {
    // 过滤结果缓存
    private var filteredImagesCache: List<ImageItem>? = null
    private var lastFilterState: String = ""
    
    // ==================== 过滤控制 ====================
    
    /**
     * 切换标签过滤
     */
    fun toggleTagFilter(tagId: Long) {
        // 如果标签在排除模式中，先移除排除状态
        if (tagState.excludedTagIds.contains(tagId)) {
            tagState.removeExcludedTagId(tagId)
            clearFilterCache()
            return
        }
        
        if (tagState.activeTagFilterIds.contains(tagId)) {
            tagState.removeActiveTagFilterId(tagId)
        } else {
            tagState.addActiveTagFilterId(tagId)
        }
        
        // 清理过滤缓存
        clearFilterCache()
    }
    
    /**
     * 切换标签排除模式
     */
    fun toggleTagExclusion(tagId: Long) {
        if (tagState.excludedTagIds.contains(tagId)) {
            tagState.removeExcludedTagId(tagId)
        } else {
            tagState.addExcludedTagId(tagId)
        }
        
        // 确保一个标签不能同时处于激活和排除状态
        if (tagState.excludedTagIds.contains(tagId)) {
            tagState.removeActiveTagFilterId(tagId)
        }
        
        // 清理过滤缓存
        clearFilterCache()
    }
    
    /**
     * 清除所有排除模式
     */
    fun clearTagExclusions() {
        tagState.updateExcludedTagIds(emptySet())
    }
    
    /**
     * 清除所有标签过滤
     */
    fun clearTagFilters() {
        tagState.clearAllFilters()
    }
    
    /**
     * 清理过滤缓存
     */
    fun clearFilterCache() {
        filteredImagesCache = null
        lastFilterState = ""
    }
    
    // ==================== 过滤计算 ====================
    
    /**
     * 计算过滤后的图片列表
     * 添加超时保护和异常处理，防止在Android 13上因权限问题导致崩溃
     */
    suspend fun computeFilteredImages(
        currentImages: List<ImageItem>,
        filters: Set<Long> = tagState.activeTagFilterIds,
        excludes: Set<Long> = tagState.excludedTagIds
    ): List<ImageItem> = withContext(Dispatchers.IO) {
        try {
            // 添加10秒超时保护，避免长时间阻塞
            withTimeout(10000L) {
                var result = currentImages
                
                // 处理标签过滤逻辑
                if (filters.isNotEmpty() || excludes.isNotEmpty()) {
                    // 情况1：只有激活过滤（正常交集过滤）
                    if (filters.isNotEmpty() && excludes.isEmpty()) {
                        result = applyActiveFilters(result, filters)
                    }
                    // 情况2：只有排除过滤
                    else if (filters.isEmpty() && excludes.isNotEmpty()) {
                        result = applyExcludeFilters(result, excludes)
                    }
                    // 情况3：同时有激活过滤和排除过滤
                    else if (filters.isNotEmpty() && excludes.isNotEmpty()) {
                        result = applyActiveFilters(result, filters)
                        result = applyExcludeFilters(result, excludes)
                    }
                }
                
                result
            }
        } catch (e: SecurityException) {
            // Android 13+ 权限问题
            Log.e("TagFilterManager", "权限错误，无法访问标签数据: ${e.message}", e)
            currentImages // 返回原始列表
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // 超时
            Log.e("TagFilterManager", "过滤超时: ${e.message}", e)
            currentImages // 返回原始列表
        } catch (e: Exception) {
            // 其他异常
            Log.e("TagFilterManager", "过滤失败: ${e.message}", e)
            currentImages // 返回原始列表
        }
    }
    
    /**
     * 应用激活过滤
     * 添加异常处理，防止数据库查询失败导致崩溃
     */
    private suspend fun applyActiveFilters(
        images: List<ImageItem>,
        filters: Set<Long>
    ): List<ImageItem> {
        try {
            // 未分类过滤：显示未被任何标签打上的图片
            if (filters.contains(-1L) && filters.size == 1) {
                val taggedPaths = tagRepo.getAllTaggedMediaPaths()
                val taggedPathSet = taggedPaths.toHashSet()
                return images.filter { !taggedPathSet.contains(it.uri.toString()) }
            }
            
            // 对每个选中标签，取 该标签 ∪ 所有子孙（含引用） 的媒体路径集合
            val effectiveTagIdsPerFilter: List<Set<Long>> = filters
                .filter { it != -1L }
                .map { tagId ->
                    try {
                        val descendants = tagRepo.getDescendantTagIds(tagId)
                        (descendants + tagId).toSet()
                    } catch (e: Exception) {
                        Log.e("TagFilterManager", "获取标签后代失败: tagId=$tagId, ${e.message}")
                        setOf(tagId) // 至少包含自己
                    }
                }
            
            if (effectiveTagIdsPerFilter.isEmpty()) {
                return images
            }
            
            var intersection: Set<String>? = null
            for (tagIdSet in effectiveTagIdsPerFilter) {
                try {
                    val paths = tagRepo.getMediaPathsByAnyTag(tagIdSet.toList())
                    val pathSet = paths.toHashSet()
                    intersection = if (intersection == null) pathSet else intersection.intersect(pathSet)
                    if (intersection.isEmpty()) break
                } catch (e: Exception) {
                    Log.e("TagFilterManager", "获取标签媒体路径失败: ${e.message}")
                    // 继续处理其他标签
                }
            }
            
            val finalSet = intersection ?: emptySet()
            return images.filter { finalSet.contains(it.uri.toString()) }
        } catch (e: Exception) {
            Log.e("TagFilterManager", "应用激活过滤失败: ${e.message}", e)
            return images // 出错时返回原始列表
        }
    }
    
    /**
     * 应用排除过滤
     * 添加异常处理，防止数据库查询失败导致崩溃
     */
    private suspend fun applyExcludeFilters(
        images: List<ImageItem>,
        excludes: Set<Long>
    ): List<ImageItem> {
        try {
            // 获取所有需要排除的媒体路径（包含子孙标签）
            val excludedPaths = mutableSetOf<String>()
            
            for (excludeTagId in excludes) {
                try {
                    val descendants = tagRepo.getDescendantTagIds(excludeTagId)
                    val allExcludeTagIds = (descendants + excludeTagId).toSet()
                    val paths = tagRepo.getMediaPathsByAnyTag(allExcludeTagIds.toList())
                    excludedPaths.addAll(paths)
                } catch (e: Exception) {
                    Log.e("TagFilterManager", "获取排除标签数据失败: tagId=$excludeTagId, ${e.message}")
                    // 继续处理其他排除标签
                }
            }
            
            // 从所有图片中排除这些路径
            return images.filter { !excludedPaths.contains(it.uri.toString()) }
        } catch (e: Exception) {
            Log.e("TagFilterManager", "应用排除过滤失败: ${e.message}", e)
            return images // 出错时返回原始列表
        }
    }
    
    /**
     * 获取过滤后的图片列表（带缓存）
     */
    suspend fun getFilteredImages(currentImages: List<ImageItem>): List<ImageItem> {
        val filters = tagState.activeTagFilterIds
        val excludes = tagState.excludedTagIds
        
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
        
        // 计算过滤结果
        val result = computeFilteredImages(currentImages, filters, excludes)
        
        // 更新缓存
        filteredImagesCache = result
        lastFilterState = currentFilterState
        
        return result
    }
    
    /**
     * 获取标签的完整图片路径（包含所有引用标签）
     */
    suspend fun getTagMediaPaths(tagId: Long): List<String> {
        val descendantIds = tagRepo.getDescendantTagIds(tagId)
        val allTagIds = listOf(tagId) + descendantIds
        return tagRepo.getMediaPathsByAnyTag(allTagIds)
    }
}

