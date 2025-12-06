package com.example.yumoflatimagemanager.media

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.util.Log

/**
 * 媒体内容观察者，用于监听媒体文件的变化
 * 这是一种更现代、更可靠的监听媒体变化的方式
 */
class MediaContentObserver(
    handler: Handler,
    private val context: Context,
    private val onMediaChanged: () -> Unit
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "MediaContentObserver"
    }

    /**
     * 当观察到的内容发生变化时调用
     */
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        // 记录日志
        Log.d(TAG, "检测到媒体内容变化: $uri")
        
        // 当媒体内容变化时，调用回调函数
        onMediaChanged()
    }

    /**
     * 注册内容观察者
     */
    fun register() {
        // 监听图片和视频的变化
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true, // 监听子目录
            this
        )
        
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true, // 监听子目录
            this
        )
        
        Log.d(TAG, "内容观察者已注册")
    }

    /**
     * 注销内容观察者
     */
    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
        Log.d(TAG, "内容观察者已注销")
    }
}