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
 * 每个格子：静音自动播放，循环
 * 滑出屏幕释放播放器
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

    override fun getItemCount() = videos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid_video, parent, false)
        // 正方形
        view.post {
            val w = view.width
            view.layoutParams.height = w
            view.requestLayout()
        }
        return VH(view)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        initPlayer(h, videos[pos].url)
    }

    override fun onViewRecycled(h: VH) {
        super.onViewRecycled(h)
        releasePlayer(h)
    }

    private fun initPlayer(h: VH, url: String) {
        val ctx = h.itemView.context ?: return
        releasePlayer(h)
        h.placeholder.visibility = View.VISIBLE

        h.player = ExoPlayer.Builder(ctx).build().apply {
            h.playerView.player = this
            setMediaItem(MediaItem.fromUri(url))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) h.placeholder.visibility = View.GONE
                }
            })
        }
    }

    private fun releasePlayer(h: VH) {
        h.player?.release()
        h.player = null
        h.playerView.player = null
    }
}
