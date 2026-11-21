package com.winter.mypass.feature.backup.model

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class BackupDestination(val installation: String, val config: CloudBackupConfig)

@Serializable
private data class StoredBackupDestination(val installation: String, val config: CloudBackupConfig?)

/**
 * 安装标识和云凭据由系统密钥加密后原子保存。
 * @author Tingdong
 * @date 2026-11-21
 */
internal class BackupConfigStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "backup-destination"))
    private val alias = "mypass.cloud.destination.v1"

    /** 读取上次验证成功的目的地。 */
    fun load(): BackupDestination? {
        val stored = read() ?: return null
        return stored.config?.let { BackupDestination(stored.installation, it) }
    }

    private fun read(): StoredBackupDestination? {
        val bytes = try { file.openRead().use { BackupFile.read(it, 128 * 1024) } }
        catch (e: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw e
            return null
        }
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes, 0, 12))
        val clear = cipher.doFinal(bytes, 12, bytes.size - 12)
        return try { Json.decodeFromString<StoredBackupDestination>(clear.decodeToString()) }
        finally { clear.fill(0) }
    }

    /** 替换单个目的地，同时保留当前安装标识。 */
    fun save(config: CloudBackupConfig): BackupDestination {
        val installation = read()?.installation ?: UUID.randomUUID().toString()
        write(StoredBackupDestination(installation, config))
        return BackupDestination(installation, config)
    }

    /** 清除连接配置，保留本机安装标识用于版本保留策略。 */
    fun clear() {
        val stored = read() ?: return
        if (stored.config != null) write(stored.copy(config = null))
    }

    private fun write(record: StoredBackupDestination) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val clear = Json.encodeToString(record).toByteArray()
        val encrypted = try { cipher.doFinal(clear) } finally { clear.fill(0) }
        require(encrypted.size + cipher.iv.size <= 128 * 1024)
        val output = file.startWrite()
        try {
            output.write(cipher.iv)
            output.write(encrypted)
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create)
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
