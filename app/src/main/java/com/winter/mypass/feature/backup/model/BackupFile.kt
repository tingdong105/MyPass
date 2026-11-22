package com.winter.mypass.feature.backup.model

import com.winter.mypass.feature.home.model.VaultBackup
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 读写 MyPass .mp 容器，支持原始版本及包含回收站的版本。
 * @author Tingdong
 * @date 2026-11-21
 */
@OptIn(ExperimentalSerializationApi::class)
internal object BackupFile {
    const val Limit = 16 * 1024 * 1024
    private const val NonceBytes = 12
    private const val TagBits = 128
    private const val LegacyVersion: Byte = 1
    private const val TrashVersion: Byte = 2
    private val magic = "MyPassMP".toByteArray(Charsets.US_ASCII)
    private val legacyHeader = magic + LegacyVersion
    private val trashHeader = magic + TrashVersion
    private val random = SecureRandom()
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256")
        .digest("com.winter.mypass/portable-mp/2026/v1".toByteArray()), "AES")

    /** 直接把序列化输出送入加密流，避免同时持有整库明文字符串和字节副本。 */
    fun encode(data: VaultBackup): ByteArray {
        val header = if (data.passwords.any { it.deletedAt != null }) trashHeader else legacyHeader
        val nonce = ByteArray(NonceBytes).also(random::nextBytes)
        val file = LimitedBytes(Limit)
        file.write(header)
        file.write(nonce)
        CipherOutputStream(file, cipher(Cipher.ENCRYPT_MODE, nonce, header)).use { Json.encodeToStream(data, it) }
        return file.toByteArray()
    }

    /** 先验证整个文件的认证标签，再解析并检查全部关系。 */
    fun decode(bytes: ByteArray): VaultBackup {
        if (bytes.size > Limit) throw BackupTooLarge()
        val headerSize = magic.size + 1
        val payloadStart = headerSize + NonceBytes
        require(bytes.size >= payloadStart + TagBits / 8 && magic.indices.all { bytes[it] == magic[it] })
        val header = when (bytes[magic.size]) {
            LegacyVersion -> legacyHeader
            TrashVersion -> trashHeader
            else -> throw UnsupportedBackupVersion()
        }
        val nonce = bytes.copyOfRange(headerSize, payloadStart)
        val clear = cipher(Cipher.DECRYPT_MODE, nonce, header).doFinal(bytes, payloadStart, bytes.size - payloadStart)
        return try {
            Json.decodeFromStream<VaultBackup>(clear.inputStream()).also(VaultBackup::validate)
        } finally {
            clear.fill(0)
        }
    }

    /** 支持未知长度的输入，在读取过程中实施大小限制。 */
    fun read(input: InputStream, limit: Int = Limit, progress: (Int) -> Unit = {}): ByteArray {
        val result = LimitedBytes(limit)
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return result.toByteArray()
            result.write(buffer, 0, count)
            progress(result.size())
        }
    }

    /**
     * 使用文件头认证容器版本。
     * @param mode 加密或解密模式
     * @param nonce 文件随机数
     * @param header 对应版本的文件头
     * @return 配置后的密码器
     */
    private fun cipher(mode: Int, nonce: ByteArray, header: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, key, GCMParameterSpec(TagBits, nonce))
        updateAAD(header)
    }

    private class LimitedBytes(private val limit: Int) : ByteArrayOutputStream() {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            checkLength(length)
            super.write(bytes, offset, length)
        }

        override fun write(value: Int) {
            checkLength(1)
            super.write(value)
        }

        private fun checkLength(length: Int) {
            if (length <= limit - count) return
            if (limit == Limit) throw BackupTooLarge()
            throw IOException("Response exceeds limit")
        }
    }
}

internal class UnsupportedBackupVersion : IOException()
internal class BackupTooLarge : IOException()
