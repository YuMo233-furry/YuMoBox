package com.example.yumoflatimagemanager.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.collection.LruCache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 视频缩略图缓存管理器
 * 实现内存缓存和磁盘缓存相结合的缓存机制
 */
class VideoThumbnailCache private constructor(context: Context) {
    private val TAG = "VideoThumbnailCache"
    
    // 内存缓存，使用LruCache实现
    private val memoryCache: LruCache<String, Bitmap>
    
    // 磁盘缓存目录
    private val diskCacheDir: File
    
    // 磁盘缓存大小上限（100MB）
    private val DISK_CACHE_SIZE: Long = 100 * 1024 * 1024
    
    init {
        // 减少视频缩略图缓存大小，避免与图片缓存冲突
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 20 // 进一步减少到1/20，避免与ImageLoaderHelper冲突
        
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                // 安全地返回Bitmap的大小，单位是KB
                return try {
                    if (bitmap.isRecycled) {
                        0 // 已回收的Bitmap大小为0
                    } else {
                        bitmap.byteCount / 1024
                    }
                } catch (e: Exception) {
                    0 // 异常情况下返回0
                }
            }
            
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
                // 当缓存项被移除时，确保Bitmap被正确回收
                if (evicted && !oldValue.isRecycled) {
                    try {
                        oldValue.recycle()
                        Log.d(TAG, "回收被移除的缓存Bitmap: $key")
                    } catch (e: Exception) {
                        Log.e(TAG, "回收Bitmap失败: ${e.message}")
                    }
                }
            }
        }
        
        // 初始化磁盘缓存目录
        diskCacheDir = File(context.cacheDir, "video_thumbnails")
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs()
        }
        
        // 清理过期的磁盘缓存文件
        cleanupExpiredCache()
    }
    
    /**
     * 从缓存中获取缩略图
     * @param uri 视频URI
     * @return 缓存的缩略图，如果没有缓存则返回null
     */
    fun getThumbnail(uri: Uri): Bitmap? {
        val key = getCacheKey(uri)
        
        // 首先从内存缓存中获取
        var bitmap = memoryCache.get(key)
        if (bitmap != null && !bitmap.isRecycled) {
            Log.d(TAG, "从内存缓存获取缩略图: $key")
            return bitmap
        }
        
        // 如果内存缓存中没有或已回收，则从磁盘缓存中获取
        bitmap = getBitmapFromDiskCache(key)
        if (bitmap != null && !bitmap.isRecycled) {
            Log.d(TAG, "从磁盘缓存获取缩略图: $key")
            // 将磁盘缓存的内容加载到内存缓存
            memoryCache.put(key, bitmap)
        }
        
        return bitmap
    }
    
    /**
     * 将缩略图添加到缓存
     * @param uri 视频URI
     * @param bitmap 要缓存的缩略图
     */
    fun putThumbnail(uri: Uri, bitmap: Bitmap) {
        if (bitmap.isRecycled) {
            Log.w(TAG, "尝试缓存已回收的Bitmap，跳过")
            return
        }
        
        val key = getCacheKey(uri)
        
        // 添加到内存缓存
        memoryCache.put(key, bitmap)
        Log.d(TAG, "添加缩略图到内存缓存: $key")
        
        // 添加到磁盘缓存
        putBitmapToDiskCache(key, bitmap)
    }
    
    /**
     * 清除所有缓存
     */
    fun clearCache() {
        memoryCache.evictAll()
        Log.d(TAG, "清除内存缓存")
        
        val files = diskCacheDir.listFiles()
        if (files != null) {
            for (file in files) {
                file.delete()
            }
        }
        Log.d(TAG, "清除磁盘缓存")
    }
    
    /**
     * 清理过期的磁盘缓存文件
     * 删除超过7天的缓存文件
     */
    private fun cleanupExpiredCache() {
        try {
            val files = diskCacheDir.listFiles()
            if (files != null) {
                val currentTime = System.currentTimeMillis()
                val expireTime = 7 * 24 * 60 * 60 * 1000L // 7天
                
                for (file in files) {
                    if (currentTime - file.lastModified() > expireTime) {
                        file.delete()
                        Log.d(TAG, "删除过期缓存文件: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理过期缓存失败: ${e.message}", e)
        }
    }
    
    /**
     * 从磁盘缓存中获取Bitmap
     */
    private fun getBitmapFromDiskCache(key: String): Bitmap? {
        val cacheFile = File(diskCacheDir, key)
        if (!cacheFile.exists()) {
            return null
        }
        
        return try {
            BitmapFactory.decodeFile(cacheFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "从磁盘读取缩略图失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 将Bitmap保存到磁盘缓存
     */
    private fun putBitmapToDiskCache(key: String, bitmap: Bitmap) {
        val cacheFile = File(diskCacheDir, key)
        
        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(cacheFile)
            // 将Bitmap压缩为PNG格式存储
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            Log.d(TAG, "保存缩略图到磁盘缓存: $key")
        } catch (e: IOException) {
            Log.e(TAG, "保存缩略图到磁盘失败: ${e.message}", e)
            // 如果保存失败，删除文件
            cacheFile.delete()
        } finally {
            fileOutputStream?.close()
        }
    }
    
    /**
     * 生成缓存键
     * 使用URI的MD5作为缓存键，避免文件名过长
     * 简化缓存键生成，避免时间戳导致的缓存失效
     */
    private fun getCacheKey(uri: Uri): String {
        val uriString = uri.toString()
        // 使用URI的MD5作为缓存键，确保相同URI总是使用相同的缓存
        // 添加前缀区分视频缩略图缓存
        return "video_thumb_${uriString.md5()}"
    }
    
    // MD5工具方法，用于生成缓存键
    private fun String.md5(): String {
        val messageDigest = java.security.MessageDigest.getInstance("MD5")
        val digest = messageDigest.digest(this.toByteArray())
        val hexString = StringBuilder()
        
        for (b in digest) {
            hexString.append(String.format("%02x", b))
        }
        
        return hexString.toString()
    }
    
    companion object {
        // 单例模式
        private var instance: VideoThumbnailCache? = null
        
        fun getInstance(context: Context): VideoThumbnailCache {
            if (instance == null) {
                instance = VideoThumbnailCache(context.applicationContext)
            }
            return instance!!
        }
    }
}