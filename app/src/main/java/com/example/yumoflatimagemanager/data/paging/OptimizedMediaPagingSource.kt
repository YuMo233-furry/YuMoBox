package com.example.yumoflatimagemanager.data.paging

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.SortConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 优化的媒体分页数据源
 * 支持真正的数据库分页查询，避免一次性加载所有数据
 */
class OptimizedMediaPagingSource(
    private val context: Context,
    private val albumId: String? = null,
    private val sortConfig: SortConfig = SortConfig()
) : PagingSource<Int, ImageItem>() {
    
    companion object {
        private const val PAGE_SIZE = 40 // 减少到40条数据，提高初始加载速度
        private const val PREFETCH_DISTANCE = 20 // 减少预取距离，降低内存压力
    }
    
    override fun getRefreshKey(state: PagingState<Int, ImageItem>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ImageItem> {
        return try {
            val page = params.key ?: 1
            val pageSize = params.loadSize.coerceAtMost(PAGE_SIZE)
            val offset = (page - 1) * pageSize
            
            val data = withContext(Dispatchers.IO) {
                loadMediaFromDatabase(offset, pageSize)
            }
            
            LoadResult.Page(
                data = data,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (data.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    
    /**
     * 从数据库分页加载媒体数据
     */
    private fun loadMediaFromDatabase(offset: Int, limit: Int): List<ImageItem> {
        val images = mutableListOf<ImageItem>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE
        )
        
        // 构建查询条件
        val selection = buildSelection()
        val selectionArgs = buildSelectionArgs()
        val sortOrder = buildSortOrder()
        
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "$sortOrder LIMIT $limit OFFSET $offset"
            )
            
            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketNameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val bucketIdColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val mimeTypeColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val dateTakenColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                
                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn)
                    val bucketName = c.getString(bucketNameColumn)
                    val bucketId = c.getString(bucketIdColumn)
                    val mimeType = c.getString(mimeTypeColumn)
                    val dateAdded = c.getLong(dateAddedColumn)
                    val dateModified = c.getLong(dateModifiedColumn)
                    val dateTaken = c.getLong(dateTakenColumn)
                    val size = c.getLong(sizeColumn)
                    
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    images.add(
                        ImageItem(
                            id = id.toString(),
                            uri = uri,
                            isVideo = mimeType?.startsWith("video/") == true,
                            albumId = bucketId ?: "",
                            name = name,
                            captureTime = if (dateTaken > 0) dateTaken else 0L,
                            modifyTime = dateModified * 1000L, // 转换为毫秒
                            createTime = dateAdded * 1000L, // 转换为毫秒
                            size = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return images
    }
    
    /**
     * 构建查询条件
     */
    private fun buildSelection(): String? {
        return when (albumId) {
            "all" -> null // 查询所有图片
            "video" -> null // 视频相册暂时返回空，后续可以扩展
            else -> albumId?.let { 
                "${MediaStore.Images.Media.BUCKET_ID} = ?" 
            }
        }
    }
    
    /**
     * 构建查询参数
     */
    private fun buildSelectionArgs(): Array<String>? {
        return albumId?.let { 
            if (it != "all" && it != "video") arrayOf(it) else null 
        }
    }
    
    /**
     * 构建排序条件
     */
    private fun buildSortOrder(): String {
        val sortField = when (sortConfig.type) {
            com.example.yumoflatimagemanager.data.SortType.NAME -> MediaStore.Images.Media.DISPLAY_NAME
            com.example.yumoflatimagemanager.data.SortType.CREATE_TIME -> MediaStore.Images.Media.DATE_ADDED
            com.example.yumoflatimagemanager.data.SortType.MODIFY_TIME -> MediaStore.Images.Media.DATE_MODIFIED
            com.example.yumoflatimagemanager.data.SortType.CAPTURE_TIME -> MediaStore.Images.Media.DATE_TAKEN
            com.example.yumoflatimagemanager.data.SortType.SIZE -> MediaStore.Images.Media.SIZE
            else -> MediaStore.Images.Media.DATE_ADDED
        }
        
        val direction = if (sortConfig.direction == com.example.yumoflatimagemanager.data.SortDirection.ASCENDING) "ASC" else "DESC"
        return "$sortField $direction"
    }
}
