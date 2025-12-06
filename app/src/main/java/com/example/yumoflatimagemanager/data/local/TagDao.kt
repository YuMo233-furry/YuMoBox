package com.example.yumoflatimagemanager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
	@Query("SELECT * FROM tags ORDER BY sortOrder, name")
	fun getAllTags(): Flow<List<TagEntity>>
	
	@Query("SELECT * FROM tags ORDER BY sortOrder, name")
	suspend fun getAllTagsList(): List<TagEntity>

	@Query("SELECT * FROM tags WHERE id = :tagId")
	suspend fun getTagById(tagId: Long): TagEntity?

	@Query("SELECT * FROM tags WHERE parentId = :parentId ORDER BY sortOrder, name")
	suspend fun getTagsByParentId(parentId: Long): List<TagEntity>

	@Query("SELECT * FROM tags WHERE parentId IS NULL ORDER BY sortOrder, name")
	suspend fun getRootTags(): List<TagEntity>

	@Insert
	suspend fun insertTag(tag: TagEntity): Long

	@Update
	suspend fun updateTag(tag: TagEntity)

	@Delete
	suspend fun deleteTag(tag: TagEntity)

	@Query("DELETE FROM tags WHERE id = :tagId")
	suspend fun deleteTagById(tagId: Long)

	@Query("SELECT * FROM media_tag_cross_ref WHERE tagId = :tagId")
	suspend fun getMediaPathsByTagId(tagId: Long): List<MediaTagCrossRef>

	@Query("SELECT * FROM media_tag_cross_ref WHERE mediaPath = :mediaPath")
	suspend fun getTagsByMediaPath(mediaPath: String): List<MediaTagCrossRef>

	@Insert
	suspend fun insertMediaTagCrossRef(crossRef: MediaTagCrossRef)

	@Delete
	suspend fun deleteMediaTagCrossRef(crossRef: MediaTagCrossRef)

	@Query("DELETE FROM media_tag_cross_ref WHERE mediaPath = :mediaPath AND tagId = :tagId")
	suspend fun deleteMediaTagCrossRefByPathAndTag(mediaPath: String, tagId: Long)

	@Query("DELETE FROM media_tag_cross_ref WHERE mediaPath = :mediaPath")
	suspend fun deleteAllTagsFromMedia(mediaPath: String)

	@Query("UPDATE media_tag_cross_ref SET mediaPath = :newPath WHERE mediaPath = :oldPath")
	suspend fun updateMediaPath(oldPath: String, newPath: String)

	@Query("DELETE FROM media_tag_cross_ref WHERE tagId = :tagId")
	suspend fun deleteAllMediaFromTag(tagId: Long)

	// 获取标签及其引用标签
	@Transaction
	@Query("SELECT * FROM tags WHERE id = :tagId")
	suspend fun getTagWithChildren(tagId: Long): TagWithChildren?

	// 获取所有根标签及其引用标签
	@Transaction
	@Query("SELECT * FROM tags WHERE parentId IS NULL ORDER BY sortOrder, name")
	suspend fun getAllRootTagsWithChildren(): List<TagWithChildren>

	// 递归获取标签的所有后代标签ID（包括引用的标签）
	// 使用应用层循环检测，避免旧版SQLite的递归CTE问题
	suspend fun getAllDescendantTagIds(tagId: Long): List<Long> {
		val result = mutableSetOf<Long>()
		val visited = mutableSetOf<Long>()
		val queue = ArrayDeque<Long>()
		
		queue.add(tagId)
		visited.add(tagId)
		result.add(tagId) // 包括原始标签本身
		
		while (queue.isNotEmpty() && result.size < 1000) { // 限制总数量防止无限循环
			val current = queue.removeFirst()
			
			// 获取子标签（通过parentId）
			val children = getChildrenIds(current)
			for (child in children) {
				if (child !in visited) {
					result.add(child)
					visited.add(child)
					queue.add(child)
				}
			}
			
			// 获取引用标签（通过tag_references）
			val references = getTagReferencesByParentId(current)
			for (ref in references) {
				if (ref.childTagId !in visited) {
					result.add(ref.childTagId)
					visited.add(ref.childTagId)
					queue.add(ref.childTagId)
				}
			}
		}
		
		return result.toList()
	}

	// 标签引用关系操作
	@Insert
	suspend fun insertTagReference(reference: TagReferenceEntity)

	@Delete
	suspend fun deleteTagReference(reference: TagReferenceEntity)

	@Query("DELETE FROM tag_references WHERE parentTagId = :parentTagId AND childTagId = :childTagId")
	suspend fun deleteTagReferenceByIds(parentTagId: Long, childTagId: Long)

	@Query("SELECT * FROM tag_references WHERE parentTagId = :parentTagId ORDER BY sortOrder")
	suspend fun getTagReferencesByParentId(parentTagId: Long): List<TagReferenceEntity>

	@Query("SELECT * FROM tag_references WHERE childTagId = :childTagId")
	suspend fun getTagReferencesByChildId(childTagId: Long): List<TagReferenceEntity>

	// 更新引用标签排序
	@Query("UPDATE tag_references SET sortOrder = :sortOrder WHERE parentTagId = :parentTagId AND childTagId = :childTagId")
	suspend fun updateTagReferenceSort(parentTagId: Long, childTagId: Long, sortOrder: Int)

	// 更新标签展开状态
	@Query("UPDATE tags SET isExpanded = :isExpanded WHERE id = :tagId")
	suspend fun updateTagExpandedState(tagId: Long, isExpanded: Boolean)

	// 更新标签图片计数
	@Query("UPDATE tags SET imageCount = :count WHERE id = :tagId")
	suspend fun updateTagImageCount(tagId: Long, count: Int)

	// 获取标签统计信息
	@Query("""
		SELECT 
			t.id as tagId,
			COUNT(DISTINCT CASE WHEN m.mediaPath IS NOT NULL THEN m.mediaPath END) as directImageCount,
			0 as totalImageCount,  -- 将在Repository中计算
			COUNT(DISTINCT tr.parentTagId) as referencedCount
		FROM tags t
		LEFT JOIN media_tag_cross_ref m ON t.id = m.tagId
		LEFT JOIN tag_references tr ON t.id = tr.childTagId
		WHERE t.id = :tagId
		GROUP BY t.id
	""")
	suspend fun getTagStatistics(tagId: Long): TagStatistics?

	// 获取标签的直接图片数量
	@Query("SELECT COUNT(*) FROM media_tag_cross_ref WHERE tagId = :tagId")
	suspend fun getDirectImageCount(tagId: Long): Int

	// 获取标签的被引用数量
	@Query("SELECT COUNT(*) FROM tag_references WHERE childTagId = :tagId")
	suspend fun getTagReferencedCount(tagId: Long): Int

	// 检查标签是否存在循环引用
	@Query("""
		WITH RECURSIVE tag_tree AS (
			SELECT :childTagId as id, 0 as level
			UNION ALL
			SELECT tr.parentTagId, tt.level + 1
			FROM tag_references tr
			INNER JOIN tag_tree tt ON tr.childTagId = tt.id
			WHERE tt.level < 100  -- 防止无限递归
		)
		SELECT COUNT(*) FROM tag_tree WHERE id = :parentTagId
	""")
	suspend fun checkCircularReference(parentTagId: Long, childTagId: Long): Int

	// 原有方法保持兼容
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun addMediaTagCrossRef(ref: MediaTagCrossRef)

	@Query("DELETE FROM media_tag_cross_ref WHERE mediaPath = :mediaPath AND tagId = :tagId")
	suspend fun removeMediaTag(mediaPath: String, tagId: Long)

	@Query("SELECT EXISTS(SELECT 1 FROM media_tag_cross_ref WHERE mediaPath = :mediaPath AND tagId = :tagId)")
	suspend fun isMediaTagged(mediaPath: String, tagId: Long): Boolean

	@Query("SELECT COUNT(DISTINCT mediaPath) FROM media_tag_cross_ref WHERE mediaPath IN (:mediaPaths) AND tagId = :tagId")
	suspend fun countMediaWithTag(mediaPaths: List<String>, tagId: Long): Int

	@Query("UPDATE tags SET parentId = :newParentId WHERE id = :tagId")
	suspend fun updateParent(tagId: Long, newParentId: Long?)

	@Query("UPDATE tags SET sortOrder = :sortOrder WHERE id = :tagId")
	suspend fun updateSortOrder(tagId: Long, sortOrder: Int)
	
	// 新的两套独立排序方法
	@Query("UPDATE tags SET referencedGroupSortOrder = :sortOrder WHERE id = :tagId")
	suspend fun updateReferencedGroupSortOrder(tagId: Long, sortOrder: Int)
	
	@Query("UPDATE tags SET normalGroupSortOrder = :sortOrder WHERE id = :tagId")
	suspend fun updateNormalGroupSortOrder(tagId: Long, sortOrder: Int)

	@Query("SELECT id FROM tags WHERE parentId = :parentId ORDER BY sortOrder ASC, id ASC")
	suspend fun getChildrenIds(parentId: Long): List<Long>

	@Query("SELECT DISTINCT mediaPath FROM media_tag_cross_ref WHERE tagId IN (:tagIds)")
	suspend fun getMediaPathsByAnyTag(tagIds: List<Long>): List<String>

	@Query("SELECT DISTINCT mediaPath FROM media_tag_cross_ref")
	suspend fun getAllTaggedMediaPaths(): List<String>

	@Transaction
	@Query("""
		SELECT * FROM tags WHERE parentId IS NULL 
		ORDER BY 
			CASE 
				WHEN referencedGroupSortOrder > 0 THEN referencedGroupSortOrder
				ELSE normalGroupSortOrder + 1000000
			END ASC, 
			name ASC
	""")
	fun observeRootTags(): Flow<List<TagWithChildren>>

	@Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
	suspend fun getByName(name: String): TagEntity?
}


