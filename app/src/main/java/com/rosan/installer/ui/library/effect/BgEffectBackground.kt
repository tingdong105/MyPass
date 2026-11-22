// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted for MyPass window size and lifecycle, 2026-11-22.

package com.rosan.installer.ui.library.effect

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.winter.mypass.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor

/**
 * 沿用 InstallerX 的 OS3 流光，离开前台时暂停。
 * @param modifier 背景范围
 * @param bgModifier 背景采样修饰符
 * @param alpha 随滚动变化的背景透明度
 * @param content 背景上方内容
 */
@Composable
internal fun BgEffectBackground(
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isRuntimeShaderSupported()) {
        Box(modifier.background(surface), content = content)
        return
    }
    val isDark = LocalDarkTheme.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !LocalInspectionMode.current
    BoxWithConstraints(modifier) {
        val device = if (maxWidth >= 840.dp) DeviceType.PAD else DeviceType.PHONE
        val painter = remember { BgEffectPainter() }
        val preset = remember(device, isDark) { BgEffectConfig.get(device, isDark, true) }
        val stage = remember { Animatable(0f) }
        LaunchedEffect(playing, preset) {
            if (!playing) return@LaunchedEffect
            var target = floor(stage.value) + 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong())
                stage.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 35f))
                target += 1f
            }
        }
        Spacer(
            Modifier.fillMaxSize().then(bgModifier).bgEffectDraw(
                painter, preset, device, isDark, surface,
                effectBackground = true, isFullSize = false, playing = playing,
                colorStage = { stage.value }, alpha = alpha,
            ),
        )
        content()
    }
}
