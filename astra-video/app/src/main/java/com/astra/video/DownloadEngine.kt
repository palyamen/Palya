package com.astra.video

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class DirectDownload(
    val id: Long,
    val title: String
)

object DownloadEngine {
    private val active = ConcurrentHashMap<String, Boolean>()

    fun outputDirectory(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "Movies")
        return File(base, "Astra Video").apply { mkdirs() }
    }

    suspend fun downloadPage(
        context: Context,
        pageUrl: String,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val directory = outputDirectory(context)
        val before = directory.listFiles()?.map { it.name }?.toSet().orEmpty()
        val processId = "astra-" + System.currentTimeMillis()
        active[processId] = true
        try {
            val request = YoutubeDLRequest(pageUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--continue")
            request.addOption("--newline")
            request.addOption("-f", "bestvideo*+bestaudio/best")
            request.addOption("-o", File(directory, "%(title).160B.%(ext)s").absolutePath)
            YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                onProgress(progress.coerceIn(0f, 100f), line.takeLast(220))
            }
            val completed = directory.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.filter { it.name !in before || it.lastModified() >= System.currentTimeMillis() - 120_000 }
                ?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("Загрузчик завершился, но файл не найден")
            completed
        } finally {
            active.remove(processId)
        }
    }

    fun enqueueDirect(
        context: Context,
        url: String,
        userAgent: String?,
        cookies: String?,
        referer: String?,
        mimeType: String?,
        contentDisposition: String?
    ): DirectDownload {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http") { "Неподдерживаемая ссылка" }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val suggested = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .ifBlank { "astra_video_" + System.currentTimeMillis() + ".mp4" }
        val safeName = suggested.map { if (it.isLetterOrDigit() || it in " ._-()") it else '_' }.joinToString("")
        val request = DownloadManager.Request(uri)
            .setTitle(safeName)
            .setDescription("Astra Video")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_MOVIES,
                "Astra Video/$safeName"
            )
        if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        if (!cookies.isNullOrBlank()) request.addRequestHeader("Cookie", cookies)
        if (!referer.isNullOrBlank()) request.addRequestHeader("Referer", referer)
        val id = manager.enqueue(request)
        return DirectDownload(id, safeName)
    }

    fun listFiles(context: Context): List<File> =
        outputDirectory(context).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun cancelDirect(context: Context, id: Long) {
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
    }
}
