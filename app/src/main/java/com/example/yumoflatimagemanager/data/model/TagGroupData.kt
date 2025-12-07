package com.example.yumoflatimagemanager.data.model

import com.example.yumoflatimagemanager.data.local.TagGroupEntity

/**
 * 标签组数据类，用于表示标签组数据，包含标签组的基本信息和关联的标签ID列表
 * 用于JSON序列化和反序列化
 */
data class TagGroupData(
    /** 标签组ID */
    val id: Long,
    /** 标签组名称 */
    val name: String,
    /** 标签组排序值 */
    val sortOrder: Int = 0,
    /** 是否为默认标签组 */
    val isDefault: Boolean = false,
    /** 标签组关联的标签ID列表 */
    val tagIds: List<Long> = emptyList()
) {
    /**
     * 转换为数据库模型的TagGroupEntity
     * @return TagGroupEntity对象
     */
    fun toTagGroupEntity(): TagGroupEntity {
        return TagGroupEntity(
            id = id,
            name = name,
            sortOrder = sortOrder,
            isDefault = isDefault
        )
    }
    
    /**
     * 创建包含新标签ID的TagGroupData副本
     * @param tagId 要添加的标签ID
     * @return 包含新标签ID的TagGroupData副本
     */
    fun withAddedTag(tagId: Long): TagGroupData {
        if (tagIds.contains(tagId)) {
            return this
        }
        return copy(tagIds = tagIds + tagId)
    }
    
    /**
     * 创建移除指定标签ID的TagGroupData副本
     * @param tagId 要移除的标签ID
     * @return 移除指定标签ID的TagGroupData副本
     */
    fun withRemovedTag(tagId: Long): TagGroupData {
        if (!tagIds.contains(tagId)) {
            return this
        }
        return copy(tagIds = tagIds - tagId)
    }
}

/**
 * 从数据库模型的TagGroupEntity转换为TagGroupData
 * @param tagIds 标签组关联的标签ID列表
 * @return TagGroupData对象
 */
fun TagGroupEntity.toTagGroupData(tagIds: List<Long> = emptyList()): TagGroupData {
    return TagGroupData(
        id = id,
        name = name,
        sortOrder = sortOrder,
        isDefault = isDefault,
        tagIds = tagIds
    )
}
