package com.yuafeng.videoswiper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

/**
 * 平铺模式适配器
 * 优化：只在完全可见且稳定时播放，避免同时创建过多播放器
 */
class GridAdapter(
    private val videos: List<VideoItem>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<GridAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val playerView: PlayerView = v.findViewById(R.id.gridPlayerView)
        val placeholder: View = v.findViewById(R.id.gridPlaceholder)
        var player: ExoPlayer? = null
        var isPlaying = false
    }

    private var isScrolling = false
    private val activePlayers = mutableSetOf<Int>()
    private val MAX_CONCURRENT_PLAYERS = 4

    override fun getItemCount() = videos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid_video, parent, false)
        // 正方形：用父容器宽度 / 2 计算（2列网格）
        val w = parent.width / 2
        if (w > 0) {
            view.layoutParams = ViewGroup.LayoutParams(w, w)
        } else {
            // 父容器还没布局完，用 post 等一次
            view.post {
                val pw = parent.width / 2
                if (pw > 0) {
                    view.layoutParams = ViewGroup.LayoutParams(pw, pw)
                }
            }
        }
        return VH(view)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        
        // 初始显示占位符
        h.placeholder.visibility = View.VISIBLE
        h.isPlaying = false
        
        // 如果不在滚动且位置合理，尝试播放
        if (!isScrolling && shouldPlay(pos)) {
            startPlayback(h, pos)
        }
    }

    override fun onViewRecycled(h: VH) {
        super.onViewRecycled(h)
        val pos = h.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            activePlayers.remove(pos)
        }
        releasePlayer(h)
    }

    fun onScrollStateChanged(newState: Int) {
        isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
        if (!isScrolling) {
            // 滚动停止，刷新以触发播放
            notifyDataSetChanged()
        }
    }

    private fun shouldPlay(pos: Int): Boolean {
        // 限制同时播放的数量
        return activePlayers.size < MAX_CONCURRENT_PLAYERS && !activePlayers.contains(pos)
    }

    private fun startPlayback(h: VH, pos: Int) {
        val ctx = h.itemView.context ?: return
        
        // 如果已经有播放器在播放这个位置，跳过
        if (activePlayers.contains(pos)) return
        
        activePlayers.add(pos)
        h.placeholder.visibility = View.VISIBLE

        h.player = ExoPlayer.Builder(ctx).build().apply {
            h.playerView.player = this
            setMediaItem(MediaItem.fromUri(videos[pos].url))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
            h.isPlaying = true
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        h.placeholder.visibility = View.GONE
                    }
                }
            })
        }
    }

    private fun releasePlayer(h: VH) {
        h.player?.release()
        h.player = null
        h.playerView.player = null
        h.isPlaying = false
    }

    /**
     * 释放所有播放器（切换模式时调用）
     */
    fun releaseAllPlayers(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i)) as? VH
            holder?.let { releasePlayer(it) }
        }
        activePlayers.clear()
    }
}
