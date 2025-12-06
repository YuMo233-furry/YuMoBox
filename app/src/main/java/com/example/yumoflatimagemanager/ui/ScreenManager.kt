package com.example.yumoflatimagemanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yumoflatimagemanager.ui.screens.Screen

/**
 * 屏幕管理器，负责处理屏幕导航相关的逻辑
 */
interface ScreenManager {
    // 当前显示的屏幕
    var currentScreen: Screen
    
    // 切换到相册列表屏幕
    fun navigateToAlbums()
    
    // 切换到相册详情屏幕
    fun navigateToAlbumDetail()
    
    // 处理返回按钮点击
    fun handleBackPress()
}

/**
 * ScreenManager的具体实现
 */
class ScreenManagerImpl : ScreenManager {
    override var currentScreen: Screen by mutableStateOf(Screen.Albums)
    
    override fun navigateToAlbums() {
        currentScreen = Screen.Albums
    }
    
    override fun navigateToAlbumDetail() {
        currentScreen = Screen.AlbumDetail
    }
    
    override fun handleBackPress() {
        // 根据当前屏幕决定返回行为
        when (currentScreen) {
            is Screen.AlbumDetail -> {
                // 从相册详情页返回相册列表
                navigateToAlbums()
            }
            is Screen.Albums -> {
                // 在相册列表页，通常是退出应用或执行其他操作
                // 这里我们只记录日志，实际的退出应用逻辑应该在Activity中处理
                println("Back pressed on Albums screen")
            }
        }
    }
}

/**
 * 在Compose中创建ScreenManager实例的辅助函数
 */
fun createScreenManager(): ScreenManager {
    return ScreenManagerImpl()
}