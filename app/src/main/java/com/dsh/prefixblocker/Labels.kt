package com.dsh.prefixblocker

import android.content.Context

/** 枚举到界面文案的转换。 */
fun matchTypeLabel(context: Context, matchType: MatchType): String = when (matchType) {
    MatchType.PREFIX -> context.getString(R.string.match_prefix)
    MatchType.CONTAINS -> context.getString(R.string.match_contains)
    MatchType.REGEX -> context.getString(R.string.match_regex)
}

fun actionLabel(context: Context, action: RuleAction): String = when (action) {
    RuleAction.BLOCK -> context.getString(R.string.action_block)
    RuleAction.SILENCE -> context.getString(R.string.action_silence)
    RuleAction.ALLOW -> context.getString(R.string.action_allow)
}

/** 拦截记录里保存的是枚举名，这里换算回界面文案。 */
fun actionLabelFromName(context: Context, name: String): String {
    val action = RuleAction.values().firstOrNull { it.name == name } ?: return name
    return actionLabel(context, action)
}
