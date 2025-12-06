package com.example.yumoflatimagemanager.data

import android.net.Uri
import androidx.annotation.DrawableRes
import com.example.yumoflatimagemanager.data.SortConfig
import java.io.Serializable

/**
 * 相册数据模型
 */
data class Album(
    val id: String,
    val name: String,
    @DrawableRes val coverImage: Int,
    val coverUri: Uri? = null,
    val count: Int,
    val type: AlbumType,
    val sortConfig: SortConfig = SortConfig(), // 添加排序配置，默认使用标准配置
    val images: List<ImageItem> = emptyList(), // 添加相册中的图片列表
    val isPrivate: Boolean = false // 标识相册是否为私密相册
) : Serializable {
    // 创建一个用于缓存的版本，不包含Uri
    fun toCacheable(): AlbumCacheable {
        return AlbumCacheable(
            id = id,
            name = name,
            coverImage = coverImage,
            coverUriString = coverUri?.toString(),
            count = count,
            type = type,
            sortConfig = sortConfig,
            isPrivate = isPrivate
        )
    }
    
    companion object {
        fun fromCacheable(cacheable: AlbumCacheable): Album {
            val coverUri = cacheable.coverUriString?.let { Uri.parse(it) }
            return Album(
                id = cacheable.id,
                name = cacheable.name,
                coverImage = cacheable.coverImage,
                coverUri = coverUri,
                count = cacheable.count,
                type = cacheable.type,
                sortConfig = cacheable.sortConfig,
                images = emptyList(), // 缓存版本不包含图片列表
                isPrivate = cacheable.isPrivate
            )
        }
    }
}

// 用于缓存的相册数据类，不包含Uri对象
data class AlbumCacheable(
    val id: String,
    val name: String,
    @DrawableRes val coverImage: Int,
    val coverUriString: String? = null,
    val count: Int,
    val type: AlbumType,
    val sortConfig: SortConfig = SortConfig(),
    val isPrivate: Boolean = false
) : Serializable