package com.example.yumoflatimagemanager.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.AlbumType
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.SortConfig
import com.example.yumoflatimagemanager.data.SortDirection
import com.example.yumoflatimagemanager.data.SortType
import com.example.yumoflatimagemanager.R
import com.example.yumoflatimagemanager.secure.SecureModeManager

/**
 * 媒体管理器，负责加载和管理设备上的媒体文件
 */
object MediaManager {
    
    /**
     * 从设备加载所有图片
     */
    fun loadImagesFromDevice(context: Context): List<ImageItem> {
        val images = mutableListOf<ImageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,        // 创建时间
            MediaStore.Images.Media.DATE_MODIFIED,     // 修改时间
            MediaStore.Images.Media.DATE_TAKEN,        // 拍摄时间
            MediaStore.Images.Media.SIZE               // 文件大小
        )
        val selection = null
        val selectionArgs = null
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            android.util.Log.d("MediaManager", "开始加载图片，查询MediaStore...")
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                android.util.Log.d("MediaManager", "查询结果：${cursor.count}张图片")
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketDisplayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val bucketId = cursor.getString(bucketIdColumn)
                    val bucketDisplayName = cursor.getString(bucketDisplayNameColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn) * 1000L // 转换为毫秒
                    val dateModified = cursor.getLong(dateModifiedColumn) * 1000L // 转换为毫秒
                    val dateTaken = cursor.getLong(dateTakenColumn)
                    val size = cursor.getLong(sizeColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    // 使用BUCKET_DISPLAY_NAME作为albumId，这样我们就能获取实际的文件夹名称
                    images.add(
                        ImageItem(
                            id = id.toString(),
                            uri = uri,
                            isVideo = false,
                            albumId = bucketDisplayName,
                            name = name,
                            captureTime = if (dateTaken > 0) dateTaken else dateAdded, // 优先使用拍摄时间
                            modifyTime = dateModified,
                            createTime = dateAdded,
                            size = size
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Android 13+ 权限问题
            android.util.Log.e("MediaManager", "权限错误，无法访问图片: ${e.message}")
            android.util.Log.e("MediaManager", "请确保已授予READ_MEDIA_IMAGES权限（Android 13+）或READ_EXTERNAL_STORAGE权限")
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            // 无效的查询参数
            android.util.Log.e("MediaManager", "查询参数错误: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            android.util.Log.e("MediaManager", "加载图片失败: ${e.message}")
            e.printStackTrace()
        }
        android.util.Log.d("MediaManager", "图片加载完成，共${images.size}张")
        return images
    }
    
    /**
     * 从设备加载所有视频
     */
    fun loadVideosFromDevice(context: Context): List<ImageItem> {
        val videos = mutableListOf<ImageItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,        // 创建时间
            MediaStore.Video.Media.DATE_MODIFIED,     // 修改时间
            MediaStore.Video.Media.DATE_TAKEN,        // 拍摄时间
            MediaStore.Video.Media.SIZE               // 文件大小
        )
        val selection = null
        val selectionArgs = null
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val bucketDisplayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val bucketDisplayName = cursor.getString(bucketDisplayNameColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn) * 1000L // 转换为毫秒
                    val dateModified = cursor.getLong(dateModifiedColumn) * 1000L // 转换为毫秒
                    val dateTaken = cursor.getLong(dateTakenColumn)
                    val size = cursor.getLong(sizeColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    // 使用BUCKET_DISPLAY_NAME作为albumId，这样我们就能获取实际的文件夹名称
                    videos.add(
                        ImageItem(
                            id = id.toString(),
                            uri = uri,
                            isVideo = true,
                            albumId = bucketDisplayName,
                            name = name,
                            captureTime = if (dateTaken > 0) dateTaken else dateAdded, // 优先使用拍摄时间
                            modifyTime = dateModified,
                            createTime = dateAdded,
                            size = size
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Android 13+ 权限问题
            android.util.Log.e("MediaManager", "权限错误，无法访问视频: ${e.message}")
            android.util.Log.e("MediaManager", "请确保已授予READ_MEDIA_VIDEO权限（Android 13+）或READ_EXTERNAL_STORAGE权限")
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            // 无效的查询参数
            android.util.Log.e("MediaManager", "查询参数错误: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            android.util.Log.e("MediaManager", "加载视频失败: ${e.message}")
            e.printStackTrace()
        }
        return videos
    }
    
    /**
     * 从设备扫描所有包含图片的文件夹并创建相册
     */
    fun scanMediaFolders(context: Context, allImages: List<ImageItem>): List<Album> {
        val folderMap = mutableMapOf<String, MutableList<ImageItem>>()
        val albums = mutableListOf<Album>()
        
        // 按文件夹分组图片
        for (image in allImages) {
            if (!folderMap.containsKey(image.albumId)) {
                folderMap[image.albumId] = mutableListOf()
            }
            folderMap[image.albumId]?.add(image)
        }
        
        // 为每个文件夹创建相册
        for ((folderPath, images) in folderMap) {
            // 提取文件夹名称（处理各种路径格式，包括中文路径）
            val folderName = extractFolderName(folderPath)
            
            // 使用文件夹中的第一张图片作为封面
            val coverUri = if (images.isNotEmpty()) images[0].uri else null
            
            // 检查是否为特殊文件夹（相机、截图等）
            val albumType = when {
                isCameraFolder(folderName, folderPath) -> AlbumType.MAIN
                folderName.contains("截图", ignoreCase = true) || 
                folderPath.contains("screenshot", ignoreCase = true) -> AlbumType.MAIN
                else -> AlbumType.CUSTOM
            }
            
            // 为相机相册使用更明确的名称
            val displayName = if (albumType == AlbumType.MAIN && isCameraFolder(folderName, folderPath)) {
                "相机"
            } else {
                folderName
            }
            
            // 检查该相册是否为私密相册
            val isPrivate = SecureModeManager.isAlbumPrivate(context, folderPath)
            
            albums.add(
                Album(
                    id = folderPath,
                    name = displayName,
                    coverImage = R.drawable.ic_launcher_foreground,
                    coverUri = coverUri,
                    count = images.size,
                    type = albumType,
                    sortConfig = SortConfig(), // 设置默认排序配置
                    isPrivate = isPrivate // 设置相册的私密状态
                )
            )
        }
        
        return albums
    }
    
    /**
     * 根据排序配置对图片列表进行排序
     */
    fun sortImages(images: List<ImageItem>, sortConfig: SortConfig): List<ImageItem> {
        return images.sortedWith(Comparator<ImageItem> {
            image1, image2 ->
            val comparison = when (sortConfig.type) {
                SortType.CAPTURE_TIME -> image1.captureTime.compareTo(image2.captureTime)
                SortType.MODIFY_TIME -> image1.modifyTime.compareTo(image2.modifyTime)
                SortType.CREATE_TIME -> image1.createTime.compareTo(image2.createTime)
                SortType.NAME -> {
                    val name1 = image1.name ?: ""
                    val name2 = image2.name ?: ""
                    name1.compareTo(name2, ignoreCase = true)
                }
                SortType.SIZE -> image1.size.compareTo(image2.size)
                SortType.IMAGE_COUNT -> 0 // 图片数量排序不适用于单个图片
            }
            
            // 根据排序方向调整比较结果
            if (sortConfig.direction == SortDirection.DESCENDING) -
 comparison else comparison
        })
    }
    
    /**
     * 提取文件夹名称，处理各种路径格式，确保中文路径正确显示
     */
    private fun extractFolderName(path: String): String {
        try {
            // 处理不同的路径分隔符
            val pathParts = path.split('/', '\\')
            return pathParts.lastOrNull { it.isNotEmpty() } ?: path
        } catch (e: Exception) {
            // 处理可能的编码异常，确保返回有意义的名称
            return path.takeLast(30) // 至少返回路径的最后30个字符
        }
    }
    
    /**
     * 检查是否为相机文件夹，适配不同机型的相机路径
     */
    private fun isCameraFolder(folderName: String, folderPath: String): Boolean {
        val lowercaseName = folderName.lowercase()
        val lowercasePath = folderPath.lowercase()
        
        // 常见相机文件夹模式
        return lowercaseName.contains("camera") || 
               lowercaseName.contains("相机") ||
               lowercasePath.contains("dcim/camera") ||
               lowercasePath.contains("dcim\\camera") ||
               lowercasePath.contains("camera") && lowercasePath.contains("dcim") ||
               lowercasePath.contains("照相机")
    }
}