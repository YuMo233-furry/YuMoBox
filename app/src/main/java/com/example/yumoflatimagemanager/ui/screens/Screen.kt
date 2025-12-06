package com.example.yumoflatimagemanager.ui.screens

/**
 * 屏幕类型密封类，用于表示应用程序的不同屏幕
 */
sealed class Screen {
    /**
     * 相册列表屏幕
     */
    object Albums : Screen()
    
    /**
     * 相册详情屏幕
     */
    object AlbumDetail : Screen()
}