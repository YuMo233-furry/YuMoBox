package com.example.yumoflatimagemanager.startup

import android.content.Context
import android.util.Log
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.data.SortType
import com.example.yumoflatimagemanager.data.SortDirection
import com.example.yumoflatimagemanager.media.SimplifiedImageLoaderHelper
import kotlinx.coroutines.*
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * 启动缓存管理器
 * 负责在应用启动时快速加载缓存数据，实现即点即用的体验
 */
object StartupCacheManager {
    private const val TAG = "StartupCacheManager"
    private const val CACHE_DIR = "startup_cache"
    private const val ALBUMS_CACHE_FILE = "albums_cache.dat"
    private const val IMAGES_CACHE_FILE = "images_cache.dat"
    private const val CACHE_VERSION = 1
    private const val CACHE_EXPIRE_TIME = 5 * 60 * 1000L // 5分钟缓存过期
    
    private var cacheDir: File? = null
    private var isInitialized = false
    
    /**
     * 初始化缓存管理器
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir!!.exists()) {
            cacheDir!!.mkdirs()
        }
        isInitialized = true
        Log.d(TAG, "启动缓存管理器初始化完成")
    }
    
    /**
     * 保存相册数据到缓存
     */
    suspend fun saveAlbumsCache(albums: List<Album>) {
        if (!isInitialized) return
        
        try {
            val cacheFile = File(cacheDir, ALBUMS_CACHE_FILE)
            val cacheableAlbums = albums.map { it.toCacheable() }
            ObjectOutputStream(cacheFile.outputStream()).use { oos ->
                oos.writeInt(CACHE_VERSION)
                oos.writeLong(System.currentTimeMillis())
                oos.writeObject(cacheableAlbums)
            }
            Log.d(TAG, "相册数据缓存保存成功，共${albums.size}个相册")
        } catch (e: Exception) {
            Log.e(TAG, "保存相册缓存失败: ${e.message}")
            // 清理损坏的缓存文件
            try {
                val cacheFile = File(cacheDir, ALBUMS_CACHE_FILE)
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
            } catch (cleanupException: Exception) {
                Log.e(TAG, "清理损坏的缓存文件失败: ${cleanupException.message}")
            }
        }
    }
    
    /**
     * 从缓存加载相册数据
     */
    suspend fun loadAlbumsCache(): List<Album>? {
        if (!isInitialized) return null
        
        try {
            val cacheFile = File(cacheDir, ALBUMS_CACHE_FILE)
            if (!cacheFile.exists()) return null
            
            ObjectInputStream(cacheFile.inputStream()).use { ois ->
                val version = ois.readInt()
                val cacheTime = ois.readLong()
                
                // 检查缓存版本和过期时间
                if (version != CACHE_VERSION || 
                    (System.currentTimeMillis() - cacheTime) > CACHE_EXPIRE_TIME) {
                    return null
                }
                
                @Suppress("UNCHECKED_CAST")
                val cacheableAlbums = ois.readObject() as List<com.example.yumoflatimagemanager.data.AlbumCacheable>
                val albums = cacheableAlbums.map { com.example.yumoflatimagemanager.data.Album.fromCacheable(it) }
                Log.d(TAG, "从缓存加载相册数据成功，共${albums.size}个相册")
                return albums
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载相册缓存失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 保存图片数据到缓存
     */
    suspend fun saveImagesCache(images: List<ImageItem>) {
        if (!isInitialized) return
        
        try {
            val cacheFile = File(cacheDir, IMAGES_CACHE_FILE)
            val cacheableImages = images.map { it.toCacheable() }
            ObjectOutputStream(cacheFile.outputStream()).use { oos ->
                oos.writeInt(CACHE_VERSION)
                oos.writeLong(System.currentTimeMillis())
                oos.writeObject(cacheableImages)
            }
            Log.d(TAG, "图片数据缓存保存成功，共${images.size}张图片")
        } catch (e: Exception) {
            Log.e(TAG, "保存图片缓存失败: ${e.message}")
            // 清理损坏的缓存文件
            try {
                val cacheFile = File(cacheDir, IMAGES_CACHE_FILE)
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
            } catch (cleanupException: Exception) {
                Log.e(TAG, "清理损坏的缓存文件失败: ${cleanupException.message}")
            }
        }
    }
    
    /**
     * 从缓存加载图片数据
     */
    suspend fun loadImagesCache(): List<ImageItem>? {
        if (!isInitialized) return null
        
        try {
            val cacheFile = File(cacheDir, IMAGES_CACHE_FILE)
            if (!cacheFile.exists()) return null
            
            ObjectInputStream(cacheFile.inputStream()).use { ois ->
                val version = ois.readInt()
                val cacheTime = ois.readLong()
                
                // 检查缓存版本和过期时间
                if (version != CACHE_VERSION || 
                    (System.currentTimeMillis() - cacheTime) > CACHE_EXPIRE_TIME) {
                    return null
                }
                
                @Suppress("UNCHECKED_CAST")
                val cacheableImages = ois.readObject() as List<com.example.yumoflatimagemanager.data.ImageItemCacheable>
                val images = cacheableImages.map { com.example.yumoflatimagemanager.data.ImageItem.fromCacheable(it) }
                Log.d(TAG, "从缓存加载图片数据成功，共${images.size}张图片")
                return images
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载图片缓存失败: ${e.message}")
            return null
        }
    }
    
    /**
     * 预加载关键数据
     */
    suspend fun preloadCriticalData(context: Context) {
        if (!isInitialized) return
        
        try {
            // 预加载前20张图片的超低质量缩略图
            val cachedImages = loadImagesCache()
            if (cachedImages != null) {
                val criticalImages = cachedImages.take(20)
                criticalImages.forEach { imageItem ->
                    try {
                        SimplifiedImageLoaderHelper.getUltraLowQualityThumbnail(context, imageItem.uri)
                    } catch (e: Exception) {
                        // 忽略单个图片的预加载失败
                    }
                }
                Log.d(TAG, "关键数据预加载完成，预加载了${criticalImages.size}张图片")
            }
        } catch (e: Exception) {
            Log.e(TAG, "预加载关键数据失败: ${e.message}")
        }
    }
    
    /**
     * 清理过期缓存
     */
    fun clearExpiredCache() {
        if (!isInitialized) return
        
        try {
            val currentTime = System.currentTimeMillis()
            cacheDir?.listFiles()?.forEach { file ->
                if (file.isFile && (currentTime - file.lastModified()) > CACHE_EXPIRE_TIME) {
                    file.delete()
                    Log.d(TAG, "清理过期缓存文件: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理过期缓存失败: ${e.message}")
        }
    }
    
    /**
     * 清理所有缓存
     */
    fun clearAllCache() {
        if (!isInitialized) return
        
        try {
            cacheDir?.listFiles()?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "清理所有启动缓存")
        } catch (e: Exception) {
            Log.e(TAG, "清理所有缓存失败: ${e.message}")
        }
    }
}
