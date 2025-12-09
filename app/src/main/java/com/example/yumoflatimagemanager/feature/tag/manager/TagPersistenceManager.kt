package com.example.yumoflatimagemanager.feature.tag.manager

import com.example.yumoflatimagemanager.data.ConfigManager
import com.example.yumoflatimagemanager.feature.tag.state.TagState

/**
 * 标签持久化管理器
 * 负责标签状态的保存和恢复
 */
class TagPersistenceManager(
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
     * 保存所有标签状态
     */
    fun saveAllTagStates() {
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.activeTagFilterIds = tagState.activeTagFilterIds
        tagConfig.excludedTagIds = tagState.excludedTagIds
        tagConfig.expandedTagIds = tagState.expandedTagIds
        tagConfig.expandedReferencedTagIds = tagState.expandedReferencedTagIds
        tagConfig.tagDrawerScrollIndex = tagState.tagDrawerScrollIndex
        tagConfig.selectedTagGroupId = tagState.selectedTagGroupId ?: 1L // 如果为null，保存默认的"未分组"标签组
        ConfigManager.writeTagConfig(tagConfig)
    }
    
    /**
     * 保存当前选中的标签组
     */
    fun saveTagGroupSelection(selectedGroupId: Long? = tagState.selectedTagGroupId) {
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.selectedTagGroupId = selectedGroupId ?: 1L // 如果为null，保存默认的"未分组"标签组
        ConfigManager.writeTagConfig(tagConfig)
    }

    /**
     * 读取已保存的标签组选择
     */
    fun getSavedTagGroupId(): Long? {
        val tagConfig = ConfigManager.readTagConfig()
        return tagConfig.selectedTagGroupId
    }
    
    /**
     * 保存激活的标签过滤
     */
    fun saveActiveTagFilters() {
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.activeTagFilterIds = tagState.activeTagFilterIds
        ConfigManager.writeTagConfig(tagConfig)
    }
    
    /**
     * 保存排除的标签
     */
    fun saveExcludedTags() {
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.excludedTagIds = tagState.excludedTagIds
        ConfigManager.writeTagConfig(tagConfig)
    }
    
    /**
     * 保存展开的标签
     */
    fun saveExpandedTags() {
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.expandedTagIds = tagState.expandedTagIds
        ConfigManager.writeTagConfig(tagConfig)
    }
    
    /**
     * 保存展开的引用标签
     */
    fun saveExpandedReferencedTags() {
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.expandedReferencedTagIds = tagState.expandedReferencedTagIds
        ConfigManager.writeTagConfig(tagConfig)
    }
    
    /**
     * 保存标签抽屉滚动位置
     */
    fun saveTagDrawerScrollPosition(index: Int) {
        tagState.updateTagDrawerScrollIndex(index)
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.tagDrawerScrollIndex = index
        ConfigManager.writeTagConfig(tagConfig)
    }
    
    // ==================== 恢复状态 ====================
    
    /**
     * 恢复激活的标签过滤
     */
    fun restoreActiveTagFilters() {
        val tagConfig = ConfigManager.readTagConfig()
        tagState.updateActiveTagFilterIds(tagConfig.activeTagFilterIds)
    }
    
    /**
     * 恢复排除的标签
     */
    fun restoreExcludedTags() {
        val tagConfig = ConfigManager.readTagConfig()
        tagState.updateExcludedTagIds(tagConfig.excludedTagIds)
    }
    
    /**
     * 恢复展开的标签
     */
    fun restoreExpandedTags() {
        val tagConfig = ConfigManager.readTagConfig()
        tagState.updateExpandedTagIds(tagConfig.expandedTagIds)
    }
    
    /**
     * 恢复展开的引用标签
     */
    fun restoreExpandedReferencedTags() {
        val tagConfig = ConfigManager.readTagConfig()
        tagState.updateExpandedReferencedTagIds(tagConfig.expandedReferencedTagIds)
    }
    
    /**
     * 恢复标签抽屉滚动位置
     */
    fun restoreTagDrawerScrollPosition(): Int {
        val tagConfig = ConfigManager.readTagConfig()
        val index = tagConfig.tagDrawerScrollIndex
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
        restoreTagGroupSelection()
    }
    
    /**
     * 恢复标签组选择
     */
    fun restoreTagGroupSelection() {
        val tagConfig = ConfigManager.readTagConfig()
        val savedGroupId = tagConfig.selectedTagGroupId ?: 1L
        tagState.setTagGroupSelection(savedGroupId)
    }
    
    /**
     * 重置所有标签状态
     */
    fun resetAllTagStates() {
        tagState.resetAllStates()
        
        // 清理持久化数据
        val tagConfig = ConfigManager.readTagConfig()
        tagConfig.activeTagFilterIds = emptySet()
        tagConfig.excludedTagIds = emptySet()
        tagConfig.expandedTagIds = emptySet()
        tagConfig.expandedReferencedTagIds = emptySet()
        tagConfig.tagDrawerScrollIndex = 0
        tagConfig.selectedTagGroupId = 1L // 重置为默认的"未分组"标签组
        ConfigManager.writeTagConfig(tagConfig)
    }
}

