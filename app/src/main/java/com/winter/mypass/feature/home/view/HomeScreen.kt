package com.winter.mypass.feature.home.view

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.winter.mypass.R
import com.winter.mypass.ui.theme.MyPassTheme

/**
 * 首页问候内容，数据由路由入口传入。
 * @param name 问候对象
 * @param modifier 页面布局修饰符
 */
@Composable
fun HomeScreen(name: String, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { innerPadding ->
        Text(
            text = stringResource(R.string.home_greeting, name),
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }
}

/**
 * 手机与平板明暗主题预览。
 */
@Preview(name = "手机 · 浅色", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "手机 · 深色", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "平板 · 浅色", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "平板 · 深色", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenPreview() {
    MyPassTheme(dynamicColor = false) {
        HomeScreen(name = "Android")
    }
}
