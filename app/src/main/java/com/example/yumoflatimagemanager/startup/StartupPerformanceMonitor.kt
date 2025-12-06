package com.example.yumoflatimagemanager.startup

import android.util.Log
import kotlinx.coroutines.*

/**
 * 启动性能监控器
 * 监控应用启动各个阶段的性能，帮助优化启动速度
 */
object StartupPerformanceMonitor {
    private const val TAG = "StartupPerformance"
    
    private val startTime = System.currentTimeMillis()
    private val stageTimes = mutableMapOf<String, Long>()
    
    /**
     * 记录阶段开始时间
     */
    fun startStage(stageName: String) {
        stageTimes[stageName] = System.currentTimeMillis()
        Log.d(TAG, "开始阶段: $stageName")
    }
    
    /**
     * 记录阶段结束时间
     */
    fun endStage(stageName: String) {
        val startTime = stageTimes[stageName] ?: return
        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "完成阶段: $stageName, 耗时: ${duration}ms")
    }
    
    /**
     * 记录总启动时间
     */
    fun logTotalStartupTime() {
        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "总启动时间: ${totalTime}ms")
        
        // 如果启动时间超过2秒，记录警告
        if (totalTime > 2000) {
            Log.w(TAG, "启动时间过长: ${totalTime}ms，建议优化")
        }
    }
    
    /**
     * 记录关键性能指标
     */
    fun logPerformanceMetrics() {
        val totalTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "=== 启动性能报告 ===")
        Log.i(TAG, "总启动时间: ${totalTime}ms")
        
        stageTimes.forEach { (stage, startTime) ->
            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "$stage: ${duration}ms")
        }
        Log.i(TAG, "==================")
    }
}
