package com.winter.mypass.feature.applock.view

import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.winter.mypass.R
import com.winter.mypass.feature.applock.model.CredentialType
import com.winter.mypass.feature.applock.viewmodel.AppLockState
import com.winter.mypass.feature.applock.viewmodel.LockError
import com.winter.mypass.feature.applock.viewmodel.LockScreen
import com.winter.mypass.feature.applock.viewmodel.LockStorageError
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 显示应用锁的设置、解锁或错误页面。
 * @param state 当前界面状态
 * @param onChoose 选择凭据类型
 * @param onChooseAgain 返回类型选择
 * @param onSubmit 提交凭据
 * @param onRetry 重试读取本机凭据
 */
@Composable
internal fun AppLockScreen(
    state: AppLockState,
    onChoose: (CredentialType) -> Unit,
    onChooseAgain: () -> Unit,
    onSubmit: (CharArray) -> Unit,
    onRetry: () -> Unit,
) {
    val screen = state.screen
    val greetingAsset = when (screen) {
        LockScreen.Choose, is LockScreen.Setup -> "lottie/lock_first_greeting.json"
        is LockScreen.Locked -> "lottie/lock_welcome_back.json"
        else -> null
    }
    val greeting = rememberLockGreeting(greetingAsset)
    BackHandler(enabled = screen is LockScreen.Setup) {
        if (!state.busy) onChooseAgain()
    }
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).clipToBounds(),
        contentKey = { it.screen.pageKey },
        transitionSpec = {
            val from = initialState.screen.setupStep
            val to = targetState.screen.setupStep
            if (from != null && to != null && to != from) {
                val direction = if (to > from) forward else -forward
                val enter = fadeIn(tween(220)) +
                    slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { direction * it / 8 }
                val exit = fadeOut(tween(140)) +
                    slideOutHorizontally(tween(210, easing = FastOutSlowInEasing)) { -direction * it / 10 }
                enter togetherWith exit
            } else {
                fadeIn(tween(200)) togetherWith fadeOut(tween(120))
            }
        },
        label = "App lock page",
    ) { pageState ->
        val active = pageState.screen.pageKey == state.screen.pageKey
        AppLockPage(
            state = pageState.copy(inputRevision = state.inputRevision),
            active = active,
            greeting = greeting,
            onChoose = onChoose,
            onChooseAgain = onChooseAgain,
            onSubmit = onSubmit,
            onRetry = onRetry,
        )
    }
}

private val LockScreen.pageKey: Any
    get() = if (this is LockScreen.Locked) type else this

private val LockScreen.setupStep: Int?
    get() = when (this) {
        LockScreen.Choose -> 0
        is LockScreen.Setup -> if (confirm) 2 else 1
        else -> null
    }

/**
 * 绘制应用锁单页，退出中的页面仅保留过渡画面。
 * @param state 当前页的显示状态
 * @param active 是否为可交互的目标页
 * @param greeting 按可用高度绘制问候动画
 * @param onChoose 选择凭据类型
 * @param onChooseAgain 返回类型选择
 * @param onSubmit 提交凭据
 * @param onRetry 重试读取凭据
 */
@Composable
private fun AppLockPage(
    state: AppLockState,
    active: Boolean,
    greeting: @Composable (Dp) -> Unit,
    onChoose: (CredentialType) -> Unit,
    onChooseAgain: () -> Unit,
    onSubmit: (CharArray) -> Unit,
    onRetry: () -> Unit,
) {
    val screen = state.screen
    val type = when (screen) {
        is LockScreen.Setup -> screen.type
        is LockScreen.Locked -> screen.type
        else -> null
    }
    Scaffold(
        topBar = {
            if (screen is LockScreen.Setup) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = Theme.spacing.small),
                ) {
                    IconButton(
                        onClick = onChooseAgain,
                        enabled = active && !state.busy,
                        modifier = Modifier.size(Theme.sizes.minimumTouchTarget),
                    ) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.lock_back))
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            val viewportHeight = maxHeight
            val lowerKeypad = type != null && type.pinLength > 0 && maxWidth < 600.dp && viewportHeight >= 560.dp
            val headerInset = if (lowerKeypad) (viewportHeight - 520.dp) / 3 else 0.dp
            val greetingHeight = if (viewportHeight < 480.dp) 90.dp else 130.dp
            val decoration: @Composable () -> Unit = { greeting(greetingHeight) }
            val maxPatternSide = (viewportHeight - Theme.spacing.large * 2).coerceIn(0.dp, 288.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(
                        start = Theme.spacing.large,
                        end = Theme.spacing.large,
                        top = Theme.spacing.large + headerInset,
                        bottom = Theme.spacing.large,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (lowerKeypad) Arrangement.SpaceBetween
                else Arrangement.spacedBy(Theme.spacing.medium, Alignment.CenterVertically),
            ) {
                when (screen) {
                    LockScreen.Loading -> LockProgress()
                    LockScreen.Choose -> {
                        LockHeader(R.string.lock_setup_title, stringResource(R.string.lock_choose_hint), decoration)
                        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                            CredentialType.entries.forEach { type ->
                                TextButton(
                                    text = stringResource(type.label),
                                    onClick = { onChoose(type) },
                                    enabled = active,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    is LockScreen.Setup -> CredentialEntry(
                        type = screen.type,
                        title = if (screen.confirm) R.string.lock_confirm_title else R.string.lock_setup_title,
                        decoration = decoration,
                        state = state,
                        active = active,
                        maxPatternSide = maxPatternSide,
                        onSubmit = onSubmit,
                    )
                    is LockScreen.Locked -> CredentialEntry(
                        type = screen.type,
                        title = R.string.lock_unlock_title,
                        decoration = decoration,
                        state = state,
                        active = active,
                        maxPatternSide = maxPatternSide,
                        retryAt = screen.retryAt,
                        onSubmit = onSubmit,
                    )
                    is LockScreen.Unavailable -> {
                        val detail = when (screen.reason) {
                            LockStorageError.InvalidRecord -> R.string.lock_storage_invalid_record
                            LockStorageError.MissingKey -> R.string.lock_storage_missing_key
                            LockStorageError.Retryable -> R.string.lock_storage_error
                        }
                        LockHeader(R.string.lock_storage_error_title, stringResource(detail), decoration)
                        if (screen.reason == LockStorageError.Retryable) {
                            TextButton(
                                text = stringResource(R.string.lock_retry),
                                onClick = onRetry,
                                enabled = active,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                    LockScreen.Unlocked -> Unit
                }
            }
        }
    }
}

/**
 * 展示凭据输入及相应的错误或等待状态。
 * @param type 当前凭据类型
 * @param title 页面标题资源
 * @param decoration 标题上方的问候动画
 * @param state 页面状态
 * @param active 是否为可交互的目标页
 * @param maxPatternSide 当前视窗下的九宫格边长上限
 * @param retryAt 允许再次验证的单调时钟时间
 * @param onSubmit 提交输入
 */
@Composable
private fun CredentialEntry(
    type: CredentialType,
    @StringRes title: Int,
    decoration: @Composable () -> Unit,
    state: AppLockState,
    active: Boolean,
    maxPatternSide: Dp,
    onSubmit: (CharArray) -> Unit,
    retryAt: Long = 0,
) {
    val seconds by key(retryAt) {
        produceState(remainingSeconds(retryAt), retryAt) {
            while (value > 0) {
                val remaining = retryAt - SystemClock.elapsedRealtime()
                if (remaining > 0) delay((remaining - 1) % 1_000 + 1)
                value = remainingSeconds(retryAt)
            }
        }
    }
    LockHeader(title, stringResource(type.hint), decoration)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
    ) {
        if (type.pinLength > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = Theme.spacing.large),
                contentAlignment = Alignment.Center,
            ) {
                CredentialFeedback(state, seconds)
            }
        }
        val enabled = active && !state.busy && seconds == 0L
        when (type) {
            CredentialType.Pattern -> key(state.inputRevision) {
                PatternInput(enabled = enabled, maxSide = maxPatternSide, onSubmit = onSubmit)
            }
            CredentialType.Pin4, CredentialType.Pin6 -> PinInput(
                length = type.pinLength,
                enabled = enabled,
                inputRevision = state.inputRevision,
                showCompletion = state.busy || !active,
                onSubmit = onSubmit,
            )
            CredentialType.Password -> key(state.inputRevision) {
                PasswordInput(enabled = enabled, onSubmit = onSubmit)
            }
        }
        if (type.pinLength == 0) CredentialFeedback(state, seconds)
    }
}

/**
 * 显示验证进度、重试倒计时或错误提示。
 * @param state 当前凭据操作状态
 * @param seconds 重试前剩余秒数
 */
@Composable
private fun CredentialFeedback(state: AppLockState, seconds: Long) {
    when {
        state.busy -> LockProgress()
        seconds > 0 -> Text(
            text = stringResource(R.string.lock_wait, seconds),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.body2.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.Center,
        )
        state.error != null -> Text(
            text = stringResource(
                when (state.error) {
                    LockError.Invalid -> R.string.lock_invalid
                    LockError.Mismatch -> R.string.lock_mismatch
                    LockError.Incorrect -> R.string.lock_incorrect
                },
            ),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.body2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 仅在凭据操作持续较久时显示进度，避免快速完成时闪现转圈。
 */
@Composable
private fun LockProgress() {
    val visible by produceState(false) {
        delay(300)
        value = true
    }
    if (visible) InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
}

/**
 * 按凭据状态播放一次本地问候动画，设置时在各步骤之间延续同一次播放。
 * @param asset 问候动画在 assets 中的路径，未配置时显示静态锁图标
 * @return 接受显示高度的问候内容
 */
@Composable
private fun rememberLockGreeting(asset: String?): @Composable (Dp) -> Unit {
    if (asset == null) return { LockIcon() }
    val result = rememberLottieComposition(LottieCompositionSpec.Asset(asset))
    val composition by result
    val playback = animateLottieCompositionAsState(composition)
    return { height ->
        val modifier = Modifier.size(width = height * 1.5f, height = height)
        if (result.isFailure) {
            Box(modifier, contentAlignment = Alignment.Center) { LockIcon() }
        } else {
            LottieAnimation(composition, progress = { playback.progress }, modifier = modifier)
        }
    }
}

/**
 * 显示问候动画不可用时的应用锁图标。
 */
@Composable
private fun LockIcon() {
    Icon(imageVector = MiuixIcons.Lock, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
}

/**
 * 显示应用锁问候、标题及简短说明。
 * @param title 标题资源
 * @param description 当前步骤的说明
 * @param decoration 标题上方的内容
 */
@Composable
private fun LockHeader(@StringRes title: Int, description: String, decoration: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
    ) {
        decoration()
        Text(
            text = stringResource(title),
            style = MiuixTheme.textStyles.title1.copy(fontSize = 28.sp, lineBreak = LineBreak.Heading),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.body2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 计算倒计时的剩余秒数。示例：remainingSeconds(0)。
 * @param retryAt 单调时钟截止时间
 * @return 向上取整的剩余秒数
 */
private fun remainingSeconds(retryAt: Long): Long =
    ((retryAt - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1_000

private val CredentialType.label: Int
    get() = when (this) {
        CredentialType.Pattern -> R.string.lock_type_pattern
        CredentialType.Pin4 -> R.string.lock_type_pin4
        CredentialType.Pin6 -> R.string.lock_type_pin6
        CredentialType.Password -> R.string.lock_type_password
    }

private val CredentialType.hint: Int
    get() = when (this) {
        CredentialType.Pattern -> R.string.lock_pattern_hint
        CredentialType.Pin4 -> R.string.lock_pin4_hint
        CredentialType.Pin6 -> R.string.lock_pin6_hint
        CredentialType.Password -> R.string.lock_password_hint
    }

/**
 * 预览首次设置页面的手机与平板布局。
 */
@Preview(name = "手机 · 浅色", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "手机 · 深色", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "平板 · 浅色", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "平板 · 深色", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppLockSetupPreview() {
    MyPassTheme(dynamicColor = false) {
        AppLockScreen(AppLockState(LockScreen.Choose), {}, {}, {}, {})
    }
}

/**
 * 预览解锁页面的手机与平板布局。
 */
@Preview(name = "手机 · 浅色", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "手机 · 深色", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "平板 · 浅色", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "平板 · 深色", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppLockUnlockPreview() {
    MyPassTheme(dynamicColor = false) {
        AppLockScreen(AppLockState(LockScreen.Locked(CredentialType.Pin6, 0)), {}, {}, {}, {})
    }
}
