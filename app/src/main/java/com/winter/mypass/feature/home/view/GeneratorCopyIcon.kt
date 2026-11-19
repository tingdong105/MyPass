package com.winter.mypass.feature.home.view

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.LocalContentColor

private const val CopyConfirmationAsset = "lottie/generator_copy.json"

/**
 * 密码复制成功后播放一次确认动效。
 * @param copyVersion 每次复制成功后递增，从零开始。
 * @param modifier 图标的布局修饰符。
 */
@Composable
internal fun GeneratorCopyIcon(copyVersion: Int, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(CopyConfirmationAsset))
    val playback = rememberLottieAnimatable()
    val color = LocalContentColor.current.toArgb()
    val ink = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, color, "**", "Icon ink"),
    )

    LaunchedEffect(copyVersion, composition) {
        if (copyVersion > 0 && composition != null && ValueAnimator.areAnimatorsEnabled()) {
            playback.animate(composition, iterations = 1, initialProgress = 0f)
        }
    }

    val iconModifier = modifier.size(18.dp)
    if (composition == null) {
        Icon(MiuixIcons.Copy, contentDescription = null, modifier = iconModifier)
    } else {
        LottieAnimation(composition, progress = { playback.progress }, modifier = iconModifier, dynamicProperties = ink)
    }
}
