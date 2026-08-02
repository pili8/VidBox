package com.yuafeng.videoswiper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var loadingView: ProgressBar
    private lateinit var settings: SettingsManager

    private val videoList = mutableListOf<VideoItem>()
    private lateinit var adapter: VideoPagerAdapter

    private val PREFETCH_THRESHOLD = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        loadingView = findViewById(R.id.loadingView)

        requestPerm()
        setupPager()
    }

    override fun onResume() {
        super.onResume()
        // 每次回来都检查源，从设置页返回后可能变了
        if (videoList.isEmpty()) {
            checkAndLoad()
        }
    }

    private fun checkAndLoad() {
        val src = settings.getEnabledSources()
        if (src.isEmpty()) {
            // 没有可用源，自动跳设置
            Toast.makeText(this, "请先添加并启用视频源", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        } else {
            loadInitial()
        }
    }

    private fun requestPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_VIDEO), 1001)
            }
        }
    }

    private fun setupPager() {
        adapter = VideoPagerAdapter(this, videoList)
        viewPager.adapter = adapter
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager.offscreenPageLimit = 1

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(pos: Int) {
                if (pos >= videoList.size - PREFETCH_THRESHOLD) loadMore()
            }
        })
    }

    private fun loadInitial() {
        loadingView.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val src = settings.getEnabledSources()
                val videos = VideoApi.prefetch(6, src)
                if (videos.isNotEmpty()) {
                    videoList.addAll(videos)
                    adapter.notifyItemRangeInserted(0, videos.size)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                loadingView.visibility = View.GONE
            }
        }
    }

    private fun loadMore() {
        lifecycleScope.launch {
            val src = settings.getEnabledSources()
            val more = VideoApi.prefetch(3, src)
            if (more.isNotEmpty()) adapter.addVideos(more)
        }
    }
}
