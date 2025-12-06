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
    
    // Moshi实例
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    // 配置缓存
    private var securityConfigCache: SecurityConfig? = null
    private var albumConfigCache: AlbumConfig? = null
    private var tagConfigCache: TagConfig? = null
    private var watermarkConfigCache: WatermarkConfig? = null
    
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
            securityConfigCache = readConfig(FILE_NAME_SECURITY, SecurityConfig::class.java)
                ?: SecurityConfig()
        }
        return securityConfigCache!!
    }
    
    /**
     * 写入安全模式配置
     * @param config 安全模式配置
     * @return 是否写入成功
     */
    fun writeSecurityConfig(config: SecurityConfig): Boolean {
        val result = writeConfig(FILE_NAME_SECURITY, config)
        if (result) {
            securityConfigCache = config
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
            albumConfigCache = readConfig(FILE_NAME_ALBUM, AlbumConfig::class.java)
                ?: AlbumConfig()
        }
        return albumConfigCache!!
    }
    
    /**
     * 写入相册配置
     * @param config 相册配置
     * @return 是否写入成功
     */
    fun writeAlbumConfig(config: AlbumConfig): Boolean {
        val result = writeConfig(FILE_NAME_ALBUM, config)
        if (result) {
            albumConfigCache = config
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
            tagConfigCache = readConfig(FILE_NAME_TAG, TagConfig::class.java)
                ?: TagConfig()
        }
        return tagConfigCache!!
    }
    
    /**
     * 写入标签配置
     * @param config 标签配置
     * @return 是否写入成功
     */
    fun writeTagConfig(config: TagConfig): Boolean {
        val result = writeConfig(FILE_NAME_TAG, config)
        if (result) {
            tagConfigCache = config
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
            watermarkConfigCache = readConfig(FILE_NAME_WATERMARK, WatermarkConfig::class.java)
                ?: WatermarkConfig()
        }
        return watermarkConfigCache!!
    }
    
    /**
     * 写入水印配置
     * @param config 水印配置
     * @return 是否写入成功
     */
    fun writeWatermarkConfig(config: WatermarkConfig): Boolean {
        val result = writeConfig(FILE_NAME_WATERMARK, config)
        if (result) {
            watermarkConfigCache = config
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
