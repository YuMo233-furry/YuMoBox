package com.example.yumoflatimagemanager.ui.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 渐变工具类，提供各种渐变效果的创建方法
 * 解决渐变中间断开、过渡不自然等问题
 */
object GradientUtils {
    
    /**
     * 创建垂直渐变，确保过渡自然且不会中间断开
     * 
     * @param baseColor 基础颜色
     * @param startAlpha 起始透明度 (0.0f-1.0f)
     * @param endAlpha 结束透明度 (0.0f-1.0f)
     * @param steps 渐变步骤数，值越大过渡越平滑
     * @param height 渐变高度
     * @return 垂直渐变Brush对象
     */
    fun createSmoothVerticalGradient(
        baseColor: Color = Color.Black,
        startAlpha: Float = 0.7f,
        endAlpha: Float = 0.0f,
        steps: Int = 10, // 增加步骤数以获得更平滑的过渡
        height: Float = 500f // 增加默认高度以防止截断
    ): Brush {
        // 优化透明度分布，使过渡更加自然
        val colors = mutableListOf<Color>()
        
        // 前半部分保持较高的透明度，后半部分渐变更明显
        for (i in 0 until steps) {
            // 使用非线性分布来获得更自然的效果
            val progress = i.toFloat() / (steps - 1)
            // 使用缓动函数使渐变更加平滑
            val easedProgress = if (progress < 0.5f) {
                // 前半部分保持较高的透明度
                0.1f * (progress * 2)
            } else {
                // 后半部分透明度变化更明显
                0.1f + 0.9f * ((progress - 0.5f) * 2)
            }
            val alpha = startAlpha + (endAlpha - startAlpha) * easedProgress
            colors.add(baseColor.copy(alpha = alpha))
        }
        
        // 创建并返回垂直渐变
        return Brush.verticalGradient(
            colors = colors,
            startY = 0f,
            endY = height
        )
    }
    
    /**
     * 创建自定义垂直渐变，可以指定中间颜色点
     * 
     * @param colors 颜色列表
     * @param height 渐变高度
     * @return 垂直渐变Brush对象
     */
    fun createCustomVerticalGradient(
        colors: List<Color>,
        height: Float = 300f
    ): Brush {
        return Brush.verticalGradient(
            colors = colors,
            startY = 0f,
            endY = height
        )
    }
    
    /**
     * 创建水平渐变
     * 
     * @param colors 颜色列表
     * @param width 渐变宽度
     * @return 水平渐变Brush对象
     */
    fun createHorizontalGradient(
        colors: List<Color>,
        width: Float = 300f
    ): Brush {
        return Brush.horizontalGradient(
            colors = colors,
            startX = 0f,
            endX = width
        )
    }
    
    /**
     * 创建从指定起点到终点的线性渐变
     * 
     * @param colors 颜色列表
     * @param start 起始点
     * @param end 结束点
     * @return 线性渐变Brush对象
     */
    fun createLinearGradient(
        colors: List<Color>,
        start: Offset = Offset(0f, 0f),
        end: Offset = Offset(300f, 300f)
    ): Brush {
        return Brush.linearGradient(
            colors = colors,
            start = start,
            end = end
        )
    }
    
    /**
     * 创建TopAppBar专用的渐变背景
     * 特别优化以避免中间断开的问题
     * 
     * @return 优化的TopAppBar渐变背景
     */
    fun createTopAppBarGradient(): Brush {
        // 使用更多的中间颜色点，确保过渡平滑自然
        return Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.7f),
                Color.Black.copy(alpha = 0.6f),
                Color.Black.copy(alpha = 0.5f),
                Color.Black.copy(alpha = 0.4f),
                Color.Black.copy(alpha = 0.3f),
                Color.Black.copy(alpha = 0.2f),
                Color.Black.copy(alpha = 0.1f),
                Color.Black.copy(alpha = 0.05f),
                Color.Black.copy(alpha = 0.0f)
            ),
            startY = 0f,
            endY = 300f // 适当的高度，确保渐变完全显示
        )
    }
}