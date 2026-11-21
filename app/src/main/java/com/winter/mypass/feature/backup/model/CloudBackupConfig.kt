package com.winter.mypass.feature.backup.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.util.UUID

internal enum class ConfigField { Endpoint, Region, Bucket, Directory, Account, Secret, Retention }

internal class InvalidConfig(val field: ConfigField) : IllegalArgumentException()

/** 对象存储预设及 WebDAV 类型。 */
@Serializable
internal enum class Provider {
    WebDAV, AWS, COS, OSS, Kodo,
    // 保留旧配置的序列化名称，已有连接可继续备份和还原。
    @SerialName("S3") LegacyS3;

    companion object {
        val available = listOf(WebDAV, AWS, COS, OSS, Kodo)
    }
}

/**
 * 用户保存的单个云端目的地。
 * @author Tingdong
 * @date 2026-11-21
 */
@Serializable
internal data class CloudBackupConfig(
    val provider: Provider = Provider.WebDAV,
    val endpoint: String = "",
    val region: String = "",
    val bucket: String = "",
    val directory: String = "MyPass",
    val account: String = "",
    val secret: String = "",
    val retain: Int = 3,
    val pathStyle: Boolean = false,
) {
    /** 调试输出仅包含服务类型。 */
    override fun toString(): String = "CloudBackupConfig(provider=$provider)"

    /** 根据服务与地域生成默认端点，保留自定义地址。 */
    fun address(): HttpUrl {
        val entered = endpoint.trim().ifEmpty {
            if (!region.matches(RegionPattern)) throw InvalidConfig(ConfigField.Region)
            when (provider) {
                Provider.AWS -> if (region.startsWith("cn-")) "https://s3.$region.amazonaws.com.cn" else "https://s3.$region.amazonaws.com"
                Provider.COS -> "https://cos.$region.myqcloud.com"
                Provider.OSS -> "https://oss-$region.aliyuncs.com"
                Provider.Kodo -> "https://s3.$region.qiniucs.com"
                else -> throw InvalidConfig(ConfigField.Endpoint)
            }
        }
        val normalized = if (provider == Provider.COS && "://" !in entered) "https://$entered" else entered
        val address = normalized.toHttpUrlOrNull() ?: throw InvalidConfig(ConfigField.Endpoint)
        if (address.username.isNotEmpty() || address.password.isNotEmpty() || address.query != null || address.fragment != null) {
            throw InvalidConfig(ConfigField.Endpoint)
        }
        if (!address.isHttps) {
            if (provider != Provider.WebDAV) throw InvalidConfig(ConfigField.Endpoint)
            // IP 字面量就地验证，主机名由传输层在每次 DNS 解析时验证。
            val literal = ':' in address.host || address.host.all { it.isDigit() || it == '.' }
            if (literal && !isPrivateLiteral(address.host)) throw InvalidConfig(ConfigField.Endpoint)
        }
        return address
    }

    /** 校验配置后构造桶或 WebDAV 根目录。 */
    fun root(): HttpUrl {
        if (provider in setOf(Provider.WebDAV, Provider.LegacyS3) && endpoint.isBlank()) throw InvalidConfig(ConfigField.Endpoint)
        val url = address()
        val builder = url.newBuilder()
        if (provider != Provider.WebDAV) {
            signingRegion(url)
            val cosEndpoint = if (provider == Provider.COS) CosEndpoint.matchEntire(url.host) else null
            val cosBucket = cosEndpoint?.groups?.get(1)?.value
            val name = if (provider == Provider.COS) cosBucketName(cosBucket) else bucket
            if (!name.matches(BucketPattern) || ".." in name) throw InvalidConfig(ConfigField.Bucket)
            when {
                provider == Provider.LegacyS3 && pathStyle -> builder.addPathSegment(name)
                provider != Provider.COS || (cosEndpoint != null && cosBucket == null) ->
                    builder.host("$name.${url.host}")
            }
        }
        if (account.isBlank() || (provider == Provider.WebDAV && ':' in account)) throw InvalidConfig(ConfigField.Account)
        if (secret.isEmpty()) throw InvalidConfig(ConfigField.Secret)
        if (retain < 1) throw InvalidConfig(ConfigField.Retention)
        return builder.build()
    }

    /** COS 标准域名带有地域，签名使用与访问地址一致的值。 */
    fun signingRegion(url: HttpUrl): String {
        val endpointRegion = if (provider == Provider.COS) {
            CosEndpoint.matchEntire(url.host)?.groups?.get(2)?.value?.takeUnless { it == "accelerate" }
        } else null
        val entered = region.trim()
        val resolved = if (endpointRegion != null && (entered.isEmpty() || entered == "auto")) endpointRegion else entered
        if (!resolved.matches(RegionPattern) || (provider == Provider.COS && resolved == "auto") ||
            (endpointRegion != null && resolved != endpointRegion)
        ) {
            throw InvalidConfig(ConfigField.Region)
        }
        return resolved
    }

    /** 对象操作前确认自定义域名绑定的桶全名。 */
    fun expectedCustomCosBucket(url: HttpUrl): String? =
        if (provider == Provider.COS && !CosEndpoint.matches(url.host)) cosBucketName(null) else null

    /** 完整腾讯云域名可以提供桶名，自定义源站域名必须手填桶名。 */
    private fun cosBucketName(fromHost: String?): String {
        val entered = bucket.trim()
        val name = when {
            fromHost == null -> entered
            entered.isEmpty() || entered == fromHost || entered == fromHost.substringBeforeLast('-') -> fromHost
            else -> throw InvalidConfig(ConfigField.Bucket)
        }
        if (!name.matches(CosBucketPattern)) throw InvalidConfig(ConfigField.Bucket)
        return name
    }

    fun segments(): List<String> = directory.trim('/').split('/').filter { it.isNotEmpty() }.also {
        if (it.any { part -> part == "." || part == ".." || '\\' in part || part.any(Char::isISOControl) }) {
            throw InvalidConfig(ConfigField.Directory)
        }
    }

    companion object {
        private val RegionPattern = Regex("[a-z0-9-]+")
        private val BucketPattern = Regex("[a-z0-9][a-z0-9.-]*[a-z0-9]")
        private val CosBucketPattern = Regex("[a-z0-9-]+-[0-9]+")
        private val CosEndpoint = Regex(
            "(?:([a-z0-9-]+)\\.)?(?:cos\\.([a-z0-9-]+)\\.myqcloud\\.com|cos-internal\\.accelerate\\.tencentcos\\.cn)",
        )

        /** 识别 IPv4 私网、IPv6 本地地址与回环地址。 */
        fun isPrivateLiteral(host: String): Boolean {
            if (':' !in host && !host.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))) return false
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
            return address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                (address.address.size == 16 && address.address[0].toInt() and 0xfe == 0xfc)
        }
    }
}

/** 只清理符合本应用命名规则且安装标识一致的文件。 */
internal object BackupNames {
    private val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val pattern = Regex("MyPass-([0-9]{13})-($uuid)-($uuid)\\.mp")

    fun create(installation: String, time: Long) = "MyPass-$time-$installation-${UUID.randomUUID()}.mp"
    fun installation(name: String): String? = pattern.matchEntire(name)?.groupValues?.get(2)

    fun time(name: String) = pattern.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()
    fun expired(files: List<String>, installation: String, newest: String, retain: Int): List<String> =
        files.distinct().filter { it != newest && BackupNames.installation(it) == installation }
            .sortedDescending().drop(retain - 1)
}
