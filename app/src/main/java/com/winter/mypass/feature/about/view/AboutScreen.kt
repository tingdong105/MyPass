// SPDX-License-Identifier: GPL-3.0-only
// About layout adapted from InstallerX Revived, Copyright (C) 2025-2026 contributors.

package com.winter.mypass.feature.about.view

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosan.installer.ui.library.effect.BgEffectBackground
import com.winter.mypass.BuildConfig
import com.winter.mypass.R
import com.winter.mypass.feature.home.view.VaultIcon
import com.winter.mypass.feature.home.view.VaultLayout
import com.winter.mypass.feature.home.view.VaultLink
import com.winter.mypass.feature.home.view.VaultPreviews
import com.winter.mypass.ui.theme.LocalDarkTheme
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 展示流光标识、版本与项目入口。
 * @param onBack 返回设置页
 * @param onLicenses 打开开源代码许可
 */
@Composable
internal fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val dark = LocalDarkTheme.current
    val colors = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val list = rememberLazyListState()
    var headerHeight by remember { mutableIntStateOf(0) }
    var logoHeight by remember { mutableStateOf(300.dp) }
    val progress by remember {
        derivedStateOf {
            if (headerHeight <= 0) 0f
            else if (list.firstVisibleItemIndex > 0) 1f
            else (list.firstVisibleItemScrollOffset.toFloat() / headerHeight).coerceIn(0f, 1f)
        }
    }
    val supported = isRuntimeShaderSupported()
    val backdrop = rememberLayerBackdrop()
    val cardBlend = remember(dark) {
        if (dark) listOf(BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity))
        else listOf(
            BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
        )
    }
    val logoBlend = remember(dark) {
        if (dark) listOf(
            BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
            BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
        ) else listOf(
            BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
            BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
        )
    }
    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                SmallTopAppBar(
                    title = stringResource(R.string.about_title),
                    modifier = Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth(),
                    color = colors.surface.copy(alpha = progress),
                    titleColor = colors.onSurface.copy(alpha = progress),
                    navigationIcon = { VaultIcon(MiuixIcons.Back, R.string.lock_back, onClick = onBack) },
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { insets ->
        BgEffectBackground(
            modifier = Modifier.fillMaxSize(),
            bgModifier = if (supported) Modifier.layerBackdrop(backdrop) else Modifier,
            alpha = { 1f - progress },
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = VaultLayout.PaneWidth).fillMaxWidth()
                    .padding(top = insets.calculateTopPadding() + 92.dp)
                    .onSizeChanged { with(density) { logoHeight = it.height.toDp() } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(88.dp).graphicsLayer {
                        val fade = ((progress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - fade
                        scaleX = 1f - fade * 0.05f
                        scaleY = scaleX
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(if (supported) R.drawable.ic_launcher_monochrome else R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.requiredSize(if (supported) 160.dp else 120.dp).then(
                            if (supported) Modifier.textureBlur(
                                backdrop, RoundedCornerShape(16.dp), blurRadius = 200f,
                                colors = BlurColors(blendColors = logoBlend), contentBlendMode = BlendMode.DstIn,
                            ) else Modifier,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.app_name),
                    Modifier.padding(top = 12.dp, bottom = 5.dp).graphicsLayer {
                        val fade = ((progress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - fade
                        scaleX = 1f - fade * 0.05f
                        scaleY = scaleX
                    }.then(
                        if (supported) Modifier.textureBlur(
                            backdrop, RoundedCornerShape(16.dp), blurRadius = 150f,
                            colors = BlurColors(blendColors = logoBlend), contentBlendMode = BlendMode.DstIn,
                        ) else Modifier,
                    ),
                    fontWeight = FontWeight.Bold, fontSize = 35.sp,
                )
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    Modifier.padding(horizontal = Theme.spacing.large).graphicsLayer {
                        val fade = ((progress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - fade
                        scaleX = 1f - fade * 0.05f
                        scaleY = scaleX
                    },
                    color = colors.onSurfaceVariantSummary, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
            }
            LazyColumn(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = VaultLayout.PaneWidth)
                    .fillMaxSize().consumeWindowInsets(insets),
                state = list,
                contentPadding = insets,
            ) {
                item(key = "header") {
                    Box(Modifier.fillMaxWidth().height(logoHeight + 218.dp).onSizeChanged { headerHeight = it.height })
                }
                item(key = "links") {
                    Column(Modifier.fillParentMaxHeight().padding(bottom = insets.calculateBottomPadding())) {
                        SmallTitle(stringResource(R.string.about_title))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).then(
                                if (supported) Modifier.textureBlur(
                                    backdrop, RoundedCornerShape(16.dp), blurRadius = 60f,
                                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                    colors = BlurColors(blendColors = cardBlend),
                                ) else Modifier,
                            ),
                            insideMargin = PaddingValues(0.dp),
                            colors = CardDefaults.defaultColors(
                                if (supported) Color.Transparent else colors.surfaceContainer, colors.onSurface,
                            ),
                        ) {
                            VaultLink(
                                title = stringResource(R.string.about_github),
                                summary = stringResource(R.string.about_repository_name),
                            ) {
                                try {
                                    uri.openUri(MyPassRepositoryUrl)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
                                } catch (_: IllegalArgumentException) {
                                    Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
                                }
                            }
                            VaultLink(stringResource(R.string.about_licenses), onClick = onLicenses)
                        }
                    }
                }
            }
        }
    }
}

internal const val MyPassRepositoryUrl = "https://github.com/tingdong105/MyPass"

/** 预览关于软件的手机、平板和明暗主题。 */
@VaultPreviews
@Composable
private fun AboutPreview() {
    MyPassTheme { AboutScreen({}, {}) }
}
