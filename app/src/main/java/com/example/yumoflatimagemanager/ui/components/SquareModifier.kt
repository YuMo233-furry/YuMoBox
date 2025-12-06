/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints

/**
 * 强制正方形的自定义 Modifier
 * 
 * 参考 PictureSelector 的 SquareRelativeLayout 实现
 * 在测量阶段强制高度等于宽度，确保容器是 1:1 的正方形
 * 
 * 这样可以避免图片加载时容器尺寸不确定导致的模糊或变形问题
 */
fun Modifier.forceSquare(): Modifier = this.then(
    ForceSquareModifier()
)

private class ForceSquareModifier : androidx.compose.ui.layout.LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        // 获取可用的最大宽度
        val width = constraints.maxWidth
        
        // 创建固定的正方形约束：宽度和高度都等于 width
        val squareConstraints = Constraints.fixed(width, width)
        
        // 使用正方形约束测量子元素
        val placeable = measurable.measure(squareConstraints)
        
        // 布局为正方形尺寸
        return layout(width, width) {
            placeable.place(0, 0)
        }
    }
}

