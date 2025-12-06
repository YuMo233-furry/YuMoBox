package com.example.yumoflatimagemanager.secure

import android.content.Context
import android.os.Environment
import android.util.Log
import ando.file.core.FileLogger
import ando.file.core.FileUtils
import com.example.yumoflatimagemanager.data.PreferencesManager
import com.example.yumoflatimagemanager.media.AlbumPathManager
import com.example.yumoflatimagemanager.permissions.PermissionsManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 安全模式管理器，处理私密相册的相关操作
 */
object SecureModeManager {
    private const val TAG = "SecureModeManager"
    private const val NOMEDIA_FILENAME = ".nomedia"
    private const val PREF_SECURE_MODE_ENABLED = "secure_mode_enabled"
    private const val PREF_PRIVATE_ALBUMS = "private_albums"

    /**
     * 检查文件夹是否包含子文件夹
     * @param folderPath 要检查的文件夹路径
     * @return 是否包含子文件夹
     */
    fun containsSubFolders(folderPath: String): Boolean {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            return false
        }

        // 列出目录中的所有文件和子目录
        val files = folder.listFiles() ?: return false
        
        // 检查是否存在子目录
        return files.any { it.isDirectory }
    }
    
    /**
     * 检查安全模式是否已启用
     */
    fun isSecureModeEnabled(context: Context): Boolean {
        return PreferencesManager.getInstance(context)
            .getBoolean(PREF_SECURE_MODE_ENABLED, false)
    }
    
    /**
     * 切换安全模式状态
     */
    fun toggleSecureMode(context: Context, isEnabled: Boolean): Boolean {
        val preferencesManager = PreferencesManager.getInstance(context)
        preferencesManager.putBoolean(PREF_SECURE_MODE_ENABLED, isEnabled)
        
        // 确保已获取必要的权限
        if (!PermissionsManager.hasRequiredPermissions(context)) {
            FileLogger.e("Storage permission not granted")
            return false
        }
        
        // 对于Android 9及以上版本，检查额外的存储权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // Android 9引入了更严格的存储权限控制
            // 检查应用是否有访问外部存储的能力
            val hasFullAccess = Environment.isExternalStorageLegacy()
            if (!hasFullAccess) {
                FileLogger.w("Android 9+ detected without legacy storage access. Some operations may be limited.")
                // 这里可以选择提示用户授予更广泛的存储访问权限
            }
        }
        
        // 获取所有私密相册ID列表
        val privateAlbumIds = getPrivateAlbumIds(context)
        
        // 如果没有私密相册，直接返回成功
        if (privateAlbumIds.isEmpty()) {
            FileLogger.i("No private albums to process")
            return true
        }
        
        // 使用CoroutineScope来处理suspend函数调用
        val successSemaphore = java.util.concurrent.atomic.AtomicBoolean(true)
        CoroutineScope(Dispatchers.IO).launch {
            // 并行处理所有私密相册的文件夹
            val jobs = privateAlbumIds.map {
                launch {
                    processAlbum(context, it, isEnabled, successSemaphore)
                }
            }
            
            // 等待所有任务完成
            jobs.forEach { it.join() }
        }
        
        return true
    }
    
    /**
     * 处理单个相册的隐私设置
     */
    private suspend fun processAlbum(
        context: Context,
        albumId: String,
        isEnabled: Boolean,
        successSemaphore: java.util.concurrent.atomic.AtomicBoolean
    ) {
        // 首先尝试使用原始ID查找路径
        var albumPath = AlbumPathManager.getAlbumRealPath(context, albumId)
        
        // 如果没有找到，尝试在ID前添加点号查找（可能是私密模式下的文件夹名）
        if (albumPath == null && !albumId.startsWith(".")) {
            albumPath = AlbumPathManager.getAlbumRealPath(context, ".$albumId")
        }
        
        if (albumPath != null) {
            val result = if (isEnabled) {
                // 启用安全模式：将文件夹名前添加点号并创建.nomedia文件
                makeAlbumPrivate(context, albumPath)
            } else {
                // 禁用安全模式：移除文件夹名前的点号并删除.nomedia文件
                makeAlbumPublic(context, albumPath)
            }
            
            // 如果任何一个操作失败，将successSemaphore设置为false
            if (!result) {
                successSemaphore.set(false)
                FileLogger.e("Failed to ${if (isEnabled) "privatize" else "publicize"} album: $albumId")
            }
        } else {
            successSemaphore.set(false)
            FileLogger.e("Cannot find album path for $albumId")
        }
    }
    
    /**
     * 将相册设为私密
     * @param albumPath 相册的完整路径
     */
    suspend fun makeAlbumPrivate(context: Context, albumPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val originalFile = File(albumPath)
                if (!originalFile.exists() || !originalFile.isDirectory) {
                    ando.file.core.FileLogger.e("Album directory does not exist: $albumPath")
                    return@withContext false
                }
                
                val parentDir = originalFile.parentFile ?: return@withContext false
                val originalName = originalFile.name
                
                // 如果文件夹名称已经以点号开头，不需要重命名
                if (originalName.startsWith(".")) {
                    // 但需要确保.nomedia文件存在
                    return@withContext createNomediaFile(albumPath)
                }
                
                // 创建新的文件夹名（添加点号前缀）
                val newName = ".$originalName"
                val newFile = File(parentDir, newName)
                
                // 检查新文件夹名是否已存在
                if (newFile.exists()) {
                    ando.file.core.FileLogger.e("Private album with the same name already exists: $newName")
                    // 确保.nomedia文件存在
                    return@withContext createNomediaFile(newFile.absolutePath)
                }
                
                // 执行重命名操作
                // 增强重命名逻辑，添加重试机制
                var renamed = false
                val maxRetries = 3
                var retryCount = 0
                
                while (!renamed && retryCount < maxRetries) {
                    try {
                        renamed = originalFile.renameTo(newFile)
                        if (renamed) break
                        
                        // 重试前短暂休眠
                        Thread.sleep(100)
                        retryCount++
                    } catch (e: SecurityException) {
                        // 捕获安全异常，常见于Android 9上的文件操作权限问题
                        ando.file.core.FileLogger.e("SecurityException when renaming album directory: ${e.message}")
                        
                        // 尝试使用另一种方法重命名（复制再删除）
                        renamed = copyDirectoryContents(originalFile, newFile)
                        if (renamed) {
                            // 复制成功后删除原文件夹
                            deleteDirectory(originalFile)
                            break
                        }
                        
                        retryCount = maxRetries // 不再重试
                    } catch (e: Exception) {
                        // 捕获其他可能的异常
                        ando.file.core.FileLogger.e("Exception when renaming album directory: ${e.message}")
                        retryCount++
                    }
                }
                
                if (renamed) {
                    ando.file.core.FileLogger.i("Album made private: $albumPath -> ${newFile.absolutePath}")
                    
                    // 创建.nomedia文件，使媒体扫描器忽略此文件夹
                    val nomediaCreated = createNomediaFile(newFile.absolutePath)
                    if (!nomediaCreated) {
                            ando.file.core.FileLogger.e("Failed to create .nomedia file for private album")
                        }
                    
                    // 即使.nomedia文件创建失败，只要重命名成功也算成功
                    return@withContext true
                } else {
                    ando.file.core.FileLogger.e("Failed to rename album directory after $maxRetries attempts: $albumPath")
                    return@withContext false
                }
            } catch (e: Exception) {
                ando.file.core.FileLogger.e("Error making album private: ${e.message}")
                e.printStackTrace()
                return@withContext false
            }
        }
    }
    
    /**
     * 将私密相册设为公开
     * @param albumPath 相册的完整路径（带有点号前缀）
     */
    suspend fun makeAlbumPublic(context: Context, albumPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val privateFile = File(albumPath)
                if (!privateFile.exists() || !privateFile.isDirectory) {
                    ando.file.core.FileLogger.e("Private album directory does not exist: $albumPath")
                    return@withContext false
                }
                
                val parentDir = privateFile.parentFile ?: return@withContext false
                val privateName = privateFile.name
                
                // 如果文件夹名称不以点号开头，不需要重命名
                if (!privateName.startsWith(".")) {
                    // 但需要确保.nomedia文件不存在
                    return@withContext deleteNomediaFile(albumPath)
                }
                
                // 创建新的文件夹名（移除点号前缀）
                val newName = privateName.substring(1)
                val newFile = File(parentDir, newName)
                
                // 检查新名称是否已存在
                if (newFile.exists()) {
                    ando.file.core.FileLogger.e("Public album with the same name already exists: $newName")
                    return@withContext false
                }
                
                // 先删除.nomedia文件
                deleteNomediaFile(albumPath)
                
                // 执行重命名操作
                // 增强重命名逻辑，添加重试机制
                var renamed = false
                val maxRetries = 3
                var retryCount = 0
                
                while (!renamed && retryCount < maxRetries) {
                    try {
                        renamed = privateFile.renameTo(newFile)
                        if (renamed) break
                        
                        // 重试前短暂休眠
                        Thread.sleep(100)
                        retryCount++
                    } catch (e: SecurityException) {
                        // 捕获安全异常，常见于Android 9上的文件操作权限问题
                        ando.file.core.FileLogger.e("SecurityException when renaming private album directory: ${e.message}")
                        
                        // 尝试使用另一种方法重命名（复制再删除）
                        renamed = copyDirectoryContents(privateFile, newFile)
                        if (renamed) {
                            // 复制成功后删除原文件夹
                            deleteDirectory(privateFile)
                            break
                        }
                        
                        retryCount = maxRetries // 不再重试
                    } catch (e: Exception) {
                        // 捕获其他可能的异常
                        ando.file.core.FileLogger.e("Exception when renaming private album directory: ${e.message}")
                        retryCount++
                    }
                }
                
                if (renamed) {
                    ando.file.core.FileLogger.i("Album made public: $albumPath -> ${newFile.absolutePath}")
                } else {
                    ando.file.core.FileLogger.e("Failed to rename private album directory after $maxRetries attempts: $albumPath")
                }
                
                return@withContext renamed
            } catch (e: Exception) {
                ando.file.core.FileLogger.e("Error making album public: ${e.message}")
                e.printStackTrace()
                return@withContext false
            }
        }
    }
    
    /**
     * 创建.nomedia文件
     * 增强了文件创建逻辑，添加重试机制和备选方法
     */
    private fun createNomediaFile(directoryPath: String): Boolean {
        return try {
            val directory = File(directoryPath)
            if (!directory.exists() || !directory.isDirectory) {
                ando.file.core.FileLogger.e("Directory does not exist for .nomedia file: $directoryPath")
                return false
            }
            
            val nomediaFile = File(directoryPath, NOMEDIA_FILENAME)
            
            // 如果文件已存在，直接返回成功
                if (nomediaFile.exists()) {
                    ando.file.core.FileLogger.i(".nomedia file already exists: ${nomediaFile.absolutePath}")
                    return true
                }
            
            // 增强创建文件逻辑，添加重试机制
            var created = false
            val maxRetries = 3
            var retryCount = 0
            
            while (!created && retryCount < maxRetries) {
                try {
                    created = nomediaFile.createNewFile()
                    if (created) break
                    
                    // 重试前短暂休眠
                    Thread.sleep(100)
                    retryCount++
                } catch (e: SecurityException) {
                    // 捕获安全异常，常见于Android 9上的文件操作权限问题
                            ando.file.core.FileLogger.e("SecurityException when creating .nomedia file: ${e.message}")
                            retryCount = maxRetries // 不再重试
                        }
            }
            
            if (created) {
                    ando.file.core.FileLogger.i("Created .nomedia file: ${nomediaFile.absolutePath}")
                } else {
                    ando.file.core.FileLogger.e("Failed to create .nomedia file after $maxRetries attempts: ${nomediaFile.absolutePath}")
                
                // 尝试备选方法：使用FileUtils创建文件（如果可用）
                try {
                    // 使用FileOutputStream创建文件
                    val outputStream = java.io.FileOutputStream(nomediaFile)
                    outputStream.close()
                    created = nomediaFile.exists()
                    if (created) {
                            ando.file.core.FileLogger.i("Successfully created .nomedia file using alternative method")
                        }
                } catch (e: Exception) {
                    ando.file.core.FileLogger.e("Failed to create .nomedia file using alternative method: ${e.message}")
                }
            }
            
            return created
        } catch (e: Exception) {
                ando.file.core.FileLogger.e("Error creating .nomedia file: ${e.message}")
                e.printStackTrace()
                false
            }
    }
    
    /**
     * 删除.nomedia文件
     * 增强了文件删除逻辑，添加重试机制和备选方法
     */
    private fun deleteNomediaFile(directoryPath: String): Boolean {
        return try {
            val directory = File(directoryPath)
            if (!directory.exists() || !directory.isDirectory) {
                ando.file.core.FileLogger.e("Directory does not exist for .nomedia file: $directoryPath")
                return true // 如果目录不存在，也视为成功
            }
            
            val nomediaFile = File(directoryPath, NOMEDIA_FILENAME)
            
            // 如果文件不存在，直接返回成功
                if (!nomediaFile.exists()) {
                    ando.file.core.FileLogger.i(".nomedia file does not exist: ${nomediaFile.absolutePath}")
                    return true
                }
            
            // 增强删除文件逻辑，添加重试机制
            var deleted = false
            val maxRetries = 3
            var retryCount = 0
            
            while (!deleted && retryCount < maxRetries) {
                try {
                    deleted = nomediaFile.delete()
                    if (deleted) break
                    
                    // 重试前短暂休眠
                    Thread.sleep(100)
                    retryCount++
                } catch (e: SecurityException) {
                    // 捕获安全异常，常见于Android 9上的文件操作权限问题
                            ando.file.core.FileLogger.e("SecurityException when deleting .nomedia file: ${e.message}")
                            retryCount = maxRetries // 不再重试
                        }
            }
            
            if (deleted) {
                    ando.file.core.FileLogger.i("Deleted .nomedia file: ${nomediaFile.absolutePath}")
                } else {
                    ando.file.core.FileLogger.e("Failed to delete .nomedia file after $maxRetries attempts: ${nomediaFile.absolutePath}")
                
                // 尝试备选方法：使用FileUtils删除文件（如果可用）
                try {
                    // FileUtils.deleteFile返回0表示成功
                    val result = ando.file.core.FileUtils.deleteFile(nomediaFile)
                    deleted = (result == 0)
                    if (deleted) {
                            ando.file.core.FileLogger.i("Successfully deleted .nomedia file using alternative method")
                        }
                } catch (e: Exception) {
                    ando.file.core.FileLogger.e("Failed to delete .nomedia file using alternative method: ${e.message}")
                }
            }
            
            return deleted
        } catch (e: Exception) {
                ando.file.core.FileLogger.e("Error deleting .nomedia file: ${e.message}")
                e.printStackTrace()
                false
            }
    }
    
    /**
     * 复制目录内容（作为renameTo的备选方案）
     * 在renameTo失败时使用
     */
    private fun copyDirectoryContents(sourceDir: File, destDir: File): Boolean {
        try {
            // 增强的目录创建逻辑，特别是针对Android 9
            if (!destDir.exists()) {
                // 使用mkdirsWithMode确保目录有正确的权限
                val created = mkdirsWithMode(destDir)
                if (!created) {
                    ando.file.core.FileLogger.e("Failed to create destination directory: ${destDir.absolutePath}")
                    return false
                }
            }
            
            val files = sourceDir.listFiles() ?: return false
            
            for (file in files) {
                val destFile = File(destDir, file.name)
                
                if (file.isDirectory) {
                    if (!copyDirectoryContents(file, destFile)) {
                        return false
                    }
                } else {
                    // 尝试多种文件复制方法以适应不同Android版本
                    val copied = copyFileWithMultipleMethods(file, destFile)
                    if (!copied) {
                        ando.file.core.FileLogger.e("Failed to copy file: ${file.absolutePath}")
                        return false
                    }
                }
            }
            
            return true
        } catch (e: Exception) {
            ando.file.core.FileLogger.e("Error copying directory contents: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * 创建目录并设置适当的权限，增强Android 9兼容性
     */
    private fun mkdirsWithMode(directory: File): Boolean {
        try {
            if (directory.mkdirs()) {
                // 设置目录权限为可读可写
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    // Android 9及以上版本需要特殊处理
                    directory.setReadable(true, false)
                    directory.setWritable(true, false)
                    directory.setExecutable(true, false)
                }
                return true
            }
            return false
        } catch (e: Exception) {
            ando.file.core.FileLogger.e("Error creating directory with mode: ${e.message}")
            return false
        }
    }
    
    /**
     * 使用多种方法尝试复制文件，以提高在不同Android版本上的兼容性
     */
    private fun copyFileWithMultipleMethods(source: File, destination: File): Boolean {
        // 方法1：使用Java NIO
        try {
            java.nio.file.Files.copy(
                source.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            return true
        } catch (e: Exception) {
            ando.file.core.FileLogger.e("Failed to copy file using Java NIO: ${e.message}")
        }
        
        // 方法2：使用标准Java IO流
        try {
            java.io.FileInputStream(source).use { inputStream ->
                java.io.FileOutputStream(destination).use { outputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            ando.file.core.FileLogger.e("Failed to copy file using Java IO streams: ${e.message}")
        }
        
        // 已经尝试了两种主要的文件复制方法，如果都失败则返回false
        // 移除FileUtils方法调用，因为无法确定其正确的参数签名
        
        
        return false
    }
    
    /**
     * 删除目录（递归）
     */
    private fun deleteDirectory(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) {
            return true
        }
        
        val files = dir.listFiles() ?: return false
        
        for (file in files) {
            if (file.isDirectory) {
                if (!deleteDirectory(file)) {
                    return false
                }
            } else {
                if (!file.delete()) {
                    // 尝试使用FileUtils删除
                        // FileUtils.deleteFile返回0表示成功
                        val result = ando.file.core.FileUtils.deleteFile(file)
                        if (result != 0) {
                            ando.file.core.FileLogger.e("Failed to delete file: ${file.absolutePath}")
                            return false
                        }
                }
            }
        }
        
        return dir.delete()
    }
    
    /**
     * 获取私密相册的ID列表
     */
    fun getPrivateAlbumIds(context: Context): List<String> {
        val preferencesManager = PreferencesManager.getInstance(context)
        val privateAlbumsString = preferencesManager.getString(PREF_PRIVATE_ALBUMS, "")
        
        return if (privateAlbumsString.isNullOrEmpty()) {
            emptyList()
        } else {
            privateAlbumsString.split(",").filter { it.isNotEmpty() }
        }
    }
    
    /**
     * 保存私密相册的ID列表
     */
    fun savePrivateAlbumIds(context: Context, albumIds: List<String>) {
        val preferencesManager = PreferencesManager.getInstance(context)
        val idsString = albumIds.joinToString(",")
        preferencesManager.putString(PREF_PRIVATE_ALBUMS, idsString)
    }
    
    /**
     * 添加相册到私密列表
     */
    fun addPrivateAlbum(context: Context, albumId: String) {
        val currentIds = getPrivateAlbumIds(context).toMutableList()
        if (!currentIds.contains(albumId)) {
            currentIds.add(albumId)
            savePrivateAlbumIds(context, currentIds)
        }
    }
    
    /**
     * 从私密列表中移除相册
     */
    fun removePrivateAlbum(context: Context, albumId: String) {
        val currentIds = getPrivateAlbumIds(context).toMutableList()
        if (currentIds.contains(albumId)) {
            currentIds.remove(albumId)
            savePrivateAlbumIds(context, currentIds)
        }
    }
    
    /**
     * 检查相册是否为私密相册
     */
    fun isAlbumPrivate(context: Context, albumId: String): Boolean {
        return getPrivateAlbumIds(context).contains(albumId)
    }
}