package com.example.yumoflatimagemanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 刷新管理器，负责协调应用各个模块的刷新逻辑
 * 解决返回主菜单后再进入相册，选择模式和菜单状态不刷新的问题
 */
interface RefreshManager {
    // 刷新所有UI状态
    fun refreshAll()
    
    // 刷新选择模式状态
    fun refreshSelectionMode()
    
    // 刷新菜单状态
    fun refreshMenuState()
    
    // 当屏幕切换时执行的刷新操作
    fun onScreenChanged()
    
    // 获取当前刷新key，用于强制组件重新创建
    val refreshKey: Int
}

/**
 * RefreshManager的具体实现
 */
class RefreshManagerImpl(
    private val selectionManager: SelectionManager,
    private val screenManager: ScreenManager
) : RefreshManager {
    
    // 刷新key，用于强制组件重新创建
    override var refreshKey by mutableStateOf(0)
    
    override fun refreshAll() {
        // 刷新所有状态
        refreshSelectionMode()
        refreshMenuState()
        
        // 增加刷新key，强制组件重新创建
        refreshKey++
    }
    
    override fun refreshSelectionMode() {
        // 如果当前处于选择模式，则退出选择模式并清除选择
        if (selectionManager.isSelectionMode) {
            selectionManager.toggleSelectionMode()
        }
    }
    
    override fun refreshMenuState() {
        // 增加刷新key，强制组件重新创建，从而重置菜单状态
        refreshKey++
    }
    
    override fun onScreenChanged() {
        // 当屏幕切换时，执行必要的刷新操作
        // 特别是从相册详情返回到相册列表，然后再次进入相册详情时
        refreshAll()
    }
}

/**
 * 在Compose中创建RefreshManager实例的辅助函数
 */
fun createRefreshManager(
    selectionManager: SelectionManager,
    screenManager: ScreenManager
): RefreshManager {
    return RefreshManagerImpl(selectionManager, screenManager)
}