package com.example.yumoflatimagemanager.feature.tag.model

import com.example.yumoflatimagemanager.data.local.TagEntity
import com.example.yumoflatimagemanager.data.local.TagReferenceEntity

/**
 * 标签相关的数据模型类
 */

/**
 * 缓存被删除标签的数据类
 * 用于实现撤回删除功能
 */
data class DeletedTagCache(
    val tag: TagEntity,
    val associatedMediaPaths: List<String>,
    val childReferences: List<TagReferenceEntity>,
    val parentReferences: List<TagReferenceEntity>,
    val childTags: List<TagEntity>
)

/**
 * 缓存被删除的标签组，用于撤回
 */
data class DeletedTagGroupCache(
    val tagGroupData: com.example.yumoflatimagemanager.data.model.TagGroupData,
    val previousSelectedGroupId: Long?
)

/**
 * 批量操作结果
 */
data class BatchResult(
    val successCount: Int,
    val failureCount: Int
)

