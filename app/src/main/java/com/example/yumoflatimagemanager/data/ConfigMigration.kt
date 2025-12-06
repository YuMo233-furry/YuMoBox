package com.example.yumoflatimagemanager.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.yumoflatimagemanager.data.AlbumConfig
import com.example.yumoflatimagemanager.data.ScrollPosition
import com.example.yumoflatimagemanager.data.SecurityConfig
import com.example.yumoflatimagemanager.data.TagConfig
import com.example.yumoflatimagemanager.data.WatermarkConfig
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.data.SortDirection
import com.example.yumoflatimagemanager.data.SortType

/**
 * 配置迁移类，负责将现有的 SharedPreferences 配置迁移到新的文件存储系统中
 */
object ConfigMigration {
    
    // 日志标签
    private const val TAG = "ConfigMigration"
    
    // SharedPreferences 名称
    private const val PREFERENCES_NAME = "YuMoFlatImageManagerPreferences"
    
    // 安全模式相关键
    private const val PREF_PRIVATE_ALBUMS = "private_albums"
    private const val PREF_SECURE_MODE_ENABLED = "secure_mode_enabled"
    
    // 相册配置相关键
    private const val PREF_SORT_TYPE_PREFIX = "sort_type_"
    private const val PREF_SORT_DIRECTION_PREFIX = "sort_direction_"
    private const val PREF_GRID_COLUMNS_PREFIX = "grid_columns_"
    private const val PREF_ALBUMS_SORT_TYPE = "albums_sort_type"
    private const val PREF_ALBUMS_SORT_DIRECTION = "albums_sort_direction"
    
    // 标签配置相关键
    private const val PREF_ACTIVE_TAGS = "active_tags"
    private const val PREF_EXCLUDED_TAGS = "excluded_tags"
    private const val PREF_EXPANDED_TAGS = "expanded_tags"
    private const val PREF_EXPANDED_REFERENCED_TAGS = "expanded_referenced_tags"
    private const val PREF_TAG_DRAWER_SCROLL_INDEX = "tag_drawer_scroll_index"
    private const val PREF_GRID_SCROLL_INDEX = "grid_scroll_index"
    private const val PREF_GRID_SCROLL_OFFSET = "grid_scroll_offset"
    
    // 水印配置相关键
    private const val PREF_WATERMARK_VISIBLE = "watermark_visible"
    private const val PREF_DEFAULT_WATERMARK_PRESET_ID = "default_watermark_preset_id"
    
    /**
     * 执行配置迁移
     * @param context 上下文
     * @return 是否迁移成功
     */
    fun migrateConfig(context: Context): Boolean {
        Log.d(TAG, "开始执行配置迁移")
        
        try {
            // 1. 检查是否已迁移
            if (ConfigManager.isConfigMigrated()) {
                Log.d(TAG, "配置已迁移，跳过迁移过程")
                return true
            }
            
            // 2. 创建配置目录
            if (!ConfigManager.createConfigDirectory()) {
                Log.e(TAG, "创建配置目录失败")
                return false
            }
            
            // 3. 获取 SharedPreferences
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            
            // 4. 迁移安全模式配置
            migrateSecurityConfig(preferences)
            
            // 5. 迁移相册配置
            migrateAlbumConfig(preferences)
            
            // 6. 迁移标签配置
            migrateTagConfig(preferences)
            
            // 7. 迁移水印配置
            migrateWatermarkConfig(preferences)
            
            // 8. 标记迁移完成
            if (!ConfigManager.markConfigMigrated()) {
                Log.e(TAG, "标记迁移完成失败")
                return false
            }
            
            Log.d(TAG, "配置迁移成功")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "配置迁移失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 迁移安全模式配置
     * @param preferences SharedPreferences 实例
     */
    private fun migrateSecurityConfig(preferences: SharedPreferences) {
        Log.d(TAG, "开始迁移安全模式配置")
        
        // 读取私密相册ID列表
        val privateAlbumsString = preferences.getString(PREF_PRIVATE_ALBUMS, "") ?: ""
        val privateAlbumIds = if (privateAlbumsString.isNotEmpty()) {
            privateAlbumsString.split(",").filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        
        // 读取安全模式启用状态
        val isSecureModeEnabled = preferences.getBoolean(PREF_SECURE_MODE_ENABLED, false)
        
        // 创建安全模式配置对象
        val securityConfig = SecurityConfig(
            isSecureModeEnabled = isSecureModeEnabled,
            privateAlbumIds = privateAlbumIds
        )
        
        // 写入配置文件
        if (!ConfigManager.writeSecurityConfig(securityConfig)) {
            Log.e(TAG, "写入安全模式配置失败")
        } else {
            Log.d(TAG, "安全模式配置迁移成功")
        }
    }
    
    /**
     * 迁移相册配置
     * @param preferences SharedPreferences 实例
     */
    private fun migrateAlbumConfig(preferences: SharedPreferences) {
        Log.d(TAG, "开始迁移相册配置")
        
        // 创建相册配置对象
        val albumConfig = AlbumConfig()
        
        // 迁移网格列数配置
        migrateGridColumns(preferences, albumConfig)
        
        // 迁移排序配置
        migrateSortConfigs(preferences, albumConfig)
        
        // 迁移相册列表排序配置
        migrateAlbumsSortConfig(preferences, albumConfig)
        
        // 迁移网格滚动位置
        migrateGridScrollPositions(preferences, albumConfig)
        
        // 写入配置文件
        if (!ConfigManager.writeAlbumConfig(albumConfig)) {
            Log.e(TAG, "写入相册配置失败")
        } else {
            Log.d(TAG, "相册配置迁移成功")
        }
    }
    
    /**
     * 迁移网格列数配置
     * @param preferences SharedPreferences 实例
     * @param albumConfig 相册配置对象
     */
    private fun migrateGridColumns(preferences: SharedPreferences, albumConfig: AlbumConfig) {
        val allKeys = preferences.all.keys
        for (key in allKeys) {
            if (key.startsWith(PREF_GRID_COLUMNS_PREFIX)) {
                val albumId = key.substring(PREF_GRID_COLUMNS_PREFIX.length)
                val columns = preferences.getInt(key, 3)
                albumConfig.gridColumns[albumId] = columns
            }
        }
    }
    
    /**
     * 迁移排序配置
     * @param preferences SharedPreferences 实例
     * @param albumConfig 相册配置对象
     */
    private fun migrateSortConfigs(preferences: SharedPreferences, albumConfig: AlbumConfig) {
        val allKeys = preferences.all.keys
        
        // 收集所有相册ID
        val albumIds = mutableSetOf<String>()
        for (key in allKeys) {
            if (key.startsWith(PREF_SORT_TYPE_PREFIX)) {
                val albumId = key.substring(PREF_SORT_TYPE_PREFIX.length)
                albumIds.add(albumId)
            }
        }
        
        // 迁移每个相册的排序配置
        for (albumId in albumIds) {
            val sortTypeString = preferences.getString("${PREF_SORT_TYPE_PREFIX}$albumId", null)
            val sortDirectionString = preferences.getString("${PREF_SORT_DIRECTION_PREFIX}$albumId", null)
            
            val sortType = sortTypeString?.let {
                try {
                    SortType.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    SortType.CAPTURE_TIME
                }
            } ?: SortType.CAPTURE_TIME
            
            val sortDirection = sortDirectionString?.let {
                try {
                    SortDirection.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    SortDirection.DESCENDING
                }
            } ?: SortDirection.DESCENDING
            
            albumConfig.sortConfigs[albumId] = SortConfig(sortType, sortDirection)
        }
    }
    
    /**
     * 迁移相册列表排序配置
     * @param preferences SharedPreferences 实例
     * @param albumConfig 相册配置对象
     */
    private fun migrateAlbumsSortConfig(preferences: SharedPreferences, albumConfig: AlbumConfig) {
        val sortTypeString = preferences.getString(PREF_ALBUMS_SORT_TYPE, null)
        val sortDirectionString = preferences.getString(PREF_ALBUMS_SORT_DIRECTION, null)
        
        val sortType = sortTypeString?.let {
            try {
                SortType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                SortType.MODIFY_TIME
            }
        } ?: SortType.MODIFY_TIME
        
        val sortDirection = sortDirectionString?.let {
            try {
                SortDirection.valueOf(it)
            } catch (e: IllegalArgumentException) {
                SortDirection.DESCENDING
            }
        } ?: SortDirection.DESCENDING
        
        albumConfig.albumsSortConfig = SortConfig(sortType, sortDirection)
    }
    
    /**
     * 迁移网格滚动位置
     * @param preferences SharedPreferences 实例
     * @param albumConfig 相册配置对象
     */
    private fun migrateGridScrollPositions(preferences: SharedPreferences, albumConfig: AlbumConfig) {
        // 这里简化处理，只保存默认相册的滚动位置
        // 实际应用中可能需要根据不同的相册保存不同的滚动位置
        val scrollIndex = preferences.getString(PREF_GRID_SCROLL_INDEX, "0")?.toIntOrNull() ?: 0
        val scrollOffset = preferences.getString(PREF_GRID_SCROLL_OFFSET, "0")?.toIntOrNull() ?: 0
        
        albumConfig.gridScrollPositions["default"] = ScrollPosition(scrollIndex, scrollOffset)
    }
    
    /**
     * 迁移标签配置
     * @param preferences SharedPreferences 实例
     */
    private fun migrateTagConfig(preferences: SharedPreferences) {
        Log.d(TAG, "开始迁移标签配置")
        
        // 迁移激活的标签过滤ID
        val activeTagsString = preferences.getString(PREF_ACTIVE_TAGS, "") ?: ""
        val activeTagFilterIds = if (activeTagsString.isNotEmpty()) {
            activeTagsString.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        } else {
            emptySet()
        }
        
        // 迁移排除模式的标签ID
        val excludedTagsString = preferences.getString(PREF_EXCLUDED_TAGS, "") ?: ""
        val excludedTagIds = if (excludedTagsString.isNotEmpty()) {
            excludedTagsString.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        } else {
            emptySet()
        }
        
        // 迁移展开的标签ID
        val expandedTagsString = preferences.getString(PREF_EXPANDED_TAGS, "") ?: ""
        val expandedTagIds = if (expandedTagsString.isNotEmpty()) {
            expandedTagsString.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        } else {
            emptySet()
        }
        
        // 迁移展开的引用标签ID
        val expandedReferencedTagsString = preferences.getString(PREF_EXPANDED_REFERENCED_TAGS, "") ?: ""
        val expandedReferencedTagIds = if (expandedReferencedTagsString.isNotEmpty()) {
            expandedReferencedTagsString.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        } else {
            emptySet()
        }
        
        // 迁移标签抽屉滚动索引
        val tagDrawerScrollIndex = preferences.getString(PREF_TAG_DRAWER_SCROLL_INDEX, "0")?.toIntOrNull() ?: 0
        
        // 创建标签配置对象
        val tagConfig = TagConfig(
            expandedTagIds = expandedTagIds,
            activeTagFilterIds = activeTagFilterIds,
            excludedTagIds = excludedTagIds,
            expandedReferencedTagIds = expandedReferencedTagIds,
            tagDrawerScrollIndex = tagDrawerScrollIndex
        )
        
        // 写入配置文件
        if (!ConfigManager.writeTagConfig(tagConfig)) {
            Log.e(TAG, "写入标签配置失败")
        } else {
            Log.d(TAG, "标签配置迁移成功")
        }
    }
    
    /**
     * 迁移水印配置
     * @param preferences SharedPreferences 实例
     */
    private fun migrateWatermarkConfig(preferences: SharedPreferences) {
        Log.d(TAG, "开始迁移水印配置")
        
        // 迁移水印可见性
        val isWatermarkVisible = preferences.getBoolean(PREF_WATERMARK_VISIBLE, false)
        
        // 迁移默认水印预设ID
        val defaultPresetId = preferences.getLong(PREF_DEFAULT_WATERMARK_PRESET_ID, 0)
        
        // 创建水印配置对象
        val watermarkConfig = WatermarkConfig(
            isWatermarkVisible = isWatermarkVisible,
            defaultPresetId = defaultPresetId
        )
        
        // 写入配置文件
        if (!ConfigManager.writeWatermarkConfig(watermarkConfig)) {
            Log.e(TAG, "写入水印配置失败")
        } else {
            Log.d(TAG, "水印配置迁移成功")
        }
    }
    
    /**
     * 清理旧的 SharedPreferences 配置
     * @param context 上下文
     * @return 是否清理成功
     */
    fun clearOldPreferences(context: Context): Boolean {
        try {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            preferences.edit().clear().apply()
            Log.d(TAG, "旧的 SharedPreferences 配置清理成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "清理旧的 SharedPreferences 配置失败: ${e.message}", e)
            return false
        }
    }
}