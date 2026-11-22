package com.winter.mypass.feature.about.view

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winter.mypass.R
import com.winter.mypass.feature.about.model.OpenSourceLibrary
import com.winter.mypass.feature.about.model.OpenSourceLicense
import com.winter.mypass.feature.about.viewmodel.OpenSourceLicensesViewModel
import com.winter.mypass.feature.about.viewmodel.OpenSourceState
import com.winter.mypass.feature.home.view.VaultColumn
import com.winter.mypass.feature.home.view.VaultFrame
import com.winter.mypass.feature.home.view.VaultGroup
import com.winter.mypass.feature.home.view.VaultIcon
import com.winter.mypass.feature.home.view.VaultLayout
import com.winter.mypass.feature.home.view.VaultLink
import com.winter.mypass.feature.home.view.VaultPreviews
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 为开源清单提供页面级状态。
 * @param libraryId 详情依赖标识，空值表示清单
 * @param onBack 返回上一页
 * @param onLibrary 打开依赖详情
 */
@Composable
internal fun OpenSourceLicensesRoute(libraryId: String?, onBack: () -> Unit, onLibrary: (String) -> Unit) {
    val resources = LocalContext.current.applicationContext.resources
    val model = viewModel { OpenSourceLicensesViewModel(resources) }
    val state by model.state.collectAsStateWithLifecycle()
    OpenSourceLicensesScreen(state, libraryId, onBack, model::load, onLibrary)
}

/**
 * 展示完整依赖清单与可选择复制的许可全文。
 * @param state 加载状态
 * @param libraryId 当前详情的依赖标识
 * @param onBack 返回上一页
 * @param onRetry 加载失败后重试
 * @param onLibrary 打开单项详情
 */
@Composable
private fun OpenSourceLicensesScreen(
    state: OpenSourceState,
    libraryId: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLibrary: (String) -> Unit,
) {
    val libraries = (state as? OpenSourceState.Ready)?.libraries
    val selected = libraries?.firstOrNull { it.id == libraryId }
    val context = LocalContext.current
    val handler = LocalUriHandler.current
    val openUrl: (String) -> Unit = { url ->
        try {
            handler.openUri(url)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
        } catch (_: IllegalArgumentException) {
            Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
    val website = selected?.website?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    VaultFrame(
        title = selected?.name ?: stringResource(R.string.about_licenses),
        navigationIcon = { VaultIcon(MiuixIcons.Back, R.string.lock_back, onClick = onBack) },
        actions = {
            if (website != null) {
                VaultIcon(MiuixIcons.Link, R.string.about_license_website) { openUrl(website) }
            }
        },
    ) { body ->
        when {
            state == OpenSourceState.Loading -> LicenseStatus(body, R.string.about_license_loading, loading = true)
            state == OpenSourceState.Failed -> LicenseStatus(body, R.string.about_license_error, onRetry = onRetry)
            libraryId != null && selected == null -> LicenseStatus(body, R.string.about_license_missing)
            selected != null -> LicenseDetails(selected, body, openUrl)
            libraries.isNullOrEmpty() -> LicenseStatus(body, R.string.about_license_empty)
            else -> Box(body, contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Theme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
                ) {
                    items(libraries, key = { it.id }) { library ->
                        VaultGroup {
                            VaultLink(
                                title = library.name,
                                summary = library.licenses.joinToString { it.name }
                                    .ifBlank { stringResource(R.string.about_license_unknown) },
                                value = library.version.takeIf(String::isNotBlank),
                                onClick = { onLibrary(library.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 显示加载、失败或空清单。
 * @param modifier 页面内容范围
 * @param message 状态文案
 * @param loading 是否显示进度圈
 * @param onRetry 可选重试动作
 */
@Composable
private fun LicenseStatus(modifier: Modifier, @androidx.annotation.StringRes message: Int, loading: Boolean = false, onRetry: (() -> Unit)? = null) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium)) {
            if (loading) CircularProgressIndicator()
            Text(stringResource(message))
            if (onRetry != null) TextButton(stringResource(R.string.lock_retry), onClick = onRetry)
        }
    }
}

/**
 * 展示依赖作者、来源及本地许可全文。
 * @param library 当前依赖
 * @param modifier 已处理安全边距的内容区域
 * @param onOpenUrl 使用浏览器打开许可链接
 */
@Composable
private fun LicenseDetails(library: OpenSourceLibrary, modifier: Modifier, onOpenUrl: (String) -> Unit) {
    VaultColumn(modifier) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                Text(library.name, style = MiuixTheme.textStyles.title3)
                if (library.version.isNotBlank()) Text(library.version, style = MiuixTheme.textStyles.body2)
                if (library.author.isNotBlank()) Text(library.author, style = MiuixTheme.textStyles.body2)
                Text(library.id, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            }
        }
        if (library.licenses.isEmpty()) Text(stringResource(R.string.about_license_unknown))
        library.licenses.forEach { license ->
            val content = remember(license.content) {
                if (license.content.contains(Regex("<(?:br|p|div|a|span|h[1-6])\\b", RegexOption.IGNORE_CASE))) {
                    AnnotatedString.fromHtml(license.content).text
                } else license.content
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium)) {
                        Text(license.name, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Medium)
                        Text(
                            content.ifBlank { stringResource(R.string.about_license_text_unavailable) },
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
                if (license.url.startsWith("https://") || license.url.startsWith("http://")) {
                    TextButton(stringResource(R.string.about_license_original), onClick = { onOpenUrl(license.url) })
                }
            }
        }
    }
}

/** 预览开源清单的手机、平板和明暗主题。 */
@VaultPreviews
@Composable
private fun OpenSourceLicensesPreview() {
    MyPassTheme {
        OpenSourceLicensesScreen(
            OpenSourceState.Ready(listOf(
                OpenSourceLibrary(
                    "top.yukonga.miuix.kmp:miuix-ui-android", "Miuix", "0.9.4", "compose-miuix-ui contributors",
                    "https://github.com/compose-miuix-ui/miuix", listOf(OpenSourceLicense("Apache-2.0", "", "")),
                ),
            )), null, {}, {}, {},
        )
    }
}

/** 预览许可详情的手机、平板和明暗主题。 */
@VaultPreviews
@Composable
private fun OpenSourceLicenseDetailsPreview() {
    MyPassTheme {
        OpenSourceLicensesScreen(
            OpenSourceState.Ready(listOf(
                OpenSourceLibrary(
                    "InstallerX-Revived", "InstallerX Revived", "26.09", "InstallerX Revived contributors",
                    "https://github.com/wxxsfxyzm/InstallerX-Revived",
                    listOf(OpenSourceLicense("GNU General Public License v3.0", "", "GNU GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007")),
                ),
            )), "InstallerX-Revived", {}, {}, {},
        )
    }
}
