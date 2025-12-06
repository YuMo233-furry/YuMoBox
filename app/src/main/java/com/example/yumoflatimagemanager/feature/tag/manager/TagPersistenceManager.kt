package com.example.yumoflatimagemanager.feature.tag.manager

import com.example.yumoflatimagemanager.data.PreferencesManager
import com.example.yumoflatimagemanager.feature.tag.state.TagState

/**
 * 标签持久化管理器
 * 负责标签状态的保存和恢复
 */
class TagPersistenceManager(
    private val preferencesManager: PreferencesManager,
    private val tagState: TagState
) {
    
    // 持久化键名
    private val PREF_ACTIVE_TAGS = "active_tags"
    private val PREF_EXCLUDED_TAGS = "excluded_tags"
    private val PREF_EXPANDED_TAGS = "expanded_tags"
    private val PREF_EXPANDED_REFERENCED_TAGS = "expanded_referenced_tags"
    private val PREF_TAG_DRAWER_SCROLL_INDEX = "tag_drawer_scroll_index"
    
    // ==================== 保存状态 ====================
    
    /**
     * 保存激活的标签过滤
     */
    fun saveActiveTagFilters() {
        preferencesManager.putString(
            PREF_ACTIVE_TAGS,
            tagState.activeTagFilterIds.joinToString(",")
        )
    }
    
    /**
     * 保存排除的标签
     */
    fun saveExcludedTags() {
        preferencesManager.putString(
            PREF_EXCLUDED_TAGS,
            tagState.excludedTagIds.joinToString(",")
        )
    }
    
    /**
     * 保存展开的标签
     */
    fun saveExpandedTags() {
        preferencesManager.putString(
            PREF_EXPANDED_TAGS,
            tagState.expandedTagIds.joinToString(",")
        )
    }
    
    /**
     * 保存展开的引用标签
     */
    fun saveExpandedReferencedTags() {
        preferencesManager.putString(
            PREF_EXPANDED_REFERENCED_TAGS,
            tagState.expandedReferencedTagIds.joinToString(",")
        )
    }
    
    /**
     * 保存标签抽屉滚动位置
     */
    fun saveTagDrawerScrollPosition(index: Int) {
        tagState.updateTagDrawerScrollIndex(index)
        preferencesManager.putString(PREF_TAG_DRAWER_SCROLL_INDEX, index.toString())
    }
    
    // ==================== 恢复状态 ====================
    
    /**
     * 恢复激活的标签过滤
     */
    fun restoreActiveTagFilters() {
        val saved = preferencesManager.getString(PREF_ACTIVE_TAGS, "")
        if (saved.isNotBlank()) {
            val ids = saved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
            tagState.updateActiveTagFilterIds(ids)
        }
    }
    
    /**
     * 恢复排除的标签
     */
    fun restoreExcludedTags() {
        val saved = preferencesManager.getString(PREF_EXCLUDED_TAGS, "")
        if (saved.isNotBlank()) {
            val ids = saved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
            tagState.updateExcludedTagIds(ids)
        }
    }
    
    /**
     * 恢复展开的标签
     */
    fun restoreExpandedTags() {
        val saved = preferencesManager.getString(PREF_EXPANDED_TAGS, "")
        if (saved.isNotBlank()) {
            val ids = saved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
            tagState.updateExpandedTagIds(ids)
        }
    }
    
    /**
     * 恢复展开的引用标签
     */
    fun restoreExpandedReferencedTags() {
        val saved = preferencesManager.getString(PREF_EXPANDED_REFERENCED_TAGS, "")
        if (saved.isNotBlank()) {
            val ids = saved.split(',').mapNotNull { it.toLongOrNull() }.toSet()
            tagState.updateExpandedReferencedTagIds(ids)
        }
    }
    
    /**
     * 恢复标签抽屉滚动位置
     */
    fun restoreTagDrawerScrollPosition(): Int {
        val saved = preferencesManager.getString(PREF_TAG_DRAWER_SCROLL_INDEX, "0")
        val index = saved.toIntOrNull() ?: 0
        tagState.updateTagDrawerScrollIndex(index)
        return index
    }
    
    /**
     * 恢复所有标签状态
     */
    fun restoreAllTagStates() {
        restoreActiveTagFilters()
        restoreExcludedTags()
        restoreExpandedTags()
        restoreExpandedReferencedTags()
        restoreTagDrawerScrollPosition()
    }
    
    /**
     * 重置所有标签状态
     */
    fun resetAllTagStates() {
        tagState.resetAllStates()
        
        // 清理持久化数据
        preferencesManager.putString(PREF_ACTIVE_TAGS, "")
        preferencesManager.putString(PREF_EXCLUDED_TAGS, "")
        preferencesManager.putString(PREF_EXPANDED_TAGS, "")
        preferencesManager.putString(PREF_EXPANDED_REFERENCED_TAGS, "")
        preferencesManager.putString(PREF_TAG_DRAWER_SCROLL_INDEX, "0")
    }
}

