package com.example.yumoflatimagemanager.media

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import coil.request.ImageRequest

/**
 * 视频缩略图处理工具类
 * 实现基于MediaMetadataRetriever的视频缩略图提取
 */
object VideoThumbnailHelper {
    private const val TAG = "VideoThumbnailHelper"
    private const val THUMBNAIL_SIZE = 256
    
    /**
     * 格式化视频时长
     * @param durationMs 视频时长（毫秒）
     * @return 格式化后的时长字符串，如：10:10 或 10:10:10
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    /**
     * 获取视频时长
     * @param context 上下文
     * @param uri 视频URI
     * @return 视频时长（毫秒），如果无法获取则返回0
     */
    fun getVideoDuration(context: Context, uri: Uri): Long {
        // 使用同步锁避免并发访问同一个视频文件
        val lockKey = uri.toString()
        return synchronized(lockKey) {
            var duration = 0L
            var retriever: MediaMetadataRetriever? = null
            var assetFileDescriptor: AssetFileDescriptor? = null
            
            try {
                retriever = MediaMetadataRetriever()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    retriever.setDataSource(context, uri)
                } else {
                    try {
                        assetFileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                        if (assetFileDescriptor != null) {
                            retriever.setDataSource(
                                assetFileDescriptor.fileDescriptor,
                                assetFileDescriptor.startOffset,
                                assetFileDescriptor.length
                            )
                        } else {
                            retriever.setDataSource(uri.toString())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "设置数据源失败: ${e.message}", e)
                        try {
                            retriever.setDataSource(uri.toString())
                        } catch (innerE: Exception) {
                            Log.e(TAG, "设置URI字符串数据源也失败: ${innerE.message}", innerE)
                            return@synchronized 0L
                        }
                    }
                }
                
                val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = durationString?.toLongOrNull() ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "获取视频时长失败: ${e.message}", e)
            } finally {
                // 确保资源被正确释放
                try {
                    assetFileDescriptor?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "关闭AssetFileDescriptor失败: ${e.message}")
                }
                
                try {
                    retriever?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "释放MediaMetadataRetriever失败: ${e.message}", e)
                }
            }
            
            duration
        }
    }
    
    /**
     * 获取视频缩略图
     * @param context 上下文
     * @param uri 视频URI
     * @return 视频缩略图Bitmap，如果无法生成则返回null
     */
    fun getVideoThumbnail(context: Context, uri: Uri): Bitmap? {
        // 首先检查缓存
        val thumbnailCache = VideoThumbnailCache.getInstance(context)
        val cachedBitmap = thumbnailCache.getThumbnail(uri)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            Log.d(TAG, "从缓存获取视频缩略图成功")
            return cachedBitmap
        }
        
        // 使用同步锁避免并发访问同一个视频文件
        val lockKey = uri.toString()
        synchronized(lockKey) {
            var bitmap: Bitmap? = null
            var retriever: MediaMetadataRetriever? = null
            var assetFileDescriptor: AssetFileDescriptor? = null
            
            try {
                retriever = MediaMetadataRetriever()
                
                // 设置视频数据源，添加详细日志
                Log.d(TAG, "尝试从URI获取视频缩略图: $uri")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    retriever.setDataSource(context, uri)
                    Log.d(TAG, "使用Android P及以上API设置数据源")
                } else {
                    // 兼容旧版Android系统
                    try {
                        assetFileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                        if (assetFileDescriptor != null) {
                            retriever.setDataSource(
                                assetFileDescriptor.fileDescriptor,
                                assetFileDescriptor.startOffset,
                                assetFileDescriptor.length
                            )
                            Log.d(TAG, "使用AssetFileDescriptor设置数据源成功")
                        } else {
                            Log.w(TAG, "AssetFileDescriptor为空，尝试直接设置URI")
                            // 备用方案：直接使用URI
                            retriever.setDataSource(uri.toString())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "设置AssetFileDescriptor失败: ${e.message}", e)
                        // 再次尝试使用URI
                        try {
                            retriever.setDataSource(uri.toString())
                            Log.d(TAG, "成功使用URI字符串设置数据源")
                        } catch (innerE: Exception) {
                            Log.e(TAG, "设置URI字符串数据源也失败: ${innerE.message}", innerE)
                            return null // 如果所有方法都失败，直接返回null
                        }
                    }
                }
                
                // 获取视频时长
                val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationString?.toLongOrNull() ?: 0
                Log.d(TAG, "视频时长: $duration 毫秒")
                
                // 首先尝试从首帧(0毫秒)获取缩略图
                // 如果首帧获取失败，再尝试其他时间点
                val timePoints = if (duration > 0) {
                    listOf(
                        0L,                // 首帧
                        duration / 4,      // 1/4处
                        duration / 2       // 中间
                    )
                } else {
                    listOf(0L, 1000L, 3000L) // 0毫秒、1秒、3秒
                }
                
                // 尝试不同的时间点提取缩略图
                for (timeMs in timePoints) {
                    try {
                        // 修复时间点计算：getFrameAtTime需要微秒，所以timeMs * 1000
                        bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        
                        if (bitmap != null && !bitmap.isRecycled) {
                            Log.d(TAG, "成功在时间点 $timeMs 毫秒提取到缩略图，尺寸: ${bitmap.width}x${bitmap.height}")
                            break // 成功获取后退出循环
                        } else {
                            Log.w(TAG, "在时间点 $timeMs 毫秒未能提取到缩略图")
                            bitmap = null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "提取时间点 $timeMs 毫秒的缩略图失败: ${e.message}")
                        bitmap = null
                    }
                }
                
                // 如果仍然没有获取到缩略图，尝试使用默认选项
                if (bitmap == null) {
                    Log.w(TAG, "尝试所有时间点后仍未获取到缩略图，使用默认选项")
                    try {
                        bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                        if (bitmap != null && !bitmap.isRecycled) {
                            Log.d(TAG, "使用默认选项成功提取到缩略图，尺寸: ${bitmap.width}x${bitmap.height}")
                        } else {
                            // 最后尝试：使用任意时间点
                            bitmap = retriever.getFrameAtTime()
                            if (bitmap != null && !bitmap.isRecycled) {
                                Log.d(TAG, "使用任意时间点成功提取到缩略图，尺寸: ${bitmap.width}x${bitmap.height}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "使用默认选项提取缩略图失败: ${e.message}")
                        bitmap = null
                    }
                }
                
                // 调整缩略图大小
                if (bitmap != null) {
                    bitmap = resizeBitmap(bitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                    Log.d(TAG, "调整后的缩略图尺寸: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e(TAG, "无法从视频中提取任何缩略图")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "获取视频缩略图失败: ${e.message}", e)
            } finally {
                // 确保资源被正确释放
                try {
                    assetFileDescriptor?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "关闭AssetFileDescriptor失败: ${e.message}")
                }
                
                try {
                    retriever?.release()
                    Log.d(TAG, "MediaMetadataRetriever已释放")
                } catch (e: Exception) {
                    Log.e(TAG, "释放MediaMetadataRetriever失败: ${e.message}")
                }
            }
            
            // 如果获取到了缩略图，将其缓存
            if (bitmap != null && !bitmap.isRecycled) {
                thumbnailCache.putThumbnail(uri, bitmap)
            }
            
            return bitmap
        }
    }
    
    /**
     * 调整位图大小
     * @param bitmap 原始位图
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @return 调整后的位图
     */
    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        Log.d(TAG, "调整前的Bitmap尺寸: ${width}x$height")
        
        // 计算缩放比例
        val scaleWidth = targetWidth.toFloat() / width
        val scaleHeight = targetHeight.toFloat() / height
        val scaleFactor = Math.min(scaleWidth, scaleHeight)
        
        Log.d(TAG, "缩放因子: $scaleFactor")
        
        // 创建缩放后的Bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            (width * scaleFactor).toInt(),
            (height * scaleFactor).toInt(),
            true
        )
        
        // 如果原始Bitmap与缩放后的不同，则回收原始Bitmap
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }
        
        return scaledBitmap
    }
    
    /**
     * 创建适合视频的ImageRequest
     * @param context 上下文
     * @param uri 视频URI
     * @return 配置好的ImageRequest
     */
    fun createVideoImageRequest(context: Context, uri: Uri): ImageRequest {
        return ImageRequest.Builder(context)
            .data(uri)
            .setParameter("videoFrameMillis", 1000) // 使用1秒处的帧
            .allowHardware(false) // 禁用硬件加速以提高兼容性
            .build()
    }
}