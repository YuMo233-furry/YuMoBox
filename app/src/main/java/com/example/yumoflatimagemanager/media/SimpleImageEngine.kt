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
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.yumoflatimagemanager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 简化的图片加载引擎
 * 参考PictureSelector的简洁实现，但保持原有功能：
 * - 支持动态列数切换
 * - 保持流畅动画效果
 * - 保持原有UI样式
 * - 简化复杂的多级缓存逻辑
 */
object SimpleImageEngine {
    
    private const val TAG = "SimpleImageEngine"
    
    // 根据网格列数动态计算图片尺寸
    fun getGridImageSize(columnCount: Int): Int {
        return when (columnCount) {
            2 -> 400  // 2列时使用较大尺寸
            3 -> 300  // 3列时使用中等尺寸
            4 -> 250  // 4列时使用较小尺寸
            5 -> 200  // 5列时使用小尺寸
            6 -> 180  // 6列时使用更小尺寸
            7 -> 160  // 7列时使用很小尺寸
            8 -> 150  // 8列时使用最小尺寸
            else -> 200 // 默认尺寸
        }
    }
    
    /**
     * 加载网格图片（用于相册列表）
     * 支持动态列数，根据列数调整图片尺寸
     */
    fun loadGridImage(context: Context, uri: String, imageView: ImageView, columnCount: Int = 4) {
        val size = getGridImageSize(columnCount)
        Glide.with(context)
            .load(uri)
            .override(size, size)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .into(imageView)
    }
    
    /**
     * 加载相册封面
     */
    fun loadAlbumCover(context: Context, uri: String, imageView: ImageView) {
        Glide.with(context)
            .asBitmap()
            .load(uri)
            .override(180, 180)
            .sizeMultiplier(0.5f) // 内存优化
            .transform(CenterCrop(), RoundedCorners(8))
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .into(imageView)
    }
    
    /**
     * 加载预览图片（用于大图查看）
     */
    fun loadPreviewImage(context: Context, uri: String, imageView: ImageView) {
        Glide.with(context)
            .load(uri)
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .into(imageView)
    }
    
    /**
     * 加载指定尺寸的图片
     */
    fun loadImage(
        context: Context, 
        uri: String, 
        imageView: ImageView, 
        maxWidth: Int, 
        maxHeight: Int
    ) {
        Glide.with(context)
            .load(uri)
            .override(maxWidth, maxHeight)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .into(imageView)
    }
    
    /**
     * 暂停请求（滑动优化）
     */
    fun pauseRequests(context: Context) {
        Glide.with(context).pauseRequests()
    }
    
    /**
     * 恢复请求（滑动优化）
     */
    fun resumeRequests(context: Context) {
        Glide.with(context).resumeRequests()
    }
    
    /**
     * 清理内存缓存
     */
    fun clearMemoryCache(context: Context) {
        Glide.get(context).clearMemory()
    }
    
    /**
     * 清理磁盘缓存
     */
    fun clearDiskCache(context: Context) {
        Glide.get(context).clearDiskCache()
    }
    
    /**
     * 创建优化的图片请求（保持原有接口兼容性）
     */
    fun createOptimizedImageRequest(
        context: Context,
        uri: Uri,
        isThumbnail: Boolean = false,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        columnCount: Int = 4
    ): ImageRequest {
        val size = if (isThumbnail) getGridImageSize(columnCount) else (targetWidth.takeIf { it > 0 } ?: 1024)
        
        return ImageRequest.Builder(context)
            .data(uri)
            .size(size, size)
            .crossfade(true)
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .build()
    }
    
    /**
     * 异步加载图片到Bitmap（用于Compose）
     * 保持原有的异步加载功能
     */
    suspend fun loadImageAsync(
        context: Context,
        uri: Uri,
        isThumbnail: Boolean = true,
        columnCount: Int = 4
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val size = if (isThumbnail) getGridImageSize(columnCount) else 1024
            // 使用简化的加载逻辑，但保持异步特性
            loadBitmapFromUri(context, uri, size, size)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从URI加载Bitmap的简化实现
     */
    private fun loadBitmapFromUri(context: Context, uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            // 计算采样率
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
            null
        }
    }
    
    /**
     * 计算采样率
     */
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
}
