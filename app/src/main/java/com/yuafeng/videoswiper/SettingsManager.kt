package com.yuafeng.videoswiper

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("video_swiper_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DOWNLOAD_DIR = "download_dir"
        private const val KEY_API_SOURCES = "api_sources"
        private const val KEY_MUTED = "muted"
        private const val KEY_HAS_INIT = "has_init"

        private val DEFAULT_SOURCES = emptyList<ApiSource>()

        private val DEFAULT_DIR =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                .absolutePath + "/VideoSwiper"
    }

    // ========== 静音 ==========

    var muted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, true)
        set(v) = prefs.edit().putBoolean(KEY_MUTED, v).apply()

    // ========== 下载目录 ==========

    var downloadDir: String
        get() = prefs.getString(KEY_DOWNLOAD_DIR, DEFAULT_DIR) ?: DEFAULT_DIR
        set(v) = prefs.edit().putString(KEY_DOWNLOAD_DIR, v).apply()

    // ========== 视频源 ==========

    fun getSources(): MutableList<ApiSource> {
        if (!prefs.getBoolean(KEY_HAS_INIT, false)) {
            // 首次安装：清空旧数据，不设默认源
            saveSources(emptyList())
            prefs.edit().putBoolean(KEY_HAS_INIT, true).apply()
            return mutableListOf()
        }
        val json = prefs.getString(KEY_API_SOURCES, "[]") ?: "[]"
        val type = object : TypeToken<List<ApiSource>>() {}.type
        return try {
            val list = Gson().fromJson<List<ApiSource>>(json, type)
            list.map { it.withDefaults() }.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun getEnabledSources(): List<ApiSource> =
        getSources().filter { it.enabled }

    fun saveSources(list: List<ApiSource>) {
        prefs.edit().putString(KEY_API_SOURCES, Gson().toJson(list)).apply()
    }

    fun addSource(s: ApiSource) {
        val l = getSources(); l.add(s); saveSources(l)
    }

    fun removeSource(i: Int) {
        val l = getSources()
        if (i in l.indices) { l.removeAt(i); saveSources(l) }
    }

    fun updateSource(i: Int, s: ApiSource) {
        val l = getSources()
        if (i in l.indices) { l[i] = s; saveSources(l) }
    }
}

data class ApiSource(
    val name: String,
    val url: String,
    val weight: Int = 5,
    val enabled: Boolean = true
) {
    // Gson 反序列化旧数据时 enabled 可能为 false，这里兜底
    fun withDefaults() = copy(
        weight = weight.coerceIn(1, 9),
        enabled = true  // 旧数据默认启用
    )
}
