package com.example.yumoflatimagemanager.data

import android.os.Environment
import android.util.Log
import ando.file.core.FileUtils
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 配置管理类，负责统一管理所有配置文件
 * 使用Moshi进行JSON序列化和反序列化
 */
object ConfigManager {
    
    // 日志标签
    private const val TAG = "ConfigManager"
    
    // 配置文件目录
    private const val CONFIG_DIR_NAME = "config"
    private const val YUMOBOX_DIR_NAME = "YuMoBox"
    
    // 配置文件名
    private const val FILE_NAME_SECURITY = "security.json"
    private const val FILE_NAME_ALBUM = "album.json"
    private const val FILE_NAME_TAG = "tag.json"
    private const val FILE_NAME_WATERMARK = "watermark.json"
    private const val FILE_NAME_MIGRATION = "migration.json"
    
    private enum class LoadState {
        UNINITIALIZED,
        LOADED_FROM_DISK,
        DEFAULT_CREATED,
        FALLBACK_DUE_TO_READ_ERROR
    }
    
    // Moshi实例
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    // 配置缓存
    private var securityConfigCache: SecurityConfig? = null
    private var albumConfigCache: AlbumConfig? = null
    private var tagConfigCache: TagConfig? = null
    private var watermarkConfigCache: WatermarkConfig? = null
    
    // 记录配置加载状态，用于避免在读取失败后用空数据覆盖已有文件
    private var securityLoadState = LoadState.UNINITIALIZED
    private var albumLoadState = LoadState.UNINITIALIZED
    private var tagLoadState = LoadState.UNINITIALIZED
    private var watermarkLoadState = LoadState.UNINITIALIZED
    
    /**
     * 获取配置存储根目录
     * @return 配置存储根目录
     */
    fun getConfigRootDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), "$YUMOBOX_DIR_NAME/$CONFIG_DIR_NAME")
    }
    
    /**
     * 创建配置目录
     * @return 是否创建成功
     */
    fun createConfigDirectory(): Boolean {
        val configDir = getConfigRootDirectory()
        if (!configDir.exists()) {
            return configDir.mkdirs()
        }
        return true
    }
    
    /**
     * 获取配置文件
     * @param fileName 配置文件名
     * @return 配置文件对象
     */
    fun getConfigFile(fileName: String): File {
        return File(getConfigRootDirectory(), fileName)
    }
    
    /**
     * 读取配置
     * @param fileName 配置文件名
     * @param clazz 配置类
     * @return 配置对象，如果文件不存在或读取失败则返回null
     */
    private fun <T : Any> readConfig(fileName: String, clazz: Class<T>): T? {
        val configFile = getConfigFile(fileName)
        if (!configFile.exists()) {
            return null
        }
        
        return try {
            val jsonString = FileInputStream(configFile).bufferedReader().use { it.readText() }
            val adapter: JsonAdapter<T> = moshi.adapter(clazz)
            adapter.fromJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read config file $fileName: ${e.message}")
            null
        }
    }
    
    /**
     * 写入配置
     * @param fileName 配置文件名
     * @param config 配置对象
     * @return 是否写入成功
     */
    private fun <T : Any> writeConfig(fileName: String, config: T): Boolean {
        val configFile = getConfigFile(fileName)
        return try {
            val adapter: JsonAdapter<T> = moshi.adapter(config::class.java as Class<T>)
            val jsonString = adapter.toJson(config)
            FileOutputStream(configFile).bufferedWriter().use { it.write(jsonString) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write config file $fileName: ${e.message}")
            false
        }
    }
    
    // ==================== 安全模式配置 ==================== //
    
    /**
     * 读取安全模式配置
     * @return 安全模式配置
     */
    fun readSecurityConfig(): SecurityConfig {
        if (securityConfigCache == null) {
            val configFromDisk = readConfig(FILE_NAME_SECURITY, SecurityConfig::class.java)
            if (configFromDisk != null) {
                securityLoadState = LoadState.LOADED_FROM_DISK
                securityConfigCache = configFromDisk
            } else {
                securityLoadState = if (getConfigFile(FILE_NAME_SECURITY).exists()) {
                    LoadState.FALLBACK_DUE_TO_READ_ERROR
                } else {
                    LoadState.DEFAULT_CREATED
                }
                securityConfigCache = SecurityConfig()
            }
        }
        return securityConfigCache!!
    }
    
    /**
     * 写入安全模式配置
     * @param config 安全模式配置
     * @return 是否写入成功
     */
    fun writeSecurityConfig(config: SecurityConfig): Boolean {
        if (securityLoadState == LoadState.FALLBACK_DUE_TO_READ_ERROR) {
            val diskConfig = readConfig(FILE_NAME_SECURITY, SecurityConfig::class.java)
            when {
                diskConfig != null -> {
                    securityLoadState = LoadState.LOADED_FROM_DISK
                    securityConfigCache = diskConfig
                    return false // 磁盘数据可读，优先让调用方重新加载
                }
                getConfigFile(FILE_NAME_SECURITY).exists() -> {
                    Log.w(TAG, "Skip writing $FILE_NAME_SECURITY to avoid overwriting unreadable existing data")
                    return false
                }
                else -> securityLoadState = LoadState.DEFAULT_CREATED
            }
        }
        
        val result = writeConfig(FILE_NAME_SECURITY, config)
        if (result) {
            securityConfigCache = config
            securityLoadState = LoadState.LOADED_FROM_DISK
        }
        return result
    }
    
    // ==================== 相册配置 ==================== //
    
    /**
     * 读取相册配置
     * @return 相册配置
     */
    fun readAlbumConfig(): AlbumConfig {
        if (albumConfigCache == null) {
            val configFile = getConfigFile(FILE_NAME_ALBUM)
            var config: AlbumConfig? = null
            
            if (configFile.exists()) {
                try {
                    // 尝试读取新格式配置
                    config = readConfig(FILE_NAME_ALBUM, AlbumConfig::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "读取相册配置失败，可能是旧格式，尝试迁移: ${e.message}")
                    // 如果读取失败，可能是旧格式，尝试迁移
                    config = tryMigrateOldFormat()
                }
                
                if (config != null) {
                    albumLoadState = LoadState.LOADED_FROM_DISK
                } else {
                    albumLoadState = LoadState.FALLBACK_DUE_TO_READ_ERROR
                }
            } else {
                albumLoadState = LoadState.DEFAULT_CREATED
            }
            
            albumConfigCache = config ?: AlbumConfig()
        }
        return albumConfigCache!!
    }
    
    /**
     * 尝试迁移旧格式配置（如果存在）
     * 如果配置文件中存在Int类型的gridColumns，尝试迁移到新格式
     */
    private fun tryMigrateOldFormat(): AlbumConfig? {
        val configFile = getConfigFile(FILE_NAME_ALBUM)
        if (!configFile.exists()) {
            return null
        }
        
        return try {
            val jsonString = FileInputStream(configFile).bufferedReader().use { it.readText() }
            
            // 检查是否是旧格式（gridColumns包含数字而不是对象）
            // 简单检查：如果gridColumns的值是数字数组格式，则是旧格式
            if (jsonString.contains("\"gridColumns\"")) {
                // 尝试手动解析JSON并迁移
                // 由于Moshi不支持部分迁移，我们创建一个新的配置对象
                val newConfig = AlbumConfig()
                
                // 使用org.json或手动解析来提取旧数据（如果可用）
                // 这里简化处理：如果解析失败，返回新配置，旧的Int配置会在下次写入时丢失
                // 实际应用中，可以在ConfigMigration中完成所有迁移工作
                Log.d(TAG, "检测到可能的旧格式配置，使用默认配置")
                newConfig
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "尝试迁移旧格式配置时出错: ${e.message}")
            null
        }
    }
    
    /**
     * 写入相册配置
     * @param config 相册配置
     * @return 是否写入成功
     */
    fun writeAlbumConfig(config: AlbumConfig): Boolean {
        if (albumLoadState == LoadState.FALLBACK_DUE_TO_READ_ERROR) {
            val diskConfig = readConfig(FILE_NAME_ALBUM, AlbumConfig::class.java)
            when {
                diskConfig != null -> {
                    albumLoadState = LoadState.LOADED_FROM_DISK
                    albumConfigCache = diskConfig
                    return false // 先让调用方重新获取最新配置，避免覆盖
                }
                getConfigFile(FILE_NAME_ALBUM).exists() -> {
                    Log.w(TAG, "Skip writing $FILE_NAME_ALBUM to avoid overwriting unreadable existing data")
                    return false
                }
                else -> albumLoadState = LoadState.DEFAULT_CREATED
            }
        }
        
        val result = writeConfig(FILE_NAME_ALBUM, config)
        if (result) {
            albumConfigCache = config
            albumLoadState = LoadState.LOADED_FROM_DISK
        }
        return result
    }
    
    // ==================== 标签配置 ==================== //
    
    /**
     * 读取标签配置
     * @return 标签配置
     */
    fun readTagConfig(): TagConfig {
        if (tagConfigCache == null) {
            val configFromDisk = readConfig(FILE_NAME_TAG, TagConfig::class.java)
            if (configFromDisk != null) {
                tagLoadState = LoadState.LOADED_FROM_DISK
                tagConfigCache = configFromDisk
            } else {
                tagLoadState = if (getConfigFile(FILE_NAME_TAG).exists()) {
                    LoadState.FALLBACK_DUE_TO_READ_ERROR
                } else {
                    LoadState.DEFAULT_CREATED
                }
                tagConfigCache = TagConfig()
            }
        }
        return tagConfigCache!!
    }
    
    /**
     * 写入标签配置
     * @param config 标签配置
     * @return 是否写入成功
     */
    fun writeTagConfig(config: TagConfig): Boolean {
        if (tagLoadState == LoadState.FALLBACK_DUE_TO_READ_ERROR) {
            val diskConfig = readConfig(FILE_NAME_TAG, TagConfig::class.java)
            when {
                diskConfig != null -> {
                    tagLoadState = LoadState.LOADED_FROM_DISK
                    tagConfigCache = diskConfig
                    return false
                }
                getConfigFile(FILE_NAME_TAG).exists() -> {
                    Log.w(TAG, "Skip writing $FILE_NAME_TAG to avoid overwriting unreadable existing data")
                    return false
                }
                else -> tagLoadState = LoadState.DEFAULT_CREATED
            }
        }
        
        val result = writeConfig(FILE_NAME_TAG, config)
        if (result) {
            tagConfigCache = config
            tagLoadState = LoadState.LOADED_FROM_DISK
        }
        return result
    }
    
    // ==================== 水印配置 ==================== //
    
    /**
     * 读取水印配置
     * @return 水印配置
     */
    fun readWatermarkConfig(): WatermarkConfig {
        if (watermarkConfigCache == null) {
            val configFromDisk = readConfig(FILE_NAME_WATERMARK, WatermarkConfig::class.java)
            if (configFromDisk != null) {
                watermarkLoadState = LoadState.LOADED_FROM_DISK
                watermarkConfigCache = configFromDisk
            } else {
                watermarkLoadState = if (getConfigFile(FILE_NAME_WATERMARK).exists()) {
                    LoadState.FALLBACK_DUE_TO_READ_ERROR
                } else {
                    LoadState.DEFAULT_CREATED
                }
                watermarkConfigCache = WatermarkConfig()
            }
        }
        return watermarkConfigCache!!
    }
    
    /**
     * 写入水印配置
     * @param config 水印配置
     * @return 是否写入成功
     */
    fun writeWatermarkConfig(config: WatermarkConfig): Boolean {
        if (watermarkLoadState == LoadState.FALLBACK_DUE_TO_READ_ERROR) {
            val diskConfig = readConfig(FILE_NAME_WATERMARK, WatermarkConfig::class.java)
            when {
                diskConfig != null -> {
                    watermarkLoadState = LoadState.LOADED_FROM_DISK
                    watermarkConfigCache = diskConfig
                    return false
                }
                getConfigFile(FILE_NAME_WATERMARK).exists() -> {
                    Log.w(TAG, "Skip writing $FILE_NAME_WATERMARK to avoid overwriting unreadable existing data")
                    return false
                }
                else -> watermarkLoadState = LoadState.DEFAULT_CREATED
            }
        }
        
        val result = writeConfig(FILE_NAME_WATERMARK, config)
        if (result) {
            watermarkConfigCache = config
            watermarkLoadState = LoadState.LOADED_FROM_DISK
        }
        return result
    }
    
    // ==================== 迁移配置 ==================== //
    
    /**
     * 读取迁移配置
     * @return 迁移配置
     */
    fun readMigrationConfig(): MigrationConfig {
        return readConfig(FILE_NAME_MIGRATION, MigrationConfig::class.java)
            ?: MigrationConfig()
    }
    
    /**
     * 写入迁移配置
     * @param config 迁移配置
     * @return 是否写入成功
     */
    fun writeMigrationConfig(config: MigrationConfig): Boolean {
        return writeConfig(FILE_NAME_MIGRATION, config)
    }
    
    /**
     * 检查配置是否已迁移
     * @return 是否已迁移
     */
    fun isConfigMigrated(): Boolean {
        return readMigrationConfig().isConfigMigrated
    }
    
    /**
     * 标记配置已迁移
     * @return 是否标记成功
     */
    fun markConfigMigrated(): Boolean {
        return writeMigrationConfig(MigrationConfig(true))
    }
    
    /**
     * 清除配置缓存
     */
    fun clearCache() {
        securityConfigCache = null
        albumConfigCache = null
        tagConfigCache = null
        watermarkConfigCache = null
        securityLoadState = LoadState.UNINITIALIZED
        albumLoadState = LoadState.UNINITIALIZED
        tagLoadState = LoadState.UNINITIALIZED
        watermarkLoadState = LoadState.UNINITIALIZED
    }
    
    /**
     * 删除所有配置文件
     * @return 是否删除成功
     */
    fun deleteAllConfigFiles(): Boolean {
        val configDir = getConfigRootDirectory()
        if (!configDir.exists()) {
            return true
        }
        
        val files = configDir.listFiles()
        if (files.isNullOrEmpty()) {
            return true
        }
        
        var allDeleted = true
        for (file in files) {
            if (!file.delete()) {
                allDeleted = false
            }
        }
        
        if (allDeleted) {
            clearCache()
        }
        
        return allDeleted
    }
}
