package com.dsh.prefixblocker

import android.content.Context

/**
 * 规则的本地存储。
 *
 * 规则量级只有几十条，用 SharedPreferences + JSON 足够，也不引入 Room / KSP 这些额外构建复杂度。
 */
class RuleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): MutableList<CallRule> = CallRule.listFromJson(prefs.getString(KEY_RULES, null))

    fun save(rules: List<CallRule>) {
        prefs.edit().putString(KEY_RULES, CallRule.listToJson(rules)).apply()
    }

    /** 首次安装时写入几条默认关闭的示例规则，让用户照着改。 */
    fun seedIfEmpty() {
        if (prefs.contains(KEY_RULES)) return
        save(defaultRules())
    }

    private fun defaultRules(): List<CallRule> = listOf(
        CallRule(
            id = 1L,
            pattern = "400",
            matchType = MatchType.PREFIX,
            action = RuleAction.BLOCK,
            enabled = false,
            note = "400 开头的企业客服号（示例，默认关闭）"
        ),
        CallRule(
            id = 2L,
            pattern = "95",
            matchType = MatchType.PREFIX,
            action = RuleAction.BLOCK,
            enabled = false,
            note = "95 开头的银行/客服短号（示例，默认关闭）"
        ),
        CallRule(
            id = 3L,
            pattern = "^1[3-9]\\d{9}$",
            matchType = MatchType.REGEX,
            action = RuleAction.SILENCE,
            enabled = false,
            note = "全部手机号静音拦截（正则示例，默认关闭，慎用）"
        )
    )

    companion object {
        private const val PREFS_NAME = "prefix_blocker_rules"
        private const val KEY_RULES = "rules_json"
    }
}
