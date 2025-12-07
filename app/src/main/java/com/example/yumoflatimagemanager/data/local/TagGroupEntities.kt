package com.example.yumoflatimagemanager.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 标签组实体类
 * 用于存储标签组的基本信息
 */
@Entity(tableName = "tag_groups")
data class TagGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false // 默认组（如"未分组"）不可删除
)

/**
 * 标签组与标签的多对多关联表
 * 用于存储标签与标签组的归属关系
 */
@Entity(
    tableName = "tag_group_tag_cross_ref",
    primaryKeys = ["tagGroupId", "tagId"],
    indices = [
        Index(value = ["tagGroupId"]),
        Index(value = ["tagId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TagGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagGroupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TagGroupTagCrossRef(
    val tagGroupId: Long,
    val tagId: Long
)

/**
 * 带标签列表的标签组数据类
 * 用于在UI中展示标签组及其包含的标签
 * 注意：这是一个关系类，用于@Transaction查询，直接包含TagGroupEntity的所有字段
 */
data class TagGroupWithTags(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TagGroupTagCrossRef::class,
            parentColumn = "tagGroupId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)
