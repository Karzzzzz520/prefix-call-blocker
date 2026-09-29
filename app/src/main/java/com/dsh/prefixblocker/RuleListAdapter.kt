package com.dsh.prefixblocker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dsh.prefixblocker.databinding.ItemRuleBinding

/** 规则列表适配器。 */
class RuleListAdapter(
    private val onToggle: (CallRule, Boolean) -> Unit,
    private val onDelete: (CallRule) -> Unit,
    private val onEdit: (CallRule) -> Unit
) : RecyclerView.Adapter<RuleListAdapter.RuleViewHolder>() {

    private val items = mutableListOf<CallRule>()

    fun submit(list: List<CallRule>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class RuleViewHolder(private val binding: ItemRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: CallRule) {
            val context = binding.root.context
            binding.tvPattern.text =
                if (rule.pattern.isEmpty()) context.getString(R.string.hidden_number) else rule.pattern
            binding.tvMeta.text = context.getString(
                R.string.rule_meta,
                matchTypeLabel(context, rule.matchType),
                actionLabel(context, rule.action),
                if (rule.note.isBlank()) context.getString(R.string.no_note) else rule.note
            )

            // 重新绑定前先摘掉监听，避免复用 ViewHolder 时把旧规则的状态写回去
            binding.swEnabled.setOnCheckedChangeListener(null)
            binding.swEnabled.isChecked = rule.enabled
            binding.swEnabled.setOnCheckedChangeListener { _, checked -> onToggle(rule, checked) }

            binding.btnEdit.setOnClickListener { onEdit(rule) }
            binding.btnDelete.setOnClickListener { onDelete(rule) }
        }
    }
}
