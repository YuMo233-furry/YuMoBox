package com.example.yumoflatimagemanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.AlbumType
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.R

/**
 * 相册管理器，负责处理相册相关的状态和逻辑
 */
interface AlbumManager {
    // 当前选中的相册
    var selectedAlbum: Album?
    
    // 主要相册列表
    var mainAlbums: List<Album>
    
    // 自定义相册列表
    var customAlbums: List<Album>
    
    // 合并后的相册列表
    var mergedAlbums: List<Album>
    
    // 切换到指定相册
    fun switchToAlbum(album: Album)
    
    // 创建新相册
    fun createAlbum(name: String): Album
    
    // 更新相册排序配置
    fun updateAlbumSortConfig(album: Album, sortConfig: SortConfig): Album
    
    // 删除相册
    fun deleteAlbum(album: Album)
    
    // 重命名相册
    fun renameAlbum(album: Album, newName: String): Boolean
}

/**
 * AlbumManager的具体实现
 */
class AlbumManagerImpl : AlbumManager {
    override var selectedAlbum: Album? by mutableStateOf(null)
    override var mainAlbums: List<Album> by mutableStateOf(emptyList())
    override var customAlbums: List<Album> by mutableStateOf(emptyList())
    override var mergedAlbums: List<Album> by mutableStateOf(emptyList())
    
    override fun switchToAlbum(album: Album) {
        selectedAlbum = album
    }
    
    override fun deleteAlbum(album: Album) {
        // 根据相册类型从相应的列表中移除
        if (album.type == AlbumType.MAIN) {
            mainAlbums = mainAlbums.filter { it.id != album.id }
        } else {
            customAlbums = customAlbums.filter { it.id != album.id }
        }
        
        // 从合并后的列表中移除
        mergedAlbums = mergedAlbums.filter { it.id != album.id }
        
        // 如果删除的是当前选中的相册，清除选中状态
        if (selectedAlbum?.id == album.id) {
            selectedAlbum = null
        }
    }
    
    override fun createAlbum(name: String): Album {
        val newAlbum = Album(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            coverImage = R.drawable.ic_launcher_background, // 默认封面图片
            coverUri = null, // 初始没有封面
            count = 0,
            type = AlbumType.CUSTOM,
            sortConfig = SortConfig() // 设置默认排序配置
        )
        
        // 更新自定义相册列表
        customAlbums = customAlbums + newAlbum
        
        // 更新合并后的相册列表
        mergedAlbums = mergedAlbums + newAlbum
        
        return newAlbum
    }
    
    override fun updateAlbumSortConfig(album: Album, sortConfig: SortConfig): Album {
        val updatedAlbum = album.copy(sortConfig = sortConfig)
        
        // 根据相册类型更新相应的列表
        if (album.type == AlbumType.MAIN) {
            mainAlbums = mainAlbums.map { if (it.id == album.id) updatedAlbum else it }
        } else {
            customAlbums = customAlbums.map { if (it.id == album.id) updatedAlbum else it }
        }
        
        // 更新合并后的相册列表
        mergedAlbums = mergedAlbums.map { if (it.id == album.id) updatedAlbum else it }
        
        // 如果是当前选中的相册，也更新选中的相册
        if (selectedAlbum?.id == album.id) {
            selectedAlbum = updatedAlbum
        }
        
        return updatedAlbum
    }
    
    override fun renameAlbum(album: Album, newName: String): Boolean {
        // 主要相册不能重命名
        if (album.type == AlbumType.MAIN) {
            return false
        }
        
        // 创建重命名后的相册对象
        val renamedAlbum = album.copy(name = newName)
        
        // 更新自定义相册列表
        customAlbums = customAlbums.map { if (it.id == album.id) renamedAlbum else it }
        
        // 更新合并后的相册列表
        mergedAlbums = mergedAlbums.map { if (it.id == album.id) renamedAlbum else it }
        
        // 如果是当前选中的相册，也更新选中的相册
        if (selectedAlbum?.id == album.id) {
            selectedAlbum = renamedAlbum
        }
        
        return true
    }
}

/**
 * 在Compose中创建AlbumManager实例的辅助函数
 */
fun createAlbumManager(): AlbumManager {
    return AlbumManagerImpl()
}