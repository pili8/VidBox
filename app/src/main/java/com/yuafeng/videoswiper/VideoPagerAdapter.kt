package com.yuafeng.videoswiper

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager2 适配器
 * 管理视频 Fragment 的创建和回收
 */
class VideoPagerAdapter(
    activity: FragmentActivity,
    private val videos: MutableList<VideoItem>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = videos.size

    override fun createFragment(position: Int): Fragment {
        return VideoPlayerFragment.newInstance(videos[position])
    }

    /**
     * 添加更多视频（无限滚动用）
     */
    fun addVideos(newVideos: List<VideoItem>) {
        val startPos = videos.size
        videos.addAll(newVideos)
        notifyItemRangeInserted(startPos, newVideos.size)
    }

    /**
     * 获取视频列表
     */
    fun getVideos(): List<VideoItem> = videos
}
