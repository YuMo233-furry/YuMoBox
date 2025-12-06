package com.example.yumoflatimagemanager.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 媒体文件变化广播接收器，用于监听设备上媒体文件的添加、删除和修改
 */
class MediaReceiver : BroadcastReceiver() {
    
    companion object {
        // 用于回调的接口
        interface MediaChangeListener {
            fun onMediaChanged()
        }
        
        // 存储监听器的弱引用列表（防止内存泄漏）
        private val changeListeners = mutableListOf<MediaChangeListener>()
        
        // 注册监听器
        fun registerListener(listener: MediaChangeListener) {
            if (!changeListeners.contains(listener)) {
                changeListeners.add(listener)
            }
        }
        
        // 取消注册监听器
        fun unregisterListener(listener: MediaChangeListener) {
            changeListeners.remove(listener)
        }
        
        // 通知所有监听器媒体文件已变化
        private fun notifyMediaChanged() {
            for (listener in changeListeners) {
                listener.onMediaChanged()
            }
        }
    }
    
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            val action = it.action
            
            // 检查是否是媒体文件变化的广播
            when (action) {
                // 注意：ACTION_MEDIA_SCANNER_SCAN_FILE已在较新的API中弃用
                // 但为了向后兼容，我们仍然保留它
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, // 单个文件扫描完成
                Intent.ACTION_MEDIA_SCANNER_FINISHED, // 媒体扫描完成
                Intent.ACTION_MEDIA_SCANNER_STARTED,  // 媒体扫描开始
                Intent.ACTION_MEDIA_MOUNTED,          // 存储设备挂载
                Intent.ACTION_MEDIA_UNMOUNTED,        // 存储设备卸载
                Intent.ACTION_MEDIA_REMOVED,          // 存储设备移除
                Intent.ACTION_MEDIA_EJECT             // 存储设备弹出
                -> {
                    // 记录日志
                    Log.d("MediaReceiver", "收到媒体变化广播: $action")
                    // 通知所有监听器
                    notifyMediaChanged()
                }
            }
        }
    }
}