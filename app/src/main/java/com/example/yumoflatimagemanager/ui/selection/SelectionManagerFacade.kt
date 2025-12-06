/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.selection

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.ui.SelectionManager

/**
 * SelectionManagerFacade接口
 * 扩展SelectionManager，添加滑动选择所需的方法
 */
interface SelectionManagerFacade : SelectionManager {
    // 获取选中图片的索引列表
    fun getSelectedIndices(): Set<Int>
    
    // 添加图片到选择列表
    fun addToSelection(images: List<ImageItem>)
    
    // 从选择列表中移除图片
    fun removeFromSelection(images: List<ImageItem>)
    
    // 开始拖拽选择
    fun startDrag(position: Offset, images: List<ImageItem>, findItemAtPosition: (Offset) -> Int?)
    
    // 更新拖拽选择
    fun updateDrag(position: Offset, images: List<ImageItem>, findItemAtPosition: (Offset) -> Int?)
    
    // 结束拖拽选择
    fun endDrag()
    
    
    // 设置图片点击处理器
    fun setupImageClickHandler(
        onImageView: (ImageItem) -> Unit,
        onSelectionChange: (List<ImageItem>) -> Unit
    )
    
    // 同步外部状态
    fun syncExternalState(isSelectionMode: Boolean, selectedImages: List<ImageItem>)
    
    // 设置拖拽选择处理器
    fun setupDragSelectionHandler(
        gridColumnCount: Int,
        onSelectionChange: (List<ImageItem>) -> Unit
    )
    
    // 是否正在拖拽
    val isDragging: Boolean
    
    // 图片点击处理器
    val imageClickHandler: Any?
    
    // 拖拽选择处理器
    val dragSelectionHandler: Any?
}

/**
 * SelectionManagerFacade的具体实现
 */
class SelectionManagerFacadeImpl(
    private val selectionManager: SelectionManager
) : SelectionManagerFacade {
    
    // 拖拽选择状态
    override var isDragging by mutableStateOf(false)
    private var dragStartIndex by mutableStateOf(-1)
    private var dragEndIndex by mutableStateOf(-1)
    private var dragMode: DragMode = DragMode.SELECT
    
    // 回调函数
    private var onImageView: ((ImageItem) -> Unit)? = null
    private var onSelectionChange: ((List<ImageItem>) -> Unit)? = null
    
    // 处理器引用
    override val imageClickHandler: Any? = null
    override val dragSelectionHandler: Any? = null
    
    enum class DragMode { SELECT, DESELECT }
    
    // 委托给原始的SelectionManager
    override val isSelectionMode: Boolean get() = selectionManager.isSelectionMode
    override val selectedImages: List<ImageItem> get() = selectionManager.selectedImages
    override val selectedAlbums: List<com.example.yumoflatimagemanager.data.Album> get() = selectionManager.selectedAlbums
    
    override fun toggleSelectionMode() = selectionManager.toggleSelectionMode()
    override fun selectImage(image: ImageItem) = selectionManager.selectImage(image)
    override fun selectImages(images: List<ImageItem>) = selectionManager.selectImages(images)
    override fun selectAllImages(images: List<ImageItem>) = selectionManager.selectAllImages(images)
    override fun selectAlbum(album: com.example.yumoflatimagemanager.data.Album) = selectionManager.selectAlbum(album)
    override fun selectAlbums(albums: List<com.example.yumoflatimagemanager.data.Album>) = selectionManager.selectAlbums(albums)
    override fun selectAllAlbums(albums: List<com.example.yumoflatimagemanager.data.Album>) = selectionManager.selectAllAlbums(albums)
    override fun clearSelection() = selectionManager.clearSelection()
    override fun isImageSelected(image: ImageItem) = selectionManager.isImageSelected(image)
    override fun isAlbumSelected(album: com.example.yumoflatimagemanager.data.Album) = selectionManager.isAlbumSelected(album)
    override fun deleteSelectedImages() = selectionManager.deleteSelectedImages()
    
    override fun getSelectedIndices(): Set<Int> {
        // 这里需要根据具体的图片列表来计算索引
        // 暂时返回空集合，实际使用时需要传入图片列表
        return emptySet()
    }
    
    /**
     * 根据图片列表获取选中图片的索引
     */
    fun getSelectedIndices(images: List<ImageItem>): Set<Int> {
        val indices = mutableSetOf<Int>()
        selectedImages.forEach { selectedImage ->
            val index = images.indexOf(selectedImage)
            if (index >= 0) {
                indices.add(index)
            }
        }
        return indices
    }
    
    override fun addToSelection(images: List<ImageItem>) {
        if (!isSelectionMode) return
        
        val currentSelected = selectedImages.toMutableList()
        images.forEach { image ->
            if (!currentSelected.contains(image)) {
                currentSelected.add(image)
            }
        }
        selectionManager.selectImages(currentSelected)
        onSelectionChange?.invoke(currentSelected)
    }
    
    override fun removeFromSelection(images: List<ImageItem>) {
        if (!isSelectionMode) return
        
        val currentSelected = selectedImages.toMutableList()
        currentSelected.removeAll(images.toSet())
        selectionManager.selectImages(currentSelected)
        onSelectionChange?.invoke(currentSelected)
    }
    
    override fun startDrag(position: Offset, images: List<ImageItem>, findItemAtPosition: (Offset) -> Int?) {
        if (!isSelectionMode) return
        
        val startIndex = findItemAtPosition(position)
        if (startIndex != null && startIndex >= 0 && startIndex < images.size) {
            isDragging = true
            dragStartIndex = startIndex
            dragEndIndex = startIndex
            
            val currentImage = images[startIndex]
            // 依据起点是否已选中决定拖拽模式（选择/取消选择）
            dragMode = if (isImageSelected(currentImage)) DragMode.DESELECT else DragMode.SELECT
        }
    }
    
    override fun updateDrag(position: Offset, images: List<ImageItem>, findItemAtPosition: (Offset) -> Int?) {
        if (!isSelectionMode || !isDragging) return
        
        val endIndex = findItemAtPosition(position)
        if (endIndex != null && endIndex >= 0 && endIndex < images.size) {
            if (endIndex != dragEndIndex) {
                dragEndIndex = endIndex
                applyRectSelection(images)
            }
        }
    }
    
    override fun endDrag() {
        isDragging = false
        dragStartIndex = -1
        dragEndIndex = -1
    }
    
    
    override fun setupImageClickHandler(
        onImageView: (ImageItem) -> Unit,
        onSelectionChange: (List<ImageItem>) -> Unit
    ) {
        this.onImageView = onImageView
        this.onSelectionChange = onSelectionChange
    }
    
    override fun syncExternalState(isSelectionMode: Boolean, selectedImages: List<ImageItem>) {
        // 同步选择模式状态
        if (isSelectionMode != this.isSelectionMode) {
            selectionManager.toggleSelectionMode()
        }
        
        // 如果退出选择模式，清空选择
        if (!isSelectionMode && this.selectedImages.isNotEmpty()) {
            selectionManager.clearSelection()
        }
    }
    
    override fun setupDragSelectionHandler(
        gridColumnCount: Int,
        onSelectionChange: (List<ImageItem>) -> Unit
    ) {
        this.onSelectionChange = onSelectionChange
        // 这里可以设置拖拽选择相关的配置
    }
    
    /**
     * 应用矩形选择
     */
    private fun applyRectSelection(images: List<ImageItem>) {
        if (dragStartIndex == -1 || dragEndIndex == -1) return
        
        val startRow = dragStartIndex / 4 // 假设4列网格
        val startCol = dragStartIndex % 4
        val endRow = dragEndIndex / 4
        val endCol = dragEndIndex % 4
        
        val minRow = minOf(startRow, endRow)
        val maxRow = maxOf(startRow, endRow)
        val minCol = minOf(startCol, endCol)
        val maxCol = maxOf(startCol, endCol)
        
        val selectedImages = mutableListOf<ImageItem>()
        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                val index = row * 4 + col
                if (index >= 0 && index < images.size) {
                    selectedImages.add(images[index])
                }
            }
        }
        
        when (dragMode) {
            DragMode.SELECT -> addToSelection(selectedImages)
            DragMode.DESELECT -> removeFromSelection(selectedImages)
        }
    }
}

/**
 * 创建SelectionManagerFacade实例的辅助函数
 */
fun createSelectionManagerFacade(selectionManager: SelectionManager): SelectionManagerFacade {
    return SelectionManagerFacadeImpl(selectionManager)
}