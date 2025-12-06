/*
This Source Code Form is subject to the terms of the Apache Public License,
ver. 2.0. If a copy of the Apache 2.0 was not distributed with this file, You can
obtain one at

                    https://www.apache.org/licenses/LICENSE-2.0

Copyright (c) 2025 YuMo
*/
package com.example.yumoflatimagemanager.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 震动反馈工具类
 * 
 * 使用 Android 原生 Vibrator API 提供可靠的震动反馈
 * 兼容不同的 Android 版本
 */
object VibrationHelper {
    
    /**
     * 获取 Vibrator 实例
     * 兼容 API 31+ (VibratorManager) 和旧版本
     */
    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: 使用 VibratorManager
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                // 旧版本: 直接获取 Vibrator
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 执行震动
     * 兼容 API 26+ (VibrationEffect) 和更低版本
     */
    private fun performVibration(context: Context, durationMs: Long, amplitude: Int) {
        val vibrator = getVibrator(context) ?: return
        
        // 检查设备是否支持震动
        if (!vibrator.hasVibrator()) {
            return
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+: 使用 VibrationEffect
                val effect = VibrationEffect.createOneShot(durationMs, amplitude)
                vibrator.vibrate(effect)
            } else {
                // 旧版本: 使用已弃用的方法
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 长按震动反馈
     * 用于进入多选模式时的震动提示
     * 
     * @param context Context 实例
     */
    fun performLongPressVibration(context: Context) {
        // 50ms 强震动，提供明确的反馈
        performVibration(context, 50, VibrationEffect.DEFAULT_AMPLITUDE)
    }
    
    /**
     * 拖拽选择震动反馈
     * 用于拖拽选择图片时的轻微震动提示
     * 
     * @param context Context 实例
     */
    fun performSelectionVibration(context: Context) {
        // 20ms 轻震动，避免过于频繁的强震动
        performVibration(context, 20, 100)
    }
}

