package com.example.yumoflatimagemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.ui.components.SimplifiedImageCard
import com.example.yumoflatimagemanager.ui.selection.*
import com.example.yumoflatimagemanager.ui.selection.detectSlideSelection
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.example.yumoflatimagemanager.media.SimplifiedImageLoaderHelper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.Context
import android.app.Activity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import com.example.yumoflatimagemanager.ui.performance.ScrollPerformanceManager
import kotlinx.coroutines.delay

/**
 * 相册详情网格组件
 * 负责显示图片网格和处理交互事件
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailGrid(
    images: List<ImageItem>,
    selectionManager: SelectionManagerFacade,
    lazyGridState: LazyGridState,
    gridColumnCount: Int,
    spacingPx: Float,
    topBarHeightPx: Float,
    onImageClick: (ImageItem) -> Unit,
    onImageLongClick: (ImageItem) -> Unit,
    onImageDragSelect: (List<ImageItem>) -> Unit,
    onShowResetConfirmation: () -> Unit = {} // 显示重置确认对话框回调
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    
    
    // 滑动选择处理器（参考 PictureSelector 的实现）
    val slideSelectionHandler = remember(selectionManager, lazyGridState, context) {
        SlideSelectionHandler(
            selectionManager = selectionManager,
            gridState = lazyGridState,
            context = context,
            onSelectionChange = { onImageDragSelect(selectionManager.selectedImages) }
        )
    }
    // 当网格数据(images)因过滤/标签变化而更新时，终止当前拖动选择，避免使用过期索引
    LaunchedEffect(images) {
        if (slideSelectionHandler.isActive) {
            slideSelectionHandler.end()
        }
    }
    
    // 使用新的滚动性能管理器
    val scrollPerformanceState = ScrollPerformanceManager.rememberScrollPerformanceState(
        lazyGridState = lazyGridState,
        images = images,
        gridColumnCount = gridColumnCount,
        scrollStateListener = object : ScrollPerformanceManager.ScrollStateListener {
            override fun onScrollFast() {
                // 快速滚动时的优化
            }
            override fun onScrollSlow() {
                // 慢速滚动时的优化
            }
            override fun onScrollIdle() {
                // 滚动停止时的优化
            }
        },
        preloadListener = object : ScrollPerformanceManager.PreloadListener {
            override fun onPreloadImages(uris: List<android.net.Uri>) {
                // 预加载图片
            }
        }
    )
    
    // 预加载逻辑已移至ScrollPerformanceManager中处理
    
    // 滑动性能优化：使用稳定的key避免重组
    val optimizedImages = remember(images) {
        images // 直接使用原始列表，避免不必要的副本创建
    }
    
    // 参考PictureSelector的进场动画延迟策略，避免与数据加载冲突
    var shouldShowContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 延迟显示内容，避免进场动画与数据加载冲突
        delay(50L) // 参考PictureSelector的延迟策略
        shouldShowContent = true
    }
    
    // 调试日志：检查点击事件处理状态
    LaunchedEffect(Unit) {
        println("AlbumDetailGrid初始化 - 使用新的滑动选择系统")
    }
    
    // 使用自定义的嵌套滚动连接来处理滚动冲突
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // 选择模式下允许滚动，不阻止页面滚动
                return androidx.compose.ui.geometry.Offset.Zero // 不消费，允许正常滚动
            }
            
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // 不消费剩余滚动事件
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 始终监听滑动选择事件，确保长按后能立即衔接拖拽
            // detectSlideSelection 内部会根据 handler.isActive 判断是否处理
            .pointerInput(images) {
                detectSlideSelection(
                    handler = slideSelectionHandler,
                    images = { optimizedImages },
                    findItemAtPosition = { position: Offset ->
                        findItemAtPosition(
                            position = position,
                            spanCount = gridColumnCount,
                            images = optimizedImages,
                            spacing = spacingPx,
                            topBarHeight = topBarHeightPx,
                            gridState = lazyGridState
                        )
                    },
                    density = density
                )
            }
    ) {
        if (images.isEmpty()) {
            // 显示空状态提示，居中显示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "当前过滤没有任何内容",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 清除过滤文字按钮
                    TextButton(
                        onClick = onShowResetConfirmation
                    ) {
                        Text(
                            text = "清除过滤",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Fixed(gridColumnCount),
        contentPadding = PaddingValues(top = 120.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        // 性能优化配置 - 参考PictureSelector
        userScrollEnabled = true,
        // 减少预加载页数，提高滑动性能
        modifier = Modifier.nestedScroll(nestedScrollConnection)
    ) {
        // 使用itemsIndexed优化性能，添加稳定的key避免重组
        itemsIndexed(
            items = optimizedImages,
            key = { index, image -> 
                // 使用稳定的key，避免不必要的重组
                image.uri.toString()
            },
            contentType = { _, image -> 
                // 添加 contentType 以优化重组，确保删除后正确更新
                if (image.isVideo) "video" else "image"
            }
        ) { index, image ->
            // 移除不必要的计算，参考PictureSelector的简洁实现
            SimplifiedImageCard(
                image = image,
                onImageClick = {
                    if (selectionManager.isSelectionMode) {
                        selectionManager.selectImage(image)
                        onImageDragSelect(selectionManager.selectedImages)
                    } else {
                        onImageClick(image)
                    }
                },
                onImageLongClick = {
                    // 始终委托给外部逻辑：确保全局选择模式与上端菜单状态一致更新
                    onImageLongClick(image)
                    // 然后在本地启动滑动选择（锚点保护逻辑在 handler 内部处理）
                    slideSelectionHandler.start(index, optimizedImages, triggerHaptic = false)
                },
                isSelected = selectionManager.isImageSelected(image),
                isSelectionMode = selectionManager.isSelectionMode,
                columnCount = gridColumnCount,
                shouldLoadImage = true, // 始终加载，参考PictureSelector
                onPreviewClick = { previewImage ->
                    // 多选模式下的预览功能：打开大图查看器
                    val activity = context as? Activity
                    if (activity != null) {
                        // 获取当前图片在列表中的位置
                        val position = optimizedImages.indexOf(previewImage)
                        if (position >= 0) {
                            // 转换为 Uri 列表
                            val uris = optimizedImages.map { it.uri }
                            // 启动图片查看器
                            com.example.yumoflatimagemanager.ui.components.PhotoViewerActivity.start(
                                activity, 
                                uris, 
                                position
                            )
                        }
                    }
                },
                modifier = Modifier
                    .animateItem()
            )
            }
        }
        
        }
    }
}

/**
 * 计算针对高密度网格的智能预加载范围
 */
private fun calculatePreloadRangeForDenseGrid(
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    direction: Int,
    velocity: Float,
    isFastScrolling: Boolean,
    gridColumnCount: Int,
    totalImages: Int
): Pair<Int, Int> {
    val absVelocity = kotlin.math.abs(velocity)
    val isDenseGrid = gridColumnCount >= 6
    
    return when {
        // 极快滑动：不预加载或只预加载很少
        absVelocity > 1500f -> {
            val preloadCount = if (isDenseGrid) 0 else gridColumnCount / 4 // 极快滑动时大幅减少预加载
            if (preloadCount == 0) {
                firstVisibleIndex to firstVisibleIndex // 不预加载
            } else if (direction > 0) {
                val start = lastVisibleIndex + 1
                val end = minOf(totalImages - 1, start + preloadCount - 1)
                start to end
            } else {
                val end = firstVisibleIndex - 1
                val start = maxOf(0, end - preloadCount + 1)
                start to end
            }
        }
        
        // 快速滑动：大幅减少预加载
        absVelocity > 800f -> {
            val preloadCount = if (isDenseGrid) gridColumnCount / 2 else gridColumnCount
            if (direction > 0) {
                val start = lastVisibleIndex + 1
                val end = minOf(totalImages - 1, start + preloadCount - 1)
                start to end
            } else {
                val end = firstVisibleIndex - 1
                val start = maxOf(0, end - preloadCount + 1)
                start to end
            }
        }
        
        // 缓慢滑动：大幅增加预加载 - 针对高密度网格优化
        absVelocity < 200f -> {
            val preloadCount = if (isDenseGrid) gridColumnCount * 3 else gridColumnCount * 5 // 高密度网格减少预加载
            if (direction > 0) {
                val start = lastVisibleIndex + 1
                val end = minOf(totalImages - 1, start + preloadCount - 1)
                start to end
            } else {
                val end = firstVisibleIndex - 1
                val start = maxOf(0, end - preloadCount + 1)
                start to end
            }
        }
        
        // 正常滑动：适度预加载 - 针对高密度网格优化
        else -> {
            val preloadCount = if (isDenseGrid) gridColumnCount * 2 else gridColumnCount * 4 // 高密度网格减少预加载
            if (direction > 0) {
                val start = lastVisibleIndex + 1
                val end = minOf(totalImages - 1, start + preloadCount - 1)
                start to end
            } else {
                val end = firstVisibleIndex - 1
                val start = maxOf(0, end - preloadCount + 1)
                start to end
            }
        }
    }
}

/**
 * 计算智能预加载范围（保留原函数作为备用）
 */
private fun calculatePreloadRange(
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    direction: Int,
    velocity: Float,
    isFastScrolling: Boolean,
    gridColumnCount: Int,
    totalImages: Int
): Pair<Int, Int> {
    val absVelocity = kotlin.math.abs(velocity)
    
    return when {
        // 极快滑动：只预加载1行
        absVelocity > 2000f -> {
            if (direction > 0) {
                val start = lastVisibleIndex + 1
                val end = minOf(totalImages - 1, start + gridColumnCount - 1)
                start to end
            } else {
                val end = firstVisibleIndex - 1
                val start = maxOf(0, end - gridColumnCount + 1)
                start to end
            }
        }
        
        // 快速滑动：预加载2行
        absVelocity > 1000f -> {
            if (direction > 0) {
                val start = lastVisibleIndex + 1
                val end = minOf(totalImages - 1, start + gridColumnCount * 2 - 1)
                start to end
            } else {
                val end = firstVisibleIndex - 1
                val start = maxOf(0, end - gridColumnCount * 2 + 1)
                start to end
            }
        }
        
        // 正常滑动：预加载3行
        else -> {
            if (direction > 0) {
                val start = lastVisibleIndex + 1
                val end = minOf(totalImages - 1, start + gridColumnCount * 3 - 1)
                start to end
            } else {
                val end = firstVisibleIndex - 1
                val start = maxOf(0, end - gridColumnCount * 3 + 1)
                start to end
            }
        }
    }
}

/**
 * 执行针对高密度网格的智能预加载
 */
private fun executeDenseGridPreload(
    context: Context,
    images: List<ImageItem>,
    preloadRange: Pair<Int, Int>,
    isFastScrolling: Boolean,
    gridColumnCount: Int,
    preloadedIndices: Set<Int>,
    onPreloadComplete: (Set<Int>) -> Unit
) {
    val (start, end) = preloadRange
    if (start > end || start < 0 || end >= images.size) return
    
    // 过滤出需要预加载的图片（排除已预加载的）
    val indicesToPreload = (start..end).filter { it !in preloadedIndices }
    if (indicesToPreload.isEmpty()) return
    
    val preloadImages = indicesToPreload.map { images[it] }
    val preloadUris = preloadImages.map { it.uri }
    
    // 针对高密度网格的预加载策略 - 更激进的策略
    val isDenseGrid = gridColumnCount >= 6
    val isThumbnail = when {
        isFastScrolling && isDenseGrid -> true // 高密度快速滑动：强制缩略图
        isFastScrolling -> true // 普通快速滑动：缩略图
        isDenseGrid && preloadImages.size > 25 -> true // 高密度大量图片：缩略图（大幅提高阈值）
        preloadImages.size > 35 -> true // 普通大量图片：缩略图（大幅提高阈值）
        else -> false // 其他情况：标准质量，确保缓慢滑动时使用高质量
    }
    
    // 高密度网格使用批量预加载 - 针对高密度网格优化批次大小
    if (isDenseGrid) {
        val batchSize = when {
            isFastScrolling -> 2 // 快速滑动时更小的批次
            preloadImages.size > 25 -> 4 // 大量图片时中等批次
            else -> 6 // 正常情况，减少批次大小
        }
        SimplifiedImageLoaderHelper.preloadImages(context, preloadUris, isThumbnail)
    } else {
        // 普通网格也使用批量预加载，提高效率
        val batchSize = when {
            isFastScrolling -> 4 // 快速滑动时小批次
            preloadImages.size > 35 -> 7 // 大量图片时中等批次
            else -> 10 // 正常情况，大幅增加批次大小
        }
        SimplifiedImageLoaderHelper.preloadImages(context, preloadUris, isThumbnail)
    }
    
    // 更新预加载状态
    val newPreloadedIndices = preloadedIndices + indicesToPreload.toSet()
    onPreloadComplete(newPreloadedIndices)
}

/**
 * 执行智能预加载（保留原函数作为备用）
 */
private fun executeSmartPreload(
    context: Context,
    images: List<ImageItem>,
    preloadRange: Pair<Int, Int>,
    isFastScrolling: Boolean,
    preloadedIndices: Set<Int>,
    onPreloadComplete: (Set<Int>) -> Unit
) {
    val (start, end) = preloadRange
    if (start > end || start < 0 || end >= images.size) return
    
    // 过滤出需要预加载的图片（排除已预加载的）
    val indicesToPreload = (start..end).filter { it !in preloadedIndices }
    if (indicesToPreload.isEmpty()) return
    
    val preloadImages = indicesToPreload.map { images[it] }
    val preloadUris = preloadImages.map { it.uri }
    
    // 根据滑动状态选择预加载策略
    val isThumbnail = isFastScrolling || preloadImages.size > 15
    
    // 执行预加载
    SimplifiedImageLoaderHelper.preloadImages(context, preloadUris, isThumbnail)
    
    // 更新预加载状态
    val newPreloadedIndices = preloadedIndices + indicesToPreload.toSet()
    onPreloadComplete(newPreloadedIndices)
}

/**
 * 在指定位置查找对应的网格项
 * 改进：增加容错，支持边界外的触摸点
 */
private fun findItemAtPosition(
    position: Offset,
    spanCount: Int,
    images: List<ImageItem>,
    spacing: Float,
    topBarHeight: Float,
    gridState: LazyGridState
): Int? {
    val layoutInfo = gridState.layoutInfo
    val adjustedY = position.y - topBarHeight
    val visibleItems = layoutInfo.visibleItemsInfo
    
    if (visibleItems.isEmpty()) return null
    
    // 扩大容差，特别是对于边界情况
    val tolerance = spacing * 2f
    
    // 1. 首先尝试精确匹配
    for (itemInfo in visibleItems) {
        val itemLeft = itemInfo.offset.x.toFloat()
        val itemTop = itemInfo.offset.y.toFloat()
        val itemRight = itemLeft + itemInfo.size.width
        val itemBottom = itemTop + itemInfo.size.height
        
        if (position.x >= itemLeft && position.x <= itemRight && 
            adjustedY >= itemTop && adjustedY <= itemBottom) {
            return itemInfo.index
        }
    }
    
    // 2. 如果未精确匹配，使用最近邻算法（带容差）
    val nearestItem = visibleItems.minByOrNull { itemInfo ->
        val itemCenterX = itemInfo.offset.x + itemInfo.size.width / 2f
        val itemCenterY = itemInfo.offset.y + itemInfo.size.height / 2f
        
        val dx = position.x - itemCenterX
        val dy = adjustedY - itemCenterY
        
        // 计算加权距离（水平方向权重更高，改善水平滑动）
        val weightedDistance = kotlin.math.sqrt(dx * dx * 0.5f + dy * dy * 1.5f)
        weightedDistance
    }
    
    // 3. 检查最近的项目是否在合理范围内
    if (nearestItem != null) {
        val itemCenterX = nearestItem.offset.x + nearestItem.size.width / 2f
        val itemCenterY = nearestItem.offset.y + nearestItem.size.height / 2f
        val dx = kotlin.math.abs(position.x - itemCenterX)
        val dy = kotlin.math.abs(adjustedY - itemCenterY)
        
        // 如果距离在合理范围内（一个半单元格的距离），则返回
        val maxDistance = kotlin.math.max(nearestItem.size.width, nearestItem.size.height) * 1.5f
        if (dx <= maxDistance && dy <= maxDistance) {
            return nearestItem.index
        }
    }
    
    // 4. 如果还是找不到，根据位置估算（容错处理）
    return estimateItemIndex(position, adjustedY, spanCount, visibleItems, images)
}

/**
 * 根据位置估算项目索引（容错处理）
 */
private fun estimateItemIndex(
    position: Offset,
    adjustedY: Float,
    spanCount: Int,
    visibleItems: List<androidx.compose.foundation.lazy.grid.LazyGridItemInfo>,
    images: List<ImageItem>
): Int? {
    if (visibleItems.isEmpty()) return null
    
    // 找到最靠近的行
    val rowToItems = visibleItems.groupBy { it.index / spanCount }
    val rows = rowToItems.keys.sorted()
    
    val targetRow = when {
        adjustedY < rowToItems[rows.first()]!!.first().offset.y -> rows.first()
        adjustedY > rowToItems[rows.last()]!!.last().let { it.offset.y + it.size.height } -> rows.last()
        else -> rows.minByOrNull { row ->
            val rowItems = rowToItems[row]!!
            val rowTop = rowItems.minOf { it.offset.y }
            val rowBottom = rowItems.maxOf { it.offset.y + it.size.height }
            val rowCenter = (rowTop + rowBottom) / 2f
            kotlin.math.abs(adjustedY - rowCenter)
        } ?: rows.first()
    }
    
    // 在该行中找到最靠近的列
    val rowItems = rowToItems[targetRow]!!.sortedBy { it.offset.x }
    val targetItem = when {
        position.x < rowItems.first().offset.x -> rowItems.first()
        position.x > rowItems.last().let { it.offset.x + it.size.width } -> rowItems.last()
        else -> rowItems.minByOrNull { info ->
            val centerX = info.offset.x + info.size.width / 2f
            kotlin.math.abs(position.x - centerX)
        } ?: rowItems.first()
    }
    
    val idx = targetItem.index
    return if (idx in 0 until images.size) idx else null
}

/**
 * 优化的空间索引查找函数
 */
private fun findItemAtPositionOptimized(
    position: Offset,
    spanCount: Int,
    images: List<ImageItem>,
    spacing: Float,
    topBarHeight: Float,
    gridState: LazyGridState
): Int? {
    val layoutInfo = gridState.layoutInfo
    val adjustedY = position.y - topBarHeight
    val visibleItems = layoutInfo.visibleItemsInfo
    
    if (visibleItems.isEmpty()) return null
    
    // 使用空间索引优化查找
    val spatialIndex = mutableMapOf<Int, MutableList<LazyGridItemInfo>>()
    visibleItems.forEach { itemInfo ->
        val row = itemInfo.index / spanCount
        if (!spatialIndex.containsKey(row)) {
            spatialIndex[row] = mutableListOf()
        }
        spatialIndex[row]!!.add(itemInfo)
    }
    
    // 快速查找：先按行过滤，再按列过滤
    val targetRow = findTargetRow(spatialIndex, adjustedY)
    val rowItems = spatialIndex[targetRow] ?: return null
    
    // 在该行中查找最近的列
    return findNearestColumn(rowItems, position.x)
}

/**
 * 查找目标行
 */
private fun findTargetRow(
    spatialIndex: Map<Int, MutableList<LazyGridItemInfo>>,
    adjustedY: Float
): Int {
    val rows = spatialIndex.keys.sorted()
    if (rows.isEmpty()) return 0
    
    val firstRow = rows.first()
    val lastRow = rows.last()
    
    // 获取边界信息
    val firstRowTop = spatialIndex[firstRow]!!.minOf { it.offset.y }
    val lastRowBottom = spatialIndex[lastRow]!!.maxOf { it.offset.y + it.size.height }
    
    return when {
        adjustedY < firstRowTop -> firstRow
        adjustedY > lastRowBottom -> lastRow
        else -> {
            // 二分查找最近的行
            rows.minByOrNull { row ->
                val rowItems = spatialIndex[row]!!
                val top = rowItems.minOf { it.offset.y }
                val bottom = rowItems.maxOf { it.offset.y + it.size.height }
                val center = (top + bottom) / 2f
                kotlin.math.abs(adjustedY - center)
            } ?: firstRow
        }
    }
}

/**
 * 查找最近的列
 */
private fun findNearestColumn(
    rowItems: List<LazyGridItemInfo>,
    x: Float
): Int {
    return rowItems.minByOrNull { itemInfo ->
        val centerX = itemInfo.offset.x + itemInfo.size.width / 2f
        kotlin.math.abs(x - centerX)
    }?.index ?: 0
}