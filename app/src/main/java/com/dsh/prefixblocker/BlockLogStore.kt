package com.dsh.prefixblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一条拦截记录。 */
data class BlockLogEntry(
    val number: String,
    val pattern: String,
    val action: String,
    val timeMillis: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("number", number)
        put("pattern", pattern)
        put("action", action)
        put("timeMillis", timeMillis)
    }

    companion object {
        fun fromJson(obj: JSONObject): BlockLogEntry = BlockLogEntry(
            number = obj.optString("number", ""),
            pattern = obj.optString("pattern", ""),
            action = obj.optString("action", ""),
            timeMillis = obj.optLong("timeMillis", 0L)
        )
    }
}

/** 拦截记录的本地存储，仅保留最近 [MAX_ENTRIES] 条。 */
class BlockLogStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 新记录插入到最前面。 */
    fun append(entry: BlockLogEntry) {
        val list = load().toMutableList()
        list.add(0, entry)
        while (list.size > MAX_ENTRIES) list.removeAt(list.size - 1)
        save(list)
    }

    fun load(): List<BlockLogEntry> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        val result = mutableListOf<BlockLogEntry>()
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                result.add(BlockLogEntry.fromJson(obj))
            }
        } catch (t: Throwable) {
            result.clear()
        }
        return result
    }

    fun clear() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun save(list: List<BlockLogEntry>) {
        val array = JSONArray()
        for (entry in list) array.put(entry.toJson())
        prefs.edit().putString(KEY_LOGS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "prefix_blocker_logs"
        private const val KEY_LOGS = "logs_json"
        private const val MAX_ENTRIES = 300
    }
}
