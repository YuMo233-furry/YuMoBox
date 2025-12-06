package com.example.yumoflatimagemanager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 首选项管理器，负责处理应用程序的持久化配置存储
 */
class PreferencesManager private constructor(context: Context) {
    
    // SharedPreferences实例
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * 安全模式相关的辅助方法
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit {
            putBoolean(key, value)
        }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }

    fun putString(key: String, value: String) {
        preferences.edit {
            putString(key, value)
        }
    }
    
    /**
     * 保存相册的排序配置
     * @param albumId 相册ID
     * @param sortConfig 排序配置
     */
    fun saveAlbumSortConfig(albumId: String, sortConfig: SortConfig) {
        preferences.edit {
            putString("${PREF_SORT_TYPE_PREFIX}$albumId", sortConfig.type.name)
            putString("${PREF_SORT_DIRECTION_PREFIX}$albumId", sortConfig.direction.name)
        }
    }
    
    /**
     * 加载相册的排序配置
     * @param albumId 相册ID
     * @return 排序配置，如果没有保存过则返回默认配置
     */
    fun loadAlbumSortConfig(albumId: String): SortConfig {
        val sortTypeString = preferences.getString("${PREF_SORT_TYPE_PREFIX}$albumId", null)
        val sortDirectionString = preferences.getString("${PREF_SORT_DIRECTION_PREFIX}$albumId", null)
        
        val sortType = if (sortTypeString != null) {
            try {
                SortType.valueOf(sortTypeString)
            } catch (e: IllegalArgumentException) {
                SortType.CAPTURE_TIME
            }
        } else {
            SortType.CAPTURE_TIME
        }
        
        val sortDirection = if (sortDirectionString != null) {
            try {
                SortDirection.valueOf(sortDirectionString)
            } catch (e: IllegalArgumentException) {
                SortDirection.DESCENDING
            }
        } else {
            SortDirection.DESCENDING
        }
        
        return SortConfig(sortType, sortDirection)
    }
    
    /**
     * 保存相册列表的排序配置
     * @param sortConfig 排序配置
     */
    fun saveAlbumsSortConfig(sortConfig: SortConfig) {
        preferences.edit {
            putString(PREF_ALBUMS_SORT_TYPE, sortConfig.type.name)
            putString(PREF_ALBUMS_SORT_DIRECTION, sortConfig.direction.name)
        }
    }
    
    /**
     * 加载相册列表的排序配置
     * @return 排序配置，如果没有保存过则返回默认配置
     */
    fun loadAlbumsSortConfig(): SortConfig {
        val sortTypeString = preferences.getString(PREF_ALBUMS_SORT_TYPE, null)
        val sortDirectionString = preferences.getString(PREF_ALBUMS_SORT_DIRECTION, null)
        
        val sortType = if (sortTypeString != null) {
            try {
                SortType.valueOf(sortTypeString)
            } catch (e: IllegalArgumentException) {
                SortType.MODIFY_TIME
            }
        } else {
            SortType.MODIFY_TIME
        }
        
        val sortDirection = if (sortDirectionString != null) {
            try {
                SortDirection.valueOf(sortDirectionString)
            } catch (e: IllegalArgumentException) {
                SortDirection.DESCENDING
            }
        } else {
            SortDirection.DESCENDING
        }
        
        return SortConfig(sortType, sortDirection)
    }

    /**
     * 清除所有保存的排序配置
     */
    fun clearAllSortConfigs() {
        preferences.edit {
            // 获取所有以排序配置前缀开头的键并移除
            val allKeys = preferences.all.keys
            for (key in allKeys) {
                if (key.startsWith(PREF_SORT_TYPE_PREFIX) || key.startsWith(PREF_SORT_DIRECTION_PREFIX)) {
                    remove(key)
                }
            }
        }
    }

    /**
     * 保存相册的网格列数配置
     * @param albumId 相册ID
     * @param columns 网格列数
     */
    fun saveAlbumGridColumns(albumId: String, columns: Int) {
        preferences.edit {
            putInt("${PREF_GRID_COLUMNS_PREFIX}$albumId", columns)
        }
    }

    /**
     * 加载相册的网格列数配置
     * @param albumId 相册ID
     * @return 网格列数，如果没有保存过则返回默认值3
     */
    fun loadAlbumGridColumns(albumId: String): Int {
        return preferences.getInt("${PREF_GRID_COLUMNS_PREFIX}$albumId", 3)
    }
    
    companion object {
        // 常量定义
        private const val PREFERENCES_NAME = "YuMoFlatImageManagerPreferences"
        private const val PREF_SORT_TYPE_PREFIX = "sort_type_"
        private const val PREF_SORT_DIRECTION_PREFIX = "sort_direction_"
        private const val PREF_GRID_COLUMNS_PREFIX = "grid_columns_"
        private const val PREF_ALBUMS_SORT_TYPE = "albums_sort_type"
        private const val PREF_ALBUMS_SORT_DIRECTION = "albums_sort_direction"
        
        // 单例实例
        @Volatile
        private var INSTANCE: PreferencesManager? = null
        
        /**
         * 获取PreferencesManager的单例实例
         * @param context 上下文
         * @return PreferencesManager实例
         */
        fun getInstance(context: Context): PreferencesManager {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            
            synchronized(this) {
                val instance = PreferencesManager(context.applicationContext)
                INSTANCE = instance
                return instance
            }
        }
    }
}