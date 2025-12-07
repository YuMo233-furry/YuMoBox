package com.example.yumoflatimagemanager.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 防抖工具类
 * 用于防止频繁触发同一操作，确保在指定时间内只执行一次
 */
class DebounceHelper(private val scope: CoroutineScope) {
    
    // 存储不同操作的防抖任务
    private val debounceJobs = mutableMapOf<String, Job>()
    
    /**
     * 防抖执行操作
     * @param key 操作唯一标识符
     * @param delayMs 防抖延迟时间（毫秒）
     * @param action 要执行的操作
     */
    fun debounce(key: String, delayMs: Long = 300, action: suspend () -> Unit) {
        // 取消之前的任务
        debounceJobs[key]?.cancel()
        
        // 创建新任务
        val newJob = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            action()
        }
        
        debounceJobs[key] = newJob
    }
    
    /**
     * 防抖执行操作（带返回值）
     * @param key 操作唯一标识符
     * @param delayMs 防抖延迟时间（毫秒）
     * @param action 要执行的操作
     * @param resultCallback 操作结果回调
     */
    fun <T> debounceWithResult(
        key: String, 
        delayMs: Long = 300, 
        action: suspend () -> T, 
        resultCallback: (T) -> Unit
    ) {
        // 取消之前的任务
        debounceJobs[key]?.cancel()
        
        // 创建新任务
        val newJob = scope.launch(Dispatchers.IO) {
            val result = action()
            launch(Dispatchers.Main) {
                resultCallback(result)
            }
        }
        
        debounceJobs[key] = newJob
    }
    
    /**
     * 清除所有防抖任务
     */
    fun clearAll() {
        debounceJobs.forEach { it.value.cancel() }
        debounceJobs.clear()
    }
    
    /**
     * 清除指定键的防抖任务
     * @param key 操作唯一标识符
     */
    fun clear(key: String) {
        debounceJobs[key]?.cancel()
        debounceJobs.remove(key)
    }
}
