package com.example.yumoflatimagemanager.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.yumoflatimagemanager.ui.components.ImagePickerActivity

/**
 * 图片选择器助手工具类
 * 简化图片选择器的使用，提供便捷的 API
 */
object ImagePickerHelper {
    
    /**
     * 在 Composable 中使用图片选择器
     * 
     * 示例：
     * ```
     * val imagePicker = ImagePickerHelper.rememberImagePicker { uri ->
     *     imageUri = uri.toString()
     * }
     * 
     * Button(onClick = { imagePicker.launch() }) {
     *     Text("选择图片")
     * }
     * ```
     * 
     * @param onImageSelected 图片选中回调，返回选中的图片 URI
     * @return ImagePickerLauncher 实例，调用 launch() 打开选择器
     */
    @Composable
    fun rememberImagePicker(
        onImageSelected: (Uri) -> Unit
    ): ImagePickerLauncher {
        val context = LocalContext.current
        
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uriString = result.data?.getStringExtra(
                    ImagePickerActivity.RESULT_IMAGE_URI
                )
                uriString?.let { 
                    onImageSelected(Uri.parse(it))
                }
            }
        }
        
        return remember {
            ImagePickerLauncher(context, launcher)
        }
    }
    
    /**
     * Launcher 包装类
     * 提供简单的 launch() 方法启动图片选择器
     */
    class ImagePickerLauncher internal constructor(
        private val context: Context,
        private val launcher: ActivityResultLauncher<Intent>
    ) {
        /**
         * 启动图片选择器
         */
        fun launch() {
            ImagePickerActivity.start(context, launcher)
        }
    }
}

