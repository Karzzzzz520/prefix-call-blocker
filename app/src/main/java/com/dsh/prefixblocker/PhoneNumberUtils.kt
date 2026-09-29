package com.dsh.prefixblocker

/**
 * 号码的归一化与候选形式生成。
 *
 * 同一个来电，系统给出的写法可能是：
 * `+8613800138000`、`8613800138000`、`13800138000`、`008613800138000`、`010-1234 5678`。
 * 而用户写规则时通常只想写 `138` 或 `010`，所以这里把来电号码展开成若干候选形式，
 * 再让规则去匹配任意一种形式。
 */
data class NumberVariant(
    /** 候选号码字符串 */
    val value: String,
    /**
     * true 表示这是“去掉国家码”后的国内形式。
     * 匹配这种形式时要求规则至少 2 位，避免用户写一条 "1" 就把 +86 的所有号码误伤。
     */
    val strippedCountryCode: Boolean
)

object PhoneNumberUtils {

    /** 常见国家/地区码，按长度倒序，保证 +852 不会先被 +85 命中。 */
    private val COUNTRY_CODES = listOf(
        "852", "853", "855", "856", "880", "886", "850",
        "86", "81", "82", "84", "91", "92", "93", "94", "95", "98", "90",
        "60", "61", "62", "63", "64", "65", "66",
        "20", "27", "30", "31", "32", "33", "34", "36", "39",
        "40", "41", "43", "44", "45", "46", "47", "48", "49",
        "51", "52", "53", "54", "55", "56", "57", "58",
        "1", "7"
    ).sortedByDescending { it.length }

    /**
     * 把任意写法的号码归一化成国际形式。
     * 提取不到数字时返回空串，对应“未知 / 隐藏号码”。
     */
    fun canonical(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val text = raw.trim()
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return ""

        var international = text.startsWith("+")
        var body = digits
        if (body.startsWith("00")) {
            body = body.substring(2)
            international = true
        }
        if (body.isEmpty()) return ""
        return if (international) "+$body" else body
    }

    /**
     * 生成匹配用的候选形式，顺序即优先级：
     * 1. 归一化结果本身（+8613800138000）
     * 2. 去掉 "+" 的形式（8613800138000）
     * 3. 去掉国家码的国内形式（13800138000）
     */
    fun variants(raw: String?): List<NumberVariant> {
        val canonical = canonical(raw)
        if (canonical.isEmpty()) return emptyList()

        val result = ArrayList<NumberVariant>(3)
        result.add(NumberVariant(canonical, false))

        val withoutPlus = if (canonical.startsWith("+")) canonical.substring(1) else canonical
        if (withoutPlus != canonical) result.add(NumberVariant(withoutPlus, false))

        if (canonical.startsWith("+")) {
            val code = COUNTRY_CODES.firstOrNull {
                withoutPlus.startsWith(it) && withoutPlus.length > it.length
            }
            if (code != null) {
                result.add(NumberVariant(withoutPlus.substring(code.length), true))
            }
        } else if (withoutPlus.length > 11) {
            // 号码里没有 "+" 却明显超长，按 11 位国内号码再给一次匹配机会
            result.add(NumberVariant(withoutPlus.takeLast(11), true))
        }
        return result
    }

    /** 把用户输入的规则模式清理成纯号码；正则表达式原样保留。 */
    fun cleanPattern(raw: String?, matchType: MatchType): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        if (matchType == MatchType.REGEX) return text

        val international = text.startsWith("+")
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        if (international) return "+$digits"
        if (digits.startsWith("00")) return "+" + digits.substring(2)
        return digits
    }
}
