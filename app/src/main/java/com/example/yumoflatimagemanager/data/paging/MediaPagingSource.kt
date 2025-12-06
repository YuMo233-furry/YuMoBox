package com.example.yumoflatimagemanager.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.yumoflatimagemanager.data.ImageItem

class MediaPagingSource(
	private val loader: suspend (page: Int, pageSize: Int) -> List<ImageItem>
) : PagingSource<Int, ImageItem>() {
	override fun getRefreshKey(state: PagingState<Int, ImageItem>): Int? {
		return state.anchorPosition?.let { anchor ->
			state.closestPageToPosition(anchor)?.prevKey?.plus(1)
				?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
		}
	}

	override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ImageItem> {
		return try {
			val page = params.key ?: 1
			val pageSize = params.loadSize
			val data = loader(page, pageSize)
			LoadResult.Page(
				data = data,
				prevKey = if (page == 1) null else page - 1,
				nextKey = if (data.isEmpty()) null else page + 1
			)
		} catch (e: Exception) {
			LoadResult.Error(e)
		}
	}
}


