package com.example.yumoflatimagemanager.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File

/**
 * 相册路径管理器，用于获取和管理相册在系统中的实际详细路径
 * 解决不同Android版本和设备上获取相册实际路径的问题
 */
object AlbumPathManager {

    private const val TAG = "AlbumPathManager"

    /**
     * 获取相册的实际系统路径
     * @param context 上下文
     * @param albumId 相册ID，通常是文件夹名称或路径
     * @return 相册的实际系统路径，如果无法获取则返回null
     */
    fun getAlbumRealPath(context: Context, albumId: String): String? {
        try {
            // 对于Android 10及以上版本，使用MediaStore API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getAlbumRealPathForQ(context, albumId)
            } else {
                // 对于Android 9及以下版本，使用传统方法
                return getAlbumRealPathForLegacy(context, albumId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get album path for $albumId", e)
            return null
        }
    }

    /**
     * Android 10及以上版本获取相册路径的方法
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getAlbumRealPathForQ(context: Context, albumId: String): String? {
        // 在Android 10及以上版本，我们需要通过MediaStore查询来获取实际路径
        val projection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )

        // 先尝试通过bucket_display_name匹配
        val selection = "${MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(albumId)
        
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                selection,
                selectionArgs,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val relativePathIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val relativePath = cursor.getString(relativePathIndex)
                
                // 构建完整路径
                val externalStorageDir = Environment.getExternalStorageDirectory().absolutePath
                return "$externalStorageDir/$relativePath"
            }
        } finally {
            cursor?.close()
        }

        // 如果通过bucket_display_name没有找到，尝试直接使用albumId作为路径
        val directPath = if (albumId.startsWith(File.separator)) albumId else "${Environment.getExternalStorageDirectory().absolutePath}/$albumId"
        val directFile = File(directPath)
        if (directFile.exists() && directFile.isDirectory) {
            return directFile.absolutePath
        }

        return null
    }

    /**
     * Android 9及以下版本获取相册路径的方法
     * 增强了健壮性，确保在Android 9上能够正确获取相册路径
     */
    private fun getAlbumRealPathForLegacy(context: Context, albumId: String): String? {
        try {
            // 首先检查albumId是否已经是完整路径
            val potentialPath = if (albumId.startsWith(File.separator)) {
                albumId
            } else {
                "${Environment.getExternalStorageDirectory().absolutePath}/$albumId"
            }
            
            val potentialFile = File(potentialPath)
            if (potentialFile.exists() && potentialFile.isDirectory) {
                Log.d(TAG, "Found album path directly: $potentialPath")
                return potentialFile.absolutePath
            }

            // 检查是否可能是私密相册（带有点号前缀）
            val privateAlbumId = if (!albumId.startsWith(".")) ".${albumId}" else albumId
            val privatePath = if (privateAlbumId.startsWith(File.separator)) {
                privateAlbumId
            } else {
                "${Environment.getExternalStorageDirectory().absolutePath}/${privateAlbumId}"
            }
            
            val privateFile = File(privatePath)
            if (privateFile.exists() && privateFile.isDirectory) {
                Log.d(TAG, "Found private album path: $privatePath")
                return privateFile.absolutePath
            }

            // 如果不是完整路径，尝试通过MediaStore查询
            val projection = arrayOf(
                MediaStore.Images.Media.DATA
            )
            
            val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(albumId)
            
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )

                if (cursor != null && cursor.moveToFirst()) {
                    val dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val filePath = cursor.getString(dataColumnIndex)
                    
                    // 获取文件所在的目录
                    val file = File(filePath)
                    val directory = file.parentFile
                    if (directory != null && directory.exists() && directory.isDirectory) {
                        Log.d(TAG, "Found album path via MediaStore: ${directory.absolutePath}")
                        return directory.absolutePath
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MediaStore for album path", e)
            } finally {
                cursor?.close()
            }

            // 尝试常见的相册路径模式
            val commonPaths = arrayOf(
                "${Environment.getExternalStorageDirectory().absolutePath}/DCIM/$albumId",
                "${Environment.getExternalStorageDirectory().absolutePath}/Pictures/$albumId",
                "${Environment.getExternalStorageDirectory().absolutePath}/Download/$albumId",
                "${Environment.getExternalStorageDirectory().absolutePath}/DCIM/.thumbnails/$albumId",
                "${Environment.getExternalStorageDirectory().absolutePath}/Android/data/${context.packageName}/files/$albumId"
            )
            
            for (path in commonPaths) {
                val file = File(path)
                if (file.exists() && file.isDirectory) {
                    Log.d(TAG, "Found album path in common locations: $path")
                    return file.absolutePath
                }
            }

            // 最后尝试直接使用Environment.DIRECTORY_PICTURES等API
            val publicDirs = listOf(
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_DOWNLOADS,
                "Documents"
            )
            
            for (dir in publicDirs) {
                val publicPath = "${Environment.getExternalStorageDirectory().absolutePath}/$dir/$albumId"
                val publicFile = File(publicPath)
                if (publicFile.exists() && publicFile.isDirectory) {
                    Log.d(TAG, "Found album path in public directory: $publicPath")
                    return publicPath
                }
            }

            Log.e(TAG, "Failed to find album path for: $albumId")
        } catch (e: Exception) {
            Log.e(TAG, "Error in getAlbumRealPathForLegacy", e)
        }

        return null
    }

    /**
     * 验证相册路径是否有效
     * @param path 要验证的路径
     * @return 路径是否有效
     */
    fun isValidAlbumPath(path: String?): Boolean {
        if (TextUtils.isEmpty(path)) {
            return false
        }
        
        val file = File(path!!)  // 使用非空断言，因为我们已经检查了path不为空
        return file.exists() && file.isDirectory
    }

    /**
     * 格式化相册路径，确保路径格式一致
     * @param path 要格式化的路径
     * @return 格式化后的路径
     */
    fun formatAlbumPath(path: String?): String? {
        if (path == null) {
            return null
        }
        
        return File(path).absolutePath
    }

    /**
     * 获取相册的显示名称，从路径中提取
     * @param path 相册路径
     * @return 相册的显示名称
     */
    fun getAlbumDisplayNameFromPath(path: String?): String {
        if (path == null) {
            return ""
        }
        
        val file = File(path)
        return file.name
    }
}