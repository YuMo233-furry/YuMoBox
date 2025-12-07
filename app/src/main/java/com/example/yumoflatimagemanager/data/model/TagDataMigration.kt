package com.example.yumoflatimagemanager.data.model

import android.content.Context
import android.util.Log
import com.example.yumoflatimagemanager.data.ConfigManager
import com.example.yumoflatimagemanager.data.local.AppDatabase
import com.example.yumoflatimagemanager.data.model.TagData.ReferencedTag
import com.example.yumoflatimagemanager.data.model.TagData.ParentReference

/**
 * 标签数据迁移类，负责从数据库迁移到文件系统
 */
object TagDataMigration {
    
    // 日志标签
    private const val TAG = "TagDataMigration"
    
    /**
     * 执行数据迁移
     * @param context 上下文
     * @return 是否迁移成功
     */
    suspend fun migrateFromDatabase(context: Context): Boolean {
        try {
            Log.d(TAG, "开始标签数据迁移")
            
            // 1. 创建标签目录
            if (!TagFileManager.createTagsDirectory()) {
                Log.e(TAG, "创建标签目录失败")
                return false
            }
            
            // 2. 获取数据库实例
            val database = AppDatabase.get(context)
            val tagDao = database.tagDao()
            
            // 3. 获取所有标签
            val allTags = tagDao.getAllTagsList()
            Log.d(TAG, "从数据库获取到 ${allTags.size} 个标签")
            
            if (allTags.isEmpty()) {
                Log.d(TAG, "没有标签需要迁移")
                return true
            }
            
            // 4. 迁移每个标签
            for (tagEntity in allTags) {
                // 4.1 获取标签与媒体的关联关系
                val mediaTagCrossRefs = tagDao.getMediaPathsByTagId(tagEntity.id)
                val mediaPaths = mediaTagCrossRefs.map { it.mediaPath }.toMutableList()
                
                // 4.2 获取标签引用关系（该标签引用的其他标签）
                val tagReferences = tagDao.getTagReferencesByParentId(tagEntity.id)
                val referencedTags = tagReferences.map { 
                    ReferencedTag(
                        childTagId = it.childTagId,
                        sortOrder = it.sortOrder
                    )
                }.toMutableList()
                
                // 4.3 获取父引用关系（引用该标签的其他标签）
                val parentReferences = tagDao.getTagReferencesByChildId(tagEntity.id)
                val parentRefs = parentReferences.map { 
                    ParentReference(
                        parentTagId = it.parentTagId,
                        sortOrder = it.sortOrder
                    )
                }.toMutableList()
                
                // 4.4 获取标签统计信息
                val tagStatistics = tagDao.getTagStatistics(tagEntity.id)
                val directImageCount = tagStatistics?.directImageCount ?: 0
                val referencedCount = tagStatistics?.referencedCount ?: 0
                
                // 4.5 计算总图片数量（包含引用标签的图片）
                // 这里简化处理，直接使用数据库中的 imageCount
                val totalImageCount = tagEntity.imageCount
                
                // 4.6 创建 TagData 对象
                val tagData = TagData(
                    id = tagEntity.id,
                    parentId = tagEntity.parentId,
                    name = tagEntity.name,
                    sortOrder = tagEntity.sortOrder,
                    referencedGroupSortOrder = tagEntity.referencedGroupSortOrder,
                    normalGroupSortOrder = tagEntity.normalGroupSortOrder,
                    isExpanded = tagEntity.isExpanded,
                    imageCount = tagEntity.imageCount,
                    directImageCount = directImageCount,
                    totalImageCount = totalImageCount,
                    referencedCount = referencedCount,
                    mediaPaths = mediaPaths,
                    referencedTags = referencedTags,
                    parentReferences = parentRefs
                )
                
                // 4.7 写入到文件
                if (!TagFileManager.writeTag(tagData)) {
                    Log.e(TAG, "写入标签数据失败: ${tagEntity.name}")
                    return false
                }
                
                Log.d(TAG, "成功迁移标签: ${tagEntity.name} (ID: ${tagEntity.id})")
            }
            
            // 5. 标记迁移完成
            if (!markTagMigrationCompleted()) {
                Log.e(TAG, "标记迁移完成失败")
                return false
            }
            
            Log.d(TAG, "标签数据迁移成功")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "标签数据迁移失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 验证迁移结果
     * @return 是否验证成功
     */
    suspend fun verifyMigration(): Boolean {
        try {
            Log.d(TAG, "开始验证标签数据迁移结果")
            
            // 1. 获取所有迁移后的标签
            val allTagData = TagFileManager.getAllTags()
            Log.d(TAG, "从文件系统获取到 ${allTagData.size} 个标签")
            
            if (allTagData.isEmpty()) {
                Log.d(TAG, "没有标签需要验证")
                return true
            }
            
            // 2. 验证每个标签的数据完整性
            for (tagData in allTagData) {
                // 验证必填字段
                if (tagData.id <= 0) {
                    Log.e(TAG, "标签ID无效: ${tagData.name}")
                    return false
                }
                
                if (tagData.name.isBlank()) {
                    Log.e(TAG, "标签名称为空: ${tagData.id}")
                    return false
                }
                
                // 验证引用关系
                for (referencedTag in tagData.referencedTags) {
                    if (referencedTag.childTagId <= 0) {
                        Log.e(TAG, "引用标签ID无效: ${tagData.name} -> ${referencedTag.childTagId}")
                        return false
                    }
                }
                
                for (parentRef in tagData.parentReferences) {
                    if (parentRef.parentTagId <= 0) {
                        Log.e(TAG, "父引用标签ID无效: ${tagData.name} <- ${parentRef.parentTagId}")
                        return false
                    }
                }
            }
            
            Log.d(TAG, "标签数据迁移结果验证成功")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "标签数据迁移验证失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 清理数据库数据（可选）
     * @param context 上下文
     * @return 是否清理成功
     */
    suspend fun cleanupDatabase(context: Context): Boolean {
        try {
            Log.d(TAG, "开始清理数据库标签数据")
            
            val database = AppDatabase.get(context)
            val tagDao = database.tagDao()
            
            // 1. 先删除关联表数据
            // 这里不直接删除，而是保留数据库数据作为备份
            // 如果需要完全迁移，可以取消注释下面的代码
            
            // // 删除媒体标签关联
            // tagDao.deleteAllMediaTagCrossRef()
            // // 删除标签引用关系
            // tagDao.deleteAllTagReferences()
            // // 删除所有标签
            // tagDao.deleteAllTags()
            
            Log.d(TAG, "数据库标签数据清理完成")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "清理数据库标签数据失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 检查标签迁移是否已完成
     * @return 是否已完成
     */
    fun isTagMigrationCompleted(): Boolean {
        val migrationConfig = ConfigManager.readMigrationConfig()
        return migrationConfig.isTagMigrationCompleted
    }
    
    /**
     * 标记标签迁移完成
     * @return 是否标记成功
     */
    private fun markTagMigrationCompleted(): Boolean {
        val migrationConfig = ConfigManager.readMigrationConfig()
        migrationConfig.isTagMigrationCompleted = true
        return ConfigManager.writeMigrationConfig(migrationConfig)
    }
    
    /**
     * 重置迁移状态
     * @return 是否重置成功
     */
    fun resetMigrationStatus(): Boolean {
        val migrationConfig = ConfigManager.readMigrationConfig()
        migrationConfig.isTagMigrationCompleted = false
        return ConfigManager.writeMigrationConfig(migrationConfig)
    }
}
