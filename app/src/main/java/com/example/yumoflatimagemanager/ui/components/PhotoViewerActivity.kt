package com.example.yumoflatimagemanager.ui.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.yumoflatimagemanager.R
import com.example.yumoflatimagemanager.media.SimplifiedImageLoaderHelper
import com.github.chrisbanes.photoview.PhotoView
import com.github.chrisbanes.photoview.PhotoViewAttacher
import java.util.ArrayList
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

private fun View.isAttachedToWindowCompat(): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) isAttachedToWindow else windowToken != null

/**
 * 图片信息数据类
 */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String
)

/**
 * 基于PhotoView库的图片查看器Activity
 * 实现大图浏览、缩放、平移和滑动切换等功能
 */
class PhotoViewerActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var imageUris: List<Uri>
    private var currentPosition: Int = 0
    private lateinit var adapter: PhotoPagerAdapter
    private val playbackPositions = hashMapOf<String, Long>()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("playback_positions", HashMap(playbackPositions))
    }

    companion object {
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val SWIPE_THRESHOLD_PERCENTAGE = 0.3f // 切换阈值为30%

        /**
         * 启动图片查看器
         * @param activity 当前Activity
         * @param uris 图片URI列表
         * @param position 当前要显示的图片位置
         */
        fun start(activity: Activity, uris: List<Uri>, position: Int) {
            val intent = Intent(activity, PhotoViewerActivity::class.java)
            intent.putParcelableArrayListExtra(EXTRA_IMAGE_URIS, ArrayList(uris))
            intent.putExtra(EXTRA_CURRENT_POSITION, position)
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)

        @Suppress("UNCHECKED_CAST")
        (savedInstanceState?.getSerializable("playback_positions") as? HashMap<String, Long>)?.let {
            playbackPositions.putAll(it)
        }

        // 获取传递的参数
        imageUris = getImageUrisFromIntent()
        currentPosition = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0)

        // 初始化ViewPager2
        viewPager = findViewById(R.id.pager)
        setupViewPager()

        // 设置页面切换监听器
        setupPageChangeCallback()

        // 设置初始位置 - 禁用动画
        viewPager.setCurrentItem(currentPosition, false)

        // 更新图片计数器
        updateImageCounter()

        // 入场动画 - 参考PictureSelector的右边滑入效果
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
    }

    /**
     * 设置ViewPager2的属性和适配器
     */
    private fun setupViewPager() {
        // 设置滑动方向为水平
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        // 设置边缘阻力效果
        viewPager.getChildAt(0)?.overScrollMode = RecyclerView.OVER_SCROLL_ALWAYS
        
        // 设置适当的预加载页数，ViewPager2要求必须大于0
        viewPager.offscreenPageLimit = 1
        
        // 创建并设置适配器
        adapter = PhotoPagerAdapter(imageUris)
        viewPager.adapter = adapter
        
        // 设置页面切换动画 - 参考PictureSelector的优化策略
        viewPager.setPageTransformer(PhotoPageTransformer())
    }

    /**
     * 设置页面切换监听器
     */
    private fun setupPageChangeCallback() {
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                updateImageCounter()
                
                // 当页面切换完成时，重置上一个页面的缩放状态
                adapter.resetPreviousPhotoViewZoom()
                
                // 暂停非当前页面的视频播放
                pauseAllVideosExcept(position)
            }
            
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        // 当开始滑动时，暂停所有视频和GIF播放，避免滑动过程中多个媒体同时播放
                        pauseAllVideos()
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        // 滑动完全结束后，确保当前页面的媒体正常播放
                        pauseAllVideosExcept(currentPosition)
                    }
                }
            }
        })
    }
    
    /**
     * 暂停除指定位置外的所有视频和GIF播放
     */
    private fun pauseAllVideosExcept(position: Int) {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView
        recyclerView?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)
                val viewHolder = it.getChildViewHolder(child)
                when (viewHolder) {
                    is PhotoPagerAdapter.VideoViewHolder -> {
                        if (viewHolder.adapterPosition != position) {
                            viewHolder.player?.playWhenReady = false
                        }
                    }
                    is GifViewHolder -> {
                        if (viewHolder.adapterPosition != position) {
                            viewHolder.playGif(false)
                        }
                    }
                }
            }
        }
        
        // 确保当前页面的视频或GIF在页面切换后可以正常播放
        val currentViewHolder = findViewHolderForPosition(position)
        when (currentViewHolder) {
            is PhotoPagerAdapter.VideoViewHolder -> {
                currentViewHolder.player?.playWhenReady = true
            }
            is GifViewHolder -> {
                currentViewHolder.playGif(true)
            }
        }
    }
    
    /**
     * 查找指定位置的ViewHolder
     */
    private fun findViewHolderForPosition(position: Int): RecyclerView.ViewHolder? {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView
        recyclerView?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)
                val viewHolder = it.getChildViewHolder(child)
                if (viewHolder.adapterPosition == position) {
                    return viewHolder
                }
            }
        }
        return null
    }

    /**
     * 更新图片计数器显示
     */
    private fun updateImageCounter() {
        val imageCounter = findViewById<TextView>(R.id.image_counter)
        imageCounter.text = "${currentPosition + 1}/${imageUris.size}"
    }

    /**
     * 获取图片URI列表，兼容API 33及以上版本
     */
    private fun getImageUrisFromIntent(): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_IMAGE_URIS, Uri::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_IMAGE_URIS) ?: emptyList()
        }
    }

    /**
     * 设置沉浸式全屏模式
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onPause() {
        super.onPause()
        // Activity暂停时，暂停所有视频播放
        pauseAllVideos()
    }

    override fun onStop() {
        super.onStop()
        // Activity停止时，释放所有视频播放器资源
        releaseAllPlayers()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保在Activity销毁时完全释放所有资源
        releaseAllPlayers()
        // 清理高分辨率图片缓存
        clearHighResolutionCache()
    }
    
    /**
     * 清理高分辨率图片缓存
     */
    private fun clearHighResolutionCache() {
        try {
            // 清理内存中的高分辨率图片缓存
            System.gc()
            android.util.Log.d("PhotoViewer", "High resolution cache cleared")
        } catch (e: Exception) {
            android.util.Log.e("PhotoViewer", "Error clearing cache: ${e.message}")
        }
    }

    override fun finish() {
        super.finish()
        // 退出动画 - 参考PictureSelector的右边滑出效果
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }

    /**
     * 暂停所有视频播放和GIF动画
     */
    private fun pauseAllVideos() {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView
        recyclerView?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)
                val viewHolder = it.getChildViewHolder(child)
                when (viewHolder) {
                    is PhotoPagerAdapter.VideoViewHolder -> {
                        viewHolder.player?.playWhenReady = false
                    }
                    is GifViewHolder -> {
                        viewHolder.playGif(false)
                    }
                }
            }
        }
    }

    /**
     * 释放所有视频播放器资源和GIF
     */
    private fun releaseAllPlayers() {
        adapter?.let {
            // 让适配器释放所有播放器资源
            val recyclerView = viewPager.getChildAt(0) as? RecyclerView
            recyclerView?.let { rv ->
                for (i in 0 until rv.childCount) {
                    val child = rv.getChildAt(i)
                    val viewHolder = rv.getChildViewHolder(child)
                    when (viewHolder) {
                        is PhotoPagerAdapter.VideoViewHolder -> {
                            viewHolder.releasePlayer()
                        }
                        is GifViewHolder -> {
                            viewHolder.release()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 全屏状态改变时的回调方法
     * @param isFullscreen 是否进入全屏模式
     */
    fun onFullscreenChanged(isFullscreen: Boolean) {
        if (isFullscreen) {
            // 进入全屏时，设置为横屏
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            // 退出全屏时，恢复为竖屏
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /**
     * 隐藏系统UI（状态栏和导航栏）
     */
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 使用WindowInsetsController隐藏状态栏和导航栏
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // 兼容旧版本
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    /**
     * 图片查看器的适配器
     */
    inner class PhotoPagerAdapter(private val imageUris: List<Uri>) : 
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        private var lastPosition = -1

        private val VIEW_TYPE_PHOTO = 1
        private val VIEW_TYPE_VIDEO = 2
        private val VIEW_TYPE_GIF = 3

        private fun isVideo(uri: Uri): Boolean {
            val type = contentResolver.getType(uri)
            return type?.startsWith("video") == true
        }

        private fun isGif(uri: Uri): Boolean {
            val type = contentResolver.getType(uri)
            return type == "image/gif"
        }

        override fun getItemViewType(position: Int): Int {
            val uri = imageUris[position]
            return when {
                isVideo(uri) -> VIEW_TYPE_VIDEO
                isGif(uri) -> VIEW_TYPE_GIF
                else -> VIEW_TYPE_PHOTO
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_VIDEO -> {
                    val view = layoutInflater.inflate(R.layout.item_video_view, parent, false)
                    VideoViewHolder(view)
                }
                VIEW_TYPE_GIF -> {
                    val view = layoutInflater.inflate(R.layout.item_gif_view, parent, false)
                    GifViewHolder(view)
                }
                else -> {
                    val view = layoutInflater.inflate(R.layout.item_photo_view, parent, false)
                    PhotoViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val adapterPosition = holder.adapterPosition
            val uri = imageUris[adapterPosition]
            when (holder) {
                is PhotoViewHolder -> {
                    setupPhotoView(holder.photoView)
                    holder.setCurrentUri(uri)
                    loadImageWithDynamicResolution(holder.photoView, uri, holder)
                }
                is VideoViewHolder -> {
                    holder.releasePlayer()
                    val player = SimpleExoPlayer.Builder(this@PhotoViewerActivity).build().apply {
                        setMediaItem(MediaItem.Builder().setUri(uri).build())
                        prepare()
                        // 只有当前显示的页面才自动播放
                        playWhenReady = adapterPosition == viewPager.currentItem
                        val key = uri.toString()
                        val last = playbackPositions[key] ?: 0L
                        if (last > 0L) seekTo(last)
                    }
                    holder.player = player
                    holder.playerView.player = player
                    // 移除自定义点击事件，让ExoPlayer的默认控制器处理播放/暂停
                    // 确保进度条和控制按钮可以正常工作
                    holder.playerView.setShowFastForwardButton(false)
                    holder.playerView.setShowRewindButton(false)
                    holder.playerView.setShowNextButton(false)
                    holder.playerView.setShowPreviousButton(false)
                    holder.playerView.setControllerShowTimeoutMs(3000) // 3秒后自动隐藏控制器
                }
                is GifViewHolder -> {
                    holder.setCurrentUri(uri)
                    // GIF自动播放
                    holder.playGif(adapterPosition == viewPager.currentItem)
                }
            }
            lastPosition = adapterPosition
        }

        override fun getItemCount(): Int {
            return imageUris.size
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            when (holder) {
                is VideoViewHolder -> {
                    holder.player?.let { p ->
                        val pos = p.currentPosition
                        val uri = imageUris[holder.bindingAdapterPosition.takeIf { it >= 0 } ?: 0]
                        playbackPositions[uri.toString()] = pos
                    }
                    holder.releasePlayer()
                }
                is GifViewHolder -> {
                    holder.release()
                }
            }
            super.onViewRecycled(holder)
        }

        /**
         * 设置PhotoView的属性，针对高分辨率图片优化缩放倍率
         */
        private fun setupPhotoView(photoView: PhotoView) {
            // 先设置基本的PhotoView功能，避免延迟
            photoView.setZoomable(true)
            photoView.isEnabled = true
            photoView.setZoomTransitionDuration(300)
            
            // 设置默认缩放倍率，避免用户缩放时回弹 - 参考PictureSelector
            photoView.minimumScale = 1.0f
            photoView.mediumScale = 1.75f // 参考PictureSelector的DEFAULT_MID_SCALE
            photoView.maximumScale = 20.0f
            
            // 延迟设置精确的缩放参数，避免与用户操作冲突
            photoView.post {
                setupPhotoViewScaling(photoView)
            }
        }
        
        private fun setupPhotoViewScaling(photoView: PhotoView) {
            // 获取图片信息，动态设置缩放倍率
            val drawable = photoView.drawable
            if (drawable != null) {
                val imageWidth = drawable.intrinsicWidth
                val imageHeight = drawable.intrinsicHeight
                
                if (imageWidth > 0 && imageHeight > 0) {
                    // 计算图片的宽高比
                    val aspectRatio = maxOf(imageWidth, imageHeight).toFloat() / minOf(imageWidth, imageHeight).toFloat()
                    val isLongImage = aspectRatio > 3.0f || aspectRatio < 1.0f / 3.0f
            
                    // 获取屏幕尺寸
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels
                    
                    // 根据图片类型和尺寸动态计算最大缩放倍率
                    val maxScale = calculateOptimalMaxScale(imageWidth, imageHeight, screenWidth, screenHeight, isLongImage)
                    
                    // 只有在当前缩放倍率不超过新的最大缩放倍率时才更新
                    if (photoView.scale <= maxScale) {
                        photoView.maximumScale = maxScale
                    }
                    
                    android.util.Log.d("PhotoViewer", "Image: ${imageWidth}x${imageHeight}, AspectRatio: $aspectRatio, MaxScale: $maxScale")
                } else {
                    // 如果无法获取图片尺寸，使用更大的默认缩放倍率
                    photoView.maximumScale = 20.0f
                }
            } else {
                // 默认缩放倍率 - 大幅提升
                photoView.maximumScale = 20.0f
            }
            
            // 设置双击缩放行为 - 使用简化的逻辑
            val attacher = photoView.getAttacher()
            if (attacher is PhotoViewAttacher) {
                attacher.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        try {
                            val x = e.x
                            val y = e.y
                            
                            // 优化的双击缩放逻辑 - 参考PictureSelector
                            when {
                                photoView.scale > photoView.mediumScale -> {
                                    // 如果已经放大超过mediumScale，则回到原始大小
                                    photoView.setScale(1.0f, x, y, true)
                                }
                                photoView.scale > 1.0f -> {
                                    // 如果已经放大但未超过mediumScale，则放大到mediumScale
                                    photoView.setScale(photoView.mediumScale, x, y, true)
                                }
                                else -> {
                                    // 如果未放大，则放大到mediumScale
                                    photoView.setScale(photoView.mediumScale, x, y, true)
                                }
                            }
                        } catch (ex: Exception) {
                            // 异常处理
                            photoView.setScale(1.0f, true)
                        }
                        return true
                    }
                    
                    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                        return false
                    }
                    
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        // 单击退出
                        finish()
                        return true
                    }
                })
            }
        }
        
        /**
         * 检测当前设备是否运行HarmonyOS系统
         * 通过检查系统属性来判断
         */
        private fun isHarmonyOS(): Boolean {
            return try {
                val buildExClass = Class.forName("com.huawei.system.BuildEx")
                val osBrandField = buildExClass.getDeclaredField("OS_BRAND")
                osBrandField.isAccessible = true
                val brand = osBrandField.get(null).toString()
                "harmony".equals(brand, ignoreCase = true)
            } catch (e: Exception) {
                // 没有找到HarmonyOS特有的类，不是HarmonyOS
                false
            }
        }
        
        /**
         * 检测图片是否为长条状
         * 长条状图片定义为宽高比大于2:1或小于1:2的图片
         */
        private fun isLongImage(photoView: PhotoView): Boolean {
            return try {
                val drawable = photoView.drawable
                if (drawable != null) {
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight
                    
                    if (width > 0 && height > 0) {
                        val aspectRatio = width.toFloat() / height.toFloat()
                        // 宽高比大于3:1或小于1:3认为是长条状
                        aspectRatio > 2.0f || aspectRatio < 1.0f / 2.0f
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                // 如果检测失败，默认不是长条状
                false
            }
        }
        
        /**
         * 获取长条图片的缩放倍率
         * 根据图片的宽高比动态计算合适的最大缩放倍率
         */
        private fun getLongImageZoomScale(photoView: PhotoView): Float {
            return try {
                val drawable = photoView.drawable
                if (drawable != null) {
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight
                    
                    if (width > 0 && height > 0) {
                        val aspectRatio = width.toFloat() / height.toFloat()
                        
                        // 根据宽高比计算缩放倍率
                        when {
                            aspectRatio > 5.0f || aspectRatio < 1.0f / 5.0f -> {
                                // 极长条图片：允许更大的缩放倍率
                                12.0f
                            }
                            aspectRatio > 3.0f || aspectRatio < 1.0f / 3.0f -> {
                                // 长条图片：较大的缩放倍率
                                8.0f
                            }
                            aspectRatio > 2.0f || aspectRatio < 1.0f / 2.0f -> {
                                // 中等长条图片：适中的缩放倍率
                                6.0f
                            }
                            else -> {
                                // 普通图片：标准缩放倍率
                                3.0f
                            }
                        }
                    } else {
                        3.0f
                    }
                } else {
                    3.0f
                }
            } catch (e: Exception) {
                3.0f
            }
        }
        
        /**
         * 获取长条图片的中间缩放级别
         * 用于双击缩放时的中间状态
         */
        private fun getLongImageMidScale(photoView: PhotoView): Float {
            return try {
                val drawable = photoView.drawable
                if (drawable != null) {
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight
                    
                    if (width > 0 && height > 0) {
                        val aspectRatio = width.toFloat() / height.toFloat()
                        
                        // 根据宽高比计算中间缩放级别
                        when {
                            aspectRatio > 5.0f || aspectRatio < 1.0f / 5.0f -> {
                                // 极长条图片：较大的中间缩放
                                4.0f
                            }
                            aspectRatio > 3.0f || aspectRatio < 1.0f / 3.0f -> {
                                // 长条图片：适中的中间缩放
                                3.0f
                            }
                            aspectRatio > 2.0f || aspectRatio < 1.0f / 2.0f -> {
                                // 中等长条图片：标准中间缩放
                                2.5f
                            }
                            else -> {
                                // 普通图片：标准中间缩放
                                2.0f
                            }
                        }
                    } else {
                        2.0f
                    }
                } else {
                    2.0f
                }
            } catch (e: Exception) {
                2.0f
            }
        }

        /**
         * 加载图片
         */
        private fun loadImage(photoView: PhotoView, uri: Uri) {
            // 使用Coil加载图片
            photoView.load(uri) {
                crossfade(true)
                error(R.drawable.ic_launcher_foreground) // 设置错误图片
                placeholder(R.drawable.ic_launcher_foreground) // 设置占位图片
            }
        }
        
        /**
         * 高分辨率图片加载方法，支持渐进式加载和内存优化
         */
        private fun loadImageWithDynamicResolution(photoView: PhotoView, uri: Uri, holder: PhotoViewHolder) {
            // 首先获取图片信息，判断是否为高分辨率图片
            getImageInfo(uri) { imageInfo ->
                val isHighResolution = imageInfo.width > 2048 || imageInfo.height > 2048
                val isUltraHighResolution = imageInfo.width > 4096 || imageInfo.height > 4096
                
                if (isHighResolution) {
                    // 高分辨率图片：使用渐进式加载
                    loadHighResolutionImage(photoView, uri, imageInfo, isUltraHighResolution, holder)
                } else {
                    // 普通分辨率图片：直接加载
                    loadStandardImage(photoView, uri)
                }
            }
        }
        
        /**
         * 获取图片信息
         */
        private fun getImageInfo(uri: Uri, callback: (ImageInfo) -> Unit) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()
                
                callback(ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    mimeType = options.outMimeType ?: "image/jpeg"
                ))
            } catch (e: Exception) {
                android.util.Log.e("PhotoViewer", "Failed to get image info: ${e.message}")
                // 使用默认信息
                callback(ImageInfo(0, 0, "image/jpeg"))
            }
        }
        
        /**
         * 加载高分辨率图片
         */
        private fun loadHighResolutionImage(photoView: PhotoView, uri: Uri, imageInfo: ImageInfo, isUltraHigh: Boolean, holder: PhotoViewHolder) {
            // 检查内存使用情况，决定加载策略
            val memoryUsage = getMemoryUsage()
            val shouldLoadHighRes = memoryUsage < 0.8f // 内存使用率低于80%时才加载高分辨率
            
            // 先加载低分辨率预览图
            photoView.load(uri) {
                crossfade(true)
                error(R.drawable.ic_launcher_foreground)
                placeholder(R.drawable.ic_launcher_foreground)
                // 根据内存情况调整初始分辨率
                val initialSize = if (shouldLoadHighRes) 1024 else 512
                size(initialSize, initialSize)
                // 使用内存优化配置
                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                diskCachePolicy(coil.request.CachePolicy.ENABLED)
                // 根据内存情况调整图片质量
                val quality = when {
                    memoryUsage > 0.9f -> 0.6f
                    memoryUsage > 0.8f -> 0.7f
                    isUltraHigh -> 0.8f
                    else -> 0.9f
                }
                // 注意：Coil的quality方法可能需要不同的调用方式
                // 这里暂时注释掉，使用默认质量
                // this.quality(quality)
                
                listener(
                    onSuccess = { _, _ ->
                        // 图片加载完成后，延迟设置缩放参数避免与用户操作冲突
                        photoView.postDelayed({
                            setupPhotoViewScaling(photoView)
                        }, 100) // 100ms延迟，确保图片完全渲染
                        
                        // 根据内存情况决定是否加载高分辨率版本
                        if (shouldLoadHighRes) {
                            loadHighResolutionVersion(photoView, uri, imageInfo, holder)
                        } else {
                            android.util.Log.d("PhotoViewer", "Skipping high resolution load due to memory constraints")
                        }
                    },
                    onError = { _, _ ->
                        android.util.Log.e("PhotoViewer", "Failed to load preview image: $uri")
                    }
                )
            }
        }
        
        /**
         * 获取当前内存使用情况
         */
        private fun getMemoryUsage(): Float {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            return usedMemory.toFloat() / maxMemory.toFloat()
        }
        
        /**
         * 异步加载高分辨率版本
         */
        private fun loadHighResolutionVersion(photoView: PhotoView, uri: Uri, imageInfo: ImageInfo, holder: PhotoViewHolder) {
            // 记录高分辨率加载开始时间
            holder.highResLoadStartTime = System.currentTimeMillis()
            
            // 在后台线程加载高分辨率图片
            Thread {
                val startTime = System.currentTimeMillis()
                try {
                    val highResBitmap = loadHighResolutionBitmap(uri, imageInfo)
                    val loadTime = System.currentTimeMillis() - startTime
                    
                    runOnUiThread {
                        if (photoView.isAttachedToWindowCompat()) {
                            // 完整保存当前的缩放和显示状态
                            val currentScale = photoView.scale
                            val currentDisplayRect = photoView.displayRect
                            
                            // 平滑替换为高分辨率图片
                            photoView.setImageBitmap(highResBitmap)
                            
                            // 立即恢复缩放和平移状态，避免回弹
                            // 无论用户是否手动缩放，都恢复状态
                            if (currentScale > 1.0f && currentDisplayRect != null) {
                                // 使用 post 确保在图片完全渲染后再恢复，不使用动画避免回弹
                                photoView.post {
                                    photoView.setScale(currentScale, currentDisplayRect.centerX(), currentDisplayRect.centerY(), false)
                                }
                            }
                            
                            // 记录性能信息
                            val memoryUsage = getMemoryUsage()
                            android.util.Log.d("PhotoViewer", 
                                "High resolution image loaded successfully in ${loadTime}ms, " +
                                "Memory usage: ${(memoryUsage * 100).toInt()}%, " +
                                "Image size: ${imageInfo.width}x${imageInfo.height}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PhotoViewer", "Failed to load high resolution image: ${e.message}")
                }
            }.start()
        }
        
        /**
         * 加载高分辨率位图 - 优化版本，提供更高质量
         */
        private fun loadHighResolutionBitmap(uri: Uri, imageInfo: ImageInfo): Bitmap? {
            return try {
                val inputStream = contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    // 检测是否为长条图片
                    val aspectRatio = maxOf(imageInfo.width, imageInfo.height).toFloat() / minOf(imageInfo.width, imageInfo.height).toFloat()
                    val isLongImage = aspectRatio > 3.0f || aspectRatio < 1.0f / 3.0f
                    
                    // 根据图片类型和大小计算合适的采样率 - 使用更高质量
                    val maxDimension = maxOf(imageInfo.width, imageInfo.height)
                    val targetSize = when {
                        isLongImage && maxDimension > 12288 -> 8192 // 超长条图片使用更高分辨率
                        isLongImage && maxDimension > 8192 -> 6144 // 长条图片使用更大的目标尺寸
                        maxDimension > 8192 -> 6144 // 超高分辨率图片
                        isLongImage -> 4096 // 长条图片使用更高分辨率
                        else -> 3072 // 普通图片也使用较高分辨率
                    }
                    
                    inSampleSize = calculateOptimalSampleSize(imageInfo.width, imageInfo.height, targetSize)
                    inJustDecodeBounds = false
                    
                    // 根据图片类型选择配置 - 优先使用高质量
                    inPreferredConfig = when {
                        isLongImage && maxDimension > 12288 -> Bitmap.Config.RGB_565 // 超长条图片使用内存优化
                        isLongImage -> Bitmap.Config.ARGB_8888 // 长条图片使用高质量
                        else -> Bitmap.Config.ARGB_8888 // 普通图片使用最高质量
                    }
                    
                    inDither = true
                    inScaled = true
                    inPurgeable = false // 禁用可回收，保证质量
                    inInputShareable = false // 禁用共享，保证质量
                }
                
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()
                
                // 记录加载信息
                if (bitmap != null) {
                    val aspectRatio = maxOf(imageInfo.width, imageInfo.height).toFloat() / minOf(imageInfo.width, imageInfo.height).toFloat()
                    android.util.Log.d("PhotoViewer", 
                        "High-res bitmap loaded: ${bitmap.width}x${bitmap.height}, " +
                        "Original: ${imageInfo.width}x${imageInfo.height}, " +
                        "AspectRatio: $aspectRatio, " +
                        "Config: ${bitmap.config}"
                    )
                }
                
                bitmap
            } catch (e: Exception) {
                android.util.Log.e("PhotoViewer", "Error loading high resolution bitmap: ${e.message}")
                null
            }
        }
        
        /**
         * 计算最优采样率
         */
        private fun calculateOptimalSampleSize(width: Int, height: Int, targetSize: Int): Int {
            // 如果targetSize为0，表示使用原始分辨率
            if (targetSize <= 0) return 1
            
            var sampleSize = 1
            val maxDimension = maxOf(width, height)
            
            while (maxDimension / sampleSize > targetSize) {
                sampleSize *= 2
            }
            
            return sampleSize
        }
        
        /**
         * 计算最优最大缩放倍率 - 直接使用设置的最大值
         */
        private fun calculateOptimalMaxScale(imageWidth: Int, imageHeight: Int, screenWidth: Int, screenHeight: Int, isLongImage: Boolean): Float {
            val maxImageDimension = maxOf(imageWidth, imageHeight)
            
            return when {
                // 超高分辨率图片（8K及以上）
                maxImageDimension >= 7680 -> {
                    if (isLongImage) 50.0f else 40.0f
                }
                // 高分辨率图片（4K）
                maxImageDimension >= 3840 -> {
                    if (isLongImage) 40.0f else 30.0f
                }
                // 中等高分辨率图片（2K）
                maxImageDimension >= 2048 -> {
                    if (isLongImage) 30.0f else 25.0f
                }
                // 标准分辨率图片（1080p）
                maxImageDimension >= 1920 -> {
                    if (isLongImage) 100.0f else 80.0f
                }
                // 中等分辨率图片（720p）
                maxImageDimension >= 1280 -> {
                    if (isLongImage) 80.0f else 60.0f
                }
                // 低分辨率图片
                else -> {
                    if (isLongImage) 30.0f else 24.0f
                }
            }
        }
        
        /**
         * 加载标准分辨率图片
         */
        private fun loadStandardImage(photoView: PhotoView, uri: Uri) {
                    photoView.load(uri) {
                        crossfade(true)
                        error(R.drawable.ic_launcher_foreground)
                        placeholder(R.drawable.ic_launcher_foreground)
                listener(
                    onSuccess = { _, _ ->
                        // 图片加载完成后，延迟设置缩放参数避免与用户操作冲突
                        photoView.postDelayed({
                            setupPhotoViewScaling(photoView)
                        }, 100) // 100ms延迟，确保图片完全渲染
                    },
                    onError = { _, _ ->
                        android.util.Log.e("PhotoViewer", "Failed to load standard image: $uri")
                    }
                )
            }
        }
        

        /**
         * 重置上一个页面的缩放状态
         */
        fun resetPreviousPhotoViewZoom() {
            val recyclerView = viewPager.getChildAt(0) as? RecyclerView
            recyclerView?.let {
                val previousHolder = it.findViewHolderForAdapterPosition(lastPosition)
                if (previousHolder is PhotoViewHolder && previousHolder.adapterPosition != viewPager.currentItem) {
                    previousHolder.photoView.scale = 1.0f // 恢复默认缩放
                }
            }
        }

        /**
         * 图片ViewHolder
         */
        inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val photoView: PhotoView = itemView.findViewById(R.id.photo_view)
            private var currentUri: Uri? = null
            private var isHighResolutionLoaded = false
            private var lastLoadedScale = 0f
            internal var userHasZoomed = false
            internal var highResLoadStartTime = 0L
            
            init {
                // 确保PhotoView的基本功能正常
                photoView.setZoomable(true)
                photoView.isEnabled = true
                // 设置缩放监听器，实现动态分辨率切换
                setupZoomListener()
                
                // 延迟加载高分辨率图片 - 增加延迟时间，避免与初始加载冲突
                photoView.postDelayed({
                    currentUri?.let { uri ->
                        // 只有在用户没有主动缩放的情况下才自动加载高分辨率
                        if (!userHasZoomed) {
                            android.util.Log.d("PhotoViewer", "Auto-loading high resolution image")
                            loadUltraHighResolutionImage(uri, photoView.scale)
                        } else {
                            android.util.Log.d("PhotoViewer", "Skip auto-loading, user has zoomed")
                        }
                    }
                }, 2000) // 增加到2秒后自动加载，避免过早触发
            }
            
            /**
             * 设置缩放监听器 - 实现动态分辨率切换
             */
            private fun setupZoomListener() {
                val attacher = photoView.getAttacher()
                if (attacher is PhotoViewAttacher) {
                    attacher.setOnScaleChangeListener { scaleFactor, focusX, focusY ->
                        // 禁用动态分辨率切换，避免回弹问题
                        // 只在用户主动缩放时记录当前缩放级别
                        lastLoadedScale = scaleFactor
                        // 标记用户已经手动缩放
                        userHasZoomed = true
                    }
                }
            }
            
            /**
             * 加载超高分辨率图片
             */
            private fun loadUltraHighResolutionImage(uri: Uri, currentScale: Float) {
                isHighResolutionLoaded = true
                lastLoadedScale = currentScale
                
                // 在后台线程加载超高分辨率图片
                Thread {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                        inputStream?.close()
                        
                        // 根据当前缩放级别计算目标分辨率 - 降低原始分辨率触发阈值
                        val targetSize = when {
                            currentScale >= 10.0f -> 0 // 高缩放级别：使用原始分辨率
                            currentScale >= 8.0f -> 12288 // 极高缩放级别
                            currentScale >= 6.0f -> 10240 // 超高缩放级别
                            currentScale >= 4.0f -> 8192 // 超高缩放级别
                            currentScale >= 2.0f -> 6144 // 高缩放级别
                            else -> 4096
                        }
                        
                        // 重新打开输入流加载高分辨率图片
                        val highResInputStream = contentResolver.openInputStream(uri)
                        val highResOptions = BitmapFactory.Options().apply {
                            inSampleSize = calculateOptimalSampleSize(options.outWidth, options.outHeight, targetSize)
                            inJustDecodeBounds = false
                            inPreferredConfig = Bitmap.Config.ARGB_8888 // 使用最高质量
                            inDither = true
                            inScaled = true
                            inPurgeable = false
                            inInputShareable = false
                        }
                        
                        val highResBitmap = BitmapFactory.decodeStream(highResInputStream, null, highResOptions)
                        highResInputStream?.close()
                        
                        runOnUiThread {
                            if (photoView.isAttachedToWindowCompat() && highResBitmap != null) {
                                // 完整保存当前的缩放和显示状态
                                val currentScale = photoView.scale
                                val currentDisplayRect = photoView.displayRect
                                
                                // 替换为高分辨率图片
                                photoView.setImageBitmap(highResBitmap)
                                
                                // 立即恢复缩放状态，使用 PhotoView 的原生方法
                                if (currentScale > 1.0f && currentDisplayRect != null) {
                                    // 使用 post 确保在图片完全渲染后再恢复，不使用动画避免回弹
                                    photoView.post {
                                        photoView.setScale(currentScale, currentDisplayRect.centerX(), currentDisplayRect.centerY(), false)
                                    }
                                }
                                
                                val resolutionType = if (targetSize <= 0) "ORIGINAL" else "UPSCALED"
                                android.util.Log.d("PhotoViewer", 
                                    "Dynamic resolution upgrade: ${highResBitmap.width}x${highResBitmap.height} " +
                                    "for scale: $currentScale, targetSize: $targetSize, type: $resolutionType"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PhotoViewer", "Error loading ultra-high resolution image: ${e.message}")
                    }
                }.start()
            }
            
            /**
             * 长条图片实时分辨率切换
             */
            private fun loadLongImageWithRealTimeResolution(currentScale: Float) {
                currentUri?.let { uri ->
                    val drawable = photoView.drawable
                    if (drawable != null) {
                        val width = drawable.intrinsicWidth
                        val height = drawable.intrinsicHeight
                        if (width > 0 && height > 0) {
                            val aspectRatio = width.toFloat() / height.toFloat()
                            
                            // 根据当前缩放级别实时加载对应分辨率的图片
                            SimplifiedImageLoaderHelper.loadLongImageWithRealTimeResolution(
                                this@PhotoViewerActivity,
                                uri,
                                aspectRatio,
                                currentScale,
                                onSuccess = { bitmap ->
                                    // 平滑切换图片，保持缩放状态
                                    val currentScaleX = photoView.scaleX
                                    val currentScaleY = photoView.scaleY
                                    val currentTranslationX = photoView.translationX
                                    val currentTranslationY = photoView.translationY
                                    
                                    photoView.setImageBitmap(bitmap)
                                    
                                    // 恢复缩放和平移状态
                                    photoView.scaleX = currentScaleX
                                    photoView.scaleY = currentScaleY
                                    photoView.translationX = currentTranslationX
                                    photoView.translationY = currentTranslationY
                                },
                                onError = { error ->
                                    android.util.Log.e("PhotoViewer", "Failed to load long image with real-time resolution: $error")
                                }
                            )
                        }
                    }
                }
            }
            
            /**
             * 加载高分辨率图片
             */
            private fun loadHighResolutionImage(uri: Uri) {
                isHighResolutionLoaded = true
                SimplifiedImageLoaderHelper.loadImageWithDynamicResolution(
                    this@PhotoViewerActivity,
                    uri,
                    photoView.scale,
                    onSuccess = { bitmap ->
                        // 在主线程更新图片
                        photoView.setImageBitmap(bitmap)
                    },
                    onError = { error ->
                        // 如果加载失败，保持当前图片不变
                        android.util.Log.e("PhotoViewer", "Failed to load high resolution image: $error")
                    }
                )
            }
            
            /**
             * 设置当前URI
             */
            fun setCurrentUri(uri: Uri) {
                currentUri = uri
                isHighResolutionLoaded = false
                lastLoadedScale = 0f // 重置缩放级别记录
                userHasZoomed = false // 重置用户缩放标记
            }
        }

        inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val playerView: PlayerView = itemView.findViewById(R.id.player_view)
            val fullscreenButton: ImageButton = itemView.findViewById(R.id.fullscreen_button)
            var player: SimpleExoPlayer? = null
            
            init {
                // 设置全屏按钮点击事件
                fullscreenButton.setOnClickListener {
                    toggleFullscreen()
                }
            }
            
            private fun toggleFullscreen() {
                val activity = this@PhotoViewerActivity
                val window = activity.window
                val decorView = window.decorView
                
                // 判断是否是HarmonyOS设备
                val isHarmonyOS = try {
                    val buildExClass = Class.forName("com.huawei.system.BuildEx")
                    val osBrandField = buildExClass.getDeclaredField("OS_BRAND")
                    osBrandField.isAccessible = true
                    val brand = osBrandField.get(null).toString()
                    "harmony".equals(brand, ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
                
                // 判断是否是旧机型
                val isOldDevice = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                
                try {
                    if (isFullscreen) {
                        // 退出全屏
                        val attrs = activity.window.attributes
                        attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
                        window.attributes = attrs
                        
                        // 根据系统版本适配UI隐藏方式
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11及以上版本
                            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        } else {
                            // 旧版本Android
                            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        }
                        fullscreenButton.setImageResource(android.R.drawable.ic_menu_crop)
                    } else {
                        // 进入全屏
                        val attrs = activity.window.attributes
                        attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                        window.attributes = attrs
                        
                        // 根据系统版本和设备类型适配UI隐藏方式
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11及以上版本
                            window.insetsController?.apply {
                                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                        } else if (isHarmonyOS) {
                            // HarmonyOS特定适配
                            decorView.systemUiVisibility = (
                                View.SYSTEM_UI_FLAG_IMMERSIVE
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                            )
                        } else if (isOldDevice) {
                            // 旧机型适配：使用更简单的UI隐藏方式
                            decorView.systemUiVisibility = (
                                View.SYSTEM_UI_FLAG_FULLSCREEN
                            )
                        } else {
                            // 标准Android设备
                            decorView.systemUiVisibility = (
                                View.SYSTEM_UI_FLAG_IMMERSIVE
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                            )
                        }
                        fullscreenButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    }
                    
                    // 通知Activity屏幕方向可能需要改变
                    activity.onFullscreenChanged(isFullscreen)
                } catch (e: Exception) {
                    // 捕获并处理可能的异常，确保在任何机型上都不会崩溃
                }
            }
            
            private val isFullscreen: Boolean
                get() {
                    val activity = this@PhotoViewerActivity
                    // 根据系统版本适配全屏检测方式
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11及以上版本
                        try {
                            val decorView = activity.window.decorView
                            val windowInsets = decorView.rootWindowInsets
                            windowInsets == null || 
                            (windowInsets.isVisible(WindowInsets.Type.statusBars()) && 
                             windowInsets.isVisible(WindowInsets.Type.navigationBars()))
                        } catch (e: Exception) {
                            // 如果使用WindowInsets失败，回退到旧的检测方式
                            val decorView = activity.window.decorView
                            val uiOptions = decorView.systemUiVisibility
                            uiOptions and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
                        }
                    } else {
                        // 旧版本Android
                        val decorView = activity.window.decorView
                        val uiOptions = decorView.systemUiVisibility
                        uiOptions and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
                    }
                }
            
            fun releasePlayer() {
                playerView.player = null
                player?.release()
                player = null
            }
        }
    }

    /**
     * 自定义页面变换器，实现挤压效果
     */
    /**
      * 自定义页面变换器，实现挤压效果并确保正确的层级关系
      */
    inner class PhotoPageTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val pageWidth = page.width
            
            // 参考PictureSelector的页面变换策略
            when {
                position < -1 -> {
                    // 完全不可见的左侧页面
                    page.alpha = 0f
                    page.scaleX = 0.8f
                    page.scaleY = 0.8f
                    page.translationX = -pageWidth * 0.5f
                    page.z = 0f
                }
                position <= 0 -> {
                    // 当前页面或即将显示的页面
                    val alpha = 1f + position // 从1到0
                    val scale = 1f + position * 0.1f // 从1到0.9
                    
                    page.alpha = alpha.coerceIn(0f, 1f)
                    page.scaleX = scale.coerceIn(0.8f, 1f)
                    page.scaleY = scale.coerceIn(0.8f, 1f)
                    page.translationX = pageWidth * -position * 0.3f // 轻微平移
                    page.z = (1f + position) * 10f // 层级递减
                }
                position <= 1 -> {
                    // 即将显示的右侧页面
                    val alpha = 1f - position // 从1到0
                    val scale = 1f - position * 0.1f // 从1到0.9
                    
                    page.alpha = alpha.coerceIn(0f, 1f)
                    page.scaleX = scale.coerceIn(0.8f, 1f)
                    page.scaleY = scale.coerceIn(0.8f, 1f)
                    page.translationX = pageWidth * -position * 0.3f // 轻微平移
                    page.z = (1f - position) * 10f // 层级递减
                }
                else -> {
                    // 完全不可见的右侧页面
                    page.alpha = 0f
                    page.scaleX = 0.8f
                    page.scaleY = 0.8f
                    page.translationX = pageWidth * 0.5f
                    page.z = 0f
                }
            }
        }
    }

    /**
     * GIF播放ViewHolder
     */
    inner class GifViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val photoView: PhotoView = itemView.findViewById(R.id.gif_image_view)
        private var currentUri: Uri? = null
        private var isPlaying = false
        private var gifDrawable: GifDrawable? = null

        init {
            // 设置PhotoView的基本属性
            photoView.setZoomable(true)
            photoView.isEnabled = true
        }

        fun setCurrentUri(uri: Uri) {
            // 如果URI改变了，重置播放状态
            if (currentUri != uri) {
                gifDrawable?.stop()
                gifDrawable = null
                photoView.setImageDrawable(null) // 清空PhotoView中的图片
                isPlaying = false
                android.util.Log.d("GifViewHolder", "URI changed, cleared previous GIF: $currentUri -> $uri")
            }
            currentUri = uri
        }

        fun playGif(shouldPlay: Boolean) {
            currentUri?.let { uri ->
                if (shouldPlay) {
                    // 需要播放GIF
                    if (gifDrawable == null) {
                        // 如果还没有加载，先加载GIF
                        Glide.with(this@PhotoViewerActivity)
                            .asGif()
                            .load(uri)
                            .into(object : CustomTarget<GifDrawable>() {
                                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                                    photoView.setImageDrawable(resource)
                                    gifDrawable = resource
                                    resource.start() // 开始播放GIF动画
                                    isPlaying = true
                                    android.util.Log.d("GifViewHolder", "GIF动画开始播放: $uri")
                                }

                                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                    gifDrawable?.stop()
                                    gifDrawable = null
                                    isPlaying = false
                                }

                                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                    android.util.Log.e("GifViewHolder", "GIF加载失败: $uri")
                                    isPlaying = false
                                }
                            })
                    } else {
                        // 已经加载了，直接开始/继续播放
                        if (!isPlaying) {
                            gifDrawable?.start()
                            isPlaying = true
                            android.util.Log.d("GifViewHolder", "GIF动画继续播放: $uri")
                        }
                    }
                } else {
                    // 不需要播放，但要确保图片已加载（预加载场景）
                    if (gifDrawable == null && photoView.drawable == null) {
                        // 图片还没加载，加载第一帧
                        Glide.with(this@PhotoViewerActivity)
                            .asGif()
                            .load(uri)
                            .into(object : CustomTarget<GifDrawable>() {
                                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                                    photoView.setImageDrawable(resource)
                                    gifDrawable = resource
                                    // 不启动动画，保持第一帧
                                    resource.stop()
                                    isPlaying = false
                                    android.util.Log.d("GifViewHolder", "GIF已加载但不播放（预加载）: $uri")
                                }

                                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                    gifDrawable?.stop()
                                    gifDrawable = null
                                    isPlaying = false
                                }

                                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                    android.util.Log.e("GifViewHolder", "GIF加载失败: $uri")
                                }
                            })
                    } else if (isPlaying) {
                        // 已经在播放，停止动画但保留图片
                        gifDrawable?.stop()
                        isPlaying = false
                        android.util.Log.d("GifViewHolder", "GIF动画已暂停，保留显示")
                    }
                }
            }
        }

        fun release() {
            gifDrawable?.stop()
            photoView.setImageDrawable(null)
            gifDrawable = null
            isPlaying = false
        }
    }
}