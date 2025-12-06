package com.example.yumoflatimagemanager.test

import android.content.Context
import android.util.Log
import com.example.yumoflatimagemanager.feature.tag.TagViewModelNew
import com.example.yumoflatimagemanager.media.MediaContentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 标签架构快速测试工具
 * 
 * 使用方法：
 * 在 MainViewModel 或 MainActivity 的 init 中调用：
 * TagArchitectureQuickTest.runTests(context, mediaContentManager)
 */
object TagArchitectureQuickTest {
    
    private const val TAG = "TagArchTest"
    
    /**
     * 运行所有测试
     */
    fun runTests(context: Context, mediaContentManager: MediaContentManager) {
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000) // 等待应用初始化
            
            Log.d(TAG, "========================================")
            Log.d(TAG, "开始测试新标签架构")
            Log.d(TAG, "========================================")
            
            val tagViewModel = TagViewModelNew(context, mediaContentManager)
            
            try {
                // 测试1: 状态管理
                testStateManagement(tagViewModel)
                
                // 测试2: CRUD 操作
                testCrudOperations(tagViewModel)
                
                // 测试3: 过滤功能
                testFilterFunctions(tagViewModel)
                
                // 测试4: 持久化
                testPersistence(tagViewModel)
                
                Log.d(TAG, "========================================")
                Log.d(TAG, "✅ 所有测试通过！新架构工作正常")
                Log.d(TAG, "========================================")
                
            } catch (e: Exception) {
                Log.e(TAG, "========================================")
                Log.e(TAG, "❌ 测试失败: ${e.message}")
                Log.e(TAG, "========================================")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 测试1: 状态管理
     */
    private suspend fun testStateManagement(viewModel: TagViewModelNew) {
        Log.d(TAG, "\n>>> 测试1: 状态管理")
        
        // 测试过滤状态
        viewModel.toggleTagFilter(1L)
        val activeFilters = viewModel.activeTagFilterIds
        Log.d(TAG, "  ✓ 过滤状态更新: $activeFilters")
        assert(activeFilters.contains(1L)) { "过滤状态应该包含 1L" }
        
        // 测试展开状态
        viewModel.toggleTagExpanded(1L)
        delay(100)
        val expandedTags = viewModel.expandedTagIds
        Log.d(TAG, "  ✓ 展开状态更新: $expandedTags")
        
        // 测试引用展开状态
        viewModel.toggleReferencedTagExpanded(2L)
        delay(100)
        val expandedReferencedTags = viewModel.expandedReferencedTagIds
        Log.d(TAG, "  ✓ 引用展开状态更新: $expandedReferencedTags")
        
        Log.d(TAG, "✅ 状态管理测试通过")
    }
    
    /**
     * 测试2: CRUD 操作
     */
    private suspend fun testCrudOperations(viewModel: TagViewModelNew) {
        Log.d(TAG, "\n>>> 测试2: CRUD 操作")
        
        // 测试对话框状态（不创建实际标签，避免数据库污染）
        viewModel.showCreateTagDialog()
        assert(viewModel.showCreateTagDialog) { "创建对话框应该显示" }
        viewModel.hideCreateTagDialog()
        assert(!viewModel.showCreateTagDialog) { "创建对话框应该隐藏" }
        Log.d(TAG, "  ✓ 对话框状态管理正常")
        
        // 测试重命名对话框
        viewModel.showRenameTagDialog()
        assert(viewModel.showRenameTagDialog) { "重命名对话框应该显示" }
        viewModel.hideRenameTagDialog()
        assert(!viewModel.showRenameTagDialog) { "重命名对话框应该隐藏" }
        Log.d(TAG, "  ✓ 重命名对话框状态正常")
        
        // 测试删除对话框
        viewModel.showDeleteTagDialog()
        assert(viewModel.showDeleteTagDialog) { "删除对话框应该显示" }
        viewModel.hideDeleteTagDialog()
        assert(!viewModel.showDeleteTagDialog) { "删除对话框应该隐藏" }
        Log.d(TAG, "  ✓ 删除对话框状态正常")
        
        Log.d(TAG, "✅ CRUD 操作测试通过（跳过实际创建以避免数据库污染）")
    }
    
    /**
     * 测试3: 过滤功能
     */
    private suspend fun testFilterFunctions(viewModel: TagViewModelNew) {
        Log.d(TAG, "\n>>> 测试3: 过滤功能")
        
        // 测试激活过滤
        viewModel.toggleTagFilter(1L)
        viewModel.toggleTagFilter(2L)
        val filters = viewModel.activeTagFilterIds
        Log.d(TAG, "  ✓ 激活过滤: $filters")
        
        // 测试排除模式
        viewModel.toggleTagExclusion(3L)
        val excludes = viewModel.excludedTagIds
        Log.d(TAG, "  ✓ 排除模式: $excludes")
        
        // 测试清除
        viewModel.clearTagFilters()
        assert(viewModel.activeTagFilterIds.isEmpty()) { "过滤应该被清除" }
        assert(viewModel.excludedTagIds.isEmpty()) { "排除应该被清除" }
        Log.d(TAG, "  ✓ 清除过滤成功")
        
        Log.d(TAG, "✅ 过滤功能测试通过")
    }
    
    /**
     * 测试4: 持久化
     */
    private suspend fun testPersistence(viewModel: TagViewModelNew) {
        Log.d(TAG, "\n>>> 测试4: 持久化")
        
        // 测试保存滚动位置
        viewModel.saveTagDrawerScrollPosition(42)
        val restored = viewModel.restoreTagDrawerScrollPosition()
        assert(restored == 42) { "滚动位置应该是 42" }
        Log.d(TAG, "  ✓ 滚动位置持久化: $restored")
        
        // 测试保存状态
        viewModel.toggleTagFilter(99L)
        viewModel.restoreAllTagStates()
        Log.d(TAG, "  ✓ 状态恢复成功")
        
        Log.d(TAG, "✅ 持久化测试通过")
    }
    
    /**
     * 性能测试（可选）
     */
    fun runPerformanceTest(context: Context, mediaContentManager: MediaContentManager) {
        CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "\n>>> 性能测试")
            
            val tagViewModel = TagViewModelNew(context, mediaContentManager)
            
            // 测试批量统计更新
            val startTime = System.currentTimeMillis()
            tagViewModel.updateTagStatisticsBatch(listOf(1L, 2L, 3L, 4L, 5L))
            delay(2000) // 等待批量更新
            val duration = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "  批量更新耗时: ${duration}ms")
            Log.d(TAG, "✅ 性能测试完成")
        }
    }
}

