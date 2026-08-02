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
 * - 滚动时只显示占位符，停止后才播放（避免快速创建/释放）
 * - 不设硬上限，靠 RecyclerView 回收机制自然控制播放器数量
 * - 每次 bind 先释放旧播放器，避免残留黑屏
 */
class GridAdapter(
    private val videos: List<VideoItem>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<GridAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val playerView: PlayerView = v.findViewById(R.id.gridPlayerView)
        val placeholder: View = v.findViewById(R.id.gridPlaceholder)
        var player: ExoPlayer? = null
    }

    private var isScrolling = false

    override fun getItemCount() = videos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid_video, parent, false)
        val w = parent.width / 2
        if (w > 0) {
            view.layoutParams = ViewGroup.LayoutParams(w, w)
        } else {
            view.post {
                val pw = parent.width / 2
                if (pw > 0) view.layoutParams = ViewGroup.LayoutParams(pw, pw)
            }
        }
        return VH(view)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        // 先释放旧的（ViewHolder 复用时避免残留播放器/黑屏）
        releasePlayer(h)
        h.placeholder.visibility = View.VISIBLE

        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }

        // 滚动中不创建播放器，停止后才播放
        if (!isScrolling) {
            startPlayback(h, pos)
        }
    }

    override fun onViewRecycled(h: VH) {
        super.onViewRecycled(h)
        releasePlayer(h)
    }

    fun onScrollStateChanged(newState: Int) {
        val wasScrolling = isScrolling
        isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
        if (wasScrolling && !isScrolling) {
            // 刚停止滚动 → 刷新可见项，触发播放
            notifyDataSetChanged()
        }
    }

    private fun startPlayback(h: VH, pos: Int) {
        val ctx = h.itemView.context ?: return
        if (pos < 0 || pos >= videos.size) return

        h.player = ExoPlayer.Builder(ctx).build().apply {
            h.playerView.player = this
            setMediaItem(MediaItem.fromUri(videos[pos].url))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true

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
    }

    fun releaseAllPlayers(rv: RecyclerView) {
        for (i in 0 until rv.childCount) {
            (rv.getChildViewHolder(rv.getChildAt(i)) as? VH)?.let { releasePlayer(it) }
        }
    }
}
