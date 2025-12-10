package com.example.yumoflatimagemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.yumoflatimagemanager.permissions.PermissionsManager
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.ui.screens.AlbumDetailScreen
import com.example.yumoflatimagemanager.ui.components.AlbumsTopAppBar
import com.example.yumoflatimagemanager.ui.screens.AlbumsScreen
import com.example.yumoflatimagemanager.ui.screens.Screen
import com.example.yumoflatimagemanager.ui.drawer.DrawerController
import com.example.yumoflatimagemanager.ui.drawer.TagManagerDrawer
import com.example.yumoflatimagemanager.ui.components.WatermarkPresetDialog
import com.example.yumoflatimagemanager.ui.components.WatermarkEditorDialog
import com.example.yumoflatimagemanager.ui.components.WatermarkSaveOptionDialog
import com.example.yumoflatimagemanager.ui.components.WatermarkPreviewActivity
import com.example.yumoflatimagemanager.ui.screens.TagSelectionScreen
import com.example.yumoflatimagemanager.ui.components.CreateTagDialog
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.core.app.ShareCompat
import java.util.Date
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.yumoflatimagemanager.ui.components.AlbumDetailTopAppBar
import com.example.yumoflatimagemanager.ui.components.SelectionBottomBar
import com.example.yumoflatimagemanager.ui.components.CreateAlbumDialog
import com.example.yumoflatimagemanager.ui.components.MoveDialog
import com.example.yumoflatimagemanager.ui.components.AlbumDetailLayout
import com.example.yumoflatimagemanager.ui.components.TagSelectionDialog
import com.example.yumoflatimagemanager.ui.components.MoveCopyDialog
import com.example.yumoflatimagemanager.ui.components.OperationProgressDialog
import com.example.yumoflatimagemanager.ui.components.OperationResultDialog
import com.example.yumoflatimagemanager.ui.components.DeleteConfirmDialog
import com.example.yumoflatimagemanager.ui.screens.AlbumSelectionScreen

/**
 * 应用的主Activity，使用模块化组件实现图片管理器功能
 */
class MainActivity : ComponentActivity() {
    // ViewModel实例 - 使用Compose状态变量
    private var viewModel by mutableStateOf<MainViewModel?>(null)
    
    // 侧边栏控制器
    private val drawerController = DrawerController()
    
    // 权限启动器 - 必须在onCreate中直接注册
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.entries.all { it.value }
        if (isGranted) {
            // 权限被授予，先重新加载配置再加载媒体数据
            lifecycleScope.launch {
                viewModel?.onStoragePermissionGranted()
                viewModel?.loadMediaData()
            }
        } else {
            // 权限被拒绝，显示提示
            Toast.makeText(
                this,
                "需要存储权限来显示图片",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // 管理所有文件权限启动器
    private val manageAllFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionsManager.isManageAllFilesGranted()) {
            lifecycleScope.launch {
                viewModel?.onStoragePermissionGranted()
                viewModel?.loadMediaData()
            }
        } else {
            Toast.makeText(
                this,
                "需要“管理所有文件”权限以使用完整功能，请在设置中授权",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // 水印预览启动器 - 必须作为成员变量注册
    private val watermarkPreviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("MainActivity", "watermarkPreviewLauncher 回调触发, resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            android.util.Log.d("MainActivity", "result.data=${data != null}")
            if (data != null) {
                val watermarkParams = data.getParcelableArrayListExtra<com.example.yumoflatimagemanager.data.ImageWatermarkParams>(
                    WatermarkPreviewActivity.RESULT_WATERMARK_PARAMS
                )
                
                android.util.Log.d("MainActivity", "watermarkParams=${watermarkParams?.size ?: 0} 个")
                
                if (watermarkParams != null && watermarkParams.isNotEmpty()) {
                    val currentViewModel = viewModel
                    android.util.Log.d("MainActivity", "currentViewModel=${currentViewModel != null}, currentWatermarkState=${currentViewModel?.currentWatermarkState != null}")
                    if (currentViewModel?.currentWatermarkState != null) {
                        val currentState = currentViewModel.currentWatermarkState!!
                        android.util.Log.d("MainActivity", "准备调用 confirmWatermarkPositionWithParams, paramsList.size=${watermarkParams.size}")
                        // 传递完整的参数列表，而不是只用第一张的参数
                        currentViewModel.confirmWatermarkPositionWithParams(
                            paramsList = watermarkParams,
                            preset = currentState.preset
                        )
                    } else {
                        android.util.Log.e("MainActivity", "currentViewModel 或 currentWatermarkState 为 null!")
                    }
                } else {
                    android.util.Log.e("MainActivity", "watermarkParams 为 null 或 empty!")
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 记录启动开始时间
        com.example.yumoflatimagemanager.startup.StartupPerformanceMonitor.startStage("Activity创建")
        
        // 应用重启时清除滚动位置（只在真正的应用启动时清除）
        if (savedInstanceState == null && PermissionsManager.hasRequiredPermissions(this)) {
            // 这是应用的新启动，不是配置变化，且已具备权限才处理滚动位置
            val tempViewModel = MainViewModel(this)
            tempViewModel.clearScrollPositionOnAppRestart()
        }
        
        // 记录UI设置完成时间
        com.example.yumoflatimagemanager.startup.StartupPerformanceMonitor.endStage("Activity创建")
        
        // 同步初始化ViewModel，确保缓存数据立即可用
        com.example.yumoflatimagemanager.startup.StartupPerformanceMonitor.startStage("ViewModel初始化")
        
        val newViewModel = MainViewModel(this@MainActivity.application)
        newViewModel.permissionLauncher = permissionLauncher
        viewModel = newViewModel
        
        // 确保ViewModel设置完成后，立即触发UI更新
        // 这会触发App组件重新渲染，从StartupScreen切换到主界面
        android.util.Log.d("MainActivity", "ViewModel设置完成，触发UI更新")
        
        com.example.yumoflatimagemanager.startup.StartupPerformanceMonitor.endStage("ViewModel初始化")
        com.example.yumoflatimagemanager.startup.StartupPerformanceMonitor.logTotalStartupTime()
        
        // 设置内容视图，使用Compose状态管理
        setContent {
            MaterialTheme {
                // 应用主UI组件
                App(viewModel, drawerController, watermarkPreviewLauncher, manageAllFilesLauncher)
            }
        }
    }
    
    /**
     * 处理返回按钮按下事件
     */
    fun setBackPressedCallback(callback: () -> Boolean) {
        // 注意：这里应该使用官方推荐的OnBackPressedDispatcher
        // 为了保持功能不变，暂时保留这个方法
    }
    
    /**
     * 设置返回按钮的处理逻辑
     */
    private fun setupBackPressHandler() {
        // 创建返回按钮回调
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 如果ViewModel还未初始化，直接返回
                val currentViewModel = viewModel ?: return
                
                // 检查当前屏幕
                val currentScreen = currentViewModel.currentScreen
                
                // 如果在相册详情页
                if (currentScreen is Screen.AlbumDetail) {
                    // 直接调用 navigateToAlbums，与顶部栏返回按钮保持一致
                    currentViewModel.navigateToAlbums()
                } else {
                    // 检查是否处于多选模式
                    val wasInSelectionMode = currentViewModel.isSelectionMode
                    
                    // 如果处于多选模式，只退出多选模式，不将应用退到后台
                    if (wasInSelectionMode) {
                        currentViewModel.handleBackPress()
                        return
                    }
                    
                    // 先尝试处理应用内的返回逻辑（如关闭对话框等）
                    currentViewModel.handleBackPress()
                    
                    // 如果处理完应用内逻辑后仍在主页面，则将应用放到后台
                    if (currentViewModel.currentScreen is Screen.Albums) {
                        moveTaskToBack(true)
                    }
                }
            }
        }
        
        // 将回调添加到返回按钮分发器
        onBackPressedDispatcher.addCallback(this, callback)
    }
    
    // 在Activity创建时设置返回按钮处理器
    init {
        setupBackPressHandler()
    }
}

/**
     * 应用的主Compose组件
     */
    @Composable
    fun App(viewModel: MainViewModel?, drawerController: DrawerController, watermarkPreviewLauncher: ActivityResultLauncher<Intent>, manageAllFilesLauncher: ActivityResultLauncher<Intent>) {
        // 如果ViewModel还未初始化，显示加载界面
        if (viewModel == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
            return
        }
        
        // 获取当前屏幕状态
        val currentScreen = viewModel.currentScreen
        // 获取是否处于相册详情页顶部的状态
        val isAlbumDetailNearTop = viewModel.isAlbumDetailNearTop
        // 获取是否处于选择模式的状态
        val isSelectionMode = viewModel.isSelectionMode
        // 获取选中的图片数量
        val selectedImagesCount = viewModel.selectedImages.size
        
        // 添加调试日志
        android.util.Log.d("MainActivity", "App组件重新组合 - currentScreen: $currentScreen, mainAlbums: ${viewModel.mainAlbums.size}, customAlbums: ${viewModel.customAlbums.size}")
        // 创建相册对话框显示状态
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    // 移动对话框显示状态
    var showMoveDialog by remember { mutableStateOf(false) }
    // Snackbar状态
    val snackbarHostState = remember { SnackbarHostState() }
    val tagViewModel = viewModel.tagViewModel
    // 监听撤回消息状态
    val showUndoDeleteMessage = viewModel.showUndoDeleteMessage
    val deletedTagName = viewModel.deletedTagName
    val showUndoDeleteTagGroupMessage = tagViewModel.showUndoDeleteTagGroupMessage
    val deletedTagGroupName = tagViewModel.deletedTagGroupName
    
        // 检查并请求权限
        CheckAndRequestPermissions(viewModel, manageAllFilesLauncher)
    
    // 构建应用UI
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (currentScreen is Screen.Albums) {
                    // 相册列表页顶部应用栏
                    AlbumsTopAppBar(
                        viewModel = viewModel
                    )
                } else if (currentScreen is Screen.AlbumDetail) {
                    // 相册详情页顶部应用栏
                    AlbumDetailTopAppBar(
                        viewModel = viewModel,
                        isAlbumDetailNearTop = isAlbumDetailNearTop,
                        drawerController = drawerController
                    )
                }
            },
            bottomBar = {
                if (!isSelectionMode) {
                    // 普通模式下显示底部导航栏
                    if (currentScreen is Screen.Albums) {
                        BottomAppBar(
                            actions = {
                                IconButton(onClick = { drawerController.openTagManager() }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "菜单")
                                }
                            },
                            floatingActionButton = {
                                FloatingActionButton(
                                    onClick = { showCreateAlbumDialog = true }
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "添加")
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            // 移除重复的BackHandler，使用Activity级别的OnBackPressedCallback统一处理
            // 根据当前屏幕显示不同内容，使用AnimatedVisibility添加切换动画
            
            // 相册列表屏幕 - 添加进入和退出动画
            AnimatedVisibility(
                visible = currentScreen is Screen.Albums,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 200)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 200)
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                AlbumsScreen(
                    mainAlbums = viewModel.mainAlbums,
                    customAlbums = viewModel.customAlbums,
                    onAlbumClick = { viewModel.onAlbumClick(it) },
                    onCreateAlbumClick = { showCreateAlbumDialog = true },
                    viewModel = viewModel,
                    isSelectionMode = viewModel.isSelectionMode,
                    onAlbumSelect = { viewModel.onAlbumSelect(it) },
                    onAlbumLongClick = { viewModel.onAlbumLongClick(it) },
                    isAlbumSelected = { viewModel.isAlbumSelected(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            
            // 相册详情页布局 - 添加进入和退出动画
            AnimatedVisibility(
                visible = currentScreen is Screen.AlbumDetail,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 200)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 200)
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                AlbumDetailLayout(
                    viewModel = viewModel,
                    isAlbumDetailNearTop = isAlbumDetailNearTop,
                    drawerController = drawerController
                )
            }
            
            // 显示创建新相册对话框
            if (showCreateAlbumDialog) {
                CreateAlbumDialog(
                    onCreate = { albumName ->
                        viewModel.createNewAlbum(albumName)
                        showCreateAlbumDialog = false
                    },
                    onCancel = { showCreateAlbumDialog = false }
                )
            }
            
            // 显示相册选择屏幕 - 使用AnimatedVisibility添加动画
            AnimatedVisibility(
                visible = viewModel.showAlbumSelectionScreen,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 200)
                ),
                exit = when (viewModel.albumSelectionExitAnimation) {
                    AlbumSelectionExitAnimation.SLIDE_OUT -> slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 200)
                    )
                    AlbumSelectionExitAnimation.FADE_OUT -> fadeOut(
                        animationSpec = tween(durationMillis = 250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1000f) // 确保在最顶层
            ) {
                AlbumSelectionScreen(
                    albums = viewModel.albums,
                    onAlbumSelected = { album ->
                        viewModel.selectTargetAlbum(album)
                    },
                    onCancel = {
                        viewModel.hideAlbumSelection()
                    }
                )
            }
            
            // 显示移动/复制选择对话框
            if (viewModel.showMoveCopyDialog && viewModel.selectedTargetAlbum != null) {
                MoveCopyDialog(
                    targetAlbum = viewModel.selectedTargetAlbum!!,
                    selectedCount = viewModel.selectedImages.size,
                    onMove = {
                        viewModel.performMoveOperation()
                    },
                    onCopy = {
                        viewModel.performCopyOperation()
                    },
                    onCancel = {
                        viewModel.hideMoveCopyDialog()
                    }
                )
            }
            
            // 显示操作进度对话框
            if (viewModel.showOperationProgressDialog) {
                OperationProgressDialog(
                    progress = viewModel.operationProgress,
                    total = viewModel.operationTotal,
                    currentFileName = viewModel.currentOperationFileName,
                    operationType = "处理",
                    onCancel = {
                        viewModel.cancelCurrentOperation()
                    }
                )
            }
            
            // 显示删除确认对话框
            DeleteConfirmDialog(
                showDialog = viewModel.showDeleteConfirmDialog,
                message = viewModel.deleteConfirmMessage,
                onConfirm = {
                    viewModel.confirmDeleteSelectedImages()
                },
                onDismiss = {
                    viewModel.hideDeleteConfirmDialog()
                }
            )
            
            
            // 标签选择全屏页面
            AnimatedVisibility(
                visible = viewModel.showTagSelectionDialog,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1000f) // 确保在最顶层
            ) {
                TagSelectionScreen(
                    tagsFlow = viewModel.tagsFlow,
                    selectedTagIds = viewModel.selectedTagIdsForOperation,
                    onToggleTag = { viewModel.toggleTagSelection(it) },
                    onConfirm = { viewModel.addSelectedTagsToImages() },
                    onRemove = { viewModel.removeSelectedTagsFromImages() },
                    onCancel = { viewModel.hideTagSelectionDialog() },
                    onCreateTag = { viewModel.showCreateTagDialog() },
                    viewModel = viewModel
                )
            }
        }
        
        // 创建标签对话框
        if (viewModel.showCreateTagDialog) {
            CreateTagDialog(
                onDismiss = { viewModel.showCreateTagDialog = false },
                onConfirm = { tagName ->
                    viewModel.addTag(tagName)
                    viewModel.showCreateTagDialog = false
                }
            )
        }
        
        // 水印相关对话框
        if (viewModel.showWatermarkPresetDialog) {
            WatermarkPresetDialog(
                presets = viewModel.watermarkPresets,
                onSelectPreset = { preset -> viewModel.selectWatermarkPreset(preset) },
                onCreatePreset = { viewModel.createWatermarkPreset() },
                onEditPreset = { preset -> viewModel.editWatermarkPreset(preset) },
                onDeletePreset = { preset -> viewModel.deleteWatermarkPreset(preset) },
                onDismiss = { viewModel.dismissWatermarkDialogs() }
            )
        }
        
        if (viewModel.showWatermarkEditor) {
            WatermarkEditorDialog(
                preset = viewModel.editingPreset,
                onSave = { preset -> viewModel.saveWatermarkPreset(preset) },
                onDismiss = { viewModel.dismissWatermarkDialogs() }
            )
        }
        
        if (viewModel.showWatermarkSaveOption) {
            WatermarkSaveOptionDialog(
                imageCount = viewModel.selectedImages.size,
                onConfirm = { option -> viewModel.confirmWatermarkSaveOption(option) },
                onDismiss = { viewModel.dismissWatermarkDialogs() }
            )
        }
        
        // 处理水印预览
        val context = LocalContext.current
        LaunchedEffect(viewModel.watermarkPreviewTrigger) {
            if (viewModel.showWatermarkPreview && viewModel.currentWatermarkState != null) {
                val state = viewModel.currentWatermarkState!!
                val selectedImages = viewModel.selectedImages
                if (selectedImages.isNotEmpty()) {
                    val imageUris = selectedImages.map { it.uri }
                    WatermarkPreviewActivity.start(
                        context = context,
                        imageUris = imageUris,
                        currentIndex = 0,
                        watermarkText = state.preset.text ?: "",
                        watermarkSize = state.preset.textSize,
                        watermarkAlpha = state.currentAlpha,
                        watermarkX = state.currentX,
                        watermarkY = state.currentY,
                        preset = state.preset,  // 传递完整的 preset
                        launcher = watermarkPreviewLauncher
                    )
                }
            }
        }
        
        // 标签管理侧边栏 - 移到Scaffold外部确保最高层级
        TagManagerDrawer(
            viewModel = viewModel,
            isOpen = drawerController.isTagManagerOpen,
            onClose = { drawerController.closeTagManager() }
        )
        
        // Snackbar显示 - 移到最外层确保最高层级
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
        )
        
        // 显示标签组撤回删除提示
        LaunchedEffect(showUndoDeleteTagGroupMessage) {
            if (showUndoDeleteTagGroupMessage && deletedTagGroupName.isNotEmpty()) {
                val result = snackbarHostState.showSnackbar(
                    message = "已删除标签组 $deletedTagGroupName",
                    actionLabel = "撤回",
                    duration = androidx.compose.material3.SnackbarDuration.Short
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    tagViewModel.undoDeleteTagGroup()
                }
                tagViewModel.hideUndoDeleteTagGroupMessage()
            }
        }

        // 显示撤回删除提示
        LaunchedEffect(showUndoDeleteMessage) {
            if (showUndoDeleteMessage && deletedTagName.isNotEmpty()) {
                val result = snackbarHostState.showSnackbar(
                    message = "已删除 $deletedTagName",
                    actionLabel = "撤回",
                    duration = androidx.compose.material3.SnackbarDuration.Short
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    // 用户点击了撤回
                    viewModel.undoDeleteTag()
                }
                // 重置状态
                viewModel.hideUndoDeleteMessage()
            }
        }
    }
}

/**
 * 检查并请求必要的权限
 */
@Composable
private fun CheckAndRequestPermissions(
    viewModel: MainViewModel,
    manageAllFilesLauncher: ActivityResultLauncher<Intent>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 当组件生命周期进入ON_RESUME状态时检查权限
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 检查是否需要管理所有文件权限
            val needsManageAllFilesPermission = PermissionsManager.needsManageAllFilesPermission(context)
            
            if (needsManageAllFilesPermission) {
                // 需要管理所有文件权限，引导用户到设置页面
                Toast.makeText(
                    context,
                    "请授予管理所有文件权限以获得完整功能",
                    Toast.LENGTH_LONG
                ).show()
                // 打开系统设置页面
                val activity = context as? ComponentActivity
                if (activity != null) {
                    PermissionsManager.requestManageAllFilesPermission(activity, manageAllFilesLauncher)
                } else {
                    PermissionsManager.openManageAllFilesPermissionSettings(context)
                }
            } else {
                // 不需要管理所有文件权限，但可能需要其他媒体权限
                android.util.Log.d("MainActivity", "检查权限状态...")
                if (!viewModel.hasRequiredPermissions()) {
                    android.util.Log.d("MainActivity", "缺少权限，请求权限")
                    // 请求媒体权限
                    viewModel.requestPermission()
                } else {
                    android.util.Log.d("MainActivity", "已有权限，加载媒体数据")
                    // 已经有权限，直接加载媒体数据
                    viewModel.loadMediaData()
                }
            }
        }
    }
}