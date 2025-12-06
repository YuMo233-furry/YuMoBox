/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.selection

import android.content.Context
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import com.example.yumoflatimagemanager.data.ImageItem
import com.example.yumoflatimagemanager.utils.VibrationHelper
import kotlinx.coroutines.*

/**
 * 滑动选择处理器
 * 
 * 参考 PictureSelector 的实现：
 * 1. 通过外部调用 start() 来启动滑动选择（而不是在这里判断）
 * 2. 启动后才拦截触摸事件
 * 3. 未启动时完全不拦截，不干扰滚动和点击
 */
class SlideSelectionHandler(
    private val selectionManager: SelectionManagerFacade,
    private val gridState: LazyGridState,
    private val context: Context,
    private val onSelectionChange: () -> Unit
) {
    // 是否处于活动状态
    var isActive by mutableStateOf(false)
        private set
    
    // 选择范围
    private var startIndex = -1
    private var endIndex = -1
    private var lastStartIndex = -1
    private var lastEndIndex = -1
    
    // 原始选择状态（用于恢复）
    private var originalSelection = mutableSetOf<Int>()
    // 是否在当前拖拽会话中忽略锚点（长按进入后衔接拖拽时，避免第一项被取消）
    private var skipAnchorDuringDrag = false
    
    // 自动滚动
    private var autoScrollJob: Job? = null
    private var inTopSpot = false
    private var inBottomSpot = false
    private var scrollDistance = 0
    private var lastX = Float.MIN_VALUE
    private var lastY = Float.MIN_VALUE
    
    // 配置
    private val maxScrollDistance = 16
    private val autoScrollRegionSize = 56 // dp -> px
    
    /**
     * 启动滑动选择（由外部长按事件调用）
     * @param triggerHaptic 是否触发震动反馈，避免重复震动
     */
    fun start(position: Int, images: List<ImageItem>, triggerHaptic: Boolean = true) {
        if (position < 0 || position >= images.size) return
        
        println("🎯 SlideSelectionHandler.start() - position: $position, triggerHaptic: $triggerHaptic")
        
        isActive = true
        startIndex = position
        endIndex = position
        lastStartIndex = -1
        lastEndIndex = -1
        // 在本次拖拽会话开始时忽略锚点切换，直到会话结束
        skipAnchorDuringDrag = true
        
        // 震动反馈 - 提示用户进入拖动选择模式（可选）
        if (triggerHaptic) {
            println("🔊 触发长按震动反馈")
            VibrationHelper.performLongPressVibration(context)
        }
        
        // 保存原始选择状态
        originalSelection.clear()
        images.forEachIndexed { index, image ->
            if (selectionManager.isImageSelected(image)) {
                originalSelection.add(index)
            }
        }
        
        // 确保起始项被选中（长按进入多选模式时，图片应该已经被选中）
        val image = images[position]
        if (!selectionManager.isImageSelected(image)) {
            selectionManager.selectImage(image)
            onSelectionChange()
        }
    }
    
    // 上次触发震动的位置，避免重复震动
    private var lastHapticPosition = -1
    
    /**
     * 更新选择范围
     */
    fun updateRange(newPosition: Int, images: List<ImageItem>) {
        println("🔄 updateRange() - isActive: $isActive, newPosition: $newPosition, currentEndIndex: $endIndex")
        
        if (!isActive || newPosition < 0 || newPosition >= images.size) {
            println("❌ updateRange() 被跳过 - isActive: $isActive, newPosition: $newPosition, imagesSize: ${images.size}")
            return
        }
        if (newPosition == endIndex) {
            println("⏭️ updateRange() 被跳过 - 位置未变化")
            return
        }
        
        // 记录旧的位置，用于判断是否需要震动反馈
        val oldEndIndex = endIndex
        endIndex = newPosition
        
        // 当拖动到新的图片时，触发轻微的震动反馈
        // 只有当位置真正改变且不是起始位置时才震动
        if (oldEndIndex != -1 && oldEndIndex != newPosition && newPosition != lastHapticPosition) {
            println("🔊 触发拖动震动反馈 - 从 $oldEndIndex 到 $newPosition")
            VibrationHelper.performSelectionVibration(context)
            lastHapticPosition = newPosition
        }
        
        notifyRangeChange(images)
    }
    
    /**
     * 通知选择范围变化（完全按照 PictureSelector 的逻辑）
     */
    private fun notifyRangeChange(images: List<ImageItem>) {
        if (startIndex == -1 || endIndex == -1) return
        
        val newStart = minOf(startIndex, endIndex)
        val newEnd = maxOf(startIndex, endIndex)
        
        if (newStart < 0) return
        
        if (lastStartIndex == -1 || lastEndIndex == -1) {
            // 首次选择
            if (newStart != newEnd) {
                onSelectChange(newStart, newEnd, true, images)
            }
        } else {
            // 处理范围变化
            if (newStart > lastStartIndex) {
                onSelectChange(lastStartIndex, newStart - 1, false, images)
            } else if (newStart < lastStartIndex) {
                onSelectChange(newStart, lastStartIndex - 1, true, images)
            }
            
            if (newEnd > lastEndIndex) {
                onSelectChange(lastEndIndex + 1, newEnd, true, images)
            } else if (newEnd < lastEndIndex) {
                onSelectChange(newEnd + 1, lastEndIndex, false, images)
            }
        }
        
        lastStartIndex = newStart
        lastEndIndex = newEnd
        
        onSelectionChange()
    }
    
    /**
     * 处理选择变化（完全按照 PictureSelector 的逻辑）
     */
    private fun onSelectChange(start: Int, end: Int, isSelected: Boolean, images: List<ImageItem>) {
        for (i in start..end) {
            if (i < 0 || i >= images.size) continue
            
            val image = images[i]
            val wasOriginallySelected = originalSelection.contains(i)
            
            // PictureSelector 的核心逻辑
            val shouldBeSelected = isSelected != wasOriginallySelected
            val currentlySelected = selectionManager.isImageSelected(image)
            
            // 兼容：避免锚点项在拖动过程中被切换（长按衔接拖拽）
            if (skipAnchorDuringDrag && i == startIndex) {
                continue
            }
            if (shouldBeSelected != currentlySelected) {
                selectionManager.selectImage(image)
            }
        }
    }
    
    /**
     * 处理自动滚动
     */
    fun handleAutoScroll(
        touchY: Float,
        containerHeight: Float,
        density: Float,
        scope: CoroutineScope
    ) {
        val autoScrollDistancePx = autoScrollRegionSize * density
        val topBoundTo = autoScrollDistancePx
        val bottomBoundFrom = containerHeight - autoScrollDistancePx
        
        when {
            touchY <= topBoundTo -> {
                // 顶部区域
                lastY = touchY
                val speedFactor = (topBoundTo - touchY) / topBoundTo
                scrollDistance = (maxScrollDistance * speedFactor * -1f).toInt()
                if (!inTopSpot) {
                    inTopSpot = true
                    startAutoScroll(scope)
                }
            }
            touchY >= bottomBoundFrom -> {
                // 底部区域
                lastY = touchY
                val speedFactor = (touchY - bottomBoundFrom) / autoScrollDistancePx
                scrollDistance = (maxScrollDistance * speedFactor).toInt()
                if (!inBottomSpot) {
                    inBottomSpot = true
                    startAutoScroll(scope)
                }
            }
            else -> {
                // 正常区域
                inTopSpot = false
                inBottomSpot = false
                lastY = Float.MIN_VALUE
                stopAutoScroll()
            }
        }
    }
    
    /**
     * 开始自动滚动
     */
    private fun startAutoScroll(scope: CoroutineScope) {
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            while (isActive && (inTopSpot || inBottomSpot)) {
                gridState.scrollBy(scrollDistance.toFloat())
                delay(16) // ~60fps
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
     * 结束滑动选择
     */
    fun end() {
        stopAutoScroll()
        isActive = false
        startIndex = -1
        endIndex = -1
        lastStartIndex = -1
        lastEndIndex = -1
        lastHapticPosition = -1
        inTopSpot = false
        inBottomSpot = false
        lastX = Float.MIN_VALUE
        lastY = Float.MIN_VALUE
        originalSelection.clear()
        skipAnchorDuringDrag = false
    }
}

/**
 * 检测滑动选择
 * 
 * 新方案：持续监听所有触摸事件，根据 isActive 决定是否处理
 * 这样可以在长按后立即开始拖拽，无需等待新的按下事件
 */
suspend fun PointerInputScope.detectSlideSelection(
    handler: SlideSelectionHandler,
    images: () -> List<ImageItem>,
    findItemAtPosition: (Offset) -> Int?,
    density: Float
) {
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                // 持续监听所有事件（使用Main阶段，与其他手势检测器协调）
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull() ?: continue
                
                // 只有在激活状态才拦截和处理
                if (handler.isActive) {
                    if (change.pressed) {
                        // 手指按住并移动时，更新选择范围
                        val position = findItemAtPosition(change.position)
                        println("👆 detectSlideSelection - position: $position, change.position: ${change.position}")
                        if (position != null) {
                            handler.updateRange(position, images())
                            handler.handleAutoScroll(
                                touchY = change.position.y,
                                containerHeight = size.height.toFloat(),
                                density = density,
                                scope = this@coroutineScope
                            )
                        }
                        // 消费事件，防止滚动和其他交互
                        change.consume()
                    } else {
                        // 手指抬起，结束选择
                        println("👋 detectSlideSelection - 手指抬起，结束选择")
                        handler.end()
                    }
                }
            }
        }
    }
}
