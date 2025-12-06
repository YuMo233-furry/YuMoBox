package com.example.yumoflatimagemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.data.paging.MediaPagingSource
import com.example.yumoflatimagemanager.ui.components.SimplifiedImageCard

@Composable
fun AllMediaScreen(viewModel: MainViewModel, onImageClick: (ImageItem) -> Unit) {
	val pager = Pager(PagingConfig(pageSize = 60, prefetchDistance = 30)) {
		MediaPagingSource { page, pageSize ->
			// 演示：从现有内存列表分页（后续替换为MediaStore真分页）
			val all = viewModel.filteredImages
			val from = ((page - 1) * pageSize).coerceAtLeast(0)
			val to = (from + pageSize).coerceAtMost(all.size)
			if (from >= to) emptyList() else all.subList(from, to)
		}
	}
	val lazyItems = pager.flow.collectAsLazyPagingItems()

	LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(lazyItems.itemCount) {
            val image = lazyItems[it]
            if (image != null) {
                SimplifiedImageCard(
                    image = image,
                    onImageClick = onImageClick,
                    onImageLongClick = { },
                    isSelected = false,
                    isSelectionMode = false,
                    columnCount = 4 // 默认4列
                )
            }
        }
    }
}


