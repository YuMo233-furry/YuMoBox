package com.example.yumoflatimagemanager.data

import java.io.Serializable

/**
 * 排序类型枚举，表示不同的排序方式
 */
enum class SortType {
    CAPTURE_TIME,    // 按拍摄时间
    MODIFY_TIME,     // 按修改时间
    CREATE_TIME,     // 按创建时间
    NAME,            // 按名称
    SIZE,            // 按大小
    IMAGE_COUNT      // 按图片数量
}

/**
 * 排序方向枚举
 */
enum class SortDirection {
    ASCENDING,       // 正序
    DESCENDING       // 倒序
}

/**
 * 排序配置数据类，包含排序类型和方向
 */
data class SortConfig(
    val type: SortType = SortType.CAPTURE_TIME,
    val direction: SortDirection = SortDirection.DESCENDING
) : Serializable