package com.astra.video

import android.content.Context
import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

object DownloadEngine {
    private val slots = Semaphore(3)

    fun outputDirectory(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "Movies")
        return File(base, "Astra Video").apply { mkdirs() }
    }

    suspend fun download(
        context: Context,
        sourceUrl: String,
        processId: String,
        userAgent: String?,
        cookies: String?,
        referer: String?,
        audioOnly: Boolean = false,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        slots.withPermit {
            val directory = outputDirectory(context)
            val before = directory.listFiles()?.map { it.name }?.toSet().orEmpty()
            val request = YoutubeDLRequest(sourceUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--continue")
            request.addOption("--newline")
            request.addOption("--retries", "5")
            request.addOption("--fragment-retries", "5")
            request.addOption("--concurrent-fragments", "4")
            if (userAgent.isNullOrBlank().not()) request.addOption("--add-headers", "User-Agent: " + userAgent)
            if (cookies.isNullOrBlank().not()) request.addOption("--add-headers", "Cookie: " + cookies)
            if (referer.isNullOrBlank().not()) request.addOption("--add-headers", "Referer: " + referer)
            if (audioOnly) {
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
            } else {
                request.addOption("-f", "bestvideo*+bestaudio/best")
            }
            request.addOption("-o", File(directory, "%(title).160B.%(ext)s").absolutePath)

            YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                onProgress(progress.coerceIn(0f, 100f), line.takeLast(220))
            }

            directory.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.filter { it.name !in before || it.lastModified() >= System.currentTimeMillis() - 180_000 }
                ?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("Загрузчик завершился, но готовый файл не найден")
        }
    }

    fun stop(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    fun listFiles(context: Context): List<File> =
        outputDirectory(context).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
}
