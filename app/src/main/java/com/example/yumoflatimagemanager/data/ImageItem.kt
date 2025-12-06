package com.example.yumoflatimagemanager.data

import android.net.Uri
import java.io.Serializable

/**
 * 图片/视频项目数据模型
 */
data class ImageItem(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean = false,
    val albumId: String,
    val name: String? = null,
    // 添加排序相关的字段
    val captureTime: Long = 0L,       // 拍摄时间
    val modifyTime: Long = 0L,        // 修改时间
    val createTime: Long = 0L,        // 创建时间
    val size: Long = 0L               // 文件大小（字节）
) : Serializable {
    // 创建一个用于缓存的版本，不包含Uri
    fun toCacheable(): ImageItemCacheable {
        return ImageItemCacheable(
            id = id,
            uriString = uri.toString(),
            isVideo = isVideo,
            albumId = albumId,
            name = name,
            captureTime = captureTime,
            modifyTime = modifyTime,
            createTime = createTime,
            size = size
        )
    }
    
    companion object {
        fun fromCacheable(cacheable: ImageItemCacheable): ImageItem {
            return ImageItem(
                id = cacheable.id,
                uri = Uri.parse(cacheable.uriString),
                isVideo = cacheable.isVideo,
                albumId = cacheable.albumId,
                name = cacheable.name,
                captureTime = cacheable.captureTime,
                modifyTime = cacheable.modifyTime,
                createTime = cacheable.createTime,
                size = cacheable.size
            )
        }
    }
}

// 用于缓存的图片数据类，不包含Uri对象
data class ImageItemCacheable(
    val id: String,
    val uriString: String,
    val isVideo: Boolean = false,
    val albumId: String,
    val name: String? = null,
    val captureTime: Long = 0L,
    val modifyTime: Long = 0L,
    val createTime: Long = 0L,
    val size: Long = 0L
) : Serializable