package com.example.yumoflatimagemanager

import android.app.Application
import ando.file.core.FileOperator

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化FileOperator库，设置debug模式
        FileOperator.init(this, true)
    }
}