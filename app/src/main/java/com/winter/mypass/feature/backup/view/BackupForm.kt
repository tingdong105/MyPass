package com.winter.mypass.feature.backup.view

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.winter.mypass.R
import com.winter.mypass.feature.backup.model.CloudBackupConfig
import com.winter.mypass.feature.backup.model.ConfigField
import com.winter.mypass.feature.backup.model.Provider
import com.winter.mypass.feature.home.view.SettingsSection
import com.winter.mypass.feature.home.view.VaultColumn
import com.winter.mypass.feature.home.view.VaultFrame
import com.winter.mypass.feature.home.view.VaultGroup
import com.winter.mypass.feature.home.view.VaultPreviews
import com.winter.mypass.feature.home.view.VaultRowDivider
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 获取云服务的显示名称。 */
@Composable
internal fun providerName(provider: Provider): String = stringResource(when (provider) {
    Provider.WebDAV -> R.string.backup_webdav
    Provider.AWS -> R.string.backup_aws
    Provider.COS -> R.string.backup_cos
    Provider.OSS -> R.string.backup_oss
    Provider.Kodo -> R.string.backup_kodo
    Provider.LegacyS3 -> R.string.backup_objects
})

/** 在同一表单中选择服务、填写连接信息、测试并保存。 */
@Composable
internal fun BackupForm(
    draft: CloudBackupConfig,
    enabled: Boolean,
    tested: Boolean,
    invalid: ConfigField?,
    change: (CloudBackupConfig) -> Unit,
    select: (Provider) -> Unit,
    test: () -> Unit,
    save: () -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(invalid) {
        if (invalid == ConfigField.Directory || invalid == ConfigField.Retention) advanced = true
    }
    ProviderMenu(draft.provider, enabled, select)
    SettingsSection(R.string.backup_connection) {
        AnimatedContent(
            targetState = draft,
            contentKey = { it.provider },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "Connection fields",
        ) { config ->
            val active = config.provider == draft.provider
            ConnectionFields(config, enabled && active, invalid.takeIf { active }, change)
        }
    }
    VaultGroup {
        BasicComponent(title = stringResource(R.string.backup_advanced), enabled = enabled, onClick = { advanced = !advanced },
            endActions = { Text(stringResource(if (advanced) R.string.backup_collapse else R.string.backup_expand),
                style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary) })
        AnimatedVisibility(advanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                VaultRowDivider()
                Column(Modifier.padding(Theme.spacing.medium), verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                    Field(R.string.backup_directory, draft.directory, enabled && advanced, invalid == ConfigField.Directory) {
                        change(draft.copy(directory = it))
                    }
                    Field(R.string.backup_retain, draft.retain.takeIf { it > 0 }?.toString().orEmpty(),
                        enabled && advanced, invalid == ConfigField.Retention, KeyboardType.Number) { value ->
                        val count = value.toIntOrNull()
                        if (value.isEmpty() || count != null && count >= 0) change(draft.copy(retain = count ?: 0))
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        TextButton(stringResource(R.string.backup_test), onClick = test, modifier = Modifier.weight(1f), enabled = enabled)
        TextButton(stringResource(R.string.vault_save), onClick = save, modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(), enabled = enabled && tested)
    }
}

/** 使用与设置页一致的下拉菜单。 */
@Composable
private fun ProviderMenu(provider: Provider, enabled: Boolean, select: (Provider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    VaultGroup {
        Box {
            BasicComponent(title = stringResource(R.string.backup_provider), enabled = enabled, onClick = {
                focus.clearFocus()
                expanded = true
            }, endActions = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                    val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    Text(providerName(provider), style = MiuixTheme.textStyles.body2, color = color)
                    Icon(MiuixIcons.ChevronForward, null, Modifier.size(16.dp), tint = color)
                }
            })
            val entries = listOf(DropdownEntry(items = listOf(
                DropdownItem(
                    text = providerName(Provider.WebDAV),
                    selected = provider == Provider.WebDAV,
                    onClick = { select(Provider.WebDAV) },
                ),
                DropdownItem(
                    text = stringResource(R.string.backup_objects),
                    children = Provider.available.filter { it != Provider.WebDAV }.map { option ->
                        DropdownItem(text = providerName(option), selected = option == provider, onClick = { select(option) })
                    },
                ),
            )))
            OverlayCascadingListPopup(
                show = expanded && enabled, entries = entries,
                onDismissRequest = { expanded = false },
            )
        }
    }
}

/** 服务地址始终置于连接信息首位。 */
@Composable
private fun ConnectionFields(
    draft: CloudBackupConfig, enabled: Boolean, invalid: ConfigField?, change: (CloudBackupConfig) -> Unit,
) {
    val dav = draft.provider == Provider.WebDAV
    Column(Modifier.padding(Theme.spacing.medium), verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        Field(R.string.backup_endpoint, draft.endpoint, enabled, invalid == ConfigField.Endpoint, KeyboardType.Uri) {
            change(draft.copy(endpoint = it.trim()))
        }
        if (!dav) {
            val regionLabel = if (draft.provider == Provider.COS) R.string.backup_cos_region else R.string.backup_region
            Field(regionLabel, draft.region, enabled, invalid == ConfigField.Region) {
                change(draft.copy(region = it.trim()))
            }
            val bucketLabel = when (draft.provider) {
                Provider.COS -> R.string.backup_cos_bucket
                Provider.Kodo -> R.string.backup_kodo_bucket
                else -> R.string.backup_bucket
            }
            Field(bucketLabel, draft.bucket, enabled, invalid == ConfigField.Bucket) {
                change(draft.copy(bucket = it.trim()))
            }
        }
        Field(if (dav) R.string.backup_user else R.string.backup_access, draft.account,
            enabled, invalid == ConfigField.Account) { change(draft.copy(account = it)) }
        Field(if (dav) R.string.backup_password else R.string.backup_secret, draft.secret,
            enabled, invalid == ConfigField.Secret, KeyboardType.Password) { change(draft.copy(secret = it)) }
    }
}

@Composable
private fun Field(@StringRes label: Int, value: String, enabled: Boolean, invalid: Boolean,
                  type: KeyboardType = KeyboardType.Text, update: (String) -> Unit) {
    val target = remember { BringIntoViewRequester() }
    LaunchedEffect(invalid) { if (invalid) target.bringIntoView() }
    TextField(value, update, Modifier.fillMaxWidth().bringIntoViewRequester(target),
        label = stringResource(label), singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        visualTransformation = if (type == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None)
}

@VaultPreviews
@Composable
private fun ConfigurationPreview() {
    MyPassTheme {
        VaultFrame(stringResource(R.string.backup_config)) { body ->
            VaultColumn(body) { BackupForm(CloudBackupConfig(provider = Provider.OSS), true, false, null, {}, {}, {}, {}) }
        }
    }
}
