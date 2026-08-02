package com.yuafeng.videoswiper

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager
    private lateinit var adapter: ApiSourceAdapter
    private val sources = mutableListOf<ApiSource>()
    private var tvEmpty: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        setContentView(R.layout.activity_settings)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        tvEmpty = findViewById(R.id.tvEmpty)

        setupMuted()
        setupSources()
        setupDownloadDir()
    }

    private fun updateEmptyState() {
        tvEmpty?.visibility = if (sources.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupMuted() {
        val sw = findViewById<Switch>(R.id.switchMuted) ?: return
        sw.isChecked = settings.muted
        sw.setOnCheckedChangeListener { _, checked ->
            settings.muted = checked
            Toast.makeText(this, if (checked) "默认静音" else "默认有声", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSources() {
        val rv = findViewById<RecyclerView>(R.id.rvApiSources) ?: return
        sources.clear()
        sources.addAll(settings.getSources())

        adapter = ApiSourceAdapter(
            sources,
            onEdit = { pos -> showEditDialog(pos) },
            onDelete = { pos -> confirmDelete(pos) },
            onChange = { saveAll() }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        updateEmptyState()

        findViewById<TextView>(R.id.btnAddSource)?.setOnClickListener { showAddDialog() }
    }

    private fun saveAll() {
        settings.saveSources(sources)
        updateEmptyState()
    }

    private fun showAddDialog() {
        showDialog("添加视频源", "", "") { name, url ->
            sources.add(ApiSource(name, url, 5, true))
            adapter.notifyItemInserted(sources.size - 1)
            saveAll()
            Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditDialog(pos: Int) {
        if (pos !in sources.indices) return
        val s = sources[pos]
        showDialog("编辑视频源", s.name, s.url) { name, url ->
            sources[pos] = s.copy(name = name, url = url)
            adapter.updateAt(pos, sources[pos])
            saveAll()
            Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDialog(title: String, initName: String, initUrl: String, onConfirm: (String, String) -> Unit) {
        val v = layoutInflater.inflate(R.layout.dialog_add_api, null)
        v.findViewById<TextView>(R.id.tvDialogTitle).text = title
        val etName = v.findViewById<EditText>(R.id.etName).apply { setText(initName) }
        val etUrl = v.findViewById<EditText>(R.id.etUrl).apply { setText(initUrl) }
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setView(v).setCancelable(false).create()

        v.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        v.findViewById<TextView>(R.id.btnConfirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val url = etUrl.text.toString().trim()
            when {
                name.isEmpty() -> etName.error = "必填"
                url.isEmpty() -> etUrl.error = "必填"
                !url.startsWith("http") -> etUrl.error = "URL 无效"
                else -> { onConfirm(name, url); dlg.dismiss() }
            }
        }
        dlg.show()
    }

    private fun confirmDelete(pos: Int) {
        if (pos !in sources.indices) return
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("删除「${sources[pos].name}」？")
            .setPositiveButton("删除") { _, _ ->
                settings.removeSource(pos)
                adapter.removeAt(pos)
                updateEmptyState()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupDownloadDir() {
        val tv = findViewById<TextView>(R.id.tvDownloadDir) ?: return
        tv.text = simplify(settings.downloadDir)

        findViewById<View>(R.id.btnDownloadDir)?.setOnClickListener {
            val input = EditText(this).apply {
                setText(settings.downloadDir)
                setSelectAllOnFocus(true)
                setSingleLine()
                setBackgroundColor(0xFF1C1C2B.toInt())
                setPadding(40, 30, 40, 30)
                setTextColor(0xFFEEEEF0.toInt())
            }
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("下载目录")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val p = input.text.toString().trim()
                    if (p.isNotEmpty()) { settings.downloadDir = p; tv.text = simplify(p) }
                }
                .setNeutralButton("恢复默认") { _, _ ->
                    settings.downloadDir = ""; tv.text = simplify(settings.downloadDir)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun simplify(p: String) = p
        .replace("/storage/emulated/0/", "内部存储/")
        .replace("/sdcard/", "内部存储/")
        .ifEmpty { "Movies/VideoSwiper" }
}
