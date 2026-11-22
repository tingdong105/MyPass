package com.winter.mypass.feature.home.view

import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.winter.mypass.R
import com.winter.mypass.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val CopyConfirmationAsset = "lottie/generator_copy.json"

/**
 * 播放复制确认动效，播完后恢复初始图标。
 * @param copyVersion 复制成功后递增的序号
 * @param modifier 图标的布局修饰符
 * @param tint 动效和图标的颜色
 */
@Composable
internal fun VaultCopyIcon(
    copyVersion: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(CopyConfirmationAsset))
    val playback = rememberLottieAnimatable()
    val version by rememberUpdatedState(copyVersion)
    var staticConfirmation by remember { mutableStateOf(false) }
    val tintFilter = remember(tint) { PorterDuffColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_IN) }
    val ink = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR_FILTER, tintFilter, "**", "Icon ink"),
    )

    LaunchedEffect(composition) {
        snapshotFlow { version }.filter { it > 0 }.collect {
            if (composition != null && ValueAnimator.areAnimatorsEnabled()) {
                playback.animate(composition, iterations = 1, initialProgress = 0f)
            } else {
                staticConfirmation = true
                try {
                    delay(500)
                } finally {
                    staticConfirmation = false
                }
            }
        }
    }

    val iconModifier = modifier.size(18.dp)
    when {
        staticConfirmation -> Icon(MiuixIcons.Basic.Check, null, iconModifier, tint = tint)
        composition != null -> LottieAnimation(composition, { playback.progress }, iconModifier, dynamicProperties = ink)
        else -> Icon(MiuixIcons.Copy, null, iconModifier, tint = tint)
    }
}

/**
 * 复制按钮在写入成功后播放确认动效。
 * @param enabled 是否可复制
 * @param modifier 按钮在当前容器中的位置
 * @param tint 线条颜色，默认跟随主题和可用状态
 * @param onCopy 执行复制并在成功后调用传入的回调
 */
@Composable
internal fun VaultCopyButton(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
    onCopy: (() -> Unit) -> Unit,
) {
    var copyVersion by remember { mutableIntStateOf(0) }
    val label = stringResource(R.string.vault_copy_password)
    IconButton(
        onClick = { onCopy { copyVersion++ } },
        modifier = modifier.semantics { contentDescription = label },
        enabled = enabled,
        minWidth = Theme.sizes.minimumTouchTarget,
        minHeight = Theme.sizes.minimumTouchTarget,
    ) {
        VaultCopyIcon(
            copyVersion, Modifier.size(22.dp),
            tint = tint,
        )
    }
}
