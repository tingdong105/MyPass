package com.winter.mypass.feature.home.model

import java.security.SecureRandom

/**
 * 生成密码可使用的字符组。
 * @author Tingdong
 * @date 2026-11-19
 */
internal enum class PasswordGroup(val characters: String) {
    Uppercase("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    Lowercase("abcdefghijklmnopqrstuvwxyz"),
    Digits("0123456789"),
    Symbols("!@#\$%^&*_-+=?"),
}

/**
 * 使用系统安全随机源生成包含所选字符组的密码。
 * @author Tingdong
 * @date 2026-11-19
 */
internal object PasswordGenerator {
    val LengthRange = 8..64
    const val DefaultLength = 16
    private val random = SecureRandom()

    /**
     * 从所选字符集均匀采样，并检查每组选项均出现在结果中。
     * @param length 密码长度
     * @param groups 允许使用的字符组
     * @return 生成的密码
     */
    fun generate(length: Int, groups: Set<PasswordGroup>): String {
        require(length in LengthRange && groups.isNotEmpty())
        val pool = groups.joinToString("") { it.characters }
        val buffer = CharArray(length)
        try {
            do {
                for (index in buffer.indices) buffer[index] = pool[random.nextInt(pool.length)]
            } while (groups.any { group -> buffer.none { it in group.characters } })
            return buffer.concatToString()
        } finally {
            buffer.fill('\u0000')
        }
    }
}
