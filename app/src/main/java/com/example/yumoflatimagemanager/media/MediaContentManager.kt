package com.example.yumoflatimagemanager.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.AlbumType
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 媒体内容管理器，负责处理媒体内容的加载、排序和监听等逻辑
 */
class MediaContentManager(private val context: Context) {
    // 所有图片列表
    var allImages: List<ImageItem> by mutableStateOf(emptyList())
        private set
    
    // 所有视频列表
    var allVideos: List<ImageItem> by mutableStateOf(emptyList())
        private set
    
    // 相册列表
    var albums: List<Album> by mutableStateOf(emptyList())
        private set
    
    // 媒体变化监听器
    private var mediaChangeListener: MediaChangeListener? = null
    
    // 媒体内容观察者
    private lateinit var mediaContentObserver: MediaContentObserver
    
    // 协程作用域
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 防抖Handler
    private val debounceHandler = Handler(Looper.getMainLooper())
    
    // 防抖延迟时间(毫秒)
    private val DEBOUNCE_DELAY = 500L
    
    // 是否正在加载媒体内容
    private var isLoadingMedia = false
    
    // 是否已初始化
    private var isInitialized = false
    
    /**
     * 初始化媒体内容管理器
     */
    fun initialize() {
        // 创建媒体内容观察者
        mediaContentObserver = MediaContentObserver(
            Handler(Looper.getMainLooper()),
            context,
            ::onMediaContentChanged
        )
        
        // 注册媒体内容观察者
        mediaContentObserver.register()
        
        // 初始加载媒体内容
        loadAllMedia()
        
        // 标记为已初始化
        isInitialized = true
    }
    
    /**
     * 设置媒体变化监听器
     */
    fun setMediaChangeListener(listener: MediaChangeListener) {
        this.mediaChangeListener = listener
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * 加载所有媒体内容 - 同步版本
     */
    fun loadAllMedia() {
        if (isLoadingMedia) return
        
        isLoadingMedia = true
        
        // 加载所有图片
        allImages = MediaManager.loadImagesFromDevice(context)
        
        // 加载所有视频
        allVideos = MediaManager.loadVideosFromDevice(context)
        
        // 扫描相册
        scanAlbums()
        
        isLoadingMedia = false
    }
    
    /**
     * 异步加载所有媒体内容
     */
    fun loadAllMediaAsync() {
        coroutineScope.launch {
            val images = MediaManager.loadImagesFromDevice(context)
            val videos = MediaManager.loadVideosFromDevice(context)
            
            // 在主线程更新UI状态
            withContext(Dispatchers.Main) {
                allImages = images
                allVideos = videos
                scanAlbums()
                
                // 通知监听器
                mediaChangeListener?.onMediaChanged()
            }
        }
    }
    
    /**
     * 防抖处理媒体内容变化
     */
    private fun debounceMediaUpdate() {
        // 移除之前的回调
        debounceHandler.removeCallbacks(mediaUpdateRunnable)
        
        // 设置新的延迟回调
        debounceHandler.postDelayed(mediaUpdateRunnable, DEBOUNCE_DELAY)
    }
    
    /**
     * 媒体更新Runnable
     */
    private val mediaUpdateRunnable = Runnable {
        loadAllMediaAsync()
    }
    
    /**
     * 扫描相册
     */
    private fun scanAlbums() {
        // 合并所有图片和视频
        val allMediaItems = allImages + allVideos
        
        // 扫描相册
        val scannedAlbums = MediaManager.scanMediaFolders(context, allMediaItems)
        
        // 创建"所有"相册
        val allAlbum = Album(
            id = "all",
            name = "所有",
            coverImage = R.drawable.ic_launcher_foreground,
            coverUri = if (allMediaItems.isNotEmpty()) allMediaItems[0].uri else null,
            count = allMediaItems.size,
            type = AlbumType.MAIN,
            sortConfig = SortConfig()
        )
        
        // 创建"视频"相册
        val videoAlbum = Album(
            id = "video",
            name = "视频",
            coverImage = R.drawable.ic_launcher_foreground,
            coverUri = if (allVideos.isNotEmpty()) allVideos[0].uri else null,
            count = allVideos.size,
            type = AlbumType.MAIN,
            sortConfig = SortConfig()
        )
        
        // 筛选出相机相册和截图相册
        val cameraAlbum = scannedAlbums.find { it.name == "相机" || isCameraAlbum(it.name) }
        val screenshotsAlbum = scannedAlbums.find { it.name.contains("截图", ignoreCase = true) }
        
        // 构建主要相册列表
        val mainAlbums = mutableListOf<Album>()
        mainAlbums.add(allAlbum)
        mainAlbums.add(videoAlbum)
        if (screenshotsAlbum != null) mainAlbums.add(screenshotsAlbum)
        if (cameraAlbum != null) mainAlbums.add(cameraAlbum)
        
        // 构建自定义相册列表（排除主要相册）
        val customAlbums = scannedAlbums.filter { album ->
            album.type == AlbumType.CUSTOM && 
            !mainAlbums.any { it.name == album.name || it.id == album.id }
        }
        
        // 更新相册列表
        albums = mainAlbums + customAlbums
    }
    
    /**
     * 根据相册ID获取相册中的图片
     */
    fun getImagesByAlbumId(albumId: String, sortConfig: SortConfig): List<ImageItem> {
        val allMediaItems = allImages + allVideos
        
        val filteredImages = when (albumId) {
            "all" -> allMediaItems
            "video" -> allVideos
            else -> allMediaItems.filter { it.albumId == albumId }
        }
        
        // 应用排序
        return MediaManager.sortImages(filteredImages, sortConfig)
    }
    
    /**
     * 媒体内容变化回调
     */
    private fun onMediaContentChanged() {
        // 使用防抖机制避免频繁刷新
        debounceMediaUpdate()
    }
    
    /**
     * 检查是否为相机相册
     */
    private fun isCameraAlbum(folderName: String): Boolean {
        return folderName.lowercase().contains("camera") || 
               folderName.lowercase().contains("相机")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        // 注销媒体内容观察者
        context.contentResolver.unregisterContentObserver(mediaContentObserver)
        
        // 取消所有协程
        coroutineScope.cancel()
        
        // 移除所有待处理的回调
        debounceHandler.removeCallbacksAndMessages(null)
    }
    
    /**
     * 媒体变化监听器接口
     */
    interface MediaChangeListener {
        fun onMediaChanged()
    }
}