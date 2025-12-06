package com.example.yumoflatimagemanager.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.WatermarkAnchor
import com.example.yumoflatimagemanager.data.WatermarkSaveOption
import com.example.yumoflatimagemanager.data.WatermarkState
import com.example.yumoflatimagemanager.data.WatermarkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object WatermarkUtils {
    
    // 根据锚点计算初始位置
    fun calculateAnchorPosition(
        imageWidth: Int,
        imageHeight: Int,
        anchor: WatermarkAnchor,
        offsetXDp: Int,
        offsetYDp: Int,
        density: Float
    ): Pair<Float, Float> {
        val offsetX = offsetXDp * density
        val offsetY = offsetYDp * density
        
        return when (anchor) {
            WatermarkAnchor.TOP_LEFT -> Pair(offsetX, offsetY)
            WatermarkAnchor.TOP_CENTER -> Pair(imageWidth / 2f, offsetY)
            WatermarkAnchor.TOP_RIGHT -> Pair(imageWidth - offsetX, offsetY)
            WatermarkAnchor.CENTER_LEFT -> Pair(offsetX, imageHeight / 2f)
            WatermarkAnchor.CENTER -> Pair(imageWidth / 2f, imageHeight / 2f)
            WatermarkAnchor.CENTER_RIGHT -> Pair(imageWidth - offsetX, imageHeight / 2f)
            WatermarkAnchor.BOTTOM_LEFT -> Pair(offsetX, imageHeight - offsetY)
            WatermarkAnchor.BOTTOM_CENTER -> Pair(imageWidth / 2f, imageHeight - offsetY)
            WatermarkAnchor.BOTTOM_RIGHT -> Pair(imageWidth - offsetX, imageHeight - offsetY)
        }
    }
    
    // 应用水印到图片
    fun applyWatermark(
        src: Bitmap,
        state: WatermarkState,
        context: Context
    ): Bitmap {
        return when (state.preset.type) {
            WatermarkType.TEXT -> applyTextWatermark(src, state)
            WatermarkType.IMAGE -> applyImageWatermark(src, state, context)
        }
    }
    
    // 应用文字水印
    private fun applyTextWatermark(src: Bitmap, state: WatermarkState): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = state.preset.textSize
            color = state.preset.textColor
            alpha = state.currentAlpha
            isFakeBoldText = state.preset.textBold
            textAlign = Paint.Align.CENTER
        }
        
        val x = result.width * state.currentX
        val y = result.height * state.currentY
        
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(state.currentRotation)
        // 应用缩放 - 直接使用 state.currentScale
        canvas.scale(state.currentScale, state.currentScale)
        canvas.drawText(state.preset.text ?: "", 0f, 0f, paint)
        canvas.restore()
        
        return result
    }
    
    // 应用图片水印
    private fun applyImageWatermark(
        src: Bitmap,
        state: WatermarkState,
        context: Context
    ): Bitmap {
        val watermarkBitmap = loadWatermarkImage(state.preset.imageUri, context) ?: return src
        
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        // 根据预设比例缩放水印
        val targetWidth = (result.width * state.preset.imageScale).toInt()
        val scaleFactor = targetWidth.toFloat() / watermarkBitmap.width
        val targetHeight = (watermarkBitmap.height * scaleFactor).toInt()
        
        val scaledWatermark = Bitmap.createScaledBitmap(
            watermarkBitmap, targetWidth, targetHeight, true
        )
        
        val x = result.width * state.currentX - targetWidth / 2f
        val y = result.height * state.currentY - targetHeight / 2f
        
        val paint = Paint().apply { alpha = state.currentAlpha }
        
        canvas.save()
        canvas.translate(x + targetWidth / 2f, y + targetHeight / 2f)
        canvas.rotate(state.currentRotation)
        // 应用缩放 - 使用 state.currentScale
        canvas.scale(state.currentScale, state.currentScale)
        canvas.drawBitmap(scaledWatermark, -targetWidth / 2f, -targetHeight / 2f, paint)
        canvas.restore()
        
        scaledWatermark.recycle()
        watermarkBitmap.recycle()
        
        return result
    }
    
    // 批量应用水印
    suspend fun applyWatermarkBatch(
        images: List<ImageItem>,
        state: WatermarkState,
        saveOption: WatermarkSaveOption,
        context: Context,
        onProgress: (Int, Int, String) -> Unit
    ): Result<Pair<Int, List<String>>> = withContext(Dispatchers.IO) {
        var successCount = 0
        val newFiles = mutableListOf<String>()
        
        images.forEachIndexed { index, image ->
            try {
                val bitmap = context.contentResolver.openInputStream(image.uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inSampleSize = 1  // 不降采样
                        inJustDecodeBounds = false
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                } ?: throw IOException("Failed to load image")
                
                val watermarked = applyWatermark(bitmap, state, context)
                
                when (saveOption) {
                    WatermarkSaveOption.OVERWRITE -> {
                        // 覆盖原图
                        saveBitmapToUri(watermarked, image.uri, context)
                    }
                    WatermarkSaveOption.CREATE_NEW -> {
                        // 创建新文件
                        val newUri = createNewImageFile(image, context)
                        saveBitmapToUri(watermarked, newUri, context)
                        newFiles.add(newUri.toString())
                    }
                }
                
                bitmap.recycle()
                watermarked.recycle()
                successCount++
                
                onProgress(index + 1, images.size, image.name ?: "")
            } catch (e: Exception) {
                android.util.Log.e("WatermarkUtils", "Error applying watermark", e)
            }
        }
        
        Result.success(Pair(successCount, newFiles))
    }
    
    // 批量应用水印 - 使用ImageWatermarkParams
    suspend fun applyWatermarkBatchWithParams(
        imageParams: List<com.example.yumoflatimagemanager.data.ImageWatermarkParams>,
        preset: com.example.yumoflatimagemanager.data.WatermarkPreset,
        saveOption: WatermarkSaveOption,
        context: Context,
        onProgress: (Int, Int, String) -> Unit
    ): Result<Pair<Int, List<String>>> = withContext(Dispatchers.IO) {
        var successCount = 0
        val newFiles = mutableListOf<String>()
        
        android.util.Log.d("WatermarkUtils", "开始处理 ${imageParams.size} 张图片")
        
        imageParams.forEachIndexed { index, params ->
            try {
                android.util.Log.d("WatermarkUtils",
                    "处理图片$index: uri=${params.imageUri}, " +
                    "x=${params.watermarkX}, y=${params.watermarkY}, " +
                    "rotation=${params.watermarkRotation}, scale=${params.watermarkScale}"
                )
                
                val imageUri = Uri.parse(params.imageUri)
                val bitmap = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inSampleSize = 1  // 不降采样
                        inJustDecodeBounds = false
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                } ?: throw IOException("Failed to load image")
                
                val watermarked = applyWatermarkWithParams(bitmap, params, preset, context)
                
                when (saveOption) {
                    WatermarkSaveOption.OVERWRITE -> {
                        // 覆盖原图
                        saveBitmapToUri(watermarked, imageUri, context)
                    }
                    WatermarkSaveOption.CREATE_NEW -> {
                        // 创建新文件
                        val imageItem = com.example.yumoflatimagemanager.data.ImageItem(
                            id = "temp_${System.currentTimeMillis()}",
                            uri = imageUri,
                            name = imageUri.lastPathSegment ?: "watermarked_image.jpg",
                            albumId = "temp_album"
                        )
                        val newUri = createNewImageFile(imageItem, context)
                        saveBitmapToUri(watermarked, newUri, context)
                        newFiles.add(newUri.toString())
                    }
                }
                
                bitmap.recycle()
                watermarked.recycle()
                successCount++
                
                onProgress(index + 1, imageParams.size, imageUri.lastPathSegment ?: "")
            } catch (e: Exception) {
                android.util.Log.e("WatermarkUtils", "Error applying watermark", e)
            }
        }
        
        Result.success(Pair(successCount, newFiles))
    }
    
    // 使用ImageWatermarkParams应用水印
    private fun applyWatermarkWithParams(
        src: Bitmap,
        params: com.example.yumoflatimagemanager.data.ImageWatermarkParams,
        preset: com.example.yumoflatimagemanager.data.WatermarkPreset,
        context: Context
    ): Bitmap {
        return when (preset.type) {
            WatermarkType.TEXT -> applyTextWatermarkWithParams(src, params, preset)
            WatermarkType.IMAGE -> applyImageWatermarkWithParams(src, params, preset, context)
        }
    }
    
    // 使用ImageWatermarkParams应用文字水印
    private fun applyTextWatermarkWithParams(
        src: Bitmap,
        params: com.example.yumoflatimagemanager.data.ImageWatermarkParams,
        preset: com.example.yumoflatimagemanager.data.WatermarkPreset
    ): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        // 计算缩放补偿：如果预览图和原图分辨率不同，需要调整水印大小
        val resolutionScale = if (params.previewImageWidth > 0) {
            result.width.toFloat() / params.previewImageWidth
        } else {
            1.0f
        }
        
        // 调试日志
        android.util.Log.d("WatermarkUtils", 
            "保存水印 - imageWidth=${result.width}, previewWidth=${params.previewImageWidth}, " +
            "resolutionScale=$resolutionScale, preset.textSize=${preset.textSize}, " +
            "watermarkScale=${params.watermarkScale}"
        )
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = preset.textSize
            color = preset.textColor
            alpha = params.watermarkAlpha
            isFakeBoldText = preset.textBold
            textAlign = Paint.Align.CENTER
        }
        
        val x = result.width * params.watermarkX
        val y = result.height * params.watermarkY
        
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(params.watermarkRotation)
        // 应用用户调整的缩放和分辨率补偿
        canvas.scale(params.watermarkScale * resolutionScale, params.watermarkScale * resolutionScale)
        canvas.drawText(preset.text ?: "", 0f, 0f, paint)
        canvas.restore()
        
        return result
    }
    
    // 使用ImageWatermarkParams应用图片水印
    private fun applyImageWatermarkWithParams(
        src: Bitmap,
        params: com.example.yumoflatimagemanager.data.ImageWatermarkParams,
        preset: com.example.yumoflatimagemanager.data.WatermarkPreset,
        context: Context
    ): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val watermarkBitmap = loadWatermarkImage(preset.imageUri, context)
            ?: return result
        
        val targetWidth = (result.width * preset.imageScale * params.watermarkScale).toInt()
        val scaleFactor = targetWidth.toFloat() / watermarkBitmap.width
        val targetHeight = (watermarkBitmap.height * scaleFactor).toInt()
        
        val scaledWatermark = Bitmap.createScaledBitmap(
            watermarkBitmap, targetWidth, targetHeight, true
        )
        
        val x = result.width * params.watermarkX - targetWidth / 2f
        val y = result.height * params.watermarkY - targetHeight / 2f
        
        val paint = Paint().apply { alpha = params.watermarkAlpha }
        
        canvas.save()
        canvas.rotate(params.watermarkRotation, x + targetWidth / 2f, y + targetHeight / 2f)
        canvas.drawBitmap(scaledWatermark, x, y, paint)
        canvas.restore()
        
        scaledWatermark.recycle()
        watermarkBitmap.recycle()
        
        return result
    }
    
    // 创建新图片文件
    private fun createNewImageFile(original: ImageItem, context: Context): Uri {
        val fileName = "${original.name?.substringBeforeLast(".")}_watermark.png"
        
        // 获取原图片的文件夹路径
        val originalFolder = getImageFolderPath(original.uri, context)
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            // 将水印图片放在原图片的同一个文件夹中
            put(MediaStore.Images.Media.RELATIVE_PATH, originalFolder)
        }
        return context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: throw IOException("Failed to create new file")
    }
    
    // 获取图片的文件夹路径
    private fun getImageFolderPath(uri: Uri, context: Context): String {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.RELATIVE_PATH),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val pathIndex = it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    if (pathIndex >= 0) {
                        it.getString(pathIndex) ?: "Pictures"
                    } else "Pictures"
                } else "Pictures"
            } ?: "Pictures"
        } catch (e: Exception) {
            android.util.Log.e("WatermarkUtils", "Failed to get folder path", e)
            "Pictures"
        }
    }
    
    // 保存Bitmap到Uri
    private fun saveBitmapToUri(bitmap: Bitmap, uri: Uri, context: Context) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            // 使用 PNG 无损压缩，完美保留画质
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
    }
    
    // 加载水印图片
    private fun loadWatermarkImage(uriString: String?, context: Context): Bitmap? {
        if (uriString == null) return null
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = 1  // 不降采样
                    inJustDecodeBounds = false
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // 保留原有的简单方法以保持兼容性
    fun drawTextWatermark(
        src: Bitmap,
        text: String,
        sizePx: Float,
        alpha: Int,
        xRatio: Float,
        yRatio: Float,
        color: Int = Color.WHITE
    ): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.color = color
            this.alpha = alpha
        }
        val x = result.width * xRatio
        val y = result.height * yRatio
        canvas.drawText(text, x, y, paint)
        return result
    }
}


