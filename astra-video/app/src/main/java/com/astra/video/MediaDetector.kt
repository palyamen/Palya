package com.astra.video

import android.net.Uri

data class MediaCandidate(
    val url: String,
    val kind: String
)

object MediaDetector {
    private val extensionKinds = mapOf(
        ".m3u8" to "HLS-поток",
        ".mpd" to "DASH-поток",
        ".mp3" to "Аудио",
        ".m4a" to "Аудио",
        ".aac" to "Аудио",
        ".ogg" to "Аудио",
        ".wav" to "Аудио",
        ".mp4" to "Видеофайл",
        ".m4v" to "Видеофайл",
        ".webm" to "Видеофайл",
        ".mkv" to "Видеофайл",
        ".mov" to "Видеофайл"
    )

    fun detect(rawUrl: String): MediaCandidate? {
        if (!rawUrl.startsWith("http://", ignoreCase = true) &&
            !rawUrl.startsWith("https://", ignoreCase = true)
        ) return null

        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
        val path = uri.encodedPath.orEmpty().lowercase()
        val extension = extensionKinds.keys.firstOrNull { path.endsWith(it) }
        if (extension != null) return MediaCandidate(rawUrl, extensionKinds.getValue(extension))

        // Некоторые серверы отдают поток по URL без расширения, указывая MIME в query.
        val mime = sequenceOf("mime", "mime_type", "content_type", "type", "format")
            .mapNotNull { key -> uri.getQueryParameter(key)?.lowercase() }
            .firstOrNull { value ->
                value.contains("video/") || value.contains("audio/") ||
                    value.contains("mpegurl") || value.contains("dash+xml")
            } ?: return null

        val kind = when {
            mime.contains("mpegurl") -> "HLS-поток"
            mime.contains("dash+xml") -> "DASH-поток"
            mime.startsWith("audio/") -> "Аудио"
            mime.startsWith("video/") -> "Видеопоток"
            else -> return null
        }
        return MediaCandidate(rawUrl, kind)
    }
}
