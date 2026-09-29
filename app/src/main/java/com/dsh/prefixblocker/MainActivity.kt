package com.dsh.prefixblocker

import android.app.role.RoleManager
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dsh.prefixblocker.databinding.ActivityMainBinding
import com.dsh.prefixblocker.databinding.DialogRuleBinding
import java.util.regex.Pattern

/** 主界面：权限状态 + 总开关 + 规则列表。 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var ruleStore: RuleStore
    private lateinit var settings: SettingsStore
    private lateinit var adapter: RuleListAdapter
    private val rules = mutableListOf<CallRule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ruleStore = RuleStore(this)
        settings = SettingsStore(this)
        ruleStore.seedIfEmpty()

        adapter = RuleListAdapter(
            onToggle = { rule, checked -> replaceRule(rule.copy(enabled = checked)) },
            onDelete = { rule -> confirmDelete(rule) },
            onEdit = { rule -> showRuleDialog(rule) }
        )
        binding.rvRules.layoutManager = LinearLayoutManager(this)
        binding.rvRules.adapter = adapter

        binding.btnAddRule.setOnClickListener { showRuleDialog(null) }
        binding.btnLogs.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
        binding.btnGrant.setOnClickListener { requestScreeningRole() }

        binding.swEnabled.setOnCheckedChangeListener { _, checked ->
            settings.screeningEnabled = checked
        }
        binding.swHidden.setOnCheckedChangeListener { _, checked ->
            settings.blockHiddenNumbers = checked
        }
        binding.swLogOnly.setOnCheckedChangeListener { _, checked ->
            settings.logOnlyMode = checked
        }

        reloadRules()
    }

    override fun onResume() {
        super.onResume()
        binding.swEnabled.isChecked = settings.screeningEnabled
        binding.swHidden.isChecked = settings.blockHiddenNumbers
        binding.swLogOnly.isChecked = settings.logOnlyMode
        refreshStatus()
        reloadRules()
    }

    // ---------------------------------------------------------------- 规则列表

    private fun reloadRules() {
        rules.clear()
        rules.addAll(ruleStore.load())
        adapter.submit(rules)
        binding.tvEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun persistRules() {
        ruleStore.save(rules)
        adapter.submit(rules)
        binding.tvEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun replaceRule(rule: CallRule) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index < 0) return
        rules[index] = rule
        persistRules()
    }

    private fun confirmDelete(rule: CallRule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_rule_title)
            .setMessage(getString(R.string.delete_rule_message, patternText(rule)))
            .setPositiveButton(R.string.delete) { _, _ ->
                rules.removeAll { it.id == rule.id }
                persistRules()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun patternText(rule: CallRule): String =
        if (rule.pattern.isEmpty()) getString(R.string.hidden_number) else rule.pattern

    private fun nextId(): Long = (rules.maxOfOrNull { it.id } ?: 0L) + 1L

    // ---------------------------------------------------------------- 新增/编辑规则

    private fun showRuleDialog(existing: CallRule?) {
        val dialogBinding = DialogRuleBinding.inflate(LayoutInflater.from(this))

        val matchTypes = listOf(
            MatchType.PREFIX to getString(R.string.match_prefix),
            MatchType.CONTAINS to getString(R.string.match_contains),
            MatchType.REGEX to getString(R.string.match_regex)
        )
        val actions = listOf(
            RuleAction.BLOCK to getString(R.string.action_block),
            RuleAction.SILENCE to getString(R.string.action_silence),
            RuleAction.ALLOW to getString(R.string.action_allow)
        )

        val matchAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            matchTypes.map { it.second }
        )
        matchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spMatchType.adapter = matchAdapter

        val actionAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            actions.map { it.second }
        )
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spAction.adapter = actionAdapter

        if (existing != null) {
            dialogBinding.etPattern.setText(existing.pattern)
            dialogBinding.etNote.setText(existing.note)
            dialogBinding.spMatchType.setSelection(
                matchTypes.indexOfFirst { it.first == existing.matchType }.coerceAtLeast(0)
            )
            dialogBinding.spAction.setSelection(
                actions.indexOfFirst { it.first == existing.action }.coerceAtLeast(0)
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_rule else R.string.edit_rule)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                // Spinner 的选中位置在极端情况下可能是 -1，这里兜一下底
                val matchIndex = dialogBinding.spMatchType.selectedItemPosition
                    .coerceIn(0, matchTypes.lastIndex)
                val actionIndex = dialogBinding.spAction.selectedItemPosition
                    .coerceIn(0, actions.lastIndex)
                val matchType = matchTypes[matchIndex].first
                val action = actions[actionIndex].first
                val pattern = PhoneNumberUtils.cleanPattern(
                    dialogBinding.etPattern.text?.toString(),
                    matchType
                )

                if (pattern.isEmpty()) {
                    toast(getString(R.string.error_empty_pattern))
                    return@setOnClickListener
                }
                if (matchType == MatchType.REGEX) {
                    try {
                        Pattern.compile(pattern)
                    } catch (t: Throwable) {
                        toast(getString(R.string.error_bad_regex, t.message ?: ""))
                        return@setOnClickListener
                    }
                }

                val note = dialogBinding.etNote.text?.toString()?.trim().orEmpty()
                if (existing == null) {
                    rules.add(CallRule(nextId(), pattern, matchType, action, true, note))
                } else {
                    val index = rules.indexOfFirst { it.id == existing.id }
                    if (index >= 0) {
                        rules[index] = existing.copy(
                            pattern = pattern,
                            matchType = matchType,
                            action = action,
                            note = note
                        )
                    }
                }
                persistRules()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // ---------------------------------------------------------------- 系统授权

    private fun refreshStatus() {
        if (hasScreeningRole()) {
            binding.tvStatusTitle.setText(R.string.status_granted_title)
            binding.tvStatusDetail.setText(R.string.status_granted_detail)
            binding.btnGrant.visibility = View.GONE
        } else {
            binding.tvStatusTitle.setText(R.string.status_missing_title)
            binding.tvStatusDetail.setText(R.string.status_missing_detail)
            binding.btnGrant.visibility = View.VISIBLE
        }
    }

    /** Android 10 起来电筛选是一个“角色”，可以直接查询是否已被本应用持有。 */
    private fun hasScreeningRole(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                try {
                    startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    return
                } catch (t: Throwable) {
                    // 某些定制系统没有这个角色入口，退回到默认应用设置页
                }
            }
        }

        val fallbacks = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in fallbacks) {
            try {
                startActivity(intent)
                toast(getString(R.string.toast_pick_in_settings))
                return
            } catch (t: Throwable) {
                // 换下一个
            }
        }
        toast(getString(R.string.toast_pick_in_settings))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
