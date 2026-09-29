package com.dsh.prefixblocker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dsh.prefixblocker.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 拦截记录列表适配器。 */
class LogListAdapter : RecyclerView.Adapter<LogListAdapter.LogViewHolder>() {

    private val items = mutableListOf<BlockLogEntry>()
    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    fun submit(list: List<BlockLogEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class LogViewHolder(private val binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: BlockLogEntry) {
            val context = binding.root.context
            binding.tvNumber.text =
                if (entry.number.isBlank()) context.getString(R.string.hidden_number) else entry.number
            binding.tvAction.text = actionLabelFromName(context, entry.action)
            binding.tvLogMeta.text = context.getString(
                R.string.log_meta,
                entry.pattern,
                formatter.format(Date(entry.timeMillis))
            )
        }
    }
}
