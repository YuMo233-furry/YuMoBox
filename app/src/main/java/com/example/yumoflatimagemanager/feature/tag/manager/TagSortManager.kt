package com.example.yumoflatimagemanager.feature.tag.manager

import com.example.yumoflatimagemanager.data.local.TagWithChildren
import com.example.yumoflatimagemanager.data.repo.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 标签排序管理器
 * 负责标签的拖拽排序逻辑
 */
class TagSortManager(
    private val tagRepo: TagRepository,
    private val scope: CoroutineScope
) {
    
    // ==================== 拖拽排序 ====================
    
    /**
     * 移动标签到新位置（拖拽排序）
     */
    fun moveTag(fromIndex: Int, toIndex: Int, tags: List<TagWithChildren>) {
        scope.launch(Dispatchers.IO) {
            try {
                if (fromIndex < 0 || fromIndex >= tags.size ||
                    toIndex < 0 || toIndex >= tags.size ||
                    fromIndex == toIndex
                ) {
                    return@launch
                }
                
                // 实现插入逻辑：将标签插入到目标位置，后面的标签排序值+1
                insertTagAtPosition(tags, fromIndex, toIndex)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 分组内移动标签排序
     */
    fun moveTagInGroup(
        fromIndex: Int,
        toIndex: Int,
        groupTags: List<TagWithChildren>,
        isWithReferences: Boolean
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                if (fromIndex < 0 || fromIndex >= groupTags.size ||
                    toIndex < 0 || toIndex >= groupTags.size ||
                    fromIndex == toIndex
                ) {
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
     * 移动引用标签的排序
     */
    fun moveChildTag(parentTagId: Long, fromIndex: Int, toIndex: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                tagRepo.moveChildTag(parentTagId, fromIndex, toIndex)
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
    suspend fun redistributeAllTagSortOrders() {
        try {
            val allTags = tagRepo.getAllTags().first()
            // 按当前排序值排序
            val sortedTags = allTags.sortedBy { it.sortOrder }
            
            // 重新分配排序值，每个标签间隔10000，确保足够空间
            sortedTags.forEachIndexed { index, tag ->
                val newSortOrder = index * 10000
                tagRepo.updateTagSort(tag.id, newSortOrder)
                println("DEBUG: 重新分配标签 ${tag.name} 的排序值为 $newSortOrder")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 检查并修复所有标签的排序值
     */
    fun checkAndFixAllSortOrders() {
        scope.launch(Dispatchers.IO) {
            redistributeAllTagSortOrders()
        }
    }
}

