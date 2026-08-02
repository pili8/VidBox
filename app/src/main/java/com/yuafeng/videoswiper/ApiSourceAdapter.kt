package com.yuafeng.videoswiper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ApiSourceAdapter(
    private val sources: MutableList<ApiSource>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onChange: () -> Unit
) : RecyclerView.Adapter<ApiSourceAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cb: CheckBox = v.findViewById(R.id.cbEnabled)
        val name: TextView = v.findViewById(R.id.tvName)
        val url: TextView = v.findViewById(R.id.tvUrl)
        val tvWeight: TextView = v.findViewById(R.id.tvWeight)
        val tvPercent: TextView = v.findViewById(R.id.tvPercent)
        val btnDown: TextView = v.findViewById(R.id.btnWeightDown)
        val btnUp: TextView = v.findViewById(R.id.btnWeightUp)
        val edit: ImageView = v.findViewById(R.id.btnEdit)
        val del: ImageView = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_api_source, parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = sources[pos]
        h.name.text = s.name
        h.url.text = s.url
        h.cb.isChecked = s.enabled
        h.tvWeight.text = s.weight.toString()

        // 计算百分比（基于所有启用源的权重总和）
        val enabledSources = sources.filter { it.enabled }
        val totalWeight = enabledSources.sumOf { it.weight.coerceIn(1, 9) }
        if (s.enabled && totalWeight > 0) {
            val pct = (s.weight.coerceIn(1, 9) * 100.0 / totalWeight).toInt()
            h.tvPercent.text = "${pct}%"
            h.tvPercent.setTextColor(if (pct >= 50) 0xFFFF6B6B.toInt() else 0xFF6C63FF.toInt())
        } else {
            h.tvPercent.text = "—"
            h.tvPercent.setTextColor(0xFF666666.toInt())
        }

        // checkbox
        h.cb.setOnCheckedChangeListener { _, checked ->
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) {
                sources[p] = sources[p].copy(enabled = checked)
                onChange()
                notifyDataSetChanged() // 刷新所有百分比
            }
        }

        // 权重 −
        h.btnDown.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION && sources[p].weight > 1) {
                sources[p] = sources[p].copy(weight = sources[p].weight - 1)
                onChange()
                notifyDataSetChanged() // 刷新所有百分比
            }
        }

        // 权重 +
        h.btnUp.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION && sources[p].weight < 9) {
                sources[p] = sources[p].copy(weight = sources[p].weight + 1)
                onChange()
                notifyDataSetChanged() // 刷新所有百分比
            }
        }

        // 编辑
        h.edit.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onEdit(p)
        }

        // 删除
        h.del.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onDelete(p)
        }
    }

    override fun getItemCount() = sources.size

    fun removeAt(pos: Int) {
        sources.removeAt(pos)
        notifyDataSetChanged()
    }

    fun updateAt(pos: Int, s: ApiSource) {
        sources[pos] = s
        notifyDataSetChanged()
    }
}
