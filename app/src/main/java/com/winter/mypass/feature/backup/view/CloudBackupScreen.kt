package com.winter.mypass.feature.backup.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winter.mypass.R
import com.winter.mypass.feature.backup.model.BackupDestination
import com.winter.mypass.feature.backup.model.BackupNames
import com.winter.mypass.feature.backup.model.CloudBackupConfig
import com.winter.mypass.feature.backup.model.Provider
import com.winter.mypass.feature.backup.viewmodel.BackupPage
import com.winter.mypass.feature.backup.viewmodel.BackupState
import com.winter.mypass.feature.backup.viewmodel.BackupTask
import com.winter.mypass.feature.backup.viewmodel.CloudBackupViewModel
import com.winter.mypass.feature.home.view.SettingsSection
import com.winter.mypass.feature.home.view.VaultColumn
import com.winter.mypass.feature.home.view.VaultConfirm
import com.winter.mypass.feature.home.view.VaultFrame
import com.winter.mypass.feature.home.view.VaultGroup
import com.winter.mypass.feature.home.view.VaultIcon
import com.winter.mypass.feature.home.view.VaultLayout
import com.winter.mypass.feature.home.view.VaultLink
import com.winter.mypass.feature.home.view.VaultPreviews
import com.winter.mypass.feature.home.view.VaultRowDivider
import com.winter.mypass.ui.theme.MyPassTheme
import com.winter.mypass.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.DateFormat
import java.util.Date

/** 接入系统权限、文档选择器和当前解锁会话。 */
@Composable
internal fun CloudBackupScreen(model: CloudBackupViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val documents = LocalBackupDocuments.current
    val context = LocalContext.current
    val permission = "android.permission.ACCESS_LOCAL_NETWORK"
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    val storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.export() else model.storagePermissionDenied()
    }
    val export = {
        if (Build.VERSION.SDK_INT == 28 && state.exportDirectory == null &&
            context.checkSelfPermission(storagePermission) != PackageManager.PERMISSION_GRANTED) {
            storageLauncher.launch(storagePermission)
        } else model.export()
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        val message = when {
            notice.host != null -> context.getString(notice.text, notice.host)
            notice.field != null -> context.getString(notice.text, context.getString(notice.field))
            notice.text == R.string.backup_http_error -> context.getString(notice.text, notice.status)
            else -> context.getString(notice.text)
        }
        Toast.makeText(context, message, if (notice.error) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        model.consumeNotice(notice)
    }
    fun lanGranted() = Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    var permissionShown by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), model::permissionResult)
    LaunchedEffect(state.requestPermission) {
        if (!state.requestPermission) permissionShown = false
        else if (!permissionShown) {
            permissionShown = true
            launcher.launch(permission)
        }
    }
    LaunchedEffect(documents.selected, state.idle) {
        val uri = documents.selected
        if (uri != null && state.idle) {
            if (documents.directory) model.setExportDirectory(uri) else model.document(uri)
            documents.consume()
        }
    }

    val focus = LocalFocusManager.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val back = {
        focus.clearFocus()
        if (!model.back()) onBack()
    }
    BackHandler(onBack = back)
    val title = when (state.page) {
        BackupPage.Overview -> R.string.backup_title
        BackupPage.Configuration -> R.string.backup_config
        BackupPage.History -> R.string.backup_history
    }
    Box(Modifier.fillMaxSize()) {
        VaultFrame(
            stringResource(title),
            navigationIcon = { VaultIcon(MiuixIcons.Back, R.string.lock_back, state.idle, VaultLayout.HeaderIconSize, back) },
            actions = {
                when (state.page) {
                    BackupPage.Overview -> if (state.destination != null) {
                        VaultIcon(MiuixIcons.Settings, R.string.backup_config, state.idle, VaultLayout.HeaderIconSize, model::editDestination)
                    }
                    BackupPage.History -> VaultIcon(MiuixIcons.Refresh, R.string.backup_refresh, state.idle) { model.history(lanGranted()) }
                    BackupPage.Configuration -> VaultIcon(MiuixIcons.Delete, R.string.backup_clear_config,
                        state.idle, VaultLayout.HeaderIconSize) { confirmingClear = true }
                }
            },
        ) { body ->
            AnimatedContent(
                targetState = state,
                contentKey = { it.page },
                modifier = body,
                transitionSpec = {
                    val offset = if (targetState.page == BackupPage.Overview) -direction else direction
                    (slideInHorizontally(tween(240)) { offset * it / 8 } + fadeIn(tween(190))) togetherWith
                        (slideOutHorizontally(tween(220)) { -offset * it / 8 } + fadeOut(tween(140)))
                },
                label = "Backup page",
            ) { screen ->
                val active = screen.page == state.page
                when (screen.page) {
                    BackupPage.History -> BackupHistory(screen, enabled = active && screen.idle) { model.download(it, lanGranted()) }
                    BackupPage.Overview -> VaultColumn(Modifier.fillMaxSize(), horizontalPadding = Theme.spacing.medium) {
                        BackupOverview(screen, enabled = active && screen.idle, model::editDestination,
                            { model.upload(lanGranted()) }, { model.history(lanGranted()) }, export, documents.open,
                            changeLocation = documents.chooseDirectory, resetLocation = { model.setExportDirectory(null) })
                    }
                    BackupPage.Configuration -> VaultColumn(Modifier.fillMaxSize(), horizontalPadding = Theme.spacing.medium) {
                        screen.draft?.let { draft ->
                            BackupForm(draft, active && screen.idle, screen.tested, screen.fieldError,
                                model::change, model::select, test = {
                                    focus.clearFocus()
                                    model.testConnection(lanGranted())
                                }, save = {
                                    focus.clearFocus()
                                    model.save()
                                })
                        }
                    }
                }
            }
            if (state.page == BackupPage.Configuration) {
                VaultConfirm(
                    confirmingClear, stringResource(R.string.backup_clear_config), stringResource(R.string.backup_clear_summary),
                    R.string.backup_clear, { confirmingClear = false }, {
                        confirmingClear = false
                        model.clearDestination()
                    },
                )
            }
            VaultConfirm(
                state.confirmDiscard, stringResource(R.string.vault_discard_title), stringResource(R.string.vault_discard_message),
                R.string.vault_discard, model::dismissDiscard, model::discardDraft,
            )
            if (state.confirmHttp) VaultConfirm(true, stringResource(R.string.backup_http_title),
                stringResource(R.string.backup_http_warning), R.string.backup_connect, model::dismissHttp,
                { model.testConnection(lanGranted(), allowHttp = true) })
            state.preview?.let { preview ->
                VaultConfirm(true, stringResource(R.string.backup_restore_title),
                    stringResource(R.string.backup_summary, formatTime(preview.time), preview.books, preview.passwords),
                    R.string.vault_confirm_add, model::dismissPreview, model::restore, textAlign = TextAlign.Left)
            }
        }
        BackupLoading(state, Modifier.matchParentSize(), model::cancelRead)
    }
}

/** 云端主操作与本地文件操作入口。 */
@Composable
private fun BackupOverview(
    state: BackupState, enabled: Boolean, configure: () -> Unit, upload: () -> Unit,
    history: () -> Unit, export: () -> Unit, open: () -> Unit, changeLocation: () -> Unit, resetLocation: () -> Unit,
) {
    val config = state.destination?.config
    val colors = MiuixTheme.colorScheme
    VaultGroup {
        Column(Modifier.padding(Theme.spacing.medium), verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium)) {
                Icon(MiuixIcons.CloudFill, null, Modifier.size(VaultLayout.HeaderIconSize), tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text(if (config == null) stringResource(R.string.backup_cloud) else providerName(config.provider),
                        style = MiuixTheme.textStyles.title4)
                    if (config != null) {
                        val path = if (config.provider == Provider.WebDAV) config.address().host
                            else config.bucket.ifBlank { config.root().host.substringBefore('.') }
                        Text(listOf(path, config.directory.trim('/')).filter(String::isNotEmpty).joinToString("/"),
                            style = MiuixTheme.textStyles.body2, color = colors.onSurfaceVariantSummary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            TextButton(stringResource(if (config == null) R.string.backup_setup else R.string.backup_now),
                onClick = if (config == null) configure else upload, modifier = Modifier.fillMaxWidth(),
                enabled = enabled, colors = ButtonDefaults.textButtonColorsPrimary())
        }
        AnimatedVisibility(config != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                VaultRowDivider()
                VaultLink(stringResource(R.string.backup_history), icon = MiuixIcons.Folder,
                    enabled = enabled && config != null, onClick = history)
            }
        }
    }
    SettingsSection(R.string.backup_local) {
        VaultLink(stringResource(R.string.backup_export), icon = MiuixIcons.Download, enabled = enabled, onClick = export)
        VaultRowDivider()
        VaultLink(stringResource(R.string.backup_import), icon = MiuixIcons.Folder, enabled = enabled, onClick = open)
        VaultRowDivider()
        val directoryName = state.exportDirectory?.trimEnd('/')?.substringAfterLast('/')
        VaultLink(stringResource(R.string.backup_export_location),
            value = directoryName ?: stringResource(R.string.backup_export_location_default),
            icon = MiuixIcons.Settings, enabled = enabled, onClick = changeLocation)
        if (state.exportDirectory != null) {
            VaultRowDivider()
            BasicComponent(title = stringResource(R.string.backup_default_directory), enabled = enabled, onClick = resetLocation)
        }
    }
}

/** 未知进度直接显示在页面上，已知进度在弹窗内按字节展示。 */
@Composable
private fun BackupLoading(state: BackupState, modifier: Modifier, cancel: () -> Unit) {
    val task = state.task ?: return
    val percent = state.percent
    val cancellable = task == BackupTask.Read || task == BackupTask.Download
    if (percent != null) {
        Dialog(onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            VaultGroup {
                Column(
                    Modifier.fillMaxWidth().padding(Theme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(task.label), Modifier.weight(1f), style = MiuixTheme.textStyles.body2)
                        Text(stringResource(R.string.backup_progress_percent, percent),
                            style = MiuixTheme.textStyles.body2.copy(fontFeatureSettings = "tnum"))
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth(), percent / 100f)
                    if (cancellable) TextButton(stringResource(R.string.vault_cancel), modifier = Modifier.align(Alignment.CenterHorizontally), onClick = cancel)
                }
            }
        }
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.60f)).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            })
            Column(
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = Color.White, backgroundColor = Color.White.copy(alpha = 0.25f),
                ))
                Text(stringResource(task.label), style = MiuixTheme.textStyles.body2, color = Color.White)
                if (cancellable) {
                    TextButton(stringResource(R.string.vault_cancel), onClick = cancel,
                        colors = ButtonDefaults.textButtonColors(color = Color.Transparent, textColor = Color.White))
                }
            }
        }
    }
}

/** 按目录展示可选还原点，始终按需组合可见行。 */
@Composable
private fun BackupHistory(state: BackupState, enabled: Boolean, restore: (String) -> Unit) {
    val files = state.history
    if (files.isNullOrEmpty()) return
    LazyColumn(modifier = Modifier.widthIn(max = VaultLayout.PaneWidth).fillMaxWidth(),
        contentPadding = PaddingValues(Theme.spacing.medium), verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        items(files, key = { it }) { name ->
            val time = remember(name) { BackupNames.time(name) }
            val owner = remember(name) { BackupNames.installation(name) }
            val device = when (owner) {
                null -> null
                state.destination?.installation -> stringResource(R.string.backup_this_device)
                else -> stringResource(R.string.backup_other_device, owner.take(8))
            }
            VaultGroup(Modifier.animateItem()) {
                VaultLink(time?.let(::formatTime) ?: name, summary = device,
                    enabled = enabled, icon = MiuixIcons.Folder) { restore(name) }
            }
        }
    }
}

private fun formatTime(value: Long): String = DateFormat.getDateTimeInstance().format(Date(value))

@VaultPreviews
@Composable
private fun BackupOverviewPreview() {
    MyPassTheme {
        VaultFrame(stringResource(R.string.backup_title)) { body ->
            VaultColumn(body) {
                val demo = BackupDestination("demo", CloudBackupConfig(provider = Provider.COS, bucket = "mypass-123456"))
                BackupOverview(BackupState(destination = demo), true, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
}

@VaultPreviews
@Composable
private fun BackupHistoryPreview() {
    MyPassTheme {
        VaultFrame(stringResource(R.string.backup_history)) { body ->
            Box(body, contentAlignment = Alignment.TopCenter) { BackupHistory(BackupState(history = listOf("MyPass.mp")), true) {} }
        }
    }
}
