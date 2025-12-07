package com.example.yumoflatimagemanager.ui.drawer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yumoflatimagemanager.MainViewModel
import com.example.yumoflatimagemanager.data.local.TagWithChildren
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import com.example.yumoflatimagemanager.ui.components.CreateTagDialog
import com.example.yumoflatimagemanager.ui.components.RenameTagDialog
import com.example.yumoflatimagemanager.ui.components.DeleteTagDialog
import com.example.yumoflatimagemanager.ui.drawer.components.TagItem
import com.example.yumoflatimagemanager.ui.drawer.components.ReferencedTagTreeItem
import com.example.yumoflatimagemanager.ui.drawer.components.SwipeToDeleteTagItem
import com.example.yumoflatimagemanager.ui.drawer.components.DraggableTagTreeItem
import com.example.yumoflatimagemanager.ui.drawer.components.DraggableChildTagItem
import com.example.yumoflatimagemanager.ui.drawer.components.DraggableTagItem
import com.example.yumoflatimagemanager.ui.drawer.components.AddTagReferenceDialog
import com.example.yumoflatimagemanager.ui.drawer.components.TagManagerHeader
import com.example.yumoflatimagemanager.ui.drawer.components.TagGroupNavigationBar
import com.example.yumoflatimagemanager.ui.drawer.components.UntaggedTagItem
import com.example.yumoflatimagemanager.ui.drawer.components.CreateTagButton
import com.example.yumoflatimagemanager.ui.drawer.components.TagDialogsContainer
import com.example.yumoflatimagemanager.ui.drawer.components.TagListContent
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import com.example.yumoflatimagemanager.data.local.TagEntity

/**
 * 标签管理抽屉组件
 */
@Composable
fun TagManagerDrawer(
    viewModel: MainViewModel,
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 处理返回键关闭抽屉
    val backPressCallback = remember {
        object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                onClose()
            }
        }
    }
    
    // 确保回调状态与isOpen同步
    backPressCallback.isEnabled = isOpen
    
    // 使用DisposableEffect来管理返回键回调的生命周期
    val dispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    DisposableEffect(isOpen, dispatcherOwner) {
        if (dispatcherOwner != null) {
            dispatcherOwner.onBackPressedDispatcher.addCallback(backPressCallback)
        }
        onDispose {
            // 正确移除回调
            backPressCallback.remove()
        }
    }
    
    // 创建一个拖动手势处理的channel
    val dragChannel = remember { Channel<Float>() }
    val coroutineScope = rememberCoroutineScope()
    
    // 监听拖动事件
    LaunchedEffect(Unit) {
        dragChannel.receiveAsFlow().collect {
            // 如果向右拖动超过一定阈值（100像素），关闭抽屉
            if (it > 100) {
                onClose()
                keyboardController?.hide()
            }
        }
    }
    
    // 使用AnimatedVisibility显示/隐藏抽屉和背景，使用滑动动画
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            initialOffsetX = { -it } // 从左边滑入
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = slideOutHorizontally(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            targetOffsetX = { -it } // 向左滑出
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景遮罩，点击遮罩关闭抽屉
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
                        onClose()
                        keyboardController?.hide()
                    }
                    .zIndex(9f)
            )
            
            // 抽屉内容 - 改为从左边滑出
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .align(Alignment.CenterStart) // 改为左对齐
                    .zIndex(10f),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp), // 改为右边圆角
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                TagManagerContent(
                    viewModel = viewModel,
                    onClose = onClose,
                    dragChannel = dragChannel
                )
            }
        }
    }
}

/**
 * 标签管理内容
 */
@Composable
private fun TagManagerContent(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    dragChannel: Channel<Float>
) {
    val coroutineScope = rememberCoroutineScope()
    val tags by viewModel.tagsFlow.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 重置确认对话框状态
    var showResetConfirmationDialog by remember { mutableStateOf(false) }
    
    // 双击检测状态
    var lastClickTime by remember { mutableStateOf(0L) }
    
    // 拖拽排序状态 - 从ViewModel同步初始状态
    var isDragMode by remember { mutableStateOf(viewModel.isInDragMode) }
    var localTags by remember { mutableStateOf<List<TagWithChildren>>(emptyList()) }
    
    // 同步拖拽模式状态 - 确保本地状态与ViewModel状态一致
    LaunchedEffect(viewModel.isInDragMode) {
        isDragMode = viewModel.isInDragMode
    }
    
    // 打开抽屉时重置拖拽模式状态，避免状态残留
    LaunchedEffect(Unit) {
        if (viewModel.isInDragMode) {
            // 如果打开抽屉时发现处于拖拽模式，重置为普通模式
            viewModel.setDragMode(false)
        }
    }
    
    // 同步标签列表 - 只在非排序模式下同步，避免拖拽时的数据冲突
    LaunchedEffect(tags, isDragMode) {
        if (!isDragMode) {
            // 添加延迟，确保拖拽结束后的数据更新完成
            kotlinx.coroutines.delay(50)
            localTags = tags
        }
    }

   
    // 异步更新标签统计信息 - 智能缓存版本
    LaunchedEffect(tags) {
        // 只更新未缓存的标签统计信息
        val tagIds = tags.map { it.tag.id } + listOf(-1L) // 包含未分类标签
        viewModel.updateTagStatisticsBatchIfNeeded(tagIds)
    }
        
        // 搜索过滤逻辑 - 简化版，暂时只支持关键词搜索，不包含标签组过滤
        val filteredTags = remember(tags, searchQuery) {
            if (searchQuery.isBlank()) {
                tags
            } else {
                tags.filter { tagWithChildren ->
                    tagWithChildren.tag.name.contains(searchQuery, ignoreCase = true)
                }
            }
        }
        
    Column(
            modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            dragChannel.send(dragAmount)
                        }
                    }
                )
            }
    ) {
        // 标题栏，集成搜索功能
        TagManagerHeader(
            isDragMode = isDragMode,
            onDragModeToggle = { newMode ->
                isDragMode = newMode
                viewModel.setDragMode(newMode)
            },
            onResetClick = { showResetConfirmationDialog = true },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it }
        )
        
        // 标签组导航栏
        TagGroupNavigationBar(viewModel = viewModel)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 未分类标签（虚拟标签）
        UntaggedTagItem(
            viewModel = viewModel,
            lastClickTime = lastClickTime,
            onClickTimeUpdate = { lastClickTime = it }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // 标签列表 - 根据当前选中的标签组和搜索关键词过滤
        TagListContent(
            viewModel = viewModel,
            filteredTags = filteredTags,
            localTags = localTags,
            isDragMode = isDragMode,
            onLocalTagsChange = { localTags = it },
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 创建新标签按钮
        CreateTagButton(
            onClick = { viewModel.showCreateTagDialog = true }
        )
    }
    
    // 所有对话框
    TagDialogsContainer(
            viewModel = viewModel,
        showResetDialog = showResetConfirmationDialog,
        onResetDismiss = { showResetConfirmationDialog = false },
        onResetConfirm = { showResetConfirmationDialog = false },
        onClose = onClose
    )
}
