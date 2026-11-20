package com.winter.mypass.feature.applock.view

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.applock.model.CredentialType
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 带掩码圆点的应用内数字键盘，输满后提交。
 * @param length 所需位数
 * @param enabled 是否可接受输入
 * @param inputRevision 当前输入轮次
 * @param showCompletion 是否在验证或切页期间保留输满反馈
 * @param onSubmit 提交 PIN 字符数组
 */
@Composable
internal fun PinInput(
    length: Int,
    enabled: Boolean,
    inputRevision: Int,
    showCompletion: Boolean,
    onSubmit: (CharArray) -> Unit,
) {
    val view = LocalView.current
    val digits = remember(length, inputRevision) { CharArray(length) }
    var count by remember(digits) { mutableIntStateOf(0) }
    var completed by remember(length) { mutableStateOf(false) }
    DisposableEffect(digits) {
        onDispose { digits.fill('\u0000') }
    }
    LaunchedEffect(inputRevision, showCompletion) {
        if (!showCompletion) completed = false
    }

    val append: (Char) -> Unit = { digit ->
        if (enabled && count < length) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            digits[count++] = digit
            if (count == length) {
                val submission = digits.copyOf()
                digits.fill('\u0000')
                completed = true
                onSubmit(submission)
            }
        }
    }
    val filledCount = if (showCompletion && completed) length else count
    val progress = stringResource(R.string.lock_pin_progress, filledCount, length)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
            modifier = Modifier.semantics { contentDescription = progress },
        ) {
            repeat(length) { index ->
                PinDot(filled = index < filledCount)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                    repeat(3) { column ->
                        val digit = '1' + row * 3 + column
                        PinKey(digit, enabled, Modifier.weight(1f)) { append(digit) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                Spacer(Modifier.weight(1f))
                PinKey('0', enabled, Modifier.weight(1f)) { append('0') }
                PinKey(null, enabled && count > 0, Modifier.weight(1f)) {
                    if (enabled && count > 0) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        digits[--count] = '\u0000'
                    }
                }
            }
        }
    }
}

/**
 * 输入时让掩码圆点着色、上弹并回落。
 * @param filled 是否已经输入这一位
 */
@Composable
private fun PinDot(filled: Boolean) {
    val bounce = remember { Animatable(0f) }
    val color = animateColorAsState(
        targetValue = if (filled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.disabledPrimary,
        animationSpec = tween(120),
        label = "PIN dot color",
    )
    LaunchedEffect(filled) {
        if (filled) {
            bounce.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 230
                    1f at 72 using FastOutSlowInEasing
                    -0.24f at 134 using FastOutSlowInEasing
                    0.12f at 176 using LinearOutSlowInEasing
                },
            )
        } else {
            bounce.animateTo(0f, tween(100))
        }
    }
    Box(
        Modifier
            .size(12.dp)
            .graphicsLayer {
                val offset = bounce.value
                translationY = -5.dp.toPx() * offset
                scaleX = 1f + 0.22f * offset
                scaleY = 1f + 0.38f * offset
            }
            .drawBehind { drawCircle(color.value) },
    )
}

/**
 * 显示数字键或删除键。
 * @param digit 数字键对应的字符，为空时显示退格图标
 * @param enabled 是否可按下
 * @param modifier 键的布局修饰符
 * @param onClick 按键操作
 */
@Composable
private fun PinKey(digit: Char?, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        minHeight = Theme.sizes.minimumTouchTarget,
        insideMargin = PaddingValues(Theme.spacing.small),
    ) {
        if (digit == null) {
            Icon(
                painter = painterResource(R.drawable.ic_backspace),
                contentDescription = stringResource(R.string.lock_delete_digit),
            )
        } else {
            Text(digit.toString(), style = MiuixTheme.textStyles.title3)
        }
    }
}

/**
 * 显示带掩码的混合密码输入框。
 * @param enabled 是否可接受输入
 * @param submitLabel 提交按钮的文字
 * @param onSubmit 提交混合密码字符数组
 */
@Composable
internal fun PasswordInput(enabled: Boolean, @StringRes submitLabel: Int, onSubmit: (CharArray) -> Unit) {
    var value by remember { mutableStateOf("") }
    val submit: () -> Unit = {
        if (enabled && value.isNotEmpty()) {
            val submission = value.toCharArray()
            value = ""
            onSubmit(submission)
        }
    }
    TextField(
        value = value,
        onValueChange = { if (it.length <= CredentialType.MaxPasswordLength) value = it },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.lock_password_label),
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    TextButton(
        text = stringResource(submitLabel),
        onClick = submit,
        enabled = enabled && value.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColorsPrimary(),
    )
}
