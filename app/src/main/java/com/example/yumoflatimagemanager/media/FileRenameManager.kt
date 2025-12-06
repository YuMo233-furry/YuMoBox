package com.example.yumoflatimagemanager.media

import android.content.Context
import android.net.Uri
import android.media.MediaScannerConnection
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import ando.file.core.FileLogger
import ando.file.core.FileUri
import ando.file.core.FileUtils
import com.example.yumoflatimagemanager.permissions.PermissionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * 文件重命名管理器，使用FileOperator库实现文件夹和文件的重命名操作
 */
object FileRenameManager {

    /**
     * 重命名文件夹
     * @param context 上下文
     * @param oldPath 原文件夹路径
     * @param newName 新文件夹名称
     * @return 是否重命名成功
     */
    fun renameFolder(context: Context, oldPath: String, newName: String): Boolean {
        try {
            // 检查路径是否有效
            if (oldPath.isBlank() || newName.isBlank()) {
                FileLogger.e("Rename folder failed: invalid path or name")
                return false
            }

            val oldFile = File(oldPath)
            if (!oldFile.exists() || !oldFile.isDirectory) {
                FileLogger.e("Rename folder failed: directory does not exist or is not a directory")
                return false
            }

            // 获取父目录
            val parentDir = oldFile.parentFile ?: return false
            
            // 创建新的文件对象
            val newFile = File(parentDir, newName)
            
            // 检查新名称是否已存在
            if (newFile.exists()) {
                FileLogger.e("Rename folder failed: new name already exists")
                return false
            }

            // 检查存储权限
            if (!PermissionsManager.hasRequiredPermissions(context)) {
                FileLogger.e("Rename folder failed: storage permission not granted")
                return false
            }

            // 执行重命名操作
            val result = oldFile.renameTo(newFile)
            
            if (result) {
                FileLogger.i("Rename folder success: $oldPath -> ${newFile.absolutePath}")
                
                // 在后台线程使用MediaScannerConnection更新媒体库（替代已弃用的ACTION_MEDIA_SCANNER_SCAN_FILE）
                GlobalScope.launch(Dispatchers.IO) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(newFile.absolutePath, oldFile.absolutePath),
                        null
                    ) { path, uri ->
                        FileLogger.i("Media scan completed for: $path")
                    }
                }
            } else {
                FileLogger.e("Rename folder failed: unknown error")
            }
            
            return result
        } catch (e: SecurityException) {
            FileLogger.e("Rename folder security exception: ${e.message}")
            return false
        } catch (e: IOException) {
            FileLogger.e("Rename folder IO exception: ${e.message}")
            return false
        } catch (e: Exception) {
            FileLogger.e("Rename folder exception: ${e.message}")
            return false
        }
    }

    /**
     * 重命名文件
     * @param context 上下文
     * @param oldPath 原文件路径
     * @param newName 新文件名称
     * @return 是否重命名成功
     */
    @RequiresApi(android.os.Build.VERSION_CODES.Q)
    fun renameFile(context: Context, oldPath: String, newName: String): Boolean {
        try {
            // 检查路径是否有效
            if (oldPath.isBlank() || newName.isBlank()) {
                FileLogger.e("Rename file failed: invalid path or name")
                return false
            }

            // 检查存储权限
            if (!PermissionsManager.hasRequiredPermissions(context)) {
                FileLogger.e("Rename file failed: storage permission not granted")
                return false
            }

            val oldFile = File(oldPath)
            if (!oldFile.exists() || !oldFile.isFile) {
                FileLogger.e("Rename file failed: file does not exist or is not a file")
                return false
            }
            
            // 获取文件扩展名
            val fileExtension = FileUtils.getExtension(oldPath)
            val newFileName = if (fileExtension.isNotEmpty()) "$newName.$fileExtension" else newName
            
            // 获取父目录
            val parentDir = oldFile.parentFile ?: return false
            
            // 创建新的文件对象
            val newFile = File(parentDir, newFileName)
            
            // 检查新名称是否已存在
            if (newFile.exists()) {
                FileLogger.e("Rename file failed: new name already exists")
                return false
            }

            // 执行重命名操作
            val result = oldFile.renameTo(newFile)
            
            if (result) {
                FileLogger.i("Rename file success: $oldPath -> ${newFile.absolutePath}")
                
                // 在后台线程使用MediaScannerConnection更新媒体库（替代已弃用的ACTION_MEDIA_SCANNER_SCAN_FILE）
                GlobalScope.launch(Dispatchers.IO) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(newFile.absolutePath, oldFile.absolutePath),
                        null
                    ) { path, uri ->
                        FileLogger.i("Media scan completed for: $path")
                    }
                }
            } else {
                FileLogger.e("Rename file failed: unknown error")
            }
            
            return result
        } catch (e: SecurityException) {
            FileLogger.e("Rename file security exception: ${e.message}")
            return false
        } catch (e: IOException) {
            FileLogger.e("Rename file IO exception: ${e.message}")
            return false
        } catch (e: Exception) {
            FileLogger.e("Rename file exception: ${e.message}")
            return false
        }
    }


}