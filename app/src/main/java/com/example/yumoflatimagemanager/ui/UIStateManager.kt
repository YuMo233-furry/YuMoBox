package com.example.yumoflatimagemanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * UI状态管理器接口，定义UI相关的状态管理
 */
interface UiStateManager {
    // 滚动位置状态
    val isNearTop: Boolean
    
    // 更新滚动位置状态
    fun updateScrollPosition(isNearTop: Boolean)
    
    // 显示移动对话框
    fun showMoveDialog()
    
    // 隐藏移动对话框
    fun hideMoveDialog()
    
    // 移动对话框是否显示
    val isMoveDialogVisible: Boolean
    
    // 显示创建相册对话框
    fun showCreateAlbumDialog()
    
    // 隐藏创建相册对话框
    fun hideCreateAlbumDialog()
    
    // 创建相册对话框是否显示
    val isCreateAlbumDialogVisible: Boolean
    
    // 显示网格列数设置对话框
    fun showGridColumnDialog()
    
    // 隐藏网格列数设置对话框
    fun hideGridColumnDialog()
    
    // 网格列数设置对话框是否显示
    val isGridColumnDialogVisible: Boolean
}

/**
 * UiStateManager的具体实现
 */
class UiStateManagerImpl : UiStateManager {
    override var isNearTop: Boolean by mutableStateOf(true)
    override var isMoveDialogVisible: Boolean by mutableStateOf(false)
    override var isCreateAlbumDialogVisible: Boolean by mutableStateOf(false)
    override var isGridColumnDialogVisible: Boolean by mutableStateOf(false)
    
    override fun updateScrollPosition(isNearTop: Boolean) {
        this.isNearTop = isNearTop
    }
    
    override fun showMoveDialog() {
        isMoveDialogVisible = true
    }
    
    override fun hideMoveDialog() {
        isMoveDialogVisible = false
    }
    
    override fun showCreateAlbumDialog() {
        isCreateAlbumDialogVisible = true
    }
    
    override fun hideCreateAlbumDialog() {
        isCreateAlbumDialogVisible = false
    }
    
    override fun showGridColumnDialog() {
        isGridColumnDialogVisible = true
    }
    
    override fun hideGridColumnDialog() {
        isGridColumnDialogVisible = false
    }
}

/**
 * 用于Compose中记住UiStateManager实例的辅助函数
 */
@Composable
fun rememberUiStateManager(): UiStateManager {
    return UiStateManagerImpl()
}