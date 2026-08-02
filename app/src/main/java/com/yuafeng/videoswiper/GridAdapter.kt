package com.yuafeng.videoswiper

import android.content.Context
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
 * 优化策略：
 * 1. 播放器池：最大 6 个，复用而非每次新建
 * 2. 生命周期管理：onViewAttached/Detached 控制播放
 * 3. 滚动时暂停，停止后才恢复
 */
class GridAdapter(
    private val videos: List<VideoItem>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<GridAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val playerView: PlayerView = v.findViewById(R.id.gridPlayerView)
        val placeholder: View = v.findViewById(R.id.gridPlaceholder)
    }

    private var isScrolling = false
    private val playerPool = mutableListOf<ExoPlayer>()
    private val positionPlayerMap = mutableMapOf<Int, ExoPlayer>()
    private val MAX_PLAYERS = 6

    override fun getItemCount() = videos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid_video, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        h.placeholder.visibility = View.VISIBLE
        h.playerView.player = null
        
        h.itemView.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
    }

    override fun onViewAttachedToWindow(h: VH) {
        super.onViewAttachedToWindow(h)
        val pos = h.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION || isScrolling) return
        attachPlayer(h, pos)
    }

    override fun onViewDetachedFromWindow(h: VH) {
        super.onViewDetachedFromWindow(h)
        val pos = h.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return
        detachPlayer(h, pos)
    }

    fun onScrollStateChanged(newState: Int) {
        isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
    }

    fun resumePlayback(rv: RecyclerView) {
        if (isScrolling) return
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            val holder = rv.getChildViewHolder(child) as? VH ?: continue
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                attachPlayer(holder, pos)
            }
        }
    }

    fun pauseAll(rv: RecyclerView) {
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            val holder = rv.getChildViewHolder(child) as? VH ?: continue
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                detachPlayer(holder, pos)
            }
        }
    }

    private fun attachPlayer(h: VH, pos: Int) {
        if (pos < 0 || pos >= videos.size) return
        if (positionPlayerMap.containsKey(pos)) return // 已有播放器

        val player = getOrCreatePlayer(h.itemView.context)
        if (player == null) {
            // 池满了，回收最旧的
            val oldestPos = positionPlayerMap.keys.firstOrNull()
            if (oldestPos != null) {
                val oldPlayer = positionPlayerMap.remove(oldestPos)
                oldPlayer?.let { 
                    returnPlayer(it)
                    positionPlayerMap[pos] = getOrCreatePlayer(h.itemView.context)!!
                }
            }
        } else {
            positionPlayerMap[pos] = player
        }

        val p = positionPlayerMap[pos] ?: return
        h.playerView.player = p
        
        p.apply {
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

    private fun detachPlayer(h: VH, pos: Int) {
        val player = positionPlayerMap.remove(pos)
        if (player != null) {
            player.stop()
            player.clearMediaItems()
            returnPlayer(player)
        }
        h.playerView.player = null
        h.placeholder.visibility = View.VISIBLE
    }

    private fun getOrCreatePlayer(ctx: Context): ExoPlayer? {
        // 从池中取空闲播放器
        for (p in playerPool) {
            if (!positionPlayerMap.values.contains(p)) {
                return p
            }
        }
        // 池未满，新建
        if (playerPool.size < MAX_PLAYERS) {
            val player = ExoPlayer.Builder(ctx).build()
            playerPool.add(player)
            return player
        }
        return null // 池满
    }

    private fun returnPlayer(player: ExoPlayer) {
        player.playWhenReady = false
    }

    fun releaseAllPlayers(rv: RecyclerView) {
        pauseAll(rv)
        playerPool.forEach { it.release() }
        playerPool.clear()
        positionPlayerMap.clear()
    }
}