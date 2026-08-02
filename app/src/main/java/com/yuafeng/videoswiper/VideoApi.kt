package com.yuafeng.videoswiper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object VideoApi {

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 加权随机：根据 weight 选源，再请求视频
     */
    suspend fun fetchRandomVideo(sources: List<ApiSource>): VideoItem? =
        withContext(Dispatchers.IO) {
            try {
                if (sources.isEmpty()) return@withContext null
                val source = weightedPick(sources)
                val req = Request.Builder().url(source.url).head().build()
                val resp = client.newCall(req).execute()
                val videoUrl = resp.header("Location") ?: source.url
                VideoItem(url = videoUrl, title = source.name, source = source.name)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun prefetch(count: Int, sources: List<ApiSource>): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        repeat(count) { fetchRandomVideo(sources)?.let { list.add(it) } }
        return list
    }

    /**
     * 加权随机选取（自动归一化，权重 1-9，不用总和=10）
     */
    private fun weightedPick(sources: List<ApiSource>): ApiSource {
        val total = sources.sumOf { it.weight.coerceIn(1, 9) }
        if (total <= 0) return sources.random()
        var r = (0 until total).random()
        for (s in sources) {
            r -= s.weight.coerceIn(1, 9)
            if (r < 0) return s
        }
        return sources.last()
    }
}
