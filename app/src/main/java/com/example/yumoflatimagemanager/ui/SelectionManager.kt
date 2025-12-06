package com.example.yumoflatimagemanager.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.ImageItem

/**
 * SelectionManager接口定义了选择相关的操作
 */
interface SelectionManager {
    // 是否处于选择模式
    val isSelectionMode: Boolean
    
    // 当前选中的图片列表
    val selectedImages: List<ImageItem>
    
    // 当前选中的相册列表
    val selectedAlbums: List<Album>
    
    // 切换选择模式
    fun toggleSelectionMode()
    
    // 选择或取消选择单张图片
    fun selectImage(image: ImageItem)
    
    // 选择多张图片（用于滑动选择）
    fun selectImages(images: List<ImageItem>)
    
    // 全选或取消全选图片
    fun selectAllImages(images: List<ImageItem>)
    
    // 选择或取消选择单个相册
    fun selectAlbum(album: Album)
    
    // 选择多个相册
    fun selectAlbums(albums: List<Album>)
    
    // 全选或取消全选相册
    fun selectAllAlbums(albums: List<Album>)
    
    // 清除所有选择
    fun clearSelection()
    
    // 检查图片是否被选中
    fun isImageSelected(image: ImageItem): Boolean
    
    // 检查相册是否被选中
    fun isAlbumSelected(album: Album): Boolean
    
    // 模拟删除选中的图片
    fun deleteSelectedImages()
}

/**
 * SelectionManager的具体实现
 */
class SelectionManagerImpl : SelectionManager {
    override var isSelectionMode: Boolean by mutableStateOf(false)
    override var selectedImages: List<ImageItem> by mutableStateOf(emptyList())
    override var selectedAlbums: List<Album> by mutableStateOf(emptyList())
    
    override fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        if (!isSelectionMode) {
            clearSelection()
        }
    }
    
    override fun selectImage(image: ImageItem) {
        // 如果不在选择模式，先进入选择模式
        if (!isSelectionMode) {
            isSelectionMode = true
        }
        
        val currentSelected = selectedImages.toMutableList()
        // 使用id进行比较，避免对象相等性问题
        val existingIndex = currentSelected.indexOfFirst { it.id == image.id }
        if (existingIndex >= 0) {
            // 取消选择：从列表中移除
            currentSelected.removeAt(existingIndex)
        } else {
            // 选择：添加到列表
            currentSelected.add(image)
        }
        selectedImages = currentSelected
    }
    
    override fun selectImages(images: List<ImageItem>) {
        if (isSelectionMode) {
            // 统一使用基于 id 的集合运算，避免对象实例差异导致 contains 失配
            val currentSelectedById = selectedImages.associateBy { it.id }
            val incomingById = images.associateBy { it.id }
            // 合并：以传入列表为主，保持其顺序，同时补充当前已选但仍在传入集合里的唯一项
            val mergedIds = LinkedHashSet<String>()
            images.forEach { mergedIds.add(it.id) }
            selectedImages.forEach { if (incomingById.containsKey(it.id)) mergedIds.add(it.id) }
            // 回填为对象列表：优先使用传入的对象，其次回退到当前已选中的对象
            val newSelected = mergedIds.mapNotNull { incomingById[it] ?: currentSelectedById[it] }
            selectedImages = newSelected
        }
    }
    
    override fun selectAllImages(images: List<ImageItem>) {
        if (isSelectionMode) {
            selectedImages = if (selectedImages.size == images.size) {
                emptyList()
            } else {
                images
            }
        }
    }
    
    override fun selectAlbum(album: Album) {
        if (isSelectionMode) {
            selectedAlbums = if (selectedAlbums.contains(album)) {
                selectedAlbums - album
            } else {
                selectedAlbums + album
            }
        }
    }
    
    override fun selectAlbums(albums: List<Album>) {
        if (isSelectionMode) {
            selectedAlbums = selectedAlbums + albums.filter { !selectedAlbums.contains(it) }
        }
    }
    
    override fun selectAllAlbums(albums: List<Album>) {
        if (isSelectionMode) {
            selectedAlbums = if (selectedAlbums.size == albums.size) {
                emptyList()
            } else {
                albums
            }
        }
    }
    
    override fun clearSelection() {
        selectedImages = emptyList()
        selectedAlbums = emptyList()
    }
    
    override fun isImageSelected(image: ImageItem): Boolean {
        return selectedImages.any { it.id == image.id }
    }
    
    override fun isAlbumSelected(album: Album): Boolean {
        return selectedAlbums.contains(album)
    }
    
    override fun deleteSelectedImages() {
        // 实际应用中这里会处理图片删除逻辑
        // 但具体的删除操作应该由MediaContentManager处理
    }
}

/**
 * 在Compose中创建SelectionManager实例的辅助函数
 */
fun createSelectionManager(): SelectionManager {
    return SelectionManagerImpl()
}