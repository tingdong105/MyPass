package com.winter.mypass.feature.backup.view

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** 保存文档和目录选择结果，等应用解锁后再消费。 */
internal class BackupDocuments(
    val selected: Uri?,
    val directory: Boolean,
    val consume: () -> Unit,
    val chooseDirectory: () -> Unit,
    val open: () -> Unit,
)

internal val LocalBackupDocuments = staticCompositionLocalOf<BackupDocuments> { error("Missing document picker") }

@Composable
internal fun BackupDocumentHost(content: @Composable () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Uri?>(null) }
    var directory by rememberSaveable { mutableStateOf(false) }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        directory = true
        selected = uri
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        directory = false
        selected = uri
    }
    val documents = BackupDocuments(
        selected, directory, { selected = null }, { tree.launch(null) }, { open.launch(arrayOf("*/*")) },
    )
    CompositionLocalProvider(LocalBackupDocuments provides documents, content = content)
}
