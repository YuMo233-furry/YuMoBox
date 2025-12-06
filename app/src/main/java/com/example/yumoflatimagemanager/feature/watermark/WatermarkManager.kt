package com.example.yumoflatimagemanager.feature.watermark

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.WatermarkPreset
import com.example.yumoflatimagemanager.data.WatermarkSaveOption
import com.example.yumoflatimagemanager.data.WatermarkState
import com.example.yumoflatimagemanager.data.local.AppDatabase
import com.example.yumoflatimagemanager.media.WatermarkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * WatermarkManager - 处理水印的业务逻辑
 * 负责水印预设的数据库操作、水印应用到图片等
 */
class WatermarkManager(private val context: Context) {
    
    private val db by lazy { AppDatabase.get(context) }
    private val watermarkDao by lazy { db.watermarkDao() }
    
    /**
     * 获取所有水印预设的Flow
     */
    fun getAllPresetsFlow(): Flow<List<WatermarkPreset>> {
        return watermarkDao.getAllPresets()
    }
    
    /**
     * 插入新的水印预设
     */
    suspend fun insertPreset(preset: WatermarkPreset) {
        withContext(Dispatchers.IO) {
            watermarkDao.insertPreset(preset)
        }
    }
    
    /**
     * 更新水印预设
     */
    suspend fun updatePreset(preset: WatermarkPreset) {
        withContext(Dispatchers.IO) {
            watermarkDao.updatePreset(preset)
        }
    }
    
    /**
     * 删除水印预设
     */
    suspend fun deletePreset(preset: WatermarkPreset) {
        withContext(Dispatchers.IO) {
            watermarkDao.deletePreset(preset)
        }
    }
    
    /**
     * 保存水印预设（自动判断是插入还是更新）
     */
    suspend fun savePreset(preset: WatermarkPreset) {
        if (preset.id == 0L) {
            insertPreset(preset)
        } else {
            updatePreset(preset)
        }
    }
    
    /**
     * 计算水印锚点位置
     */
    fun calculateAnchorPosition(
        imageWidth: Int,
        imageHeight: Int,
        preset: WatermarkPreset,
        density: Float
    ): Pair<Float, Float> {
        return WatermarkUtils.calculateAnchorPosition(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            anchor = preset.anchor,
            offsetXDp = preset.offsetX,
            offsetYDp = preset.offsetY,
            density = density
        )
    }
    
    /**
     * 批量应用水印到图片
     * @return Result<Pair<成功数量, 新文件URI列表>>
     */
    suspend fun applyWatermarkBatch(
        images: List<ImageItem>,
        state: WatermarkState,
        saveOption: WatermarkSaveOption,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): Result<Pair<Int, List<String>>> {
        return withContext(Dispatchers.IO) {
            WatermarkUtils.applyWatermarkBatch(
                images = images,
                state = state,
                saveOption = saveOption,
                context = context,
                onProgress = onProgress
            )
        }
    }
    
    /**
     * 扫描新文件到媒体库
     */
    suspend fun scanNewFiles(fileUris: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                // 将URI转换为文件路径
                val filePaths = fileUris.mapNotNull { uriString ->
                    try {
                        val uri = Uri.parse(uriString)
                        val cursor = context.contentResolver.query(
                            uri,
                            arrayOf(MediaStore.Images.Media.DATA),
                            null,
                            null,
                            null
                        )
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val pathIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                                if (pathIndex >= 0) {
                                    it.getString(pathIndex)
                                } else null
                            } else null
                        }
                    } catch (e: Exception) {
                        Log.e("WatermarkManager", "Failed to get file path for URI: $uriString", e)
                        null
                    }
                }
                
                if (filePaths.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        filePaths.toTypedArray(),
                        null
                    ) { path, uri ->
                        Log.d("WatermarkManager", "Media scan completed for: $path")
                    }
                }
            } catch (e: Exception) {
                Log.e("WatermarkManager", "Failed to scan new files", e)
            }
        }
    }
}

