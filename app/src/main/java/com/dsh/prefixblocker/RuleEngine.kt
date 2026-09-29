package com.dsh.prefixblocker

import java.util.regex.Pattern

/** 一次来电筛选的结论。 */
sealed class ScreeningDecision {
    /** 放行 */
    object Allow : ScreeningDecision()

    /** 命中规则，需要拦截 */
    data class Deny(
        val rule: CallRule,
        val action: RuleAction,
        val number: String
    ) : ScreeningDecision()
}

/**
 * 规则匹配引擎：无 Android 依赖的纯逻辑，可直接用 JVM 单元测试覆盖。
 *
 * 优先级：
 * 1. 白名单（[RuleAction.ALLOW]）先判，命中即放行；
 * 2. 剩余规则里取“最具体”的一条，即模式最长的；长度相同时取先定义的；
 * 3. 没有任何规则命中则放行。
 */
class RuleEngine(
    private val rules: List<CallRule>,
    private val blockHiddenNumbers: Boolean = false
) {

    private val regexCache = HashMap<String, Pattern?>()

    fun decide(rawNumber: String?): ScreeningDecision {
        val candidates = PhoneNumberUtils.variants(rawNumber)
        val display = rawNumber?.trim().orEmpty()

        // 未知 / 隐藏号码
        if (candidates.isEmpty()) {
            return if (blockHiddenNumbers) {
                ScreeningDecision.Deny(HIDDEN_NUMBER_RULE, RuleAction.BLOCK, HIDDEN_NUMBER_TEXT)
            } else {
                ScreeningDecision.Allow
            }
        }

        // 1) 白名单最优先
        for (rule in rules) {
            if (!rule.enabled || rule.action != RuleAction.ALLOW) continue
            if (matches(rule, candidates)) return ScreeningDecision.Allow
        }

        // 2) 最具体的拦截规则胜出
        var best: CallRule? = null
        for (rule in rules) {
            if (!rule.enabled || rule.action == RuleAction.ALLOW) continue
            if (!matches(rule, candidates)) continue
            val current = best
            if (current == null || rule.pattern.length > current.pattern.length) {
                best = rule
            }
        }

        val hit = best ?: return ScreeningDecision.Allow
        return ScreeningDecision.Deny(hit, hit.action, display.ifEmpty { HIDDEN_NUMBER_TEXT })
    }

    private fun matches(rule: CallRule, candidates: List<NumberVariant>): Boolean {
        val pattern = rule.pattern
        if (pattern.isBlank()) return false

        for (candidate in candidates) {
            if (candidate.strippedCountryCode && pattern.length < 2) continue
            when (rule.matchType) {
                MatchType.PREFIX -> if (candidate.value.startsWith(pattern)) return true
                MatchType.CONTAINS -> if (candidate.value.contains(pattern)) return true
                MatchType.REGEX -> {
                    val compiled = compile(pattern) ?: continue
                    if (compiled.matcher(candidate.value).find()) return true
                }
            }
        }
        return false
    }

    private fun compile(pattern: String): Pattern? {
        if (regexCache.containsKey(pattern)) return regexCache[pattern]
        val compiled = try {
            Pattern.compile(pattern)
        } catch (t: Throwable) {
            null
        }
        regexCache[pattern] = compiled
        return compiled
    }

    companion object {
        const val HIDDEN_NUMBER_TEXT = "未知号码"

        /** 代表“拦截隐藏号码”这条系统规则的占位对象。 */
        val HIDDEN_NUMBER_RULE = CallRule(
            id = -1L,
            pattern = "",
            matchType = MatchType.PREFIX,
            action = RuleAction.BLOCK,
            enabled = true,
            note = "隐藏号码"
        )
    }
}
