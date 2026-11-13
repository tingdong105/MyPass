package com.winter.mypass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.winter.mypass.nav.AppNavHost
import com.winter.mypass.ui.theme.MyPassTheme

/**
 * 应用主题与导航宿主入口。
 * @author Tingdong
 * @date 2026-11-13
 */
class MainActivity : ComponentActivity() {
    /**
     * 初始化窗口并挂载导航。
     * @param savedInstanceState 系统保存的界面状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyPassTheme {
                AppNavHost()
            }
        }
    }
}
