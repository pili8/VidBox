package com.yuafeng.videoswiper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoPlayerFragment : Fragment() {

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TITLE = "title"
        private const val ARG_SOURCE = "source"

        fun newInstance(v: VideoItem) = VideoPlayerFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_URL, v.url)
                putString(ARG_TITLE, v.title)
                putString(ARG_SOURCE, v.source)
            }
        }
    }

    private var player: ExoPlayer? = null
    private var isPrepared = false
    private lateinit var playerView: PlayerView
    private lateinit var loading: ProgressBar
    private lateinit var pauseIcon: ImageView
    private lateinit var tvTime: TextView
    private lateinit var tvSource: TextView
    private lateinit var tvDivider: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnMute: ImageView
    private lateinit var btnDownload: ImageView
    private lateinit var btnSettings: ImageView

    private var videoUrl = ""
    private var videoTitle = ""
    private var videoSource = ""
    private var pausedByUser = false
    private var isMuted = true

    // 长按
    private val LONG_PRESS_MS = 1000L
    private var longPressRunnable: Runnable? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isLongPress = false
    private var downX = 0f
    private var downY = 0f
    private val MOVE_THRESHOLD = 30f // 移动超过 30px 才算滑动

    // 进度更新
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isAdded) return
            player?.let { p ->
                val pos = p.currentPosition
                val dur = p.duration.coerceAtLeast(1)
                tvTime.text = "${fmtTime(pos)} / ${fmtTime(dur)}"
                progressBar.progress = ((pos * 1000L) / dur).toInt()
            }
            progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            videoUrl = it.getString(ARG_URL, "")
            videoTitle = it.getString(ARG_TITLE, "")
            videoSource = it.getString(ARG_SOURCE, "")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View =
        inflater.inflate(R.layout.fragment_video_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playerView = view.findViewById(R.id.playerView)
        loading = view.findViewById(R.id.loadingIndicator)
        pauseIcon = view.findViewById(R.id.pauseIcon)
        tvTime = view.findViewById(R.id.tvTime)
        tvSource = view.findViewById(R.id.tvSource)
        tvDivider = view.findViewById(R.id.tvDivider)
        progressBar = view.findViewById(R.id.progressBar)
        btnMute = view.findViewById(R.id.btnMute)
        btnDownload = view.findViewById(R.id.btnDownload)
        btnSettings = view.findViewById(R.id.btnSettings)

        if (videoSource.isNotEmpty()) {
            tvSource.text = videoSource
            tvDivider.visibility = View.VISIBLE
        }

        isMuted = SettingsManager(requireContext()).muted
        updateMuteIcon()

        btnMute.setOnClickListener { toggleMute() }
        btnDownload.setOnClickListener {
            context?.let { DownloadHelper.downloadVideo(it, videoUrl, videoTitle) }
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // 按钮半透明 hover
        arrayOf(btnMute, btnDownload, btnSettings).forEach { btn ->
            btn.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> v.alpha = 1.0f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 0.4f
                }
                false
            }
            btn.alpha = 0.4f
        }

        setupTouch()

        // ★ 关键：创建时就开始加载视频数据，不等 onResume
        initPlayer(prepareOnly = true)
    }

    override fun onResume() {
        super.onResume()
        if (isAdded) {
            isMuted = SettingsManager(requireContext()).muted
            player?.volume = if (isMuted) 0f else 1f
            updateMuteIcon()
        }
        // 已经在 onViewCreated 初始化了，这里只管播放
        player?.playWhenReady = !pausedByUser
        progressHandler.post(progressRunnable)
    }

    override fun onPause() {
        super.onPause()
        progressHandler.removeCallbacks(progressRunnable)
        cancelLongPress()
        // 只暂停，不释放播放器（保留缓冲数据）
        player?.playWhenReady = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressHandler.removeCallbacks(progressRunnable)
        cancelLongPress()
        releasePlayer()
    }

    // ========== 手势 ==========

    private fun setupTouch() {
        playerView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPress = false
                    downX = e.rawX
                    downY = e.rawY
                    longPressRunnable = Runnable {
                        isLongPress = true
                        vibrate()
                        context?.let { DownloadHelper.downloadVideo(it, videoUrl, videoTitle) }
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    cancelLongPress()
                    if (!isLongPress) togglePause()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 只有移动超过阈值才取消长按（区分长按和滑动）
                    val dx = Math.abs(e.rawX - downX)
                    val dy = Math.abs(e.rawY - downY)
                    if (dx > MOVE_THRESHOLD || dy > MOVE_THRESHOLD) {
                        cancelLongPress()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    true
                }
                else -> false
            }
        }
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun vibrate() {
        try {
            val vib = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vib.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    private fun togglePause() {
        player?.let {
            if (it.isPlaying) {
                it.pause(); pausedByUser = true; pauseIcon.visibility = View.VISIBLE
            } else {
                it.play(); pausedByUser = false; pauseIcon.visibility = View.GONE
            }
        }
    }

    // ========== 静音 ==========

    private fun toggleMute() {
        isMuted = !isMuted
        player?.volume = if (isMuted) 0f else 1f
        SettingsManager(requireContext()).muted = isMuted
        updateMuteIcon()
    }

    private fun updateMuteIcon() {
        btnMute.setImageResource(
            if (isMuted) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    // ========== 播放器 ==========

    /**
     * @param prepareOnly true = 只 prepare 不播放（预加载），false = prepare + play
     */
    private fun initPlayer(prepareOnly: Boolean = false) {
        if (player != null || !isAdded || videoUrl.isEmpty()) return
        val ctx = context ?: return

        player = ExoPlayer.Builder(ctx).build().apply {
            playerView.player = this
            setMediaItem(MediaItem.fromUri(videoUrl))
            volume = if (isMuted) 0f else 1f
            // 预加载：prepare 会自动开始缓冲，不管 playWhenReady
            prepare()
            playWhenReady = if (prepareOnly) false else !pausedByUser
            repeatMode = Player.REPEAT_MODE_ALL

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (!isAdded) return
                    when (state) {
                        Player.STATE_BUFFERING -> loading.visibility = View.VISIBLE
                        Player.STATE_READY -> {
                            loading.visibility = View.GONE
                            isPrepared = true
                        }
                    }
                }
            })
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        isPrepared = false
    }

    private fun fmtTime(ms: Long): String {
        val totalSec = ms / 1000
        return "%02d:%02d".format(totalSec / 60, totalSec % 60)
    }
}
