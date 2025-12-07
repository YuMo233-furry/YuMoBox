package com.example.yumoflatimagemanager

import android.app.Application
import android.os.Handler
import android.os.Looper
import ando.file.core.FileOperator
import com.example.yumoflatimagemanager.data.ConfigMigration
import com.example.yumoflatimagemanager.data.model.TagDataMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化FileOperator库，设置debug模式
        FileOperator.init(this, true)
        
        // 执行配置迁移，将SharedPreferences中的配置迁移到文件系统
        ConfigMigration.migrateConfig(this)
        
        // 执行标签数据迁移，将数据库中的标签数据迁移到文件系统
        migrateTagData()
    }
    
    /**
     * 执行标签数据迁移
     */
    private fun migrateTagData() {
        // 使用协程异步执行迁移
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 检查是否已经完成标签数据迁移
                if (!TagDataMigration.isTagMigrationCompleted()) {
                    // 执行迁移
                    val success = TagDataMigration.migrateFromDatabase(this@AppApplication)
                    if (success) {
                        // 验证迁移结果
                        val verifySuccess = TagDataMigration.verifyMigration()
                        if (verifySuccess) {
                            // 迁移成功
                            android.util.Log.d("AppApplication", "标签数据迁移成功")
                        } else {
                            // 验证失败
                            android.util.Log.e("AppApplication", "标签数据迁移验证失败")
                        }
                    } else {
                        // 迁移失败
                        android.util.Log.e("AppApplication", "标签数据迁移失败")
                    }
                } else {
                    // 已经完成迁移
                    android.util.Log.d("AppApplication", "标签数据迁移已经完成")
                }
            } catch (e: Exception) {
                android.util.Log.e("AppApplication", "标签数据迁移异常: ${e.message}", e)
            }
        }
    }
}