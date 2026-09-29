package com.dsh.prefixblocker

import org.json.JSONArray
import org.json.JSONObject

/** 规则的匹配方式。 */
enum class MatchType {
    /** 号码以该串开头，如 "400" */
    PREFIX,
    /** 号码中包含该串，如 "5588" */
    CONTAINS,
    /** 正则表达式，如 "^1[3-9]\d{9}$" */
    REGEX
}

/** 命中规则后要执行的动作。 */
enum class RuleAction {
    /** 直接拒接，对方听到忙音/被挂断 */
    BLOCK,
    /** 静音拦截：不响铃、不提醒，但保留通话记录 */
    SILENCE,
    /** 白名单：匹配到的号码一律放行，优先级最高 */
    ALLOW
}

/**
 * 一条拦截规则。纯数据类，可安全序列化。
 */
data class CallRule(
    val id: Long,
    val pattern: String,
    val matchType: MatchType,
    val action: RuleAction,
    val enabled: Boolean,
    val note: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("pattern", pattern)
        put("matchType", matchType.name)
        put("action", action.name)
        put("enabled", enabled)
        put("note", note)
    }

    companion object {

        fun fromJson(obj: JSONObject): CallRule = CallRule(
            id = obj.optLong("id", 0L),
            pattern = obj.optString("pattern", ""),
            matchType = enumOrDefault(obj.optString("matchType"), MatchType.PREFIX),
            action = enumOrDefault(obj.optString("action"), RuleAction.BLOCK),
            enabled = obj.optBoolean("enabled", true),
            note = obj.optString("note", "")
        )

        fun listToJson(rules: List<CallRule>): String {
            val array = JSONArray()
            for (rule in rules) array.put(rule.toJson())
            return array.toString()
        }

        fun listFromJson(json: String?): MutableList<CallRule> {
            val result = mutableListOf<CallRule>()
            if (json.isNullOrBlank()) return result
            try {
                val array = JSONArray(json)
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    result.add(fromJson(obj))
                }
            } catch (t: Throwable) {
                // 本地数据损坏时当作空列表，避免整个应用崩掉
                result.clear()
            }
            return result
        }

        private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}
