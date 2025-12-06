/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.collection.LruCache
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 简化的图片加载助手
 * 参考PictureSelector的简洁实现，保持核心功能但移除复杂逻辑
 * 保持原有的功能：
 * - 动态列数支持
 * - 基本缓存机制
 * - 异步加载
 * - 内存优化
 */
object SimplifiedImageLoaderHelper {
    private const val TAG = "SimplifiedImageLoaderHelper"
    
    // 简化的缓存配置
    private const val MEMORY_CACHE_SIZE_PERCENT = 0.3f
    private const val DISK_CACHE_SIZE = 200 * 1024 * 1024L // 200MB磁盘缓存
    private const val MAX_CONCURRENT_LOADS = 8 // 适中的并发数
    
    // 根据列数动态计算图片尺寸
    fun getImageSize(columnCount: Int): Int {
        return when (columnCount) {
            2 -> 400
            3 -> 300
            4 -> 250
            5 -> 200
            6 -> 180
            7 -> 160
            8 -> 150
            else -> 200
        }
    }
    
    // 简化的内存缓存
    private lateinit var memoryCache: LruCache<String, Bitmap>
    private var diskCacheDir: File? = null
    
    // 协程池
    private val imageLoadScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val loadSemaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT_LOADS)
    
    /**
     * 初始化
     */
    fun initialize(context: Context) {
        if (!::memoryCache.isInitialized) {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            val cacheSize = (maxMemory * MEMORY_CACHE_SIZE_PERCENT).toInt()
            
            memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
                override fun sizeOf(key: String, bitmap: Bitmap): Int {
                    return try {
                        if (bitmap.isRecycled) 0 else bitmap.byteCount / 1024
                    } catch (e: Exception) {
                        0
                    }
                }
            }
            
            // 初始化磁盘缓存
            diskCacheDir = File(context.cacheDir, "simple_image_cache")
            if (!diskCacheDir!!.exists()) {
                diskCacheDir!!.mkdirs()
            }
        }
    }
    
    /**
     * 创建优化的图片请求（保持原有接口）
     */
    fun createOptimizedImageRequest(
        context: Context,
        uri: Uri,
        isThumbnail: Boolean = false,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        columnCount: Int = 4
    ): ImageRequest {
        val size = if (isThumbnail) getImageSize(columnCount) else (targetWidth.takeIf { it > 0 } ?: 1024)
        
        return ImageRequest.Builder(context)
            .data(uri)
            .size(size, size)
            .crossfade(true)
            .build()
    }
    
    /**
     * 异步加载图片（保持原有接口）
     */
    fun loadImageAsync(
        context: Context,
        uri: Uri,
        isThumbnail: Boolean = false,
        columnCount: Int = 4,
        onSuccess: (Bitmap) -> Unit,
        onError: (Throwable?) -> Unit
    ) {
        initialize(context)
        
        val cacheKey = getCacheKey(uri, isThumbnail, columnCount)
        
        imageLoadScope.launch {
            loadSemaphore.acquire()
            try {
                // 1. 检查内存缓存
                if (::memoryCache.isInitialized) {
                    val memoryBitmap = memoryCache.get(cacheKey)
                    if (memoryBitmap != null && !memoryBitmap.isRecycled) {
                        withContext(Dispatchers.Main) {
                            onSuccess(memoryBitmap)
                        }
                        return@launch
                    }
                }
                
                // 2. 检查磁盘缓存
                val diskBitmap = getBitmapFromDiskCache(cacheKey)
                if (diskBitmap != null) {
                    if (::memoryCache.isInitialized) {
                        memoryCache.put(cacheKey, diskBitmap)
                    }
                    withContext(Dispatchers.Main) {
                        onSuccess(diskBitmap)
                    }
                    return@launch
                }
                
                // 3. 从原始资源加载
                val size = if (isThumbnail) getImageSize(columnCount) else 1024
                val bitmap = loadBitmapFromUri(context, uri, size, size)
                
                if (bitmap != null) {
                    if (::memoryCache.isInitialized) {
                        memoryCache.put(cacheKey, bitmap)
                    }
                    putBitmapToDiskCache(cacheKey, bitmap)
                    withContext(Dispatchers.Main) {
                        onSuccess(bitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError(Exception("Failed to load bitmap"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            } finally {
                loadSemaphore.release()
            }
        }
    }
    
    /**
     * 获取低质量图片（保持原有接口）
     */
    fun getLowQualityBitmap(uri: Uri): Bitmap? {
        // 简化实现，直接返回null，让主加载逻辑处理
        return null
    }
    
    /**
     * 获取超低质量缩略图（保持原有接口）
     */
    fun getUltraLowQualityThumbnail(
        context: Context,
        uri: Uri
    ): Bitmap? {
        // 简化实现，直接返回null，让主加载逻辑处理
        return null
    }
    
    /**
     * 预加载图片（保持原有接口）
     */
    fun preloadImages(
        context: Context,
        uris: List<Uri>,
        isFastScrolling: Boolean = false
    ) {
        initialize(context)
        
        val maxPreload = if (isFastScrolling) 3 else 6
        val urisToPreload = uris.take(maxPreload)
        
        urisToPreload.forEach { uri ->
            imageLoadScope.launch {
                try {
                    val cacheKey = getCacheKey(uri, true, 4)
                    if (::memoryCache.isInitialized && memoryCache.get(cacheKey) == null) {
                        val size = getImageSize(4)
                        val bitmap = loadBitmapFromUri(context, uri, size, size)
                        if (bitmap != null && ::memoryCache.isInitialized) {
                            memoryCache.put(cacheKey, bitmap)
                        }
                    }
                } catch (e: Exception) {
                    // 预加载失败不影响主流程
                }
            }
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        if (::memoryCache.isInitialized) {
            memoryCache.evictAll()
        }
        diskCacheDir?.let { dir ->
            if (dir.exists()) {
                dir.deleteRecursively()
                dir.mkdirs()
            }
        }
    }
    
    /**
     * 获取内存使用情况
     */
    fun getMemoryUsage(): Float {
        val maxMemory = Runtime.getRuntime().maxMemory()
        val usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        return usedMemory.toFloat() / maxMemory.toFloat()
    }
    
    /**
     * 获取相册封面缓存（简化版本，返回null）
     */
    fun getAllAlbumCoverCache(context: Context): List<com.example.yumoflatimagemanager.data.ImageItem>? {
        // 简化版本不实现复杂的相册封面缓存
        return null
    }
    
    /**
     * 检查是否有相册封面缓存（简化版本，总是返回false）
     */
    fun hasAllAlbumCoverCache(): Boolean {
        return false
    }
    
    /**
     * 强制更新相册封面缓存（简化版本，空实现）
     */
    fun forceUpdateAllAlbumCoverCache(context: Context) {
        // 简化版本不实现复杂的相册封面缓存
    }
    
    /**
     * 预加载低质量图片（简化版本）
     */
    fun preloadLowQualityImages(context: Context, uris: List<Uri>) {
        preloadImages(context, uris, true)
    }
    
    /**
     * 预加载相册低质量图片（简化版本）
     */
    fun preloadAlbumLowQualityImages(context: Context, images: List<com.example.yumoflatimagemanager.data.ImageItem>) {
        val uris = images.map { it.uri }
        preloadImages(context, uris, true)
    }
    
    /**
     * 清理所有缓存和状态（简化版本）
     */
    fun clearAllCacheAndState() {
        // 确保在清理之前已经初始化
        if (::memoryCache.isInitialized) {
            clearCache()
        }
    }
    
    /**
     * 暂停图片加载请求（简化版本）
     */
    fun pauseRequests(context: Context) {
        // 简化版本：暂停Glide的请求
        com.bumptech.glide.Glide.with(context).pauseRequests()
    }
    
    /**
     * 恢复图片加载请求（简化版本）
     */
    fun resumeRequests(context: Context) {
        // 简化版本：恢复Glide的请求
        com.bumptech.glide.Glide.with(context).resumeRequests()
    }
    
    /**
     * 加载长图片并实时调整分辨率（简化版本）
     */
    fun loadLongImageWithRealTimeResolution(
        context: Context,
        uri: Uri,
        aspectRatio: Float,
        currentScale: Float,
        onSuccess: (android.graphics.Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        // 简化版本直接使用Glide加载
        com.bumptech.glide.Glide.with(context)
            .asBitmap()
            .load(uri)
            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                override fun onResourceReady(resource: android.graphics.Bitmap, transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?) {
                    onSuccess(resource)
                }
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    onError("Failed to load image")
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // 清理资源
                }
            })
    }
    
    /**
     * 动态分辨率加载图片（简化版本）
     */
    fun loadImageWithDynamicResolution(
        context: Context,
        uri: Uri,
        scale: Float,
        onSuccess: (android.graphics.Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        // 简化版本直接使用Glide加载
        com.bumptech.glide.Glide.with(context)
            .asBitmap()
            .load(uri)
            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                override fun onResourceReady(resource: android.graphics.Bitmap, transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?) {
                    onSuccess(resource)
                }
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    onError("Failed to load image")
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // 清理资源
                }
            })
    }
    
    // 私有辅助方法
    
    private fun getCacheKey(uri: Uri, isThumbnail: Boolean, columnCount: Int): String {
        val size = if (isThumbnail) getImageSize(columnCount) else 1024
        return "${uri.toString()}_${size}_${size}"
    }
    
    private fun loadBitmapFromUri(context: Context, uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            val sampleSize = calculateInSampleSize(options, width, height)
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
            }
            
            val newInputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(newInputStream, null, options)
            newInputStream?.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: ${e.message}")
            null
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    
    private fun getBitmapFromDiskCache(key: String): Bitmap? {
        return try {
            val file = File(diskCacheDir, key.hashCode().toString())
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun putBitmapToDiskCache(key: String, bitmap: Bitmap) {
        try {
            val file = File(diskCacheDir, key.hashCode().toString())
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to disk: ${e.message}")
        }
    }
}
