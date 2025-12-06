/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.selection

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.yumoflatimagemanager.data.ImageItem
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 基于PictureSelector滑动选择逻辑的优化实现
 * 参考PictureSelector-3.11.2的SlideSelectTouchListener和SlideSelectionHandler
 */
class PictureSelectorStyleDragHandler(
    private val selectionManager: SelectionManagerFacade,
    private val gridState: LazyGridState,
    private val onSelectionChange: (List<ImageItem>) -> Unit
) {
    // 选择状态
    var isActive = false
    private var startIndex = -1
    private var endIndex = -1
    private var lastStartIndex = -1
    private var lastEndIndex = -1
    
    // 自动滚动相关
    private var isInTopSpot = false
    private var isInBottomSpot = false
    private var scrollDistance = 0
    private var lastX = Float.MIN_VALUE
    private var lastY = Float.MIN_VALUE
    
    // 配置参数
    private val maxScrollDistance = 16
    private val autoScrollDistance = 56f // dp转px
    private val touchRegionTopOffset = 0f
    private val touchRegionBottomOffset = 0f
    private val scrollAboveTopRegion = true
    private val scrollBelowTopRegion = true
    
    // 边界区域
    private var topBoundFrom = 0f
    private var topBoundTo = 0f
    private var bottomBoundFrom = 0f
    private var bottomBoundTo = 0f
    
    // 原始选择状态（用于恢复）
    private var originalSelection = mutableSetOf<Int>()
    
    // 自动滚动协程
    private var autoScrollJob: Job? = null
    
    /**
     * 开始滑动选择
     */
    fun startSlideSelection(position: Int) {
        if (!selectionManager.isSelectionMode) return
        
        isActive = true
        startIndex = position
        endIndex = position
        lastStartIndex = position
        lastEndIndex = position
        
        // 保存原始选择状态
        originalSelection.clear()
        originalSelection.addAll(getCurrentSelection())
        
        // 处理起始位置的选择
        val isFirstSelected = originalSelection.contains(position)
        changeSelection(position, position, !isFirstSelected, true)
    }
    
    /**
     * 处理触摸事件
     */
    suspend fun handleTouchEvent(
        event: PointerEvent,
        images: List<ImageItem>,
        findItemAtPosition: (Offset) -> Int?,
        screenHeight: Float
    ) {
        if (!isActive) {
            reset()
            return
        }
        
        val change = event.changes.firstOrNull() ?: return
        
        when (change.type) {
            PointerType.Touch -> {
                when {
                    change.pressed -> {
                        // 处理移动事件
                        if (!isInTopSpot && !isInBottomSpot) {
                            changeSelectedRange(change.position, images, findItemAtPosition)
                        }
                        processAutoScroll(change.position, screenHeight)
                    }
                    change.changedToUp() || !change.pressed -> {
                        // 结束选择
                        reset()
                    }
                }
            }
        }
    }
    
    /**
     * 更新边界区域
     */
    fun updateBounds(screenHeight: Float, density: Float) {
        val autoScrollDistancePx = autoScrollDistance * density
        topBoundFrom = touchRegionTopOffset
        topBoundTo = touchRegionTopOffset + autoScrollDistancePx
        bottomBoundFrom = screenHeight + touchRegionBottomOffset - autoScrollDistancePx
        bottomBoundTo = screenHeight + touchRegionBottomOffset
    }
    
    /**
     * 改变选择范围
     */
    private fun changeSelectedRange(
        position: Offset,
        images: List<ImageItem>,
        findItemAtPosition: (Offset) -> Int?
    ) {
        val newEndIndex = findItemAtPosition(position)
        if (newEndIndex != null && newEndIndex >= 0 && newEndIndex < images.size && newEndIndex != endIndex) {
            endIndex = newEndIndex
            notifySelectRangeChange()
        }
    }
    
    /**
     * 处理自动滚动
     */
    private fun processAutoScroll(position: Offset, screenHeight: Float) {
        val y = position.y
        val scrollSpeedFactor: Float
        
        when {
            y >= topBoundFrom && y <= topBoundTo -> {
                // 顶部区域
                lastX = position.x
                lastY = position.y
                scrollSpeedFactor = ((topBoundTo - topBoundFrom) - (y - topBoundFrom)) / (topBoundTo - topBoundFrom)
                scrollDistance = (maxScrollDistance * scrollSpeedFactor * -1f).toInt()
                if (!isInTopSpot) {
                    isInTopSpot = true
                    startAutoScroll()
                }
            }
            scrollAboveTopRegion && y < topBoundFrom -> {
                // 顶部区域上方
                lastX = position.x
                lastY = position.y
                scrollDistance = -maxScrollDistance
                if (!isInTopSpot) {
                    isInTopSpot = true
                    startAutoScroll()
                }
            }
            y >= bottomBoundFrom && y <= bottomBoundTo -> {
                // 底部区域
                lastX = position.x
                lastY = position.y
                scrollSpeedFactor = (y - bottomBoundFrom) / (bottomBoundTo - bottomBoundFrom)
                scrollDistance = (maxScrollDistance * scrollSpeedFactor).toInt()
                if (!isInBottomSpot) {
                    isInBottomSpot = true
                    startAutoScroll()
                }
            }
            scrollBelowTopRegion && y > bottomBoundTo -> {
                // 底部区域下方
                lastX = position.x
                lastY = position.y
                scrollDistance = maxScrollDistance
                if (!isInTopSpot) {
                    isInTopSpot = true
                    startAutoScroll()
                }
            }
            else -> {
                // 不在边界区域
                isInBottomSpot = false
                isInTopSpot = false
                lastX = Float.MIN_VALUE
                lastY = Float.MIN_VALUE
                stopAutoScroll()
            }
        }
    }
    
    /**
     * 开始自动滚动
     */
    private fun startAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && (isInTopSpot || isInBottomSpot)) {
                gridState.scrollBy(scrollDistance.toFloat())
                delay(16) // 约60fps
            }
        }
    }
    
    /**
     * 停止自动滚动
     */
    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
    
    /**
     * 通知选择范围变化
     */
    private fun notifySelectRangeChange() {
        if (startIndex == -1 || endIndex == -1) return
        
        val newStart = min(startIndex, endIndex)
        val newEnd = max(startIndex, endIndex)
        
        if (newStart < 0) return
        
        if (lastStartIndex == -1 || lastEndIndex == -1) {
            // 首次选择
            if (newEnd - newStart == 0) {
                changeSelection(newStart, newStart, true, false)
            } else {
                changeSelection(newStart, newEnd, true, false)
            }
        } else {
            // 更新选择范围
            val lastStart = min(lastStartIndex, lastEndIndex)
            val lastEnd = max(lastStartIndex, lastEndIndex)
            
            // 处理范围变化
            if (newStart > lastStart) {
                changeSelection(lastStart, newStart - 1, false, false)
            } else if (newStart < lastStart) {
                changeSelection(newStart, lastStart - 1, true, false)
            }
            
            if (newEnd > lastEnd) {
                changeSelection(lastEnd + 1, newEnd, true, false)
            } else if (newEnd < lastEnd) {
                changeSelection(newEnd + 1, lastEnd, false, false)
            }
        }
        
        lastStartIndex = newStart
        lastEndIndex = newEnd
    }
    
    /**
     * 改变选择状态
     */
    private fun changeSelection(start: Int, end: Int, isSelected: Boolean, calledFromStart: Boolean) {
        // 这里需要与SelectionManager集成
        // 暂时使用简单的实现
        if (calledFromStart) {
            // 处理起始选择
            val shouldSelect = !originalSelection.contains(start)
            // 通知选择管理器
        } else {
            // 处理范围选择
            for (i in start..end) {
                val shouldSelect = isSelected != originalSelection.contains(i)
                // 通知选择管理器
            }
        }
    }
    
    /**
     * 获取当前选择状态
     */
    private fun getCurrentSelection(): Set<Int> {
        // 从SelectionManager获取当前选择
        return selectionManager.getSelectedIndices()
    }
    
    /**
     * 重置状态
     */
    private fun reset() {
        isActive = false
        startIndex = -1
        endIndex = -1
        lastStartIndex = -1
        lastEndIndex = -1
        isInTopSpot = false
        isInBottomSpot = false
        lastX = Float.MIN_VALUE
        lastY = Float.MIN_VALUE
        stopAutoScroll()
        originalSelection.clear()
    }
    
    /**
     * 设置激活状态
     */
    fun setActiveState(active: Boolean) {
        isActive = active
        if (!active) {
            reset()
        }
    }
}

/**
 * 扩展函数：检测滑动选择手势
 */
suspend fun PointerInputScope.detectPictureSelectorStyleDragSelection(
    selectionManager: SelectionManagerFacade,
    gridState: LazyGridState,
    images: List<ImageItem>,
    findItemAtPosition: (Offset) -> Int?,
    onSelectionChange: (List<ImageItem>) -> Unit
) = coroutineScope {
    val handler = PictureSelectorStyleDragHandler(selectionManager, gridState, onSelectionChange)
    // 注意：这里不能使用 @Composable 函数，需要从外部传入 density
    val density = 3.0f // 默认密度值，实际使用时应该从外部传入
    
    var dragStartPosition: Offset? = null
    var dragStartTime: Long = 0
    val minDragDistance = 8f
    val initialDelay = 100L
    
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull() ?: continue
            
            when {
                // 处理按下事件
                change.pressed && dragStartPosition == null -> {
                    dragStartPosition = change.position
                    dragStartTime = System.currentTimeMillis()
                }
                
                // 处理移动事件
                change.pressed && dragStartPosition != null -> {
                    val currentTime = System.currentTimeMillis()
                    val dragDistance = calculateDistance(change.position, dragStartPosition!!)
                    val timeElapsed = currentTime - dragStartTime
                    
                    // 检查是否应该开始滑动选择
                    val shouldStartDrag = selectionManager.isSelectionMode && 
                                        timeElapsed > initialDelay &&
                                        dragDistance > minDragDistance
                    
                    if (shouldStartDrag && !handler.isActive) {
                        val startIndex = findItemAtPosition(dragStartPosition!!)
                        if (startIndex != null && startIndex >= 0 && startIndex < images.size) {
                            handler.startSlideSelection(startIndex)
                            change.consume()
                        }
                    }
                    
                    if (handler.isActive) {
                        handler.updateBounds(size.height.toFloat(), density)
                        // 注意：这里需要在一个协程中调用
                        launch {
                            handler.handleTouchEvent(event, images, findItemAtPosition, size.height.toFloat())
                        }
                        change.consume()
                    }
                }
                
                // 处理释放事件
                change.changedToUp() || !change.pressed -> {
                    if (handler.isActive) {
                        handler.setActiveState(false)
                    }
                    dragStartPosition = null
                    dragStartTime = 0
                }
            }
        }
    }
}

/**
 * 计算两点之间的距离
 */
private fun calculateDistance(start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
