package com.winter.mypass.feature.home.view

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.PasswordGenerator
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.viewmodel.GeneratorOptions
import com.winter.mypass.feature.home.viewmodel.GeneratorState
import com.winter.mypass.feature.home.viewmodel.VaultState
import com.winter.mypass.feature.home.viewmodel.VaultTab
import com.winter.mypass.ui.theme.Theme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarDefaults
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 复制生成结果后在底部提供添加记录的入口。
 * @param state 当前参数与生成结果
 * @param busy 是否正在执行操作
 * @param onOptions 修改生成参数
 * @param onRefresh 使用当前参数重新生成密码
 * @param onCopy 复制密码并回调成功结果
 * @param onAdd 用刚刚复制的密码打开新增表单
 * @param snackbarHost 承载底部提示的状态
 */
@Composable
internal fun GeneratorPage(
    state: GeneratorState,
    busy: Boolean,
    onOptions: (GeneratorOptions) -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String, () -> Unit) -> Unit,
    onAdd: (String) -> Unit,
    snackbarHost: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var copyVersion by remember { mutableIntStateOf(0) }
    val message = stringResource(R.string.vault_copy_add_prompt)
    val confirm = stringResource(R.string.vault_confirm_add)
    GeneratorPanel(state, busy, onOptions, onRefresh, copyVersion = copyVersion, onCopy = { password ->
        onCopy(password) {
            copyVersion++
            scope.launch {
                snackbarHost.newestSnackbarData()?.dismiss()
                val action = snackbarHost.showSnackbar(message, actionLabel = confirm, duration = SnackbarDuration.Custom(3_000))
                if (action == SnackbarResult.ActionPerformed) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAdd(password)
                }
            }
        }
    })
}

/**
 * 显示复制反馈及纯文字的确认操作。
 * @param state 当前提示状态
 */
@Composable
internal fun GeneratorSnackbarHost(state: SnackbarHostState) {
    val colors = MiuixTheme.colorScheme
    SnackbarHost(state) { data ->
        Snackbar(
            data,
            colors = SnackbarDefaults.snackbarColors(
                actionContainerColor = Color.Transparent,
                actionContentColor = lerp(colors.primary, colors.secondaryVariant, 0.55f),
            ),
        )
    }
}

/**
 * 显示实时生成的密码及可调参数。
 * @param state 当前参数与生成结果
 * @param busy 是否正在执行操作
 * @param onOptions 修改生成参数
 * @param onRefresh 使用当前参数重新生成密码
 * @param copyVersion 最近一次成功复制的事件序号
 * @param onCopy 生成页复制密码
 */
@Composable
private fun GeneratorPanel(
    state: GeneratorState,
    busy: Boolean,
    onOptions: (GeneratorOptions) -> Unit,
    onRefresh: () -> Unit,
    copyVersion: Int,
    onCopy: (String) -> Unit,
) {
    val options = state.options
    val actionsEnabled = !busy
    val resultAvailable = actionsEnabled && state.password.isNotEmpty()
    val passwordDisplay = remember { GeneratedPasswordDisplay() }
    val refreshRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val cardInset = Theme.spacing.medium
    val fieldRadius = Theme.spacing.small
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = fieldRadius + cardInset,
            insideMargin = PaddingValues(cardInset),
        ) {
            DashedPassword(state.password, fieldRadius, passwordDisplay)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
            ) {
                GeneratorActionButton(
                    R.string.vault_refresh_password,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            MiuixIcons.Refresh, contentDescription = null,
                            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = refreshRotation.value },
                        )
                    },
                    onClick = {
                        onRefresh()
                        scope.launch {
                            refreshRotation.animateTo(
                                (refreshRotation.value.toInt() / 360 + 1) * 360f,
                                tween(420, easing = FastOutSlowInEasing),
                            )
                        }
                    },
                )
                GeneratorActionButton(
                    R.string.vault_copy_action, enabled = resultAvailable,
                    modifier = Modifier.weight(1f), primary = true,
                    icon = { VaultCopyIcon(copyVersion) },
                ) { passwordDisplay.visiblePassword?.let(onCopy) }
            }
        }
        if (state.failed) VaultErrorText(VaultProblem.Storage)
        VaultGroup(Modifier.padding(top = Theme.spacing.small)) {
            GeneratorLength(options.length, enabled = !busy) { onOptions(options.copy(length = it)) }
            GeneratorSwitch(
                R.string.vault_letters, options.letters, enabled = !busy && (!options.letters || options.digits || options.symbols),
            ) { onOptions(options.copy(letters = it)) }
            AnimatedVisibility(visible = options.letters) {
                GeneratorSwitch(R.string.vault_uppercase, options.uppercase, enabled = !busy) {
                    onOptions(options.copy(uppercase = it))
                }
            }
            GeneratorSwitch(
                R.string.vault_digits, options.digits, enabled = !busy && (!options.digits || options.letters || options.symbols),
            ) { onOptions(options.copy(digits = it)) }
            GeneratorSwitch(
                R.string.vault_symbols, options.symbols, enabled = !busy && (!options.symbols || options.letters || options.digits),
            ) { onOptions(options.copy(symbols = it)) }
        }
    }
}

/**
 * 在随内容伸缩的描边中展示密码。
 * @param password 当前生成结果
 * @param radius 虚线框圆角
 * @param display 复制时使用的文字层状态
 */
@Composable
private fun DashedPassword(password: String, radius: Dp, display: GeneratedPasswordDisplay) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth()
            .drawWithCache {
                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2
                val outline = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = colors.onSurfaceVariantSummary.copy(alpha = 0.55f),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        cornerRadius = CornerRadius(radius.toPx() - inset),
                        style = outline,
                    )
                }
            }
            // 卡片跟随这里的测量高度，描边与按钮同步移动。
            .animateContentSize(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                alignment = Alignment.TopStart,
            )
            .heightIn(min = Theme.sizes.minimumTouchTarget + Theme.spacing.large)
            .padding(horizontal = Theme.spacing.small, vertical = Theme.spacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        if (password.isEmpty()) {
            Text(
                "•••• •••• •••• ••••",
                modifier = Modifier.fillMaxWidth(),
                style = MiuixTheme.textStyles.title3,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            GeneratedPassword(password, colors.onSurface, display)
        }
    }
}

/**
 * 在胶囊按钮中组合图标和操作文字。
 * @param label 操作文字
 * @param enabled 是否允许点击
 * @param modifier 在卡片内所占的区域
 * @param primary 是否为主要操作
 * @param icon 操作图标及其动效
 * @param onClick 点击后的操作
 */
@Composable
private fun GeneratorActionButton(
    @StringRes label: Int,
    enabled: Boolean,
    modifier: Modifier,
    primary: Boolean = false,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        cornerRadius = Theme.sizes.minimumTouchTarget,
        minWidth = 0.dp,
        minHeight = 44.dp,
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
        insideMargin = PaddingValues(horizontal = Theme.spacing.medium, vertical = Theme.spacing.small),
    ) {
        icon()
        Spacer(Modifier.width(Theme.spacing.small))
        Text(stringResource(label), maxLines = 1)
    }
}

/**
 * 显示长度标题和当前值，下方的滑杆占满一行。
 * @param length 当前密码长度
 * @param enabled 是否可调
 * @param onChange 更新密码长度
 */
@Composable
private fun GeneratorLength(length: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val range = PasswordGenerator.LengthRange
    val label = stringResource(R.string.vault_length)
    val view = LocalView.current
    var requestedLength by remember(length) { mutableIntStateOf(length) }
    val stepFeedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = Theme.spacing.medium, end = Theme.spacing.medium,
            top = Theme.spacing.medium, bottom = Theme.spacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                length.toString(),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.title4.copy(fontFeatureSettings = "tnum"),
            )
        }
        Slider(
            value = length.toFloat(),
            onValueChange = {
                val next = it.roundToInt()
                if (enabled && next != requestedLength) {
                    requestedLength = next
                    view.performHapticFeedback(stepFeedback)
                    onChange(next)
                }
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            hapticEffect = SliderDefaults.SliderHapticEffect.None,
        )
    }
}

/**
 * 整行可切换的字符类型选项。
 * @param label 字符类型名称
 * @param checked 是否启用
 * @param enabled 是否可调
 * @param onChange 更新启用状态
 */
@Composable
private fun GeneratorSwitch(
    @StringRes label: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val view = LocalView.current
    BasicComponent(
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onChange(it)
        },
        title = stringResource(label),
        endActions = { Switch(checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * 预览生成结果和密码生成器的调整入口。
 */
@VaultPreviews
@Composable
internal fun GeneratorPreview() {
    VaultTabbedPreview(VaultState(tab = VaultTab.Generate, generator = GeneratorState(password = "Q3zpA5B8jNfSvMkU")))
}

/**
 * 预览最长密码在窄屏卡片中的换行。
 */
@VaultPreviews
@Composable
internal fun GeneratorLongPreview() {
    val password = "Q3zpA5B8jNfSvMkU2x7R".repeat(3) + "n8Rb"
    VaultTabbedPreview(
        VaultState(
            tab = VaultTab.Generate,
            generator = GeneratorState(options = GeneratorOptions(length = password.length), password = password),
        ),
    )
}
