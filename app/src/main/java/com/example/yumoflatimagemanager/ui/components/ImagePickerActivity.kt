package com.example.yumoflatimagemanager.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.Album
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.ui.theme.YuMoFlatImageManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImagePickerActivity : ComponentActivity() {
    
    companion object {
        const val RESULT_IMAGE_URI = "result_image_uri"
        
        fun start(
            context: Context,
            launcher: ActivityResultLauncher<Intent>
        ) {
            val intent = Intent(context, ImagePickerActivity::class.java)
            launcher.launch(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YuMoFlatImageManagerTheme {
                ImagePickerScreen(
                    onImageSelected = { uri ->
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_IMAGE_URI, uri.toString())
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerScreen(
    onImageSelected: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    
    // 直接使用 MediaContentManager，避免 ViewModel 初始化问题
    val mediaManager = remember {
        com.example.yumoflatimagemanager.media.MediaContentManager(context.applicationContext)
    }
    
    var mainAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var customAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    
    // 在后台线程加载数据
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            mediaManager.initialize()
            mediaManager.loadAllMedia()
        }
        // 加载完成后更新相册列表
        val allAlbums = mediaManager.albums
        mainAlbums = allAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.MAIN }
        customAlbums = allAlbums.filter { it.type == com.example.yumoflatimagemanager.data.AlbumType.CUSTOM }
        isLoading = false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(selectedAlbum?.name ?: "选择图片") 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedAlbum != null) {
                            selectedAlbum = null
                        } else {
                            onCancel()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        // 显示加载状态
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (selectedAlbum == null) {
            // 显示相册列表
            ImagePickerAlbumList(
                mainAlbums = mainAlbums,
                customAlbums = customAlbums,
                onAlbumClick = { album ->
                    selectedAlbum = album
                },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            // 显示图片网格
            ImagePickerImageGrid(
                album = selectedAlbum!!,
                mediaManager = mediaManager,
                onImageClick = { image ->
                    onImageSelected(image.uri)
                },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
fun ImagePickerAlbumList(
    mainAlbums: List<Album>,
    customAlbums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 主要相册
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("主要相册", fontWeight = FontWeight.Bold)
        }
        items(mainAlbums) { album ->
            AlbumCard(
                album = album,
                onAlbumClick = onAlbumClick,
                onAlbumSelect = {},
                onAlbumLongClick = { false },
                isSelectionMode = false,
                isSelected = false
            )
        }
        
        // 自定义相册
        if (customAlbums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("我的相册", fontWeight = FontWeight.Bold)
            }
            items(customAlbums) { album ->
                AlbumCard(
                    album = album,
                    onAlbumClick = onAlbumClick,
                    onAlbumSelect = {},
                    onAlbumLongClick = { false },
                    isSelectionMode = false,
                    isSelected = false
                )
            }
        }
    }
}

@Composable
fun ImagePickerImageGrid(
    album: Album,
    mediaManager: com.example.yumoflatimagemanager.media.MediaContentManager,
    onImageClick: (ImageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // 直接从 MediaContentManager 获取相册图片
    val images = remember(album.id) {
        mediaManager.getImagesByAlbumId(album.id, album.sortConfig)
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(images) { _, image ->
            SimplifiedImageCard(
                image = image,
                isSelected = false,
                isSelectionMode = false,
                onImageClick = { onImageClick(image) },
                onImageLongClick = { false }
            )
        }
    }
}
