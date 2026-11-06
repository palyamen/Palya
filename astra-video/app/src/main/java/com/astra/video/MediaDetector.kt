package com.astra.video

import android.net.Uri

data class MediaCandidate(
    val url: String,
    val kind: String
)

object MediaDetector {
    private val extensions = listOf(
        ".m3u8", ".mp4", ".m4v", ".webm", ".mkv", ".mov",
        ".mpd", ".mp3", ".m4a", ".aac", ".ogg", ".wav"
    )

    fun detect(rawUrl: String): MediaCandidate? {
        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) return null
        val path = runCatching { Uri.parse(rawUrl).encodedPath.orEmpty().lowercase() }.getOrDefault("")
        val extension = extensions.firstOrNull { path.endsWith(it) } ?: return null
        val kind = when (extension) {
            ".m3u8" -> "HLS-поток"
            ".mpd" -> "DASH-поток"
            ".mp3", ".m4a", ".aac", ".ogg", ".wav" -> "Аудио"
            else -> "Видеофайл"
        }
        return MediaCandidate(rawUrl, kind)
    }
}
