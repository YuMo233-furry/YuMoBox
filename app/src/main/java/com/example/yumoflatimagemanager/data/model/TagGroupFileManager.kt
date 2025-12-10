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
 * 标签组文件管理核心类，负责标签组数据的文件读写
 * 使用Moshi进行JSON序列化和反序列化
 */
object TagGroupFileManager {
    
    // 日志标签
    private const val TAG = "TagGroupFileManager"
    
    // 目录名称
    private const val YUMOBOX_DIR_NAME = "YuMoBox"
    private const val TAG_GROUPS_DIR_NAME = "tag_groups"
    
    // Moshi实例
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    // 标签组数据缓存
    private val tagGroupCache: ConcurrentHashMap<Long, TagGroupData> = ConcurrentHashMap()
    
    // 缓存更新时间记录
    private val cacheUpdateTimes: ConcurrentHashMap<Long, Long> = ConcurrentHashMap()
    
    // 缓存过期时间（毫秒）
    private const val CACHE_EXPIRY_TIME = 30000 // 30秒
    
    // 标签组变化通知流，用于通知标签组数据变化
    private val _tagGroupChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val tagGroupChanges: SharedFlow<Unit> = _tagGroupChanges
    
    // 初始化
    init {
        // 确保标签组目录存在
        createTagGroupsDirectory()
        
        // 初始化默认的"未分组"标签组
        initializeDefaultTagGroup()
    }
    
    /**
     * 初始化默认的"未分组"标签组
     */
    fun initializeDefaultTagGroup() {
        // 检查是否已存在"未分组"标签组，遇到异常时兜底创建
        val allGroups = runCatching { getAllTagGroups() }
            .onFailure { Log.e(TAG, "Failed to load tag groups, will recreate default", it) }
            .getOrDefault(emptyList())
        val hasDefault = allGroups.any { group ->
            // 防御空字段，避免混淆/坏数据导致崩溃
            val name = group.name
            val isDefault = group.isDefault
            (name == "未分组") || isDefault
        }
        if (!hasDefault) {
            val defaultGroup = TagGroupData(
                id = 1,
                name = "未分组",
                sortOrder = 0,
                isDefault = true,
                tagIds = emptyList()
            )
            writeTagGroup(defaultGroup)
        }
    }
    
    /**
     * 获取标签组存储根目录
     * @return 标签组存储根目录
     */
    fun getTagGroupsRootDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), "$YUMOBOX_DIR_NAME/$TAG_GROUPS_DIR_NAME")
    }
    
    /**
     * 创建标签组目录
     * @return 是否创建成功
     */
    fun createTagGroupsDirectory(): Boolean {
        val tagGroupsDir = getTagGroupsRootDirectory()
        if (!tagGroupsDir.exists()) {
            return tagGroupsDir.mkdirs()
        }
        return true
    }
    
    /**
     * 获取标签组数据文件
     * @param groupId 标签组ID
     * @return 标签组数据文件对象
     */
    fun getTagGroupFile(groupId: Long): File {
        return File(getTagGroupsRootDirectory(), "group_$groupId.json")
    }
    
    /**
     * 读取标签组数据
     * @param groupId 标签组ID
     * @param forceRefresh 是否强制刷新缓存
     * @return 标签组数据，如果文件不存在或读取失败则返回null
     */
    fun readTagGroup(groupId: Long, forceRefresh: Boolean = false): TagGroupData? {
        // 检查缓存是否存在且未过期
        val cachedData = tagGroupCache[groupId]
        val lastUpdateTime = cacheUpdateTimes[groupId]
        val currentTime = System.currentTimeMillis()
        
        if (cachedData != null && !forceRefresh && lastUpdateTime != null && 
            (currentTime - lastUpdateTime) < CACHE_EXPIRY_TIME) {
            return cachedData
        }
        
        val tagGroupFile = getTagGroupFile(groupId)
        if (!tagGroupFile.exists()) {
            // 如果文件不存在，清除缓存
            tagGroupCache.remove(groupId)
            cacheUpdateTimes.remove(groupId)
            return null
        }
        
        return try {
            val jsonString = FileInputStream(tagGroupFile).bufferedReader().use { it.readText() }
            val adapter: JsonAdapter<TagGroupData> = moshi.adapter(TagGroupData::class.java)
            val tagGroupData = adapter.fromJson(jsonString)
            tagGroupData?.let {
                tagGroupCache[groupId] = it
                cacheUpdateTimes[groupId] = currentTime
            }
            tagGroupData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read tag group file for id $groupId: ${e.message}")
            null
        }
    }
    
    /**
     * 写入标签组数据
     * @param tagGroupData 标签组数据
     * @return 是否写入成功
     */
    fun writeTagGroup(tagGroupData: TagGroupData): Boolean {
        // 创建标签组目录
        createTagGroupsDirectory()
        
        val tagGroupFile = getTagGroupFile(tagGroupData.id)
        return try {
            val adapter: JsonAdapter<TagGroupData> = moshi.adapter(TagGroupData::class.java)
            val jsonString = adapter.toJson(tagGroupData)
            FileOutputStream(tagGroupFile).bufferedWriter().use { it.write(jsonString) }
            // 更新缓存和缓存时间
            tagGroupCache[tagGroupData.id] = tagGroupData
            cacheUpdateTimes[tagGroupData.id] = System.currentTimeMillis()
            // 发送标签组变化通知
            _tagGroupChanges.tryEmit(Unit)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write tag group file for id ${tagGroupData.id}: ${e.message}")
            false
        }
    }
    
    /**
     * 删除标签组数据
     * @param groupId 标签组ID
     * @return 是否删除成功
     */
    fun deleteTagGroup(groupId: Long): Boolean {
        val tagGroupFile = getTagGroupFile(groupId)
        return try {
            val deleted = tagGroupFile.delete()
            if (deleted) {
                // 从缓存中移除
                tagGroupCache.remove(groupId)
                cacheUpdateTimes.remove(groupId)
                // 发送标签组变化通知
                _tagGroupChanges.tryEmit(Unit)
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete tag group file for id $groupId: ${e.message}")
            false
        }
    }
    
    /**
     * 刷新特定标签组的缓存
     * @param groupId 标签组ID
     * @return 刷新后的标签组数据，如果刷新失败则返回null
     */
    fun refreshTagGroupCache(groupId: Long): TagGroupData? {
        return readTagGroup(groupId, forceRefresh = true)
    }
    
    /**
     * 清除特定标签组的缓存
     * @param groupId 标签组ID
     */
    fun clearTagGroupCache(groupId: Long) {
        tagGroupCache.remove(groupId)
        cacheUpdateTimes.remove(groupId)
    }
    
    /**
     * 获取所有标签组数据
     * @return 所有标签组数据列表
     */
    fun getAllTagGroups(): List<TagGroupData> {
        val tagGroupsDir = getTagGroupsRootDirectory()
        if (!tagGroupsDir.exists()) {
            return emptyList()
        }
        
        val tagGroupFiles = tagGroupsDir.listFiles { file -> file.name.startsWith("group_") && file.name.endsWith(".json") }
        if (tagGroupFiles.isNullOrEmpty()) {
            return emptyList()
        }
        
        // 对文件按名称排序，确保结果稳定
        val sortedTagGroupFiles = tagGroupFiles.sortedBy { it.name }
        
        val allTagGroups = mutableListOf<TagGroupData>()
        
        for (tagGroupFile in sortedTagGroupFiles) {
            // 从文件名中提取groupId
            val groupId = try {
                tagGroupFile.name.substringAfter("group_").substringBefore(".json").toLong()
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Invalid tag group file name: ${tagGroupFile.name}")
                continue
            }
            
            // 先从缓存中查找，缓存中没有则读取文件
            val tagGroupData = tagGroupCache[groupId] ?: run {
                val jsonString = FileInputStream(tagGroupFile).bufferedReader().use { it.readText() }
                val adapter: JsonAdapter<TagGroupData> = moshi.adapter(TagGroupData::class.java)
                val data = adapter.fromJson(jsonString)
                data?.let { tagGroupCache[groupId] = it }
                data
            }
            
            tagGroupData?.let { allTagGroups.add(it) }
        }
        
        // 按 sortOrder 排序，确保默认标签组始终排在第一位
        return allTagGroups.sortedWith(compareBy(
            { !it.isDefault }, // 默认标签组排在前面（false < true）
            { it.sortOrder }   // 然后按 sortOrder 排序
        ))
    }
    
    /**
     * 查找标签组数据
     * @param name 标签组名称
     * @return 标签组数据，如果不存在则返回null
     */
    fun findTagGroupByName(name: String): TagGroupData? {
        // 先在缓存中查找
        tagGroupCache.values.find { it.name == name }?.let { return it }
        
        // 缓存中没有则遍历所有标签组文件
        val tagGroupsDir = getTagGroupsRootDirectory()
        if (!tagGroupsDir.exists()) {
            return null
        }
        
        val tagGroupFiles = tagGroupsDir.listFiles { file -> file.name.startsWith("group_") && file.name.endsWith(".json") }
        if (tagGroupFiles.isNullOrEmpty()) {
            return null
        }
        
        for (tagGroupFile in tagGroupFiles) {
            try {
                val jsonString = FileInputStream(tagGroupFile).bufferedReader().use { it.readText() }
                val adapter: JsonAdapter<TagGroupData> = moshi.adapter(TagGroupData::class.java)
                val tagGroupData = adapter.fromJson(jsonString)
                if (tagGroupData?.name == name) {
                    // 更新缓存
                    tagGroupCache[tagGroupData.id] = tagGroupData
                    return tagGroupData
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read tag group file ${tagGroupFile.name}: ${e.message}")
                continue
            }
        }
        
        return null
    }
    
    /**
     * 清除标签组缓存
     */
    fun clearCache() {
        tagGroupCache.clear()
        // 发送标签组变化通知
        _tagGroupChanges.tryEmit(Unit)
    }
    
    /**
     * 删除所有标签组文件
     * @return 是否删除成功
     */
    fun deleteAllTagGroups(): Boolean {
        val tagGroupsDir = getTagGroupsRootDirectory()
        if (!tagGroupsDir.exists()) {
            return true
        }
        
        val tagGroupFiles = tagGroupsDir.listFiles { file -> file.name.startsWith("group_") && file.name.endsWith(".json") }
        if (tagGroupFiles.isNullOrEmpty()) {
            return true
        }
        
        var allDeleted = true
        for (tagGroupFile in tagGroupFiles) {
            if (!tagGroupFile.delete()) {
                allDeleted = false
            }
        }
        
        if (allDeleted) {
            clearCache()
            // 发送标签组变化通知
            _tagGroupChanges.tryEmit(Unit)
        }
        
        return allDeleted
    }
    
    /**
     * 获取标签所属的标签组数据
     * @param tagId 标签ID
     * @return 标签所属的标签组数据列表
     */
    fun getTagGroupsByTagId(tagId: Long): List<TagGroupData> {
        return getAllTagGroups().filter { it.tagIds.contains(tagId) }
    }
    
    /**
     * 添加标签到标签组
     * @param tagId 标签ID
     * @param groupId 标签组ID
     * @return 是否添加成功
     */
    fun addTagToTagGroup(tagId: Long, groupId: Long): Boolean {
        val tagGroupData = readTagGroup(groupId)
        if (tagGroupData == null) {
            return false
        }
        
        val updatedTagGroupData = tagGroupData.withAddedTag(tagId)
        return writeTagGroup(updatedTagGroupData)
    }
    
    /**
     * 从标签组移除标签
     * @param tagId 标签ID
     * @param groupId 标签组ID
     * @return 是否移除成功
     */
    fun removeTagFromTagGroup(tagId: Long, groupId: Long): Boolean {
        val tagGroupData = readTagGroup(groupId)
        if (tagGroupData == null) {
            return false
        }
        
        val updatedTagGroupData = tagGroupData.withRemovedTag(tagId)
        return writeTagGroup(updatedTagGroupData)
    }
    
    /**
     * 从所有标签组中移除标签引用
     * @param tagId 标签ID
     * @return 是否移除成功
     */
    fun removeTagFromAllTagGroups(tagId: Long): Boolean {
        val allTagGroups = getAllTagGroups()
        var success = true
        
        // 遍历所有标签组，移除标签引用
        for (tagGroupData in allTagGroups) {
            if (tagGroupData.tagIds.contains(tagId)) {
                val updatedTagGroupData = tagGroupData.withRemovedTag(tagId)
                if (!writeTagGroup(updatedTagGroupData)) {
                    success = false
                }
            }
        }
        
        return success
    }
}
