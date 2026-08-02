package com.yuafeng.videoswiper

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.File

object DownloadHelper {

    fun downloadVideo(context: Context, videoUrl: String, title: String = "视频") {
        try {
            val settings = SettingsManager(context)
            val dir = File(settings.downloadDir)
            if (!dir.exists()) dir.mkdirs()

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "VideoSwiper_${System.currentTimeMillis()}.mp4"

            val req = DownloadManager.Request(Uri.parse(videoUrl))
                .setTitle("下载视频")
                .setDescription("正在下载: $title")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationUri(Uri.fromFile(File(dir, fileName)))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            dm.enqueue(req)
            val short = settings.downloadDir
                .substringAfterLast("/Movies/").ifEmpty { settings.downloadDir.substringAfterLast("/") }
            Toast.makeText(context, "已保存到 $short/", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
