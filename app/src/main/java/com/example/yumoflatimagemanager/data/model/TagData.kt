package com.example.yumoflatimagemanager.data.model

import com.example.yumoflatimagemanager.data.local.TagReferenceEntity

/**
 * 标签数据模型，用于文件存储
 * 包含标签基本信息、媒体关联、引用关系等完整数据
 */
data class TagData(
    var id: Long = 0,
    var parentId: Long? = null,
    var name: String,
    var sortOrder: Int = 0,  // 保留用于兼容性，将逐步废弃
    var referencedGroupSortOrder: Int = 0,  // 有引用标签的本体标签组的排序值
    var normalGroupSortOrder: Int = 0,      // 无引用标签的本体标签组的排序值
    var isExpanded: Boolean = true,  // 标签是否展开
    var imageCount: Int = 0,  // 标签包含的图片数量（包含引用标签）
    var directImageCount: Int = 0,  // 直接关联的图片数量
    var totalImageCount: Int = 0,   // 包含所有引用标签的图片总数
    var referencedCount: Int = 0,    // 被其他标签引用的次数
    var mediaPaths: MutableList<String> = mutableListOf(),  // 直接关联的媒体文件路径列表
    var referencedTags: MutableList<ReferencedTag> = mutableListOf(),  // 该标签引用的其他标签
    var parentReferences: MutableList<ParentReference> = mutableListOf()  // 引用该标签的其他标签
) {
    /**
     * 引用标签信息
     */
    data class ReferencedTag(
        var childTagId: Long,
        var sortOrder: Int = 0
    )

    /**
     * 父引用信息（被其他标签引用）
     */
    data class ParentReference(
        var parentTagId: Long,
        var sortOrder: Int = 0
    )
    
    /**
     * 转换为 TagEntity 对象
     */
    fun toTagEntity(): com.example.yumoflatimagemanager.data.local.TagEntity {
        return com.example.yumoflatimagemanager.data.local.TagEntity(
            id = id,
            parentId = parentId,
            name = name,
            sortOrder = sortOrder,
            referencedGroupSortOrder = referencedGroupSortOrder,
            normalGroupSortOrder = normalGroupSortOrder,
            isExpanded = isExpanded,
            imageCount = imageCount
        )
    }
}
