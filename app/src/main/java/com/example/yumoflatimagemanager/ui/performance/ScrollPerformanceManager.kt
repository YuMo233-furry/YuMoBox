/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                            https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.performance

import android.content.Context
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import com.example.yumoflatimagemanager.media.SimplifiedImageLoaderHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import android.util.Log

/**
 * 滚动性能管理器
 * 严格参考PictureSelector的简单策略，避免过度优化导致卡顿
 */
object ScrollPerformanceManager {
    private const val TAG = "ScrollPerformanceManager"
    
    // 滚动速度阈值 - 严格参考PictureSelector: Math.abs(dy) < 150
    private const val FAST_SCROLL_THRESHOLD = 150 // px per scroll event
    private const val SCROLL_CHECK_DELAY = 16L // 最小延迟，避免过于频繁
    
    /**
     * 滚动状态监听器
     */
    interface ScrollStateListener {
        fun onScrollFast()
        fun onScrollSlow()
        fun onScrollIdle()
    }
    
    /**
     * 预加载监听器
     */
    interface PreloadListener {
        fun onPreloadImages(uris: List<android.net.Uri>)
    }
    
    /**
     * 监听滚动状态并优化性能
     * 严格参考PictureSelector的超简单策略：只做暂停/恢复请求
     */
    @OptIn(FlowPreview::class)
    @Composable
    fun rememberScrollPerformanceState(
        lazyGridState: LazyGridState,
        images: List<com.example.yumoflatimagemanager.data.ImageItem>,
        gridColumnCount: Int,
        scrollStateListener: ScrollStateListener? = null,
        preloadListener: PreloadListener? = null
    ): ScrollPerformanceState {
        val context = LocalContext.current
        
        // 极简状态 - 只跟踪是否在快速滚动
        var isFastScrolling by remember { mutableStateOf(false) }
        var lastScrollOffset by remember { mutableStateOf(0) }
        
        // 严格参考PictureSelector: 只监听滚动状态变化
        LaunchedEffect(lazyGridState) {
            snapshotFlow { 
                lazyGridState.isScrollInProgress to lazyGridState.firstVisibleItemScrollOffset
            }
            .collectLatest { (scrolling, currentOffset) ->
                if (scrolling) {
                    // 计算滚动距离 - 参考PictureSelector: Math.abs(dy) < 150
                    val scrollDelta = kotlin.math.abs(currentOffset - lastScrollOffset)
                    lastScrollOffset = currentOffset
                    
                    val wasFastScrolling = isFastScrolling
                    // 简单判断：滚动距离超过阈值就是快速滚动
                    isFastScrolling = scrollDelta > FAST_SCROLL_THRESHOLD
                    
                    // 状态变化时暂停/恢复请求
                    if (isFastScrolling && !wasFastScrolling) {
                        scrollStateListener?.onScrollFast()
                        SimplifiedImageLoaderHelper.pauseRequests(context)
                    } else if (!isFastScrolling && wasFastScrolling) {
                        scrollStateListener?.onScrollSlow()
                        SimplifiedImageLoaderHelper.resumeRequests(context)
                    }
                } else {
                    // 滚动停止 - 恢复请求
                    if (isFastScrolling) {
                        isFastScrolling = false
                        scrollStateListener?.onScrollIdle()
                        SimplifiedImageLoaderHelper.resumeRequests(context)
                    }
                    lastScrollOffset = 0
                }
            }
        }
        
        // PictureSelector 没有复杂的预加载逻辑，移除
        
        return ScrollPerformanceState(
            isScrolling = lazyGridState.isScrollInProgress,
            isFastScrolling = isFastScrolling,
            shouldLoadImages = true
        )
    }
}

/**
 * 滚动性能状态
 */
data class ScrollPerformanceState(
    val isScrolling: Boolean,
    val isFastScrolling: Boolean,
    val shouldLoadImages: Boolean
)
