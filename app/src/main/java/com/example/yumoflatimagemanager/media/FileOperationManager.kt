package com.example.yumoflatimagemanager.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.Album
import ando.file.core.FileUtils
import java.io.File
import java.io.InputStream
import java.io.FileOutputStream
import kotlinx.coroutines.*

/**
 * 文件操作管理器
 * 负责处理图片的移动和复制操作
 */
object FileOperationManager {
    private const val TAG = "FileOperationManager"
    
    /**
     * 更新MediaStore，使文件在系统相册中可见
     */
    private fun updateMediaStore(context: Context, file: File, mimeType: String = "image/jpeg", originalDateAdded: Long? = null, originalDateModified: Long? = null) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.SIZE, file.length())
                
                // 使用原始时间，如果没有则使用文件时间
                put(MediaStore.Images.Media.DATE_ADDED, originalDateAdded ?: (file.lastModified() / 1000))
                put(MediaStore.Images.Media.DATE_MODIFIED, originalDateModified ?: (file.lastModified() / 1000))
            }
            
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            Log.d(TAG, "已更新MediaStore: ${file.name}, DATE_ADDED: ${originalDateAdded}, DATE_MODIFIED: ${originalDateModified}")
        } catch (e: Exception) {
            Log.e(TAG, "更新MediaStore失败: ${e.message}")
        }
    }
    
    /**
     * 从MediaStore删除文件记录
     */
    private fun removeFromMediaStore(context: Context, file: File) {
        try {
            val selection = "${MediaStore.Images.Media.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)
            context.contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            Log.d(TAG, "已从MediaStore删除: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "从MediaStore删除失败: ${e.message}")
        }
    }
    
    /**
     * 移动文件（保留元数据）
     */
    private suspend fun moveFileWithMetadata(context: Context, sourceUri: Uri, targetFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 获取原文件的时间信息
                val (originalDateAdded, originalDateModified) = getOriginalFileTimes(context, sourceUri)
                
                // 首先尝试获取源文件的实际路径
                val sourcePath = getFilePathFromUri(context, sourceUri)
                if (sourcePath != null) {
                    val sourceFile = File(sourcePath)
                    if (sourceFile.exists()) {
                        // 方法1: 尝试使用File.renameTo()（最简单且通常保留元数据）
                        if (sourceFile.renameTo(targetFile)) {
                            // 更新MediaStore，使用原始时间信息
                            updateMediaStore(context, targetFile, originalDateAdded = originalDateAdded, originalDateModified = originalDateModified)
                            // 从MediaStore删除原文件记录
                            removeFromMediaStore(context, sourceFile)
                            Log.d(TAG, "成功移动文件（使用renameTo，保留元数据和时间）: ${targetFile.name}")
                            return@withContext true
                        }
                        
                        // 方法2: 如果renameTo失败，尝试使用FileOperator库的copyFile方法
                        Log.w(TAG, "renameTo失败，尝试使用FileOperator复制: ${sourceFile.name}")
                        val copyResult = FileUtils.copyFile(sourceFile, targetFile.parent ?: "", targetFile.name)
                        if (copyResult != null) {
                            // 更新MediaStore，使用原始时间信息
                            updateMediaStore(context, targetFile, originalDateAdded = originalDateAdded, originalDateModified = originalDateModified)
                            
                            // 复制成功后删除原文件
                            val deleteResult = FileUtils.deleteFile(sourceFile)
                            if (deleteResult > 0) {
                                // 从MediaStore删除原文件记录
                                removeFromMediaStore(context, sourceFile)
                                Log.d(TAG, "成功移动文件（使用FileOperator，保留元数据和时间）: ${targetFile.name}")
                                return@withContext true
                            } else {
                                Log.w(TAG, "复制成功但无法删除原文件: ${sourceFile.name}")
                                return@withContext false
                            }
                        } else {
                            Log.e(TAG, "使用FileOperator复制文件失败: ${sourceFile.name}")
                        }
                    } else {
                        Log.e(TAG, "源文件不存在: $sourcePath")
                    }
                }
                
                // 方法3: 如果以上方法都失败，回退到字节流复制
                Log.w(TAG, "回退到字节流复制（可能丢失元数据）")
                val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
                if (inputStream == null) {
                    Log.e(TAG, "无法打开源文件流: $sourceUri")
                    return@withContext false
                }
                
                inputStream.use { input ->
                    val outputStream = FileOutputStream(targetFile)
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                // 更新MediaStore，使用原始时间信息
                updateMediaStore(context, targetFile, originalDateAdded = originalDateAdded, originalDateModified = originalDateModified)
                
                // 尝试删除原文件（如果可能的话）
                if (sourcePath != null) {
                    val sourceFile = File(sourcePath)
                    if (sourceFile.exists()) {
                        val deleteResult = FileUtils.deleteFile(sourceFile)
                        if (deleteResult > 0) {
                            // 从MediaStore删除原文件记录
                            removeFromMediaStore(context, sourceFile)
                            Log.d(TAG, "成功移动文件（字节流复制，保留时间）: ${targetFile.name}")
                        } else {
                            Log.w(TAG, "复制成功但无法删除原文件: ${sourceFile.name}")
                        }
                    }
                }
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "移动文件异常: ${e.message}")
                false
            }
        }
    }
    
    /**
     * 从URI获取实际的文件路径
     */
    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    // 对于content URI，尝试从MediaStore获取路径
                    val projection = arrayOf("_data")
                    val cursor = context.contentResolver.query(uri, projection, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val columnIndex = it.getColumnIndexOrThrow("_data")
                            it.getString(columnIndex)
                        } else null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取文件路径失败: ${e.message}")
            null
        }
    }
    
    /**
     * 从URI获取原文件的时间信息
     */
    private fun getOriginalFileTimes(context: Context, uri: Uri): Pair<Long?, Long?> {
        return try {
            when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        Pair(file.lastModified() / 1000, file.lastModified() / 1000)
                    } else {
                        Pair(null, null)
                    }
                }
                "content" -> {
                    // 从MediaStore获取时间信息
                    val projection = arrayOf(
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.DATE_MODIFIED
                    )
                    val cursor = context.contentResolver.query(uri, projection, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val dateAddedIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                            val dateModifiedIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                            val dateAdded = it.getLong(dateAddedIndex)
                            val dateModified = it.getLong(dateModifiedIndex)
                            Pair(dateAdded, dateModified)
                        } else {
                            Pair(null, null)
                        }
                    } ?: Pair(null, null)
                }
                else -> Pair(null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取原文件时间失败: ${e.message}")
            Pair(null, null)
        }
    }
    
    /**
     * 复制文件（不保留元数据，使用简单字节流复制）
     */
    private suspend fun copyFileWithoutMetadata(context: Context, sourceUri: Uri, targetFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
                if (inputStream == null) {
                    Log.e(TAG, "无法打开源文件流: $sourceUri")
                    return@withContext false
                }
                
                inputStream.use { input ->
                    val outputStream = FileOutputStream(targetFile)
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                // 对于复制操作，使用当前时间作为DATE_ADDED，保持原文件的DATE_MODIFIED
                val (_, originalDateModified) = getOriginalFileTimes(context, sourceUri)
                val currentTime = System.currentTimeMillis() / 1000
                
                // 更新MediaStore，使新文件在系统相册中可见
                updateMediaStore(context, targetFile, originalDateAdded = currentTime, originalDateModified = originalDateModified)
                
                Log.d(TAG, "成功复制文件: ${targetFile.name}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "复制文件失败: ${e.message}")
                false
            }
        }
    }
    
    /**
     * 删除文件到系统回收站
     */
    suspend fun deleteFiles(
        context: Context,
        images: List<ImageItem>,
        callback: OperationProgressCallback
    ): OperationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始删除 ${images.size} 个文件到回收站")
        
        var successCount = 0
        val failedFiles = mutableListOf<String>()
        
        try {
            // 异步并行删除文件
            val deleteJobs = images.mapIndexed { index, image ->
                async {
                    try {
                        val deleteSuccess = deleteFileToTrash(context, image.uri, image.name ?: "未知文件")
                        if (deleteSuccess) {
                            Log.d(TAG, "成功删除文件到回收站: ${image.name}")
                            Pair(true, null)
                        } else {
                            Log.e(TAG, "删除文件到回收站失败: ${image.name}")
                            Pair(false, "${image.name ?: "未知文件"}: 删除失败")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "删除文件异常: ${image.name}, 错误: ${e.message}")
                        Pair(false, "${image.name ?: "未知文件"}: ${e.message}")
                    }
                }
            }
            
            // 等待所有删除操作完成
            deleteJobs.forEachIndexed { index, job ->
                val (success, errorMessage) = job.await()
                callback.onProgress(index + 1, images.size, images[index].name ?: "未知文件")
                
                if (success) {
                    successCount++
                } else {
                    errorMessage?.let { failedFiles.add(it) }
                }
            }
            
            val result = OperationResult(
                success = successCount > 0,
                message = if (successCount == images.size) {
                    "成功删除 $successCount 个文件到回收站"
                } else {
                    "删除完成: 成功 $successCount 个，失败 ${failedFiles.size} 个"
                },
                successCount = successCount,
                totalCount = images.size,
                failedFiles = failedFiles
            )
            
            callback.onCompleted(result)
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "删除操作异常: ${e.message}")
            val result = OperationResult(
                success = false,
                message = "删除操作失败: ${e.message}"
            )
            callback.onCompleted(result)
            result
        }
    }
    
    /**
     * 删除单个文件到回收站（兼容多种机型）
     */
    private suspend fun deleteFileToTrash(context: Context, uri: Uri, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 方法1: 直接使用ContentResolver删除（最简单有效的方法）
                try {
                    val deleteResult = context.contentResolver.delete(uri, null, null)
                    if (deleteResult > 0) {
                        // 发送广播通知系统刷新
                        sendMediaStoreBroadcast(context, uri)
                        Log.d(TAG, "使用ContentResolver删除成功: $fileName")
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ContentResolver删除失败，尝试其他方法: ${e.message}")
                }
                
                // 方法2: 使用MediaStore的IS_PENDING机制 (Android 10+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        // 先标记为IS_PENDING=1
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val updateResult = context.contentResolver.update(uri, contentValues, null, null)
                        if (updateResult > 0) {
                            // 等待一下让系统处理
                            Thread.sleep(200)
                            
                            // 然后删除
                            val deleteResult = context.contentResolver.delete(uri, null, null)
                            if (deleteResult > 0) {
                                // 发送广播通知系统刷新
                                sendMediaStoreBroadcast(context, uri)
                                Log.d(TAG, "使用IS_PENDING删除成功: $fileName")
                                return@withContext true
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "IS_PENDING删除失败，尝试其他方法: ${e.message}")
                    }
                }
                
                // 方法3: 对于Android 9及以下，使用文件移动到回收站目录
                val filePath = getFilePathFromUri(context, uri)
                if (filePath != null) {
                    val sourceFile = File(filePath)
                    if (sourceFile.exists()) {
                        // 尝试移动到回收站目录
                        val trashResult = moveToTrashDirectory(context, sourceFile)
                        if (trashResult) {
                            // 从MediaStore删除记录并发送广播
                            removeFromMediaStore(context, sourceFile)
                            sendMediaStoreBroadcast(context, uri)
                            Log.d(TAG, "移动文件到回收站成功: $fileName")
                            return@withContext true
                        }
                    }
                }
                
                Log.e(TAG, "所有删除方法都失败: $fileName")
                false
                
            } catch (e: Exception) {
                Log.e(TAG, "删除文件到回收站异常: $fileName, ${e.message}")
                false
            }
        }
    }
    
    /**
     * 发送MediaStore广播通知系统刷新
     */
    private fun sendMediaStoreBroadcast(context: Context, uri: Uri) {
        try {
            // 发送媒体扫描广播
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = uri
            context.sendBroadcast(intent)
            
            // 发送媒体变更广播
            val mediaIntent = Intent(Intent.ACTION_MEDIA_MOUNTED)
            mediaIntent.data = Uri.parse("file://${Environment.getExternalStorageDirectory()}")
            context.sendBroadcast(mediaIntent)
            
            Log.d(TAG, "已发送MediaStore刷新广播")
        } catch (e: Exception) {
            Log.e(TAG, "发送MediaStore广播失败: ${e.message}")
        }
    }
    
    /**
     * 将文件移动到回收站目录（Android 9及以下）
     */
    private fun moveToTrashDirectory(context: Context, file: File): Boolean {
        return try {
            // 创建回收站目录
            val trashDir = File(context.getExternalFilesDir(null), ".trash")
            if (!trashDir.exists()) {
                trashDir.mkdirs()
            }
            
            // 生成唯一的文件名避免冲突
            val timestamp = System.currentTimeMillis()
            val extension = file.extension
            val baseName = file.nameWithoutExtension
            val trashFile = File(trashDir, "${baseName}_${timestamp}.${extension}")
            
            // 移动文件到回收站
            val moveResult = file.renameTo(trashFile)
            if (moveResult) {
                Log.d(TAG, "文件移动到回收站: ${file.name} -> ${trashFile.name}")
                true
            } else {
                Log.e(TAG, "移动文件到回收站失败: ${file.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "移动文件到回收站异常: ${e.message}")
            false
        }
    }
    
    /**
     * 操作结果数据类
     */
    data class OperationResult(
        val success: Boolean,
        val message: String,
        val successCount: Int = 0,
        val totalCount: Int = 0,
        val failedFiles: List<String> = emptyList()
    )
    
    /**
     * 操作进度回调接口
     */
    interface OperationProgressCallback {
        fun onProgress(current: Int, total: Int, fileName: String)
        fun onCompleted(result: OperationResult)
    }
    
    /**
     * 获取相册的物理路径
     */
    fun getAlbumPhysicalPath(albumName: String): String {
        return when (albumName) {
            "相机" -> "/storage/emulated/0/DCIM/Camera"
            "截图" -> "/storage/emulated/0/Pictures/Screenshots"
            "视频" -> "/storage/emulated/0/DCIM/Camera"
            else -> "/storage/emulated/0/Pictures/$albumName"
        }
    }
    
    /**
     * 移动文件到指定相册
     */
    suspend fun moveFilesToAlbum(
        context: Context,
        images: List<ImageItem>,
        targetAlbumPath: String,
        callback: OperationProgressCallback
    ): OperationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始移动 ${images.size} 个文件到 $targetAlbumPath")
        
        var successCount = 0
        val failedFiles = mutableListOf<String>()
        
        try {
            // 确保目标目录存在
            val targetDir = File(targetAlbumPath)
            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                if (!created) {
                    return@withContext OperationResult(
                        success = false,
                        message = "无法创建目标目录: $targetAlbumPath"
                    )
                }
            }
            
            // 异步并行移动文件
            val moveJobs = images.mapIndexed { index, image ->
                async {
                    try {
                        // 获取文件名
                        val fileName = image.name ?: "unknown_${System.currentTimeMillis()}.jpg"
                        val targetFile = File(targetDir, fileName)
                        
                        // 如果目标文件已存在，生成新名称
                        val finalTargetFile = if (targetFile.exists()) {
                            generateUniqueFileName(targetDir, fileName)
                        } else {
                            targetFile
                        }
                        
                        // 使用FileOperator库的移动方法（保留元数据）
                        val moveSuccess = moveFileWithMetadata(context, image.uri, finalTargetFile)
                        
                        // 更新进度
                        callback.onProgress(index + 1, images.size, image.name ?: "未知文件")
                        
                        if (moveSuccess) {
                            Pair(true, null)
                        } else {
                            Pair(false, "${image.name ?: "未知文件"}: 移动文件失败")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "移动文件失败: ${image.name}, 错误: ${e.message}")
                        callback.onProgress(index + 1, images.size, image.name ?: "未知文件")
                        Pair(false, "${image.name}: ${e.message}")
                    }
                }
            }
            
            // 等待所有移动操作完成
            moveJobs.forEach { job ->
                val (success, errorMessage) = job.await()
                if (success) {
                    successCount++
                } else {
                    errorMessage?.let { failedFiles.add(it) }
                }
            }
            
            val result = OperationResult(
                success = successCount > 0,
                message = if (successCount == images.size) {
                    "成功移动 $successCount 个文件"
                } else {
                    "移动完成: 成功 $successCount 个，失败 ${failedFiles.size} 个"
                },
                successCount = successCount,
                totalCount = images.size,
                failedFiles = failedFiles
            )
            
            callback.onCompleted(result)
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "移动操作异常: ${e.message}")
            val result = OperationResult(
                success = false,
                message = "移动操作失败: ${e.message}"
            )
            callback.onCompleted(result)
            result
        }
    }
    
    /**
     * 复制文件到指定相册
     */
    suspend fun copyFilesToAlbum(
        context: Context,
        images: List<ImageItem>,
        targetAlbumPath: String,
        callback: OperationProgressCallback
    ): OperationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始复制 ${images.size} 个文件到 $targetAlbumPath")
        
        var successCount = 0
        val failedFiles = mutableListOf<String>()
        
        try {
            // 确保目标目录存在
            val targetDir = File(targetAlbumPath)
            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                if (!created) {
                    return@withContext OperationResult(
                        success = false,
                        message = "无法创建目标目录: $targetAlbumPath"
                    )
                }
            }
            
            // 异步并行复制文件
            val copyJobs = images.mapIndexed { index, image ->
                async {
                    try {
                        // 获取文件名
                        val fileName = image.name ?: "unknown_${System.currentTimeMillis()}.jpg"
                        val targetFile = File(targetDir, fileName)
                        
                        // 如果目标文件已存在，生成新名称
                        val finalTargetFile = if (targetFile.exists()) {
                            generateUniqueFileName(targetDir, fileName)
                        } else {
                            targetFile
                        }
                        
                        // 使用简单字节流复制（不保留元数据）
                        val copySuccess = copyFileWithoutMetadata(context, image.uri, finalTargetFile)
                        
                        // 更新进度
                        callback.onProgress(index + 1, images.size, image.name ?: "未知文件")
                        
                        if (copySuccess) {
                            Pair(true, null)
                        } else {
                            Pair(false, "${image.name ?: "未知文件"}: 复制文件失败")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "复制文件失败: ${image.name}, 错误: ${e.message}")
                        callback.onProgress(index + 1, images.size, image.name ?: "未知文件")
                        Pair(false, "${image.name}: ${e.message}")
                    }
                }
            }
            
            // 等待所有复制操作完成
            copyJobs.forEach { job ->
                val (success, errorMessage) = job.await()
                if (success) {
                    successCount++
                } else {
                    errorMessage?.let { failedFiles.add(it) }
                }
            }
            
            val result = OperationResult(
                success = successCount > 0,
                message = if (successCount == images.size) {
                    "成功复制 $successCount 个文件"
                } else {
                    "复制完成: 成功 $successCount 个，失败 ${failedFiles.size} 个"
                },
                successCount = successCount,
                totalCount = images.size,
                failedFiles = failedFiles
            )
            
            callback.onCompleted(result)
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "复制操作异常: ${e.message}")
            val result = OperationResult(
                success = false,
                message = "复制操作失败: ${e.message}"
            )
            callback.onCompleted(result)
            result
        }
    }
    
    /**
     * 生成唯一的文件名
     */
    private fun generateUniqueFileName(directory: File, originalName: String): File {
        val nameWithoutExt = originalName.substringBeforeLast(".")
        val extension = originalName.substringAfterLast(".", "")
        val extWithDot = if (extension.isNotEmpty()) ".$extension" else ""
        
        var counter = 1
        var newFile: File
        do {
            val newName = "${nameWithoutExt}($counter)$extWithDot"
            newFile = File(directory, newName)
            counter++
        } while (newFile.exists())
        
        return newFile
    }
}
