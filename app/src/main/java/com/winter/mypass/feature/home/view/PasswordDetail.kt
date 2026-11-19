package com.winter.mypass.feature.home.view

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.home.model.PasswordDraft
import com.winter.mypass.feature.home.model.VaultProblem
import com.winter.mypass.feature.home.model.VaultSnapshot
import com.winter.mypass.feature.home.model.browserWebsite
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 显示单条记录，集中放置查看和复制密码的操作。
 * @param entry 当前记录
 * @param snapshot 当前可用分类
 * @param busy 是否正在执行操作
 * @param problem 操作错误
 * @param active 页面是否拥有返回事件
 * @param onBack 回到列表
 * @param onEdit 进入编辑
 * @param onDelete 删除当前记录
 * @param onCopy 复制内容及其敏感标记
 * @param overlays 显示在详情上的浮层
 */
@Composable
internal fun PasswordDetail(
    entry: PasswordDraft,
    snapshot: VaultSnapshot,
    busy: Boolean,
    problem: VaultProblem?,
    active: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String, Boolean) -> Unit,
    overlays: @Composable () -> Unit = {},
) {
    var revealed by remember(entry.id, active) { mutableStateOf(false) }
    var deleting by remember(entry.id, active) { mutableStateOf(false) }
    val enabled = active && !busy
    val colors = MiuixTheme.colorScheme
    BackHandler(enabled = active, onBack = onBack)
    VaultFrame(
        title = stringResource(R.string.vault_record),
        navigationIcon = { VaultIcon(MiuixIcons.Back, R.string.lock_back, enabled, VaultLayout.HeaderIconSize, onBack) },
        actions = { VaultIcon(MiuixIcons.Edit, R.string.vault_edit, enabled, VaultLayout.HeaderIconSize, onEdit) },
    ) { modifier ->
        val detailModifier = if (active) modifier else modifier.blockVaultBackgroundFocus()
        VaultColumn(detailModifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
            ) {
                VaultGlyph(entry.platform, large = true)
                Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                    Text(entry.platform, style = MiuixTheme.textStyles.title1)
                    if (entry.account.isNotBlank()) Text(entry.account, color = colors.onSurfaceVariantSummary)
                }
            }

            VaultErrorText(problem)
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(Theme.spacing.medium),
                colors = CardDefaults.defaultColors(colors.primary.copy(alpha = 0.10f), colors.onSurface),
            ) {
                Text(
                    stringResource(R.string.vault_password),
                    color = colors.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (revealed) entry.password else "••••••••",
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        style = MiuixTheme.textStyles.title3,
                    )
                    PasswordEye(revealed, enabled) { revealed = !revealed }
                    VaultIcon(MiuixIcons.Copy, R.string.vault_copy_password, enabled = enabled) {
                        onCopy(entry.password, true)
                    }
                }
            }

            val hasWebsite = entry.website.isNotBlank()
            val hasEmail = entry.email.isNotBlank()
            val hasNotes = entry.notes.isNotBlank()
            if (hasWebsite || hasEmail || hasNotes) VaultGroup {
                if (hasWebsite) WebsiteRow(entry.website, enabled) { onCopy(entry.website, false) }
                if (hasEmail) {
                    if (hasWebsite) VaultRowDivider()
                    BasicComponent(title = stringResource(R.string.vault_email), summary = entry.email)
                }
                if (hasNotes) {
                    if (hasWebsite || hasEmail) VaultRowDivider()
                    BasicComponent(
                        title = stringResource(R.string.vault_notes),
                        summary = entry.notes,
                    )
                }
            }

            val bookName = snapshot.books.firstOrNull { it.id == entry.bookId }?.name
            val typeName = snapshot.typeName(entry.typeId)
            val tags = snapshot.tags.filter { it.id in entry.tagIds }.joinToString("、") { it.name }
            if (bookName != null || typeName != null || tags.isNotEmpty()) VaultGroup {
                if (bookName != null) BasicComponent(title = stringResource(R.string.vault_book), summary = bookName)
                if (typeName != null) {
                    if (bookName != null) VaultRowDivider()
                    BasicComponent(title = stringResource(R.string.vault_type), summary = typeName)
                }
                if (tags.isNotEmpty()) {
                    if (bookName != null || typeName != null) VaultRowDivider()
                    BasicComponent(title = stringResource(R.string.vault_tags), summary = tags)
                }
            }
            TextButton(
                stringResource(R.string.vault_delete_record),
                onClick = { deleting = true }, enabled = enabled, modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(color = colors.errorContainer, textColor = colors.error),
            )
        }
        VaultConfirm(
            active && deleting, stringResource(R.string.vault_delete_record),
            stringResource(R.string.vault_confirm_record, entry.platform), R.string.vault_delete,
            onDismiss = { deleting = false }, onConfirm = {
                deleting = false
                onDelete()
            },
        )
        overlays()
    }
}

/**
 * 网址行的主操作是打开浏览器，次操作是复制。
 * @param address 保存的网址
 * @param enabled 是否接受点击
 * @param onCopy 复制网址
 */
@Composable
private fun WebsiteRow(address: String, enabled: Boolean, onCopy: () -> Unit) {
    val context = LocalContext.current
    val website = remember(address) { browserWebsite(address) }
    BasicComponent(
        title = stringResource(R.string.vault_website), summary = address,
        enabled = enabled,
        onClickLabel = stringResource(R.string.vault_open_url),
        onClick = website?.let { url ->
            {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.vault_no_browser, Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, R.string.vault_no_browser, Toast.LENGTH_SHORT).show()
                }
            }
        },
        startAction = { Icon(MiuixIcons.Link, null, Modifier.size(22.dp)) },
        endActions = { VaultIcon(MiuixIcons.Copy, R.string.vault_copy_url, enabled, onClick = onCopy) },
    )
}

/**
 * 密码可见性按钮使用相同的点击和无障碍语义。
 * @param revealed 密码是否可见
 * @param enabled 是否可切换
 * @param onToggle 切换可见性
 */
@Composable
internal fun PasswordEye(revealed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    VaultIcon(
        if (revealed) MiuixIcons.Hide else MiuixIcons.Show,
        if (revealed) R.string.vault_hide else R.string.vault_show,
        enabled, onClick = onToggle,
    )
}

/** 预览密码详情的分组和复制操作。 */
@VaultPreviews
@Composable
internal fun PasswordDetailPreview() {
    val entry = previewEntry()
    MyPassTheme {
        PasswordDetail(entry, previewSnapshot(), false, null, true, {}, {}, {}, { _, _ -> })
    }
}
