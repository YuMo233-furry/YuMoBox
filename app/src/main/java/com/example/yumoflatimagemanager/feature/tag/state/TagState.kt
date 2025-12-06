package com.example.yumoflatimagemanager.feature.tag.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yumoflatimagemanager.data.local.TagStatistics

/**
 * 标签状态管理类
 * 集中管理所有标签相关的 UI 状态
 */
class TagState {
    
    // ==================== 过滤状态 ====================
    
    /** 激活的标签过滤ID集合（并集） */
    var activeTagFilterIds by mutableStateOf<Set<Long>>(emptySet())
        private set
        
    /** 排除模式的标签ID集合 */
    var excludedTagIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    
    // ==================== 展开状态 ====================
    
    /** 标签展开状态 */
    var expandedTagIds by mutableStateOf<Set<Long>>(emptySet())
        private set
        
    /** 引用标签展开状态（独立于本体标签） */
    var expandedReferencedTagIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    
    // ==================== UI 状态 ====================
    
    /** 标签抽屉滚动位置 */
    var tagDrawerScrollIndex by mutableStateOf(0)
        private set
        
    /** 标签统计信息 */
    var tagStatistics by mutableStateOf<Map<Long, TagStatistics>>(emptyMap())
        private set
    
    /** 标签引用刷新触发器 */
    var tagReferenceRefreshTrigger by mutableStateOf(0L)
        private set
    
    // ==================== 更新方法 ====================
    
    fun updateActiveTagFilterIds(ids: Set<Long>) {
        activeTagFilterIds = ids
    }
    
    fun addActiveTagFilterId(id: Long) {
        activeTagFilterIds = activeTagFilterIds + id
    }
    
    fun removeActiveTagFilterId(id: Long) {
        activeTagFilterIds = activeTagFilterIds - id
    }
    
    fun updateExcludedTagIds(ids: Set<Long>) {
        excludedTagIds = ids
    }
    
    fun addExcludedTagId(id: Long) {
        excludedTagIds = excludedTagIds + id
    }
    
    fun removeExcludedTagId(id: Long) {
        excludedTagIds = excludedTagIds - id
    }
    
    fun updateExpandedTagIds(ids: Set<Long>) {
        expandedTagIds = ids
    }
    
    fun toggleExpandedTagId(id: Long) {
        expandedTagIds = if (expandedTagIds.contains(id)) {
            expandedTagIds - id
        } else {
            expandedTagIds + id
        }
    }
    
    fun updateExpandedReferencedTagIds(ids: Set<Long>) {
        expandedReferencedTagIds = ids
    }
    
    fun toggleExpandedReferencedTagId(id: Long) {
        expandedReferencedTagIds = if (expandedReferencedTagIds.contains(id)) {
            expandedReferencedTagIds - id
        } else {
            expandedReferencedTagIds + id
        }
    }
    
    fun updateTagDrawerScrollIndex(index: Int) {
        tagDrawerScrollIndex = index
    }
    
    fun updateTagStatistics(statistics: Map<Long, TagStatistics>) {
        tagStatistics = statistics
    }
    
    fun updateTagStatistic(tagId: Long, statistic: TagStatistics) {
        tagStatistics = tagStatistics.toMutableMap().apply {
            put(tagId, statistic)
        }
    }
    
    fun triggerTagReferenceRefresh() {
        tagReferenceRefreshTrigger = System.currentTimeMillis()
    }
    
    fun clearAllFilters() {
        activeTagFilterIds = emptySet()
        excludedTagIds = emptySet()
    }
    
    fun resetAllStates() {
        activeTagFilterIds = emptySet()
        excludedTagIds = emptySet()
        expandedTagIds = emptySet()
        expandedReferencedTagIds = emptySet()
        tagDrawerScrollIndex = 0
    }
}

