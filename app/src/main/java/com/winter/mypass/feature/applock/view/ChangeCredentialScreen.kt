package com.winter.mypass.feature.applock.view

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.winter.mypass.R
import com.winter.mypass.feature.applock.model.CredentialType
import com.winter.mypass.feature.applock.viewmodel.AppLockState
import com.winter.mypass.feature.applock.viewmodel.AppLockViewModel
import com.winter.mypass.feature.applock.viewmodel.LockScreen
import com.winter.mypass.feature.home.view.VaultFrame
import com.winter.mypass.feature.home.view.VaultGroup
import com.winter.mypass.feature.home.view.VaultIcon
import com.winter.mypass.feature.home.view.VaultLink
import com.winter.mypass.feature.home.view.VaultPreviews
import com.winter.mypass.feature.home.view.VaultRowDivider
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * 启动密码更换流程的四个可见步骤。
 * @author Tingdong
 * @date 2026-11-20
 */
private enum class ChangeStep { Current, Method, New, Confirm }

private val LockScreen.changeStep: ChangeStep?
    get() = when (this) {
        is LockScreen.Locked -> ChangeStep.Current
        LockScreen.Choose -> ChangeStep.Method
        is LockScreen.Setup -> if (confirm) ChangeStep.Confirm else ChangeStep.New
        else -> null
    }

private val LockScreen.changePage: Any
    get() = changeStep ?: this::class

private val LockScreen.hasInAppControls: Boolean
    get() = when (this) {
        is LockScreen.Locked -> type != CredentialType.Password
        is LockScreen.Setup -> type != CredentialType.Password
        else -> false
    }

/**
 * 连接修改密码页面和解锁会话，保存成功后返回设置。
 * @param model 当前会话的应用锁状态
 * @param onNavigateBack 返回当前导航宿主的设置页
 */
@Composable
internal fun ChangeCredentialRoute(model: AppLockViewModel, onNavigateBack: () -> Boolean) {
    val state by model.credentialChange.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val navigateBack by rememberUpdatedState(onNavigateBack)
    val onBack: () -> Unit = { if (model.backCredentialChange()) navigateBack() }
    LaunchedEffect(model, lifecycle, context) {
        model.startCredentialChange()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.credentialChange.first { it?.screen == LockScreen.Unlocked }
            if (navigateBack()) {
                model.finishCredentialChange()
                Toast.makeText(context, R.string.lock_change_success, Toast.LENGTH_SHORT).show()
            }
        }
    }
    BackHandler(onBack = onBack)
    ChangeCredentialScreen(
        state = state ?: AppLockState(),
        onBack = onBack,
        onChoose = model::chooseCredentialChangeType,
        onSubmit = model::submitCredentialChange,
        onRetry = model::startCredentialChange,
    )
}

/**
 * 绘制验证、选型和两次输入的修改流程。
 * @param state 当前修改步骤
 * @param onBack 返回上一步或设置
 * @param onChoose 选择新密码类型
 * @param onSubmit 提交当前输入
 * @param onRetry 重新读取当前凭据
 */
@Composable
private fun ChangeCredentialScreen(
    state: AppLockState,
    onBack: () -> Unit,
    onChoose: (CredentialType) -> Unit,
    onSubmit: (CharArray) -> Unit,
    onRetry: () -> Unit,
) {
    val screen = state.screen
    val forwardOffset = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val slideLimit = with(LocalDensity.current) { (Theme.spacing.large * 2).roundToPx() }
    VaultFrame(
        title = stringResource(R.string.lock_change_title),
        navigationIcon = {
            VaultIcon(
                MiuixIcons.Back, R.string.lock_back,
                enabled = !(screen is LockScreen.Setup && state.busy),
                onClick = onBack,
            )
        },
    ) { modifier ->
        BoxWithConstraints(modifier.clipToBounds()) {
            val viewportHeight = maxHeight
            val maxPatternSide = (viewportHeight - Theme.spacing.large * 2).coerceIn(0.dp, 288.dp)
            val topPadding = (viewportHeight / 12).coerceIn(Theme.spacing.medium, Theme.spacing.large * 2) +
                Theme.spacing.large + Theme.spacing.medium
            AnimatedContent(
                targetState = state,
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
                contentKey = { it.screen.changePage },
                transitionSpec = {
                    val from = initialState.screen.changeStep
                    val to = targetState.screen.changeStep
                    if (from != null && to != null && from != to) {
                        val direction = if (to > from) forwardOffset else -forwardOffset
                        val enter = fadeIn(tween(170)) +
                            slideInHorizontally(tween(235, easing = FastOutSlowInEasing)) {
                                direction * minOf(it / 6, slideLimit)
                            }
                        val exit = fadeOut(tween(130)) +
                            slideOutHorizontally(tween(195, easing = FastOutSlowInEasing)) {
                                -direction * minOf(it / 8, slideLimit)
                            }
                        enter togetherWith exit
                    } else {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(110))
                    }
                },
                label = "Credential change step",
            ) { page ->
                val step = page.screen.changeStep
                val bottomAligned = page.screen.hasInAppControls
                Column(
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = viewportHeight)
                        .padding(
                            start = Theme.spacing.large,
                            end = Theme.spacing.large,
                            top = if (step != null) topPadding else Theme.spacing.large,
                            bottom = Theme.spacing.large,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = when {
                        bottomAligned -> Arrangement.SpaceBetween
                        step == null -> Arrangement.spacedBy(Theme.spacing.large, Alignment.CenterVertically)
                        else -> Arrangement.spacedBy(Theme.spacing.large)
                    },
                ) {
                    ChangeStepContent(
                        state = page.copy(inputRevision = state.inputRevision),
                        active = page.screen.changePage == screen.changePage,
                        bottomAligned = bottomAligned,
                        maxPatternSide = maxPatternSide,
                        onChoose = onChoose,
                        onSubmit = onSubmit,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

/**
 * 显示当前步骤的表单或恢复操作。
 * @param state 当前步骤的显示状态
 * @param active 是否允许与当前步骤交互
 * @param bottomAligned 应用内输入区是否贴近底部
 * @param maxPatternSide 九宫格的边长上限
 * @param onChoose 选择解锁方式
 * @param onSubmit 提交密码输入
 * @param onRetry 重试读取密码记录
 */
@Composable
private fun ChangeStepContent(
    state: AppLockState,
    active: Boolean,
    bottomAligned: Boolean,
    maxPatternSide: Dp,
    onChoose: (CredentialType) -> Unit,
    onSubmit: (CharArray) -> Unit,
    onRetry: () -> Unit,
) {
    val submit: (CharArray) -> Unit = { input -> if (active) onSubmit(input) else input.fill('\u0000') }
    when (val screen = state.screen) {
        LockScreen.Loading -> LockProgress()
        is LockScreen.Locked -> CredentialEntry(
            type = screen.type,
            title = R.string.lock_change_current,
            decoration = {},
            state = state,
            active = active,
            maxPatternSide = maxPatternSide,
            retryAt = screen.retryAt,
            submitLabel = R.string.lock_change_verify,
            bottomAligned = bottomAligned,
            onSubmit = submit,
        )
        LockScreen.Choose -> {
            LockHeader(R.string.lock_change_type)
            VaultGroup {
                CredentialType.entries.forEachIndexed { index, type ->
                    if (index > 0) VaultRowDivider()
                    VaultLink(stringResource(type.label), enabled = active) { onChoose(type) }
                }
            }
        }
        is LockScreen.Setup -> CredentialEntry(
            type = screen.type,
            title = if (screen.confirm) R.string.lock_change_confirm else R.string.lock_change_new,
            decoration = {},
            state = state,
            active = active,
            maxPatternSide = maxPatternSide,
            submitLabel = if (screen.confirm) R.string.vault_save else R.string.lock_change_continue,
            bottomAligned = bottomAligned,
            onSubmit = submit,
        )
        is LockScreen.Unavailable -> {
            LockHeader(R.string.lock_storage_error_title, stringResource(screen.reason.message))
            TextButton(
                text = stringResource(R.string.lock_retry),
                onClick = onRetry,
                enabled = active,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
        LockScreen.Unlocked -> Unit
    }
}

/** 预览修改密码页的手机与平板布局。 */
@VaultPreviews
@Composable
private fun ChangeCredentialPreview() {
    MyPassTheme(dynamicColor = false) {
        ChangeCredentialScreen(AppLockState(LockScreen.Locked(CredentialType.Pin6, 0)), {}, {}, {}, {})
    }
}

/** 预览解锁方式选择列表。 */
@VaultPreviews
@Composable
private fun ChangeCredentialMethodPreview() {
    MyPassTheme(dynamicColor = false) {
        ChangeCredentialScreen(AppLockState(LockScreen.Choose), {}, {}, {}, {})
    }
}
