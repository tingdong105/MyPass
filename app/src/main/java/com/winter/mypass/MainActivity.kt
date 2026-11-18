package com.winter.mypass

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.winter.mypass.feature.applock.view.AppLockScreen
import com.winter.mypass.feature.applock.viewmodel.AppLockViewModel
import com.winter.mypass.feature.applock.viewmodel.LockScreen
import com.winter.mypass.nav.AppNavHost
import com.winter.mypass.ui.theme.MyPassTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用主题与导航宿主入口。
 * @author Tingdong
 * @date 2026-11-13
 */
class MainActivity : ComponentActivity() {
    private val appLock by viewModels<AppLockViewModel> {
        viewModelFactory {
            initializer { AppLockViewModel((application as App).credentialStore) }
        }
    }

    /**
     * 初始化窗口并挂载导航。
     * @param savedInstanceState 系统保存的界面状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            MyPassTheme {
                val state by appLock.state.collectAsState()
                if (state.screen == LockScreen.Unlocked) {
                    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                        val entrance = remember {
                            MutableTransitionState(false).apply { targetState = true }
                        }
                        AnimatedVisibility(
                            visibleState = entrance,
                            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 40 },
                            label = "Unlocked home",
                        ) {
                            AppNavHost()
                        }
                    }
                } else {
                    AppLockScreen(
                        state = state,
                        onChoose = appLock::choose,
                        onChooseAgain = appLock::chooseAgain,
                        onSubmit = appLock::submit,
                        onRetry = appLock::retry,
                    )
                }
            }
        }
    }

    /**
     * 进入前台时检查解锁会话的期限。
     */
    override fun onStart() {
        super.onStart()
        appLock.onForeground()
    }

    /**
     * 页面离开前台时开始应用锁计时。
     */
    override fun onStop() {
        if (!isChangingConfigurations) appLock.onBackground()
        super.onStop()
    }
}
