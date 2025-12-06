package com.example.yumoflatimagemanager.ui.drawer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 侧边栏控制器，用于管理侧边栏的状态
 */
class DrawerController {
    // 侧边栏是否打开
    var isTagManagerOpen by mutableStateOf(false)
        private set

    // 打开标签管理侧边栏
    fun openTagManager() {
        isTagManagerOpen = true
    }

    // 关闭标签管理侧边栏
    fun closeTagManager() {
        isTagManagerOpen = false
    }

    // 切换标签管理侧边栏状态
    fun toggleTagManager() {
        isTagManagerOpen = !isTagManagerOpen
    }
}