package com.winter.mypass.ui.theme

import android.animation.ValueAnimator
import android.util.Log
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winter.mypass.core.utils.MMKVUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

internal val LocalAppTheme = compositionLocalOf<AppThemeViewModel?> { null }

private const val ThemePreferenceKey = "app_theme_mode"
private const val ThemeRevealDurationMillis = 450
private val ThemeRevealEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * 管理手动主题偏好与切换请求。
 * @author Tingdong
 * @date 2026-11-22
 */
internal class AppThemeViewModel : ViewModel() {
    private val storeDispatcher = Dispatchers.IO.limitedParallelism(1)
    var darkOverride by mutableStateOf<Boolean?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var reveal by mutableStateOf<ThemeReveal?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                darkOverride = withContext(storeDispatcher) {
                    when (MMKVUtils.getString(ThemePreferenceKey)) {
                        "dark" -> true
                        "light" -> false
                        else -> null
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("AppTheme", "Could not read theme preference", error)
            }
            ready = true
        }
    }

    /**
     * 提交新的主题并按点击顺序保存偏好。
     * @param dark 切换前的主题是否为暗色
     * @param center 切换按钮在 Compose 根区域的中心
     * @param animate 是否播放圆形过渡
     */
    fun toggle(dark: Boolean, center: Offset, animate: Boolean) {
        if (!ready || reveal != null || darkOverride == !dark) return
        if (animate) reveal = ThemeReveal(dark, center)
        darkOverride = !dark
        viewModelScope.launch(storeDispatcher) {
            try {
                if (!MMKVUtils.putString(ThemePreferenceKey, if (dark) "light" else "dark")) {
                    Log.w("AppTheme", "Could not save theme preference")
                }
            } catch (error: Exception) {
                Log.w("AppTheme", "Could not save theme preference", error)
            }
        }
    }

    /**
     * 移除已经完成或中断的圆形过渡。
     * @param transition 本次过渡实例
     */
    fun finish(transition: ThemeReveal) {
        if (reveal === transition) reveal = null
    }
}

/**
 * 记录明暗主题过渡的圆心与动画进度。
 * @author Tingdong
 * @date 2026-11-22
 */
internal class ThemeReveal(val fromDark: Boolean, val center: Offset) {
    val progress = Animatable(0f)

    /**
     * 计算暗色区域的半径。
     * @param width 绘制区域宽度
     * @param height 绘制区域高度
     * @param localCenter 圆心在绘制区域中的位置
     * @return 当前帧的半径（像素）
     */
    fun radius(width: Float, height: Float, localCenter: Offset = center): Float {
        val maxRadius = hypot(
            maxOf(abs(localCenter.x), abs(width - localCenter.x)),
            maxOf(abs(localCenter.y), abs(height - localCenter.y)),
        ) + 1f
        val eased = ThemeRevealEasing.transform(progress.value)
        return maxRadius * if (fromDark) 1f - eased else eased
    }
}

/**
 * 应用全局主题偏好，并随圆形过渡更新系统栏图标。
 * @param window 应用窗口
 * @param content 应用内容
 */
@Composable
internal fun AppThemeHost(window: Window, content: @Composable () -> Unit) {
    val theme: AppThemeViewModel = viewModel()
    val view = LocalView.current
    val firstFrameReady = remember(view) { mutableStateOf(false) }
    DisposableEffect(view) {
        // 首帧应用已保存的主题和系统栏样式后，再让启动窗口退出。
        val firstDraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!firstFrameReady.value) return false
                view.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        }
        view.viewTreeObserver.addOnPreDrawListener(firstDraw)
        onDispose { view.viewTreeObserver.removeOnPreDrawListener(firstDraw) }
    }
    if (!theme.ready) return

    val dark = theme.darkOverride ?: isSystemInDarkTheme()
    val reveal = theme.reveal
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var size by remember { mutableStateOf(IntSize.Zero) }
    if (reveal != null) {
        DisposableEffect(lifecycle, reveal) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) theme.finish(reveal)
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                theme.finish(reveal)
            }
        }
        LaunchedEffect(reveal) {
            if (ValueAnimator.areAnimatorsEnabled()) {
                reveal.progress.animateTo(1f, tween(ThemeRevealDurationMillis, easing = LinearEasing))
            }
            theme.finish(reveal)
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val navigation = WindowInsets.navigationBars
    val navBottom = navigation.getBottom(density)
    val navLeft = navigation.getLeft(density, direction)
    val navRight = navigation.getRight(density, direction)
    LaunchedEffect(window, view, configuration, dark, reveal, size, statusTop, navBottom, navLeft, navRight) {
        val controller = WindowCompat.getInsetsController(window, view)
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val statusCenter = Offset(width / 2f, statusTop / 2f)
        val navCenter = when {
            navRight > 0 -> Offset(width - navRight / 2f, height / 2f)
            navLeft > 0 -> Offset(navLeft / 2f, height / 2f)
            else -> Offset(width / 2f, height - navBottom / 2f)
        }
        snapshotFlow {
            if (reveal == null) dark to dark else {
                val radius = reveal.radius(width, height)
                ((statusCenter - reveal.center).getDistance() < radius) to
                    ((navCenter - reveal.center).getDistance() < radius)
            }
        }.collect { (darkStatus, darkNavigation) ->
            controller.isAppearanceLightStatusBars = !darkStatus
            controller.isAppearanceLightNavigationBars = !darkNavigation
            firstFrameReady.value = true
        }
    }

    CompositionLocalProvider(LocalAppTheme provides theme) {
        MyPassTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize().onSizeChanged { size = it }) { content() }
        }
    }
}
