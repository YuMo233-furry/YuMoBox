package com.example.yumoflatimagemanager.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "tags")
data class TagEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val parentId: Long? = null,
    val name: String,
    val sortOrder: Int = 0,  // 保留用于兼容性，将逐步废弃
    val referencedGroupSortOrder: Int = 0,  // 有引用标签的本体标签组的排序值
    val normalGroupSortOrder: Int = 0,      // 无引用标签的本体标签组的排序值
    val isExpanded: Boolean = true,  // 标签是否展开
    val imageCount: Int = 0  // 标签包含的图片数量（包含引用标签）
)

@Entity(
	tableName = "media_tag_cross_ref",
	primaryKeys = ["mediaPath", "tagId"],
	indices = [Index("tagId")]
)
data class MediaTagCrossRef(
	val mediaPath: String,
	val tagId: Long
)

// 标签引用关系表，支持多对多标签关系（一个标签可以引用多个其他标签作为引用标签）
@Entity(
	tableName = "tag_references",
	primaryKeys = ["parentTagId", "childTagId"],
	indices = [Index("childTagId")]
)
data class TagReferenceEntity(
	val parentTagId: Long,  // 父标签ID
	val childTagId: Long,   // 引用标签ID（被引用的标签）
	val sortOrder: Int = 0  // 引用标签的排序值
)

data class TagWithChildren(
	@Embedded val tag: TagEntity,
	@Relation(parentColumn = "id", entityColumn = "parentId")
	val children: List<TagEntity>,
	@Relation(
		parentColumn = "id",
		entityColumn = "parentTagId",
		entity = TagReferenceEntity::class
	)
	val referencedTags: List<TagReferenceEntity>  // 该标签引用的其他标签（作为父）
)

// 标签统计信息
data class TagStatistics(
	val tagId: Long,
	val directImageCount: Int,  // 直接关联的图片数量
	val totalImageCount: Int,   // 包含所有引用标签的图片总数
	val referencedCount: Int    // 被其他标签引用的次数
)


