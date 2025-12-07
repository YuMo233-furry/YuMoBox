package com.example.yumoflatimagemanager.data.repo

import com.example.yumoflatimagemanager.data.local.MediaTagCrossRef
import com.example.yumoflatimagemanager.data.local.TagDao
import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import com.example.yumoflatimagemanager.data.local.TagStatistics
import com.example.yumoflatimagemanager.data.local.TagReferenceEntity
import com.example.yumoflatimagemanager.data.model.TagData
import com.example.yumoflatimagemanager.data.model.TagFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/**
 * 标签及其引用关系排序值的数据类
 */
data class TagWithReferenceOrder(
    val tag: TagEntity,
    val referenceSortOrder: Int
)

interface TagRepository {
	fun observeRootTags(): Flow<List<TagWithChildren>>
	fun getAllTags(): Flow<List<TagEntity>>
	suspend fun getAllTagsList(): List<TagEntity>
	suspend fun createTag(name: String, parentId: Long? = null): Long
	suspend fun renameTag(id: Long, newName: String, parentId: Long? = null)
	suspend fun deleteTag(id: Long)
	suspend fun addTagToMedia(mediaPath: String, tagId: Long)
	suspend fun removeTagFromMedia(mediaPath: String, tagId: Long)
	suspend fun removeAllTagsFromMedia(mediaPath: String)
	suspend fun updateMediaPath(oldPath: String, newPath: String)
	suspend fun isMediaTagged(mediaPath: String, tagId: Long): Boolean
	suspend fun areAllMediaTagged(mediaPaths: List<String>, tagId: Long): Boolean
	suspend fun getMediaPathsByAnyTag(tagIds: List<Long>): List<String>
	suspend fun updateTagParent(tagId: Long, newParentId: Long?)
	suspend fun updateTagSort(tagId: Long, sortOrder: Int)
	suspend fun updateReferencedGroupSortOrder(tagId: Long, sortOrder: Int)
	suspend fun updateNormalGroupSortOrder(tagId: Long, sortOrder: Int)
	suspend fun getDescendantTagIds(tagId: Long): List<Long>
	suspend fun getAllTaggedMediaPaths(): List<String>
	
	// 新的标签功能
	suspend fun toggleTagExpanded(tagId: Long, isExpanded: Boolean)
	suspend fun addTagReference(parentTagId: Long, childTagId: Long): Boolean
	suspend fun removeTagReference(parentTagId: Long, childTagId: Long)
	suspend fun getTagReferences(parentTagId: Long): List<TagWithReferenceOrder>
	suspend fun getTagReferencesByChildId(childTagId: Long): List<TagReferenceEntity>
	suspend fun getTagStatistics(tagId: Long): TagStatistics
	suspend fun updateTagImageCount(tagId: Long)
	suspend fun getAllReferencedTagIds(tagId: Long): List<Long>
	suspend fun getTagById(tagId: Long): TagEntity?
	suspend fun getParentTagIds(tagId: Long): List<Long>
	suspend fun getTagsByParentId(parentId: Long): List<TagEntity>
	suspend fun updateTagReferenceSort(parentTagId: Long, childTagId: Long, sortOrder: Int)
	suspend fun checkAndFixSortOrderDuplicates(parentTagId: Long)
	suspend fun moveChildTag(parentTagId: Long, fromIndex: Int, toIndex: Int)
	suspend fun refreshTags()
}

/**
 * 基于数据库的TagRepository实现
 */
class TagRepositoryImpl(private val dao: TagDao) : TagRepository {
	override fun observeRootTags(): Flow<List<TagWithChildren>> = dao.observeRootTags()
	override fun getAllTags(): Flow<List<TagEntity>> = dao.getAllTags()
	override suspend fun getAllTagsList(): List<TagEntity> = dao.getAllTagsList()
	override suspend fun createTag(name: String, parentId: Long?): Long =
		dao.insertTag(TagEntity(name = name, parentId = parentId))
	override suspend fun renameTag(id: Long, newName: String, parentId: Long?) {
		// 先获取现有标签的完整信息，保持排序值不变
		val existingTag = dao.getTagById(id)
		if (existingTag != null) {
			dao.updateTag(existingTag.copy(name = newName, parentId = parentId))
		}
	}
	override suspend fun deleteTag(id: Long) {
		// 1. 删除标签与图片的所有关联
		dao.deleteAllMediaFromTag(id)
		
		// 2. 删除该标签的所有引用关系（作为父标签）
		val childReferences = dao.getTagReferencesByParentId(id)
		childReferences.forEach { ref ->
			dao.deleteTagReference(ref)
		}
		
		// 3. 删除该标签的所有被引用关系（作为引用标签）
		val parentReferences = dao.getTagReferencesByChildId(id)
		parentReferences.forEach { ref ->
			dao.deleteTagReference(ref)
		}
		
		// 4. 处理引用标签：将引用标签的父级设为null
		val childTags = dao.getTagsByParentId(id)
		childTags.forEach { childTag ->
			dao.updateParent(childTag.id, null)
		}
		
		// 5. 最后删除标签本身
		dao.deleteTagById(id)
	}
	override suspend fun addTagToMedia(mediaPath: String, tagId: Long) =
		dao.addMediaTagCrossRef(MediaTagCrossRef(mediaPath, tagId))
	override suspend fun removeTagFromMedia(mediaPath: String, tagId: Long) =
		dao.removeMediaTag(mediaPath, tagId)
	override suspend fun removeAllTagsFromMedia(mediaPath: String) =
		dao.deleteAllTagsFromMedia(mediaPath)
	override suspend fun updateMediaPath(oldPath: String, newPath: String) =
		dao.updateMediaPath(oldPath, newPath)
	override suspend fun isMediaTagged(mediaPath: String, tagId: Long): Boolean =
		dao.isMediaTagged(mediaPath, tagId)
	override suspend fun areAllMediaTagged(mediaPaths: List<String>, tagId: Long): Boolean {
		if (mediaPaths.isEmpty()) return false
		val count = dao.countMediaWithTag(mediaPaths, tagId)
		return count == mediaPaths.size
	}

	override suspend fun getMediaPathsByAnyTag(tagIds: List<Long>): List<String> =
		if (tagIds.isEmpty()) emptyList() else dao.getMediaPathsByAnyTag(tagIds)

	override suspend fun updateTagParent(tagId: Long, newParentId: Long?) = dao.updateParent(tagId, newParentId)

	override suspend fun updateTagSort(tagId: Long, sortOrder: Int) = dao.updateSortOrder(tagId, sortOrder)
	
	override suspend fun updateReferencedGroupSortOrder(tagId: Long, sortOrder: Int) =
		dao.updateReferencedGroupSortOrder(tagId, sortOrder)
	
	override suspend fun updateNormalGroupSortOrder(tagId: Long, sortOrder: Int) =
		dao.updateNormalGroupSortOrder(tagId, sortOrder)

	// 递归获取所有子孙标签id（包括引用的标签）
	override suspend fun getDescendantTagIds(tagId: Long): List<Long> {
		return dao.getAllDescendantTagIds(tagId)
	}
	
	// 获取所有被引用的标签ID（使用应用层循环检测）
	override suspend fun getAllReferencedTagIds(tagId: Long): List<Long> {
		val result = mutableSetOf<Long>()
		val visited = mutableSetOf<Long>()
		val queue = ArrayDeque<Long>()
		
		queue.add(tagId)
		visited.add(tagId)
		
		while (queue.isNotEmpty() && result.size < 1000) {
			val current = queue.removeFirst()
			
			// 获取子标签（通过parentId）
			val children = dao.getChildrenIds(current)
			for (child in children) {
				if (child !in visited) {
					result.add(child)
					visited.add(child)
					queue.add(child)
				}
			}
			
			// 获取引用标签（通过tag_references）
			val references = dao.getTagReferencesByParentId(current)
			for (ref in references) {
				if (ref.childTagId !in visited) {
					result.add(ref.childTagId)
					visited.add(ref.childTagId)
					queue.add(ref.childTagId)
				}
			}
		}
		
		// 返回包括原始标签在内的所有相关标签ID
		return if (result.isEmpty()) {
			listOf(tagId) // 如果没有引用关系，至少返回原始标签本身
		} else {
			result.toList()
		}
	}

	// 获取所有已打标签的媒体路径，用于计算未分类图片
	override suspend fun getAllTaggedMediaPaths(): List<String> {
		return dao.getAllTaggedMediaPaths()
	}
	
	// 切换标签展开状态
	override suspend fun toggleTagExpanded(tagId: Long, isExpanded: Boolean) {
		dao.updateTagExpandedState(tagId, isExpanded)
	}
	
	// 添加标签引用（支持多对多关系）
	override suspend fun addTagReference(parentTagId: Long, childTagId: Long): Boolean {
		// 检查是否已存在
		val existing = dao.getTagReferencesByParentId(parentTagId)
		if (existing.any { it.childTagId == childTagId }) {
			return true // 已存在，返回成功
		}
		
		dao.insertTagReference(TagReferenceEntity(parentTagId, childTagId))
		return true
	}
	
	// 移除标签引用
	override suspend fun removeTagReference(parentTagId: Long, childTagId: Long) {
		dao.deleteTagReferenceByIds(parentTagId, childTagId)
	}
	
	// 获取标签的引用标签（包含引用关系的排序值）
	override suspend fun getTagReferences(parentTagId: Long): List<TagWithReferenceOrder> {
		val references = dao.getTagReferencesByParentId(parentTagId)
		return references.mapNotNull { ref ->
			dao.getTagById(ref.childTagId)?.let { tag ->
				TagWithReferenceOrder(tag, ref.sortOrder)
			}
		}
	}
	
	// 获取某个标签的所有父引用
	override suspend fun getTagReferencesByChildId(childTagId: Long): List<TagReferenceEntity> {
		return dao.getTagReferencesByChildId(childTagId)
	}
	
	// 获取标签的所有父标签和祖宗标签（使用应用层循环检测）
	override suspend fun getParentTagIds(tagId: Long): List<Long> {
		val result = mutableListOf<Long>()
		val visited = mutableSetOf<Long>()
		val queue = ArrayDeque<Long>()
		
		queue.add(tagId)
		visited.add(tagId)
		
		while (queue.isNotEmpty() && result.size < 1000) {
			val current = queue.removeFirst()
			
			// 获取直接引用当前标签的父标签
			val parentReferences = dao.getTagReferencesByChildId(current)
			for (ref in parentReferences) {
				if (ref.parentTagId !in visited) {
					result.add(ref.parentTagId)
					visited.add(ref.parentTagId)
					queue.add(ref.parentTagId)
				}
			}
			
			// 获取通过层级关系（parentId）的父标签
			val tag = dao.getTagById(current)
			if (tag?.parentId != null && tag.parentId !in visited) {
				result.add(tag.parentId)
				visited.add(tag.parentId)
				queue.add(tag.parentId)
			}
		}
		
		return result.distinct()
	}
	
	// 获取标签统计信息
	override suspend fun getTagStatistics(tagId: Long): TagStatistics {
		val directCount = dao.getDirectImageCount(tagId)
		val referencedCount = dao.getTagReferencedCount(tagId)
		val descendantIds = getDescendantTagIds(tagId)
		
		// 计算总图片数量（去重）- 包括标签自身和所有后代
		val allMediaPaths = mutableSetOf<String>()
		
		// 添加所有相关标签的媒体路径（包括原始标签和后代）
		for (tagIdToProcess in descendantIds) {
			val mediaPaths = dao.getMediaPathsByTagId(tagIdToProcess).map { it.mediaPath }
			allMediaPaths.addAll(mediaPaths)
		}
		
		return TagStatistics(
			tagId = tagId,
			directImageCount = directCount,
			totalImageCount = allMediaPaths.size,
			referencedCount = referencedCount
		)
	}
	
	// 更新标签图片计数
	override suspend fun updateTagImageCount(tagId: Long) {
		val statistics = getTagStatistics(tagId)
		dao.updateTagImageCount(tagId, statistics.totalImageCount)
	}
	
	// 根据ID获取标签
	override suspend fun getTagById(tagId: Long): TagEntity? {
		return dao.getTagById(tagId)
	}
	
	// 根据父标签ID获取引用标签列表
	override suspend fun getTagsByParentId(parentId: Long): List<TagEntity> {
		return dao.getTagsByParentId(parentId)
	}
	
	override suspend fun updateTagReferenceSort(parentTagId: Long, childTagId: Long, sortOrder: Int) {
		println("DEBUG [TagRepository]: 更新引用关系排序 - 父标签ID=$parentTagId, 子标签ID=$childTagId, 新排序值=$sortOrder")
		dao.updateTagReferenceSort(parentTagId, childTagId, sortOrder)
		println("DEBUG [TagRepository]: 引用关系排序更新完成")
	}
	
	override suspend fun checkAndFixSortOrderDuplicates(parentTagId: Long) {
		val referencedTags = dao.getTagReferencesByParentId(parentTagId).sortedBy { it.sortOrder }
		
		// 检查是否有重复的排序值
		val sortOrders = referencedTags.map { it.sortOrder }
		val hasDuplicates = sortOrders.size != sortOrders.distinct().size
		
		if (hasDuplicates) {
			println("DEBUG: 发现排序值重复，开始修复...")
			// 重新分配排序值
			referencedTags.forEachIndexed { index, refTag ->
				val newSortOrder = (index + 1) * 1000
				dao.updateTagReferenceSort(parentTagId, refTag.childTagId, newSortOrder)
				println("DEBUG: 修复引用标签排序值为 $newSortOrder")
			}
		}
	}
	
	override suspend fun moveChildTag(parentTagId: Long, fromIndex: Int, toIndex: Int) {
		val references = dao.getTagReferencesByParentId(parentTagId).sortedBy { it.sortOrder }
		if (fromIndex < 0 || fromIndex >= references.size || toIndex < 0 || toIndex >= references.size) {
			return
		}
		
		// 重新排列引用关系
		val mutableRefs = references.toMutableList()
		val movedRef = mutableRefs.removeAt(fromIndex)
		mutableRefs.add(toIndex, movedRef)
		
		// 更新排序值
		mutableRefs.forEachIndexed { index, ref ->
			val newSortOrder = (index + 1) * 1000
			dao.updateTagReferenceSort(parentTagId, ref.childTagId, newSortOrder)
		}
	}
	
	override suspend fun refreshTags() {
		// 刷新标签数据，可以在这里添加缓存清理等逻辑
		println("DEBUG: 刷新标签数据")
	}
}

/**
 * 基于文件存储的TagRepository实现
 */
class FileTagRepositoryImpl(private val dao: TagDao) : TagRepository {
    
    // 计数器，用于生成新标签ID
    private var tagIdCounter: Long = 0L
    
    override fun observeRootTags(): Flow<List<TagWithChildren>> = flow {
        withContext(Dispatchers.IO) {
            val allTags = TagFileManager.getAllTags()
            val rootTags = allTags.filter { it.parentId == null }
            
            // 将文件存储的TagData转换为数据库模型的TagWithChildren
            val tagWithChildrenList = rootTags.mapNotNull { tagData ->
                val tagEntity = tagData.toTagEntity()
                val children = allTags.filter { it.parentId == tagData.id }.map { it.toTagEntity() }
                val referencedTags = tagData.referencedTags.map { ref ->
                    TagReferenceEntity(
                        parentTagId = tagData.id,
                        childTagId = ref.childTagId,
                        sortOrder = ref.sortOrder
                    )
                }
                
                TagWithChildren(tagEntity, children, referencedTags)
            }
            
            emit(tagWithChildrenList)
        }
    }
    
    override fun getAllTags(): Flow<List<TagEntity>> = flow {
        withContext(Dispatchers.IO) {
            val allTags = TagFileManager.getAllTags()
            emit(allTags.map { it.toTagEntity() })
        }
    }
    
    override suspend fun getAllTagsList(): List<TagEntity> = withContext(Dispatchers.IO) {
        TagFileManager.getAllTags().map { it.toTagEntity() }
    }
    
    override suspend fun createTag(name: String, parentId: Long?): Long = withContext(Dispatchers.IO) {
        // 获取所有标签，找到最大ID
        val allTags = TagFileManager.getAllTags()
        tagIdCounter = allTags.maxByOrNull { it.id }?.id?.plus(1) ?: 1L
        
        val newTagData = TagData(
            id = tagIdCounter,
            name = name,
            parentId = parentId
        )
        
        TagFileManager.writeTag(newTagData)
        tagIdCounter
    }
    
    override suspend fun renameTag(id: Long, newName: String, parentId: Long?) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(id)
        if (tagData != null) {
            tagData.name = newName
            tagData.parentId = parentId
            TagFileManager.writeTag(tagData)
        }
    }
    
    override suspend fun deleteTag(id: Long) {
        withContext(Dispatchers.IO) {
            // 1. 获取所有标签
            val allTags = TagFileManager.getAllTags()
            
            // 2. 遍历所有标签，更新引用关系
            for (tagData in allTags) {
                // 2.1 删除该标签的所有引用关系（作为父标签）
                tagData.referencedTags.removeIf { it.childTagId == id }
                
                // 2.2 删除该标签的所有被引用关系（作为引用标签）
                tagData.parentReferences.removeIf { it.parentTagId == id }
                
                // 2.3 处理子标签：将子标签的父级设为null
                if (tagData.parentId == id) {
                    tagData.parentId = null
                }
                
                // 2.4 更新标签
                TagFileManager.writeTag(tagData)
            }
            
            // 3. 最后删除标签本身
            TagFileManager.deleteTag(id)
        }
    }
    
    override suspend fun addTagToMedia(mediaPath: String, tagId: Long) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        if (tagData != null && !tagData.mediaPaths.contains(mediaPath)) {
            tagData.mediaPaths.add(mediaPath)
            tagData.directImageCount = tagData.mediaPaths.size
            TagFileManager.writeTag(tagData)
        }
    }
    
    override suspend fun removeTagFromMedia(mediaPath: String, tagId: Long) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        if (tagData != null) {
            tagData.mediaPaths.remove(mediaPath)
            tagData.directImageCount = tagData.mediaPaths.size
            TagFileManager.writeTag(tagData)
        }
    }
    
    override suspend fun removeAllTagsFromMedia(mediaPath: String) = withContext(Dispatchers.IO) {
        val allTags = TagFileManager.getAllTags()
        for (tagData in allTags) {
            if (tagData.mediaPaths.contains(mediaPath)) {
                tagData.mediaPaths.remove(mediaPath)
                tagData.directImageCount = tagData.mediaPaths.size
                TagFileManager.writeTag(tagData)
            }
        }
    }
    
    override suspend fun updateMediaPath(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        val allTags = TagFileManager.getAllTags()
        for (tagData in allTags) {
            if (tagData.mediaPaths.contains(oldPath)) {
                tagData.mediaPaths.remove(oldPath)
                tagData.mediaPaths.add(newPath)
                TagFileManager.writeTag(tagData)
            }
        }
    }
    
    override suspend fun isMediaTagged(mediaPath: String, tagId: Long): Boolean = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        tagData?.mediaPaths?.contains(mediaPath) ?: false
    }
    
    override suspend fun areAllMediaTagged(mediaPaths: List<String>, tagId: Long): Boolean = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        tagData?.mediaPaths?.let { tagMediaPaths ->
            mediaPaths.all { tagMediaPaths.contains(it) }
        } ?: false
    }
    
    override suspend fun getMediaPathsByAnyTag(tagIds: List<Long>): List<String> = withContext(Dispatchers.IO) {
        if (tagIds.isEmpty()) return@withContext emptyList()
        
        val allMediaPaths = mutableSetOf<String>()
        val allTags = TagFileManager.getAllTags()
        
        for (tagId in tagIds) {
            val tagData = allTags.find { it.id == tagId }
            tagData?.mediaPaths?.let { allMediaPaths.addAll(it) }
        }
        
        allMediaPaths.toList()
    }
    
    override suspend fun updateTagParent(tagId: Long, newParentId: Long?) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        if (tagData != null) {
            tagData.parentId = newParentId
            TagFileManager.writeTag(tagData)
        }
    }
    
    override suspend fun updateTagSort(tagId: Long, sortOrder: Int) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        if (tagData != null) {
            tagData.sortOrder = sortOrder
            TagFileManager.writeTag(tagData)
        }
    }
    
    override suspend fun updateReferencedGroupSortOrder(tagId: Long, sortOrder: Int) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        if (tagData != null) {
            tagData.referencedGroupSortOrder = sortOrder
            TagFileManager.writeTag(tagData)
        }
    }
    
    override suspend fun updateNormalGroupSortOrder(tagId: Long, sortOrder: Int) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        if (tagData != null) {
            tagData.normalGroupSortOrder = sortOrder
            TagFileManager.writeTag(tagData)
        }
    }
    
    // 递归获取所有子孙标签id（包括引用的标签）
    override suspend fun getDescendantTagIds(tagId: Long): List<Long> = withContext(Dispatchers.IO) {
        val result = mutableSetOf<Long>()
        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()
        
        queue.add(tagId)
        visited.add(tagId)
        result.add(tagId)
        
        val allTags = TagFileManager.getAllTags()
        
        while (queue.isNotEmpty() && result.size < 1000) {
            val current = queue.removeFirst()
            
            // 获取子标签（通过parentId）
            val children = allTags.filter { it.parentId == current }.map { it.id }
            for (child in children) {
                if (child !in visited) {
                    result.add(child)
                    visited.add(child)
                    queue.add(child)
                }
            }
            
            // 获取引用标签（通过tag_references）
            val tagData = allTags.find { it.id == current }
            val references = tagData?.referencedTags?.map { it.childTagId } ?: emptyList()
            for (refId in references) {
                if (refId !in visited) {
                    result.add(refId)
                    visited.add(refId)
                    queue.add(refId)
                }
            }
        }
        
        result.toList()
    }
    
    // 获取所有被引用的标签ID（使用应用层循环检测）
    override suspend fun getAllReferencedTagIds(tagId: Long): List<Long> = withContext(Dispatchers.IO) {
        val result = mutableSetOf<Long>()
        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()
        
        queue.add(tagId)
        visited.add(tagId)
        
        val allTags = TagFileManager.getAllTags()
        
        while (queue.isNotEmpty() && result.size < 1000) {
            val current = queue.removeFirst()
            
            // 获取子标签（通过parentId）
            val children = allTags.filter { it.parentId == current }.map { it.id }
            for (child in children) {
                if (child !in visited) {
                    result.add(child)
                    visited.add(child)
                    queue.add(child)
                }
            }
            
            // 获取引用标签（通过referencedTags）
            val tagData = allTags.find { it.id == current }
            val references = tagData?.referencedTags?.map { it.childTagId } ?: emptyList()
            for (refId in references) {
                if (refId !in visited) {
                    result.add(refId)
                    visited.add(refId)
                    queue.add(refId)
                }
            }
        }
        
        // 返回包括原始标签在内的所有相关标签ID
        return@withContext if (result.isEmpty()) {
            listOf(tagId) // 如果没有引用关系，至少返回原始标签本身
        } else {
            result.toList()
        }
    }
    
    // 获取所有已打标签的媒体路径，用于计算未分类图片
    override suspend fun getAllTaggedMediaPaths(): List<String> = withContext(Dispatchers.IO) {
        val allTags = TagFileManager.getAllTags()
        val allMediaPaths = mutableSetOf<String>()
        
        for (tagData in allTags) {
            allMediaPaths.addAll(tagData.mediaPaths)
        }
        
        allMediaPaths.toList()
    }
    
    // 切换标签展开状态
    override suspend fun toggleTagExpanded(tagId: Long, isExpanded: Boolean) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        if (tagData != null) {
            tagData.isExpanded = isExpanded
            TagFileManager.writeTag(tagData)
        }
    }
    
    // 添加标签引用（支持多对多关系）
    override suspend fun addTagReference(parentTagId: Long, childTagId: Long): Boolean = withContext(Dispatchers.IO) {
        val parentTagData = TagFileManager.readTag(parentTagId)
        val childTagData = TagFileManager.readTag(childTagId)
        
        if (parentTagData == null || childTagData == null) {
            return@withContext false
        }
        
        // 检查是否已存在
        if (parentTagData.referencedTags.any { it.childTagId == childTagId }) {
            return@withContext true // 已存在，返回成功
        }
        
        // 添加引用关系到父标签
        parentTagData.referencedTags.add(TagData.ReferencedTag(childTagId, parentTagData.referencedTags.size * 1000))
        
        // 添加父引用关系到子标签
        childTagData.parentReferences.add(TagData.ParentReference(parentTagId, childTagData.parentReferences.size * 1000))
        
        // 写入文件
        TagFileManager.writeTag(parentTagData)
        TagFileManager.writeTag(childTagData)
        
        return@withContext true
    }
    
    // 移除标签引用
    override suspend fun removeTagReference(parentTagId: Long, childTagId: Long) = withContext(Dispatchers.IO) {
        val parentTagData = TagFileManager.readTag(parentTagId)
        val childTagData = TagFileManager.readTag(childTagId)
        
        if (parentTagData != null && childTagData != null) {
            // 从父标签移除引用关系
            parentTagData.referencedTags.removeIf { it.childTagId == childTagId }
            
            // 从子标签移除父引用关系
            childTagData.parentReferences.removeIf { it.parentTagId == parentTagId }
            
            // 写入文件
            TagFileManager.writeTag(parentTagData)
            TagFileManager.writeTag(childTagData)
        }
    }
    
    // 获取标签的引用标签（包含引用关系的排序值）
    override suspend fun getTagReferences(parentTagId: Long): List<TagWithReferenceOrder> = withContext(Dispatchers.IO) {
        val allTags = TagFileManager.getAllTags()
        val parentTagData = allTags.find { it.id == parentTagId }
        
        parentTagData?.let { tagData ->
            tagData.referencedTags.mapNotNull { ref ->
                val childTagData = allTags.find { it.id == ref.childTagId }
                childTagData?.let { childTag ->
                    TagWithReferenceOrder(childTag.toTagEntity(), ref.sortOrder)
                }
            }
        } ?: emptyList()
    }
    
    // 获取某个标签的所有父引用
    override suspend fun getTagReferencesByChildId(childTagId: Long): List<TagReferenceEntity> = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(childTagId)
        
        tagData?.let { tag ->
            tag.parentReferences.map { parentRef ->
                TagReferenceEntity(
                    parentTagId = parentRef.parentTagId,
                    childTagId = childTagId,
                    sortOrder = parentRef.sortOrder
                )
            }
        } ?: emptyList()
    }
    
    // 获取标签的所有父标签和祖宗标签（使用应用层循环检测）
    override suspend fun getParentTagIds(tagId: Long): List<Long> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Long>()
        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()
        
        queue.add(tagId)
        visited.add(tagId)
        
        val allTags = TagFileManager.getAllTags()
        
        while (queue.isNotEmpty() && result.size < 1000) {
            val current = queue.removeFirst()
            
            val tagData = allTags.find { it.id == current }
            
            // 获取直接引用当前标签的父标签
            tagData?.parentReferences?.forEach { parentRef ->
                if (parentRef.parentTagId !in visited) {
                    result.add(parentRef.parentTagId)
                    visited.add(parentRef.parentTagId)
                    queue.add(parentRef.parentTagId)
                }
            }
            
            // 获取通过层级关系（parentId）的父标签
            val parentId = tagData?.parentId
            if (parentId != null && parentId !in visited) {
                result.add(parentId)
                visited.add(parentId)
                queue.add(parentId)
            }
        }
        
        result.distinct()
    }
    
    // 获取标签统计信息
    override suspend fun getTagStatistics(tagId: Long): TagStatistics = withContext(Dispatchers.IO) {
        val allTags = TagFileManager.getAllTags()
        val tagData = allTags.find { it.id == tagId }
        
        tagData?.let { tag ->
            // 计算直接图片数量
            val directCount = tag.mediaPaths.size
            
            // 计算被引用次数
            val referencedCount = tag.parentReferences.size
            
            // 计算总图片数量（去重）- 包括标签自身和所有后代
            val descendantIds = getDescendantTagIds(tagId)
            val allMediaPaths = mutableSetOf<String>()
            
            for (tagIdToProcess in descendantIds) {
                val descendantTagData = allTags.find { it.id == tagIdToProcess }
                descendantTagData?.mediaPaths?.let { allMediaPaths.addAll(it) }
            }
            
            TagStatistics(
                tagId = tagId,
                directImageCount = directCount,
                totalImageCount = allMediaPaths.size,
                referencedCount = referencedCount
            )
        } ?: TagStatistics(tagId = tagId, directImageCount = 0, totalImageCount = 0, referencedCount = 0)
    }
    
    // 更新标签图片计数
    override suspend fun updateTagImageCount(tagId: Long) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(tagId)
        if (tagData != null) {
            val statistics = getTagStatistics(tagId)
            tagData.imageCount = statistics.totalImageCount
            tagData.directImageCount = statistics.directImageCount
            TagFileManager.writeTag(tagData)
        }
    }
    
    // 根据ID获取标签
    override suspend fun getTagById(tagId: Long): TagEntity? = withContext(Dispatchers.IO) {
        TagFileManager.readTag(tagId)?.toTagEntity()
    }
    
    // 根据父标签ID获取引用标签列表
    override suspend fun getTagsByParentId(parentId: Long): List<TagEntity> = withContext(Dispatchers.IO) {
        TagFileManager.getAllTags()
            .filter { it.parentId == parentId }
            .map { it.toTagEntity() }
    }
    
    override suspend fun updateTagReferenceSort(parentTagId: Long, childTagId: Long, sortOrder: Int) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(parentTagId)
        if (tagData != null) {
            val referencedTag = tagData.referencedTags.find { it.childTagId == childTagId }
            referencedTag?.sortOrder = sortOrder
            TagFileManager.writeTag(tagData)
        }
    }
    
    override suspend fun checkAndFixSortOrderDuplicates(parentTagId: Long) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(parentTagId)
        if (tagData != null) {
            val referencedTags = tagData.referencedTags.sortedBy { it.sortOrder }
            
            // 检查是否有重复的排序值
            val sortOrders = referencedTags.map { it.sortOrder }
            val hasDuplicates = sortOrders.size != sortOrders.distinct().size
            
            if (hasDuplicates) {
                // 重新分配排序值
                referencedTags.forEachIndexed { index, refTag ->
                    refTag.sortOrder = (index + 1) * 1000
                }
                TagFileManager.writeTag(tagData)
            }
        }
    }
    
    override suspend fun moveChildTag(parentTagId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val tagData = TagFileManager.readTag(parentTagId)
        if (tagData != null) {
            val referencedTags = tagData.referencedTags.sortedBy { it.sortOrder }.toMutableList()
            if (fromIndex < 0 || fromIndex >= referencedTags.size || toIndex < 0 || toIndex >= referencedTags.size) {
                return@withContext
            }
            
            // 重新排列引用关系
            val movedRef = referencedTags.removeAt(fromIndex)
            referencedTags.add(toIndex, movedRef)
            
            // 更新排序值
            referencedTags.forEachIndexed { index, ref ->
                ref.sortOrder = (index + 1) * 1000
            }
            
            tagData.referencedTags = referencedTags
            TagFileManager.writeTag(tagData)
        }
    }
    
    override suspend fun refreshTags() = withContext(Dispatchers.IO) {
        // 刷新标签数据，清理缓存
        TagFileManager.clearCache()
    }
    
    /**
     * 将文件存储的TagData转换为数据库模型的TagEntity
     */
    private fun TagData.toTagEntity(): TagEntity {
        return TagEntity(
            id = this.id,
            parentId = this.parentId,
            name = this.name,
            sortOrder = this.sortOrder,
            referencedGroupSortOrder = this.referencedGroupSortOrder,
            normalGroupSortOrder = this.normalGroupSortOrder,
            isExpanded = this.isExpanded,
            imageCount = this.imageCount
        )
    }
}


