package com.winter.mypass

import android.app.Application
import android.util.Log
import com.winter.mypass.core.utils.MMKVUtils
import com.winter.mypass.feature.applock.model.CredentialStore

/**
 * 应用初始化入口。
 * @author Tingdong
 * @date 2026-11-13
 */
class App : Application() {
    internal val credentialStore by lazy { CredentialStore(this) }

    /**
     * 初始化应用级存储。
     */
    override fun onCreate() {
        super.onCreate()
        MMKVUtils.init(this)
        Log.i("myPass", "onCreate: Hello!")
    }
}
