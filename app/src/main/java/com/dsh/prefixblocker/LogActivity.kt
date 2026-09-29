package com.dsh.prefixblocker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.dsh.prefixblocker.databinding.ActivityLogBinding

/** 拦截记录页。 */
class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private lateinit var store: BlockLogStore
    private lateinit var adapter: LogListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = getString(R.string.logs_title)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        store = BlockLogStore(this)
        adapter = LogListAdapter()
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = adapter

        binding.btnClear.setOnClickListener { confirmClear() }
        reload()
    }

    private fun reload() {
        val entries = store.load()
        adapter.submit(entries)
        binding.tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmClear() {
        if (store.load().isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_logs)
            .setMessage(R.string.clear_logs_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                store.clear()
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
