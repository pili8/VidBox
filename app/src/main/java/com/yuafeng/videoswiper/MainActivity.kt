package com.yuafeng.videoswiper

import android.Manifest
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch

/**
 * 主界面
 * 两种视图模式：
 * 1. 全屏模式（默认）— ViewPager2 上下滑动
 * 2. 平铺模式 — 2列网格，点击回到全屏
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var rvGrid: RecyclerView
    private lateinit var loadingView: ProgressBar
    private lateinit var settings: SettingsManager

    private val videoList = mutableListOf<VideoItem>()
    private lateinit var pagerAdapter: VideoPagerAdapter
    private var gridAdapter: GridAdapter? = null

    private var isGridMode = false
    private val PREFETCH_THRESHOLD = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        rvGrid = findViewById(R.id.rvGrid)
        loadingView = findViewById(R.id.loadingView)

        requestPerm()
        setupPager()
        setupGrid()
    }

    override fun onResume() {
        super.onResume()
        if (videoList.isEmpty()) checkAndLoad()
    }

    // ========== 视图切换 ==========

    fun toggleGridMode() {
        if (isGridMode) {
            // 切回全屏模式
            isGridMode = false
            viewPager.visibility = View.VISIBLE
            rvGrid.visibility = View.GONE
        } else {
            // 切到平铺模式
            isGridMode = true
            viewPager.visibility = View.GONE
            rvGrid.visibility = View.VISIBLE
            if (gridAdapter == null) {
                gridAdapter = GridAdapter(videoList) { pos ->
                    // 点击格子 → 回到全屏模式，定位到该视频
                    isGridMode = false
                    viewPager.visibility = View.VISIBLE
                    rvGrid.visibility = View.GONE
                    viewPager.setCurrentItem(pos, false)
                }
                rvGrid.adapter = gridAdapter
            } else {
                gridAdapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onBackPressed() {
        if (isGridMode) {
            toggleGridMode()
        } else {
            super.onBackPressed()
        }
    }

    // ========== 数据加载 ==========

    private fun checkAndLoad() {
        val src = settings.getEnabledSources()
        if (src.isEmpty()) {
            Toast.makeText(this, "请先添加并启用视频源", Toast.LENGTH_SHORT).show()
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
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
        pagerAdapter = VideoPagerAdapter(this, videoList)
        viewPager.adapter = pagerAdapter
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager.offscreenPageLimit = 1

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(pos: Int) {
                if (pos >= videoList.size - PREFETCH_THRESHOLD) loadMore()
            }
        })
    }

    private fun setupGrid() {
        rvGrid.layoutManager = GridLayoutManager(this, 2)
        rvGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return // 只关心向下滑
                val lm = recyclerView.layoutManager as GridLayoutManager
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= videoList.size - PREFETCH_THRESHOLD) loadMore()
            }
        })
    }

    private fun loadInitial() {
        loadingView.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val src = settings.getEnabledSources()
                val videos = VideoApi.prefetch(8, src)
                if (videos.isNotEmpty()) {
                    videoList.addAll(videos)
                    pagerAdapter.notifyItemRangeInserted(0, videos.size)
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
            if (more.isNotEmpty()) {
                pagerAdapter.addVideos(more)
                gridAdapter?.notifyItemRangeInserted(videoList.size - more.size, more.size)
            }
        }
    }
}
