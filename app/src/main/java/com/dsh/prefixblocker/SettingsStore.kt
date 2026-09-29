package com.dsh.prefixblocker

import android.content.Context

/** 全局开关，存放在 SharedPreferences。 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 总开关：关掉后所有来电一律放行。 */
    var screeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** 是否拦截“隐藏号码 / 未知号码”来电。 */
    var blockHiddenNumbers: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_HIDDEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BLOCK_HIDDEN, value).apply()
        }

    /**
     * 测试模式：命中规则时只写入拦截记录，不真正拦截。
     * 用来在正式启用前验证规则是否会误伤正常来电。
     */
    var logOnlyMode: Boolean
        get() = prefs.getBoolean(KEY_LOG_ONLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LOG_ONLY, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "prefix_blocker_settings"
        private const val KEY_ENABLED = "screening_enabled"
        private const val KEY_BLOCK_HIDDEN = "block_hidden_numbers"
        private const val KEY_LOG_ONLY = "log_only_mode"
    }
}
