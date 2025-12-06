package com.example.yumoflatimagemanager

import android.app.Application
import ando.file.core.FileOperator
import com.example.yumoflatimagemanager.data.ConfigMigration

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化FileOperator库，设置debug模式
        FileOperator.init(this, true)
        
        // 执行配置迁移，将SharedPreferences中的配置迁移到文件系统
        ConfigMigration.migrateConfig(this)
    }
}