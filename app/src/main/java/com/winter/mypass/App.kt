package com.winter.mypass

import android.app.Application
import android.util.Log
import com.winter.mypass.core.utils.MMKVUtils
import com.winter.mypass.feature.applock.model.CredentialStore
import com.winter.mypass.feature.home.model.VaultClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 应用初始化入口。
 * @author Tingdong
 * @date 2026-11-13
 */
class App : Application() {
    internal val credentialStore by lazy { CredentialStore(this) }
    internal val vaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val vaultClipboard by lazy { VaultClipboard(this, vaultScope) }

    /**
     * 初始化应用级存储。
     */
    override fun onCreate() {
        super.onCreate()
        MMKVUtils.init(this)
        Log.i("myPass", "onCreate: Hello!")
    }
}
