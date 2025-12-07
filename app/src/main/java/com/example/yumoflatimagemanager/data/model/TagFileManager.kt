package com.example.yumoflatimagemanager.data.model

import android.os.Environment
import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 标签文件管理核心类，负责标签数据的文件读写
 * 使用Moshi进行JSON序列化和反序列化
 */
object TagFileManager {
    
    // 日志标签
    private const val TAG = "TagFileManager"
    
    // 目录名称
    private const val YUMOBOX_DIR_NAME = "YuMoBox"
    private const val TAGS_DIR_NAME = "tags"
    
    // Moshi实例
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    // 标签数据缓存
    private val tagCache: ConcurrentHashMap<Long, TagData> = ConcurrentHashMap()
    
    // 标签变化通知流，用于通知标签数据变化
    private val _tagChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val tagChanges: SharedFlow<Unit> = _tagChanges
    
    /**
     * 获取标签存储根目录
     * @return 标签存储根目录
     */
    fun getTagsRootDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), "$YUMOBOX_DIR_NAME/$TAGS_DIR_NAME")
    }
    
    /**
     * 创建标签目录
     * @return 是否创建成功
     */
    fun createTagsDirectory(): Boolean {
        val tagsDir = getTagsRootDirectory()
        if (!tagsDir.exists()) {
            return tagsDir.mkdirs()
        }
        return true
    }
    
    /**
     * 获取标签数据文件
     * @param tagId 标签ID
     * @return 标签数据文件对象
     */
    fun getTagFile(tagId: Long): File {
        return File(getTagsRootDirectory(), "tag_$tagId.json")
    }
    
    /**
     * 读取标签数据
     * @param tagId 标签ID
     * @return 标签数据，如果文件不存在或读取失败则返回null
     */
    fun readTag(tagId: Long): TagData? {
        // 先从缓存中查找
        tagCache[tagId]?.let { return it }
        
        val tagFile = getTagFile(tagId)
        if (!tagFile.exists()) {
            return null
        }
        
        return try {
            val jsonString = FileInputStream(tagFile).bufferedReader().use { it.readText() }
            val adapter: JsonAdapter<TagData> = moshi.adapter(TagData::class.java)
            val tagData = adapter.fromJson(jsonString)
            tagData?.let { tagCache[tagId] = it }
            tagData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read tag file for id $tagId: ${e.message}")
            null
        }
    }
    
    /**
     * 写入标签数据
     * @param tagData 标签数据
     * @return 是否写入成功
     */
    fun writeTag(tagData: TagData): Boolean {
        val tagFile = getTagFile(tagData.id)
        return try {
            val adapter: JsonAdapter<TagData> = moshi.adapter(TagData::class.java)
            val jsonString = adapter.toJson(tagData)
            FileOutputStream(tagFile).bufferedWriter().use { it.write(jsonString) }
            // 更新缓存
            tagCache[tagData.id] = tagData
            // 发送标签变化通知
            _tagChanges.tryEmit(Unit)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write tag file for id ${tagData.id}: ${e.message}")
            false
        }
    }
    
    /**
     * 删除标签数据
     * @param tagId 标签ID
     * @return 是否删除成功
     */
    fun deleteTag(tagId: Long): Boolean {
        val tagFile = getTagFile(tagId)
        return try {
            val deleted = tagFile.delete()
            if (deleted) {
                // 从缓存中移除
                tagCache.remove(tagId)
                // 发送标签变化通知
                _tagChanges.tryEmit(Unit)
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete tag file for id $tagId: ${e.message}")
            false
        }
    }
    
    /**
     * 获取所有标签数据
     * @return 所有标签数据列表
     */
    fun getAllTags(): List<TagData> {
        val tagsDir = getTagsRootDirectory()
        if (!tagsDir.exists()) {
            return emptyList()
        }
        
        val tagFiles = tagsDir.listFiles { file -> file.name.startsWith("tag_") && file.name.endsWith(".json") }
        if (tagFiles.isNullOrEmpty()) {
            return emptyList()
        }
        
        val allTags = mutableListOf<TagData>()
        for (tagFile in tagFiles) {
            // 从文件名中提取tagId
            val tagId = try {
                tagFile.name.substringAfter("tag_").substringBefore(".json").toLong()
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Invalid tag file name: ${tagFile.name}")
                continue
            }
            
            // 先从缓存中查找，缓存中没有则读取文件
            val tagData = tagCache[tagId] ?: run {
                val jsonString = FileInputStream(tagFile).bufferedReader().use { it.readText() }
                val adapter: JsonAdapter<TagData> = moshi.adapter(TagData::class.java)
                val data = adapter.fromJson(jsonString)
                data?.let { tagCache[tagId] = it }
                data
            }
            
            tagData?.let { allTags.add(it) }
        }
        
        return allTags
    }
    
    /**
     * 查找标签数据
     * @param name 标签名称
     * @return 标签数据，如果不存在则返回null
     */
    fun findTagByName(name: String): TagData? {
        // 先在缓存中查找
        tagCache.values.find { it.name == name }?.let { return it }
        
        // 缓存中没有则遍历所有标签文件
        val tagsDir = getTagsRootDirectory()
        if (!tagsDir.exists()) {
            return null
        }
        
        val tagFiles = tagsDir.listFiles { file -> file.name.startsWith("tag_") && file.name.endsWith(".json") }
        if (tagFiles.isNullOrEmpty()) {
            return null
        }
        
        for (tagFile in tagFiles) {
            try {
                val jsonString = FileInputStream(tagFile).bufferedReader().use { it.readText() }
                val adapter: JsonAdapter<TagData> = moshi.adapter(TagData::class.java)
                val tagData = adapter.fromJson(jsonString)
                if (tagData?.name == name) {
                    // 更新缓存
                    tagCache[tagData.id] = tagData
                    return tagData
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read tag file ${tagFile.name}: ${e.message}")
                continue
            }
        }
        
        return null
    }
    
    /**
     * 清除标签缓存
     */
    fun clearCache() {
        tagCache.clear()
        // 发送标签变化通知
        _tagChanges.tryEmit(Unit)
    }
    
    /**
     * 删除所有标签文件
     * @return 是否删除成功
     */
    fun deleteAllTags(): Boolean {
        val tagsDir = getTagsRootDirectory()
        if (!tagsDir.exists()) {
            return true
        }
        
        val tagFiles = tagsDir.listFiles { file -> file.name.startsWith("tag_") && file.name.endsWith(".json") }
        if (tagFiles.isNullOrEmpty()) {
            return true
        }
        
        var allDeleted = true
        for (tagFile in tagFiles) {
            if (!tagFile.delete()) {
                allDeleted = false
            }
        }
        
        if (allDeleted) {
            clearCache()
            // 发送标签变化通知
            _tagChanges.tryEmit(Unit)
        }
        
        return allDeleted
    }
}
