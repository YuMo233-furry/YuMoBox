package com.example.yumoflatimagemanager.permissions

import android.Manifest
import androidx.activity.ComponentActivity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Environment
import android.util.Log

/**
 * 权限管理器，负责处理应用所需的权限
 * 包含对Android 10以下设备的特殊适配
 */
object PermissionsManager {
    private const val TAG = "PermissionsManager"
    private const val STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
    private const val WRITE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE
    
    // 定义所需的权限
    private val REQUIRED_PERMISSIONS = when {
        // Android 13+ (TIRAMISU)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        }
        // Android 10 (Q) - Android 12 (S)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        // Android 10以下设备
        else -> {
            // 对于Android 10以下设备，同时请求读写权限
            arrayOf(
                STORAGE_PERMISSION,
                WRITE_PERMISSION
            )
        }
    }
    
    /**
     * 检查是否拥有所有必要的权限
     * Android 13+ 优化：不再强制要求 MANAGE_EXTERNAL_STORAGE
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        // Android 13+ (TIRAMISU): 使用细粒度媒体权限，无需 MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Android 13+设备，检查细粒度媒体权限")
            // 只需要检查 READ_MEDIA_IMAGES 和 READ_MEDIA_VIDEO
            return REQUIRED_PERMISSIONS.all { 
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
        // Android 11-12 (R-S): 优先使用细粒度权限，MANAGE_EXTERNAL_STORAGE 可选
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "Android 11-12设备，检查READ_EXTERNAL_STORAGE权限")
            // 对于图片管理器，READ_EXTERNAL_STORAGE 通常足够
            // MANAGE_EXTERNAL_STORAGE 是可选的，仅在需要完整文件系统访问时才需要
            val hasReadPermission = REQUIRED_PERMISSIONS.all { 
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            
            if (hasReadPermission) {
                return true
            }
            
            // 如果没有READ权限，检查是否有MANAGE权限（向后兼容）
            val hasManagePermission = Environment.isExternalStorageManager()
            if (hasManagePermission) {
                Log.d(TAG, "已授予MANAGE_EXTERNAL_STORAGE权限")
                return true
            }
            
            return false
        } 
        // Android 10 (Q): 直接检查 READ_EXTERNAL_STORAGE
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "Android 10设备，检查READ_EXTERNAL_STORAGE权限")
            return REQUIRED_PERMISSIONS.all { 
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        } 
        // Android 10 以下：需要额外检查文件系统访问能力
        else {
            Log.d(TAG, "Android 10以下设备，检查存储权限")
            // 尝试访问公共目录，确保权限实际可用
            val canAccessStorage = canAccessPublicStorage(context)
            if (!canAccessStorage) {
                Log.d(TAG, "Android 10以下设备，无法访问存储空间")
                return false
            }
            
            return REQUIRED_PERMISSIONS.all { 
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    /**
     * 获取需要请求的权限列表
     */
    fun getPermissionsToRequest(context: Context): Array<String> {
        return REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
    
    /**
     * 获取所需的权限列表
     * 针对不同Android版本返回不同的权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        Log.d(TAG, "获取所需权限列表，当前Android版本: ${Build.VERSION.SDK_INT}")
        return REQUIRED_PERMISSIONS
    }
    
    /**
     * 检查是否需要请求管理所有文件权限
     * Android 13+ 优化：仅在明确需要完整文件系统访问时返回 true
     * 对于普通图片管理，返回 false（使用细粒度权限即可）
     */
    fun needsManageAllFilesPermission(context: Context): Boolean {
        // Android 13+: 使用细粒度媒体权限，不需要 MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        
        // Android 11-12: 检查是否已有必要的媒体权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 如果已经有READ_EXTERNAL_STORAGE权限，就不需要MANAGE权限
            val hasReadPermission = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasReadPermission) {
                return false // 已有足够权限
            }
            
            // 如果没有READ权限且没有MANAGE权限，返回 false（让用户先请求READ权限）
            return false
        }
        
        // Android 10 及以下：不需要 MANAGE_EXTERNAL_STORAGE
        return false
    }
    
    /**
     * 检查Android 10以下设备是否能够访问公共存储空间
     */
    private fun canAccessPublicStorage(context: Context): Boolean {
        return try {
            // 尝试访问DCIM目录
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            // 检查目录是否存在且可读写
            dcimDir.exists() || dcimDir.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "检查存储访问权限时出错: ${e.message}")
            false
        }
    }
    
    /**
     * 打开管理所有文件权限的系统设置页面
     * 针对不同Android版本有不同的实现
     */
    fun openManageAllFilesPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上版本，使用专用的设置页面
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10设备，引导到应用详情页面
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        } else {
            // Android 10以下设备，也引导到应用详情页面
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        }
    }
    
    /**
     * 注册媒体权限启动器
     */
    fun registerMediaPermissionLauncher(
        activity: ComponentActivity,
        onPermissionGranted: () -> Unit
    ): androidx.activity.result.ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            val allGranted = permissions.all { entry -> entry.value }
            if (allGranted) {
                onPermissionGranted()
            } else {
                // 权限被拒绝，可以显示说明或引导用户手动授予权限
            }
        }
    }
}