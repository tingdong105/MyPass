package com.winter.mypass.feature.home.view

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.winter.mypass.R
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val EmptyVaultAsset = "lottie/vault_empty.json"
private val EmptyVaultIllustrationWidth = 136.dp
private val EmptyVaultIllustrationHeight = 112.dp

/**
 * 以轻缓循环的空托盘和本地化文案呈现空密码本。
 * @param modifier 空状态的布局修饰符
 */
@Composable
internal fun VaultEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
    ) {
        VaultEmptyIllustration()
        Text(
            stringResource(R.string.vault_empty),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.title4,
        )
    }
}

/**
 * 从本地加载空托盘动画，应用退至后台或关闭系统动画时保留静态画面。
 */
@Composable
private fun VaultEmptyIllustration() {
    val inspecting = LocalInspectionMode.current
    val result = if (inspecting) {
        val context = LocalContext.current
        val composition = remember(context) { LottieCompositionFactory.fromAssetSync(context, EmptyVaultAsset).value }
        composition to (composition == null)
    } else {
        val loaded = rememberLottieComposition(LottieCompositionSpec.Asset(EmptyVaultAsset))
        loaded.value to loaded.isFailure
    }
    val (composition, failed) = result
    val canAnimate = if (inspecting) {
        false
    } else {
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
        lifecycle.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled()
    }
    val playback = animateLottieCompositionAsState(
        composition = composition,
        isPlaying = canAnimate,
        restartOnPlay = false,
        iterations = LottieConstants.IterateForever,
        useCompositionFrameRate = true,
    )
    val colors = MiuixTheme.colorScheme
    val dynamicColors = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, colors.onSurfaceVariantSummary.toArgb(), "**", "Tray line"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, colors.primary.toArgb(), "**", "Accent line"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, colors.primary.toArgb(), "**", "Tray wash"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, colors.onSurface.toArgb(), "**", "Tray shadow"),
    )
    val size = Modifier.size(EmptyVaultIllustrationWidth, EmptyVaultIllustrationHeight)
    if (failed) {
        Box(size, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = MiuixIcons.Folder,
                contentDescription = null,
                modifier = Modifier.size(Theme.sizes.minimumTouchTarget),
                tint = colors.onSurfaceVariantSummary,
            )
        }
    } else {
        LottieAnimation(composition, progress = { playback.progress }, modifier = size, dynamicProperties = dynamicColors)
    }
}
