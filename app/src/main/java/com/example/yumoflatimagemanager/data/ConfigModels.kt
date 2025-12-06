package com.example.yumoflatimagemanager.data

/**
 * 配置数据模型类，定义了应用中所有配置项的数据结构
 */

/**
 * 安全模式配置
 */
data class SecurityConfig(
    // 是否启用安全模式
    val isSecureModeEnabled: Boolean = false,
    // 私密相册ID列表
    val privateAlbumIds: List<String> = emptyList()
)

/**
 * 相册配置
 */
data class AlbumConfig(
    // 相册网格列数配置，key为相册ID，value为列数
    val gridColumns: MutableMap<String, Int> = mutableMapOf(),
    // 相册排序配置，key为相册ID，value为排序配置
    val sortConfigs: MutableMap<String, SortConfig> = mutableMapOf(),
    // 相册列表排序配置
    val albumsSortConfig: SortConfig = SortConfig(SortType.MODIFY_TIME, SortDirection.DESCENDING),
    // 网格滚动位置配置
    val gridScrollPositions: MutableMap<String, ScrollPosition> = mutableMapOf()
)

/**
 * 滚动位置配置
 */
data class ScrollPosition(
    val index: Int = 0,
    val offset: Int = 0
)

/**
 * 标签配置
 */
data class TagConfig(
    // 展开的标签ID集合
    val expandedTagIds: Set<Long> = emptySet(),
    // 激活的标签过滤ID集合
    val activeTagFilterIds: Set<Long> = emptySet(),
    // 排除模式的标签ID集合
    val excludedTagIds: Set<Long> = emptySet(),
    // 展开的引用标签ID集合
    val expandedReferencedTagIds: Set<Long> = emptySet(),
    // 标签抽屉滚动索引
    val tagDrawerScrollIndex: Int = 0
)

/**
 * 水印配置
 */
data class WatermarkConfig(
    // 是否显示水印
    val isWatermarkVisible: Boolean = false,
    // 默认水印预设ID
    val defaultPresetId: Long = 0
)

/**
 * 迁移标记配置
 */
data class MigrationConfig(
    // 是否已完成配置迁移
    val isConfigMigrated: Boolean = false
)
