package com.winter.mypass.feature.applock.model

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.winter.mypass.feature.home.model.VaultRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 保存应用密码封装的密码库密钥及失败次数，调用方负责 IO 调度。
 * @author Tingdong
 * @date 2026-11-18
 */
internal class CredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val file by lazy { AtomicFile(File(appContext.noBackupFilesDir, "app-lock")) }
    private val mutex = Mutex()
    private val random by lazy { SecureRandom() }

    /**
     * 存储操作的结果，解锁密钥由接收方负责清除。
     * @author Tingdong
     * @date 2026-11-18
     */
    sealed interface Result {
        data object Missing : Result
        sealed interface Verification : Result
        data class Locked(
            val type: CredentialType,
            val retryAt: Long,
            val incorrect: Boolean = false,
        ) : Verification, ChangeVerification
        class Unlocked(val vaultKey: ByteArray) : Verification
    }

    /**
     * 验证当前凭据后返回的修改授权或限流状态。
     * @author Tingdong
     * @date 2026-11-20
     */
    sealed interface ChangeVerification {
        class Authorized(val authorization: ChangeAuthorization) : ChangeVerification
    }

    /**
     * 新凭据的保存结果。
     * @author Tingdong
     * @date 2026-11-21
     */
    enum class ChangeResult { Saved, SameAsCurrent, StaleAuthorization }

    /**
     * 保管一次性修改所需的密码库密钥，并绑定原凭据记录。
     * @author Tingdong
     * @date 2026-11-20
     */
    class ChangeAuthorization internal constructor(
        internal val type: CredentialType,
        internal val salt: ByteArray,
        private var vaultKey: ByteArray?,
    ) {
        /**
         * 读取仍有效的授权密钥。
         * @return 授权持有的密码库密钥
         * @throws IllegalStateException 授权已使用或清除
         */
        internal fun requireKey(): ByteArray = checkNotNull(vaultKey)

        /** 清除未使用的临时密钥。 */
        fun clear() {
            vaultKey?.fill(0)
            vaultKey = null
        }
    }

    /**
     * 凭据记录损坏、格式不受支持或在解锁时消失。
     * @author Tingdong
     * @date 2026-11-18
     */
    class InvalidRecord(cause: Throwable? = null) : IOException("Invalid credential record", cause)

    /**
     * 本机已有密码库，但缺少对应的应用锁凭据记录。
     * @author Tingdong
     * @date 2026-11-19
     */
    class MissingRecord : IOException("Missing app lock record for existing vault")

    /**
     * 本机缺少已配置凭据所需的 Keystore 密钥。
     * @author Tingdong
     * @date 2026-11-18
     */
    class MissingKey : KeyStoreException("Missing app lock key")

    /**
     * 读取本机凭据类型和等待期限。
     * @return 按记录状态返回待设置或已上锁
     * @throws MissingRecord 已有密码库缺少对应的应用锁记录
     * @throws IOException 记录无法读写
     * @throws java.security.GeneralSecurityException 本机记录无法解密
     */
    suspend fun load(): Result = mutex.withLock {
        val record = readRecord() ?: return@withLock Result.Missing
        val current = reconcileCooldown(record)
        Result.Locked(current.type, current.retryAt)
    }

    /**
     * 确认记录和密码库均未创建后保存首个凭据。
     * @param type 密码类型
     * @param input 已经两次确认的密码
     * @return 已解锁的会话密钥，或并发设置后所需的凭据
     * @throws IllegalArgumentException 密码格式无效
     * @throws MissingRecord 已有密码库缺少对应的应用锁记录
     * @throws IOException 记录无法读写
     * @throws java.security.GeneralSecurityException 无法加密本机记录
     */
    suspend fun create(type: CredentialType, input: CharArray): Result = mutex.withLock {
        readRecord()?.let {
            val current = reconcileCooldown(it)
            return@withLock Result.Locked(current.type, current.retryAt)
        }
        require(type.accepts(input))
        val salt = ByteArray(SaltSize).also(random::nextBytes)
        val vaultKey = ByteArray(KeySize).also(random::nextBytes)
        try {
            val wrapped = encrypt(deriveKey(input, salt), credentialAad(type), vaultKey)
            val record = Record(type, salt, wrapped, failed = 0, retryAt = 0, boot = UnknownBoot)
            writeRecord(record, deviceKey(create = true))
            Result.Unlocked(vaultKey)
        } catch (failure: Throwable) {
            vaultKey.fill(0)
            throw failure
        }
    }

    /**
     * 通过解封密码库密钥验证密码并记录失败次数。
     * @param type 提交时显示的密码类型
     * @param input 待验证的密码
     * @return 已解锁的会话密钥或继续上锁的状态
     * @throws MissingRecord 已有密码库缺少对应的应用锁记录
     * @throws IOException 记录缺失或无法读写
     * @throws java.security.GeneralSecurityException 本机记录无法解密
     */
    suspend fun verify(type: CredentialType, input: CharArray): Result.Verification = mutex.withLock {
        val key = deviceKey()
        verifyRecord(key, readCurrentRecord(key), type, input)
    }

    /**
     * 使用与解锁共用的尝试次数验证当前密码。
     * @param type 提交时显示的密码类型
     * @param input 当前密码
     * @return 一次性修改授权或当前失败状态
     * @throws IOException 记录缺失或无法读写
     * @throws java.security.GeneralSecurityException 本机记录无法解密
     */
    suspend fun authorizeChange(type: CredentialType, input: CharArray): ChangeVerification = mutex.withLock {
        val key = deviceKey()
        val record = readCurrentRecord(key)
        when (val result = verifyRecord(key, record, type, input)) {
            is Result.Locked -> result
            is Result.Unlocked -> ChangeVerification.Authorized(
                ChangeAuthorization(record.type, record.salt, result.vaultKey),
            )
        }
    }

    /**
     * 使用新凭据重新封装同一个密码库密钥，并原子更新记录。
     * @param authorization 验证当前密码后取得的一次性授权
     * @param type 新密码类型
     * @param input 用户已确认的新密码
     * @return 保存结果，新旧密码相同时保留授权供重新输入
     * @throws IllegalArgumentException 新密码格式无效
     * @throws IOException 记录缺失或无法读写
     * @throws java.security.GeneralSecurityException 本机记录无法解密或加密
     */
    suspend fun replace(authorization: ChangeAuthorization, type: CredentialType, input: CharArray): ChangeResult =
        mutex.withLock {
            val vaultKey = authorization.requireKey()
            var keepAuthorization = false
            try {
                require(type.accepts(input))
                val key = deviceKey()
                val record = readRecord(key) ?: throw InvalidRecord()
                if (record.type != authorization.type || !record.salt.contentEquals(authorization.salt)) {
                    return@withLock ChangeResult.StaleAuthorization
                }
                if (type == record.type && matchesCredential(record, input)) {
                    keepAuthorization = true
                    return@withLock ChangeResult.SameAsCurrent
                }
                val salt = ByteArray(SaltSize).also(random::nextBytes)
                val wrapped = encrypt(deriveKey(input, salt), credentialAad(type), vaultKey)
                writeRecord(Record(type, salt, wrapped, failed = 0, retryAt = 0, boot = UnknownBoot), key)
                ChangeResult.Saved
            } finally {
                if (!keepAuthorization) authorization.clear()
            }
        }

    /**
     * 判断输入是否与已认证记录中的凭据相同。
     * @param record 当前凭据记录
     * @param input 待比较的新密码
     * @return 输入是否能解封当前记录
     */
    private fun matchesCredential(record: Record, input: CharArray): Boolean = try {
        unwrapVaultKey(record, input).fill(0)
        true
    } catch (_: AEADBadTagException) {
        false
    }

    /**
     * 使用输入的密码解封记录中的密码库密钥。
     * @param record 当前凭据记录
     * @param input 用于解封的密码
     * @return 由调用方负责清除的密码库密钥
     * @throws AEADBadTagException 输入无法通过认证
     */
    private fun unwrapVaultKey(record: Record, input: CharArray): ByteArray =
        decrypt(deriveKey(input, record.salt), credentialAad(record.type), record.wrapped)

    /**
     * 读取现有凭据并恢复当前引导周期内的等待期限。
     * @param key 本次操作取得的 Keystore 密钥
     * @return 当前凭据记录
     * @throws IOException 记录缺失或无法读写
     */
    private fun readCurrentRecord(key: SecretKey): Record =
        reconcileCooldown(readRecord(key) ?: throw InvalidRecord(), key)

    /**
     * 在持有存储锁时验证记录，并持久化本次尝试。
     * @param key 本次操作取得的 Keystore 密钥
     * @param record 已恢复等待期限的凭据记录
     * @param type 提交时显示的密码类型
     * @param input 待验证的密码
     * @return 解封的密钥或继续等待的状态
     * @throws IOException 记录无法保存
     */
    private fun verifyRecord(key: SecretKey, record: Record, type: CredentialType, input: CharArray): Result.Verification {
        val now = SystemClock.elapsedRealtime()
        if (record.type != type || record.retryAt > now || !type.accepts(input)) {
            return Result.Locked(record.type, record.retryAt)
        }
        val failed = if (record.failed == MaxAttempts) 1 else record.failed + 1
        val retryAt = if (failed == MaxAttempts) now + CooldownMillis else 0
        val attempted = record.copy(failed = failed, retryAt = retryAt, boot = bootCount())
        // 先落盘再验证，进程中断时也保留本次尝试。
        writeRecord(attempted, key)
        val vaultKey = try {
            unwrapVaultKey(record, input)
        } catch (_: AEADBadTagException) {
            val cooldown = if (retryAt == 0L) 0L else SystemClock.elapsedRealtime() + CooldownMillis
            if (cooldown != retryAt) writeRecord(attempted.copy(retryAt = cooldown), key)
            return Result.Locked(type, cooldown, incorrect = true)
        }
        return try {
            writeRecord(record.copy(failed = 0, retryAt = 0, boot = UnknownBoot), key)
            Result.Unlocked(vaultKey)
        } catch (failure: Throwable) {
            vaultKey.fill(0)
            throw failure
        }
    }

    /**
     * 设备重启后重新锚定已开始的等待期限。
     * @param record 已认证的本机记录
     * @param key 本次操作已经取得的 Keystore 密钥
     * @return 当前引导周期内有效的记录
     */
    private fun reconcileCooldown(record: Record, key: SecretKey? = null): Record {
        if (record.retryAt == 0L) return record
        val reconciled = record.forBoot(bootCount(), SystemClock.elapsedRealtime())
        if (reconciled !== record) writeRecord(reconciled, key ?: deviceKey())
        return reconciled
    }

    /**
     * 获取系统引导计数。
     * @return 引导计数或未知标记
     */
    private fun bootCount(): Int =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, UnknownBoot)

    /**
     * 读取并认证版本化记录，区分缺失和损坏。
     * @param key 本次操作已经取得的 Keystore 密钥
     * @return 本机记录，记录和密码库都不存在时返回 null
     * @throws MissingRecord 已有密码库缺少对应的应用锁记录
     * @throws IOException 记录格式错误或无法读取
     */
    private fun readRecord(key: SecretKey? = null): Record? {
        val source = try {
            file.openRead()
        } catch (failure: FileNotFoundException) {
            val backup = File(file.baseFile.path + ".bak")
            if (!Files.notExists(file.baseFile.toPath()) || !Files.notExists(backup.toPath())) throw failure
            val vault = appContext.getDatabasePath(VaultRepository.DatabaseName)
            try {
                Files.readAttributes(vault.toPath(), BasicFileAttributes::class.java)
            } catch (_: NoSuchFileException) {
                return null
            }
            throw MissingRecord()
        }
        try {
            val encrypted = DataInputStream(source).use { input ->
                if (input.readInt() != FormatVersion) throw InvalidRecord()
                ByteArray(IvSize + RecordSize + TagSize).also {
                    input.readFully(it)
                    if (input.read() != -1) throw InvalidRecord()
                }
            }
            val data = decrypt(key ?: deviceKey(), DeviceAad, encrypted)
            return DataInputStream(ByteArrayInputStream(data)).use { input ->
                val typeId = input.readUnsignedByte()
                val type = CredentialType.entries.firstOrNull { it.id == typeId } ?: throw InvalidRecord()
                val failed = input.readUnsignedByte()
                val retryAt = input.readLong()
                val boot = input.readInt()
                if (failed > MaxAttempts || retryAt < 0 || (failed == MaxAttempts) != (retryAt > 0)) {
                    throw InvalidRecord()
                }
                Record(
                    type = type,
                    salt = ByteArray(SaltSize).also(input::readFully),
                    wrapped = ByteArray(WrappedSize).also(input::readFully),
                    failed = failed,
                    retryAt = retryAt,
                    boot = boot,
                )
            }
        } catch (failure: EOFException) {
            throw InvalidRecord(failure)
        } catch (failure: AEADBadTagException) {
            throw InvalidRecord(failure)
        }
    }

    /**
     * 加密并原子写入记录。
     * @param record 待写入的记录
     * @param key Android Keystore 密钥
     * @throws IOException 记录无法保存
     */
    private fun writeRecord(record: Record, key: SecretKey) {
        val payload = ByteArrayOutputStream(RecordSize).apply {
            DataOutputStream(this).use { output ->
                output.writeByte(record.type.id)
                output.writeByte(record.failed)
                output.writeLong(record.retryAt)
                output.writeInt(record.boot)
                output.write(record.salt)
                output.write(record.wrapped)
            }
        }.toByteArray()
        replaceRecord(file.baseFile, encrypt(key, DeviceAad, payload))
    }

    /**
     * 读取应用专用的 AES 密钥，首次设置时允许创建。
     * @param create 是否允许生成密钥
     * @return 不可直接导出的 Keystore 密钥
     * @throws MissingKey 已配置记录缺少密钥
     */
    private fun deviceKey(create: Boolean = false): SecretKey {
        val store = KeyStore.getInstance(KeyStoreProvider).apply { load(null) }
        (store.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        if (!create) throw MissingKey()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreProvider).run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(KeySize * Byte.SIZE_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    /**
     * 从输入和随机盐派生密码封装密钥。
     * @param input 应用密码
     * @param salt 本机记录的盐
     * @return AES 密钥
     */
    private fun deriveKey(input: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(input, salt, KdfIterations, KeySize * Byte.SIZE_BITS)
        try {
            val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return try {
                SecretKeySpec(derived, "AES")
            } finally {
                derived.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * 生成绑定凭据类型的认证关联数据。
     * @param type 应用密码类型
     * @return 格式版本与类型字节
     */
    private fun credentialAad(type: CredentialType): ByteArray = byteArrayOf(1, type.id.toByte())

    /**
     * 使用随机 IV 加密并认证数据。
     * @param key AES 密钥
     * @param aad 认证关联数据
     * @param plaintext 待封装的数据
     * @return IV 与带认证标签的密文
     */
    private fun encrypt(key: SecretKey, aad: ByteArray, plaintext: ByteArray): ByteArray =
        Cipher.getInstance(Transformation).run {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(aad)
            val nonce = iv
            check(nonce.size == IvSize)
            nonce + doFinal(plaintext)
        }

    /**
     * 验证并解密带 IV 前缀的密文。
     * @param key AES 密钥
     * @param aad 认证关联数据
     * @param wrapped IV 与带认证标签的密文
     * @return 解封的数据
     * @throws AEADBadTagException 认证失败
     */
    private fun decrypt(key: SecretKey, aad: ByteArray, wrapped: ByteArray): ByteArray =
        Cipher.getInstance(Transformation).run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagSize * Byte.SIZE_BITS, wrapped, 0, IvSize))
            updateAAD(aad)
            doFinal(wrapped, IvSize, wrapped.size - IvSize)
        }

    /**
     * 本机记录的认证载荷。
     * @author Tingdong
     * @date 2026-11-18
     */
    private data class Record(
        val type: CredentialType,
        val salt: ByteArray,
        val wrapped: ByteArray,
        val failed: Int,
        val retryAt: Long,
        val boot: Int,
    ) {
        /**
         * 按引导周期恢复冷却。确认重启时，开机后的时间计入等待。
         * @param currentBoot 当前引导计数
         * @param now 本次开机后的毫秒数
         * @return 恢复后的记录
         */
        fun forBoot(currentBoot: Int, now: Long): Record {
            if (retryAt == 0L || (boot == currentBoot && retryAt <= now + CooldownMillis)) return this
            val confirmedReboot = boot != UnknownBoot && currentBoot != UnknownBoot && boot != currentBoot
            val deadline = if (confirmedReboot) CooldownMillis else now + CooldownMillis
            return if (now >= deadline) copy(failed = 0, retryAt = 0, boot = UnknownBoot)
            else copy(retryAt = deadline, boot = currentBoot)
        }
    }

    private companion object {
        const val FormatVersion = 0x4d504c01
        const val KeyStoreProvider = "AndroidKeyStore"
        const val KeyAlias = "mypass.app-lock.v1"
        const val Transformation = "AES/GCM/NoPadding"
        const val KdfIterations = 600_000
        const val KeySize = 32
        const val SaltSize = 16
        const val IvSize = 12
        const val TagSize = 16
        const val WrappedSize = IvSize + KeySize + TagSize
        const val RecordSize = 2 + Long.SIZE_BYTES + Int.SIZE_BYTES + SaltSize + WrappedSize
        const val MaxAttempts = 5
        const val CooldownMillis = 30_000L
        const val UnknownBoot = -1
        val DeviceAad = byteArrayOf(0x4d, 0x50, 0x4c, 0x01)

        /**
         * 同步同目录临时文件后原子替换凭据记录。
         * @param destination 正式记录的路径
         * @param encrypted 带 IV 和认证标签的密文
         * @throws IOException 写入、同步或替换失败
         */
        fun replaceRecord(destination: File, encrypted: ByteArray) {
            val pending = File(destination.path + ".new")
            try {
                FileOutputStream(pending).use { stream ->
                    val output = DataOutputStream(stream)
                    output.writeInt(FormatVersion)
                    output.write(encrypted)
                    output.flush()
                    stream.fd.sync()
                }
                Files.move(
                    pending.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (failure: IOException) {
                try {
                    Files.deleteIfExists(pending.toPath())
                } catch (cleanupFailure: IOException) {
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        }
    }
}
