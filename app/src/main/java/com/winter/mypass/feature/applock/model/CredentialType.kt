package com.winter.mypass.feature.applock.model

/**
 * 应用密码类型及持久化编号。
 * @author Tingdong
 * @date 2026-11-18
 */
internal enum class CredentialType(val id: Int, val pinLength: Int = 0) {
    Pattern(1),
    Pin4(2, 4),
    Pin6(3, 6),
    Password(4);

    /**
     * 检查输入是否符合当前类型的规则。
     * @param input 待检查的凭据
     * @return 是否符合规则
     */
    fun accepts(input: CharArray): Boolean = when (this) {
        Pattern -> {
            val points = mutableListOf<Int>()
            input.size in 4..9 && input.all { character ->
                val point = character - '1'
                point in 0..8 && point !in points && points.addPatternPoint(point) == 1
            }
        }
        Pin4, Pin6 -> input.size == pinLength && input.all { it in '0'..'9' }
        Password -> input.size in 8..MaxPasswordLength &&
            input.all { it.isAsciiLetter() || it in '0'..'9' } &&
            input.any { it.isAsciiLetter() } && input.any { it in '0'..'9' }
    }

    companion object {
        const val MaxPasswordLength = 64
    }
}

/**
 * 向图案追加网格点及尚未选中的中间点。示例：points.addPatternPoint(2)。
 * @param point 行优先排列的零起始点编号
 * @return 新增点数
 */
internal fun MutableList<Int>.addPatternPoint(point: Int): Int {
    if (point in this) return 0
    val previousSize = size
    lastOrNull()?.let { previous ->
        val rowSum = previous / 3 + point / 3
        val columnSum = previous % 3 + point % 3
        if (rowSum % 2 == 0 && columnSum % 2 == 0) {
            val middle = rowSum / 2 * 3 + columnSum / 2
            if (middle !in this) add(middle)
        }
    }
    add(point)
    return size - previousSize
}

/**
 * 判断字符是否为英文字母。示例：'A'.isAsciiLetter()。
 * @return 是否为 ASCII 字母
 */
private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'
