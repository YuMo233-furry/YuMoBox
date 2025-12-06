/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 自定义图标集合
 */
object CustomIcons {
    /**
     * 预览/全屏图标
     * 左上角和右下角有L形状的图标
     */
    val Preview: ImageVector
        get() {
            if (_preview != null) {
                return _preview!!
            }
            _preview = materialIcon(name = "Filled.Preview") {
                // 左上角的L形状
                materialPath {
                    // 垂直线（上部分）
                    moveTo(4f, 4f)
                    lineTo(4f, 10f)
                    lineTo(6f, 10f)
                    lineTo(6f, 6f)
                    // 水平线（左部分）
                    lineTo(10f, 6f)
                    lineTo(10f, 4f)
                    lineTo(4f, 4f)
                    close()
                }
                // 右下角的L形状（旋转180度）
                materialPath {
                    // 垂直线（下部分）
                    moveTo(20f, 20f)
                    lineTo(20f, 14f)
                    lineTo(18f, 14f)
                    lineTo(18f, 18f)
                    // 水平线（右部分）
                    lineTo(14f, 18f)
                    lineTo(14f, 20f)
                    lineTo(20f, 20f)
                    close()
                }
            }
            return _preview!!
        }

    private var _preview: ImageVector? = null
}

