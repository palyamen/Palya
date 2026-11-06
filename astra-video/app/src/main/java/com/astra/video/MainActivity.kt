package com.astra.video

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.ffmpeg.FFmpeg
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView

private val Back = Color(0xFF0B0D12)
private val Panel = Color(0xFF171B25)
private val Accent = Color(0xFFFF3158)
private val Muted = Color(0xFF9299A8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(11, 13, 18)
        window.navigationBarColor = AndroidColor.rgb(11, 13, 18)
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            Toast.makeText(this, "Модуль загрузки видео не запустился: ${e.message}", Toast.LENGTH_LONG).show()
        }
        setContent { AstraVideoApp() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AstraVideoApp() {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var shareUrl by remember { mutableStateOf("") }
    var showAppHeader by remember { mutableStateOf(true) }

    LaunchedEffect(tab) {
        showAppHeader = true
        kotlinx.coroutines.delay(4000)
        showAppHeader = false
    }

    BackHandler(enabled = tab != 0) {
        tab = 0
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Back, surface = Panel, primary = Accent,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Back,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Color.White) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Search, null) }, label = { Text("Поиск") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.PlayCircle, null) }, label = { Text("Плеер") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Download, null) }, label = { Text("Загрузки") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (showAppHeader) Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).background(Accent, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ASTRA VIDEO", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Видео рядом", fontSize = 11.sp, color = Muted)
                    }
                }

                when (tab) {
                    0 -> {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .background(Panel, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = Muted)
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = query, onValueChange = { query = it },
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) Text("Найти видео на YouTube", color = Muted)
                                    inner()
                                }
                            )
                            IconButton(onClick = {
                                if (query.isNotBlank()) {
                                    pageUrl = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
                                }
                            }) { Icon(Icons.Default.ArrowForward, "Искать", tint = Accent) }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (pageUrl.isNotEmpty()) {
                            YoutubeWebView(url = pageUrl, onVideo = { url ->
                                videoUrl = url
                                val id = Uri.parse(url).lastPathSegment
                                shareUrl = if (!id.isNullOrBlank()) "https://www.youtube.com/watch?v=$id" else url
                                tab = 1
                            }, modifier = Modifier.weight(1f))
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.OndemandVideo, null, tint = Accent, modifier = Modifier.size(64.dp))
                                    Spacer(Modifier.height(14.dp))
                                    Text("Ищи видео и смотри внутри приложения", color = Color.White, fontSize = 16.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Введи название в строку поиска", color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    1 -> {
                        if (videoUrl.isNotBlank()) {
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                        .background(Panel, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = shareUrl.ifBlank { videoUrl },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val context = LocalContext.current
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Ссылка на видео", shareUrl.ifBlank { videoUrl }))
                                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, "Скопировать ссылку", tint = Accent)
                                    }
                                }
                                YoutubeWebView(url = videoUrl, onVideo = { url ->
                                    videoUrl = url
                                    val id = Uri.parse(url).lastPathSegment
                                    shareUrl = if (!id.isNullOrBlank()) "https://www.youtube.com/watch?v=$id" else url
                                }, modifier = Modifier.weight(1f))
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlayCircle, null, tint = Accent, modifier = Modifier.size(70.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("Твой видеоплеер", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Выбери ролик в поиске YouTube", color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    else -> DownloadsScreen()
                }
            }
        }
    }
}

private data class DownloadItem(
    val id: Long, val title: String, val status: String,
    val downloaded: Long, val total: Long, val mime: String
)

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "размер неизвестен"
    if (bytes < 1024) return "$bytes Б"
    if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f КБ", bytes / 1024.0)
    if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0))
    return String.format(Locale.getDefault(), "%.2f ГБ", bytes / (1024.0 * 1024.0 * 1024.0))
}

@Composable
private fun DownloadsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var extractorProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) { delay(1200); refreshKey++ }
    }

    val downloads = remember(refreshKey) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val result = mutableListOf<DownloadItem>()
        val cursor = manager.query(DownloadManager.Query())
        cursor?.use {
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val totalCol = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val doneCol = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val mimeCol = it.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
            while (it.moveToNext()) {
                val status = when (it.getInt(statusCol)) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Готово"
                    DownloadManager.STATUS_FAILED -> "Ошибка загрузки"
                    DownloadManager.STATUS_RUNNING -> "Загружается"
                    DownloadManager.STATUS_PENDING -> "В очереди"
                    DownloadManager.STATUS_PAUSED -> "Приостановлено"
                    else -> "Неизвестно"
                }
                result.add(DownloadItem(
                    it.getLong(idCol), it.getString(titleCol) ?: "Файл", status,
                    if (doneCol >= 0) it.getLong(doneCol) else 0L,
                    if (totalCol >= 0) it.getLong(totalCol) else -1L,
                    if (mimeCol >= 0) it.getString(mimeCol).orEmpty() else ""
                ))
            }
        }
        result.reversed()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Загрузки", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Вставь ссылку YouTube (включая m.youtube.com) или прямую ссылку на медиафайл.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(18.dp))
        Text("Ссылка на видео", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InputPanel(value = url, onValueChange = { url = it }, hint = "https://m.youtube.com/watch?v=...")
        Spacer(Modifier.height(12.dp))
        Text("Имя файла (необязательно)", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InputPanel(value = fileName, onValueChange = { fileName = it }, hint = "Моё видео.mp4")
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val rawInput = url.trim()
                val enteredUrl = if (rawInput.startsWith("https://", true) || rawInput.startsWith("http://", true)) rawInput else "https://$rawInput"
                val parsed = Uri.parse(enteredUrl)
                if ((parsed.scheme == "https" || parsed.scheme == "http") && !parsed.host.isNullOrBlank()) {
                    scope.launch {
                        checking = true
                        val host = parsed.host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.").removePrefix("m.")
                        val normalizedUrl = enteredUrl.lowercase(Locale.ROOT)
                        val isYouTube = host == "youtu.be" || host.endsWith(".youtu.be") ||
                            host == "youtube.com" || host.endsWith(".youtube.com") ||
                            normalizedUrl.contains("youtube.com/") || normalizedUrl.contains("youtu.be/")
                        if (isYouTube) {
                            message = "Распознала YouTube. Получаю варианты видео…"
                            extractorProgress = 0f
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Astra Video")
                                    if (!dir.exists()) dir.mkdirs()
                                    val request = YoutubeDLRequest(enteredUrl)
                                    request.addOption("--no-playlist")
                                    request.addOption("--no-mtime")
                                    request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                                    request.addOption("--merge-output-format", "mp4")
                                    request.addOption("-o", dir.absolutePath + "/%(title).180B.%(ext)s")
                                    YoutubeDL.getInstance().execute(request, "astra-video-download") { progress, _, line ->
                                        extractorProgress = progress.coerceIn(0f, 100f)
                                        message = if (line.isNotBlank()) line.takeLast(180) else "Скачиваю видео…"
                                    }
                                    "Готово! Файл сохранён в Загрузки/Astra Video."
                                } catch (e: Exception) {
                                    "Не удалось скачать видео: " + (e.message ?: "ошибка извлечения").take(220)
                                }
                            }
                            message = result
                            checking = false
                            refreshKey++
                        } else {
                        message = "Проверяю прямую ссылку и тип файла…"
                        val check = withContext(Dispatchers.IO) {
                            try {
                                val connection = (URL(enteredUrl).openConnection() as HttpURLConnection).apply {
                                    requestMethod = "HEAD"
                                    connectTimeout = 12000
                                    readTimeout = 12000
                                    instanceFollowRedirects = true
                                    setRequestProperty("User-Agent", "Mozilla/5.0 AstraVideo")
                                }
                                val code = connection.responseCode
                                val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
                                val length = connection.contentLengthLong
                                connection.disconnect()
                                Triple(code, mime, length)
                            } catch (_: Exception) { Triple(-1, "", -1L) }
                        }
                        val (code, mime, length) = check
                        val pathPart = Uri.parse(enteredUrl).lastPathSegment.orEmpty().lowercase(Locale.ROOT)
                        val mediaExtension = listOf(".mp4", ".m4v", ".webm", ".mkv", ".mov", ".mp3", ".m4a", ".aac", ".ogg", ".wav").any { pathPart.endsWith(it) }
                        val isMedia = mime.startsWith("video/") || mime.startsWith("audio/") ||
                            mime == "application/octet-stream" && mediaExtension
                        if (code in 200..299 && (mime == "text/html" || mime == "application/json" || mime.startsWith("text/"))) {
                            message = "Это веб-страница, а не видеофайл. Нужна прямая ссылка на .mp4 или другой медиафайл."
                        } else if (code in 200..299 && !isMedia && !mediaExtension) {
                            message = "Сервер не подтвердил, что по ссылке находится видео или аудио. Загрузка отменена."
                        } else if (code in 200..299 || code == -1 && mediaExtension) {
                            try {
                                val guessed = parsed.lastPathSegment?.takeIf { it.contains('.') } ?: ("astra_video_" + System.currentTimeMillis() + ".mp4")
                                val safeName = fileName.trim().ifBlank { guessed }.map { if (it.isLetterOrDigit() || it in " ._-") it else '_' }.joinToString("")
                                val request = DownloadManager.Request(parsed)
                                    .setTitle(safeName)
                                    .setDescription("Astra Video • " + if (length > 0) formatBytes(length) else "размер уточняется")
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setAllowedOverMetered(true)
                                    .setAllowedOverRoaming(false)
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                                if (mime.isNotBlank()) request.setMimeType(mime)
                                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                manager.enqueue(request)
                                message = "Загрузка началась • " + if (length > 0) formatBytes(length) else "размер неизвестен"
                                url = ""
                                fileName = ""
                                refreshKey++
                            } catch (_: Exception) {
                                message = "Не удалось начать загрузку. Проверь ссылку и свободное место."
                            }
                        } else {
                            message = "Сервер вернул ошибку HTTP $code. Проверь ссылку."
                        }
                        checking = false
                        }
                    }
                } else { message = "Введи корректную http/https-ссылку." }
            },
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Проверяю ссылку…")
            } else {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Проверить и скачать")
            }
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            if (extractorProgress > 0f && extractorProgress < 100f) {
                LinearProgressIndicator(progress = { extractorProgress / 100f }, modifier = Modifier.fillMaxWidth(), color = Accent)
                Text("YouTube: ${extractorProgress.toInt()}%", color = Muted, fontSize = 11.sp)
            }
            Text(message, color = if (message.contains("отменена") || message.contains("ошибку") || message.contains("страница")) Accent else Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Мои загрузки", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, "Обновить", tint = Accent) }
        }
        if (downloads.isEmpty()) {
            Text("Пока ничего не загружено", color = Muted, fontSize = 13.sp)
        } else {
            downloads.forEach { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Panel, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = Accent)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontSize = 14.sp, maxLines = 2)
                            Text(item.status, color = if (item.status == "Ошибка загрузки") Accent else Muted, fontSize = 12.sp)
                        }
                        Text(if (item.total > 0) formatBytes(item.downloaded) + " / " + formatBytes(item.total) else formatBytes(item.downloaded), color = Muted, fontSize = 11.sp)
                    }
                    if (item.status == "Загружается" && item.total > 0) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (item.downloaded.toFloat() / item.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(), color = Accent
                        )
                    }
                    if (item.status == "Готово") {
                        Spacer(Modifier.height(6.dp))
                        Text("Тип: " + item.mime.ifBlank { "не определён" } + " • Размер: " + formatBytes(item.total), color = Muted, fontSize = 11.sp)
                        TextButton(onClick = {
                            try {
                                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                val fileUri = manager.getUriForDownloadedFile(item.id)
                                if (fileUri == null) {
                                    Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(fileUri, item.mime.ifBlank { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Открыть файл"))
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, "Не найдено приложение для открытия файла", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, null, tint = Accent)
                            Spacer(Modifier.width(5.dp))
                            Text("Открыть файл", color = Accent)
                        }
                    }
                    if (item.status == "Ошибка загрузки") {
                        Text("Загрузка не завершилась. Попробуй другую прямую ссылку.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("YouTube-ссылки обрабатываются встроенным yt-dlp. Скачивай только материалы, на которые у тебя есть права или разрешение владельца. Прямые ссылки на MP4/WebM по-прежнему поддерживаются.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun InputPanel(value: String, onValueChange: (String) -> Unit, hint: String) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, color = Muted, fontSize = 14.sp)
                inner()
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YoutubeWebView(url: String, onVideo: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.userAgentString = settings.userAgentString + " AstraVideo/1.1"
                webChromeClient = AstraChromeClient(context)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        val host = uri.host.orEmpty()
                        val id = when {
                            host.contains("youtu.be") -> uri.lastPathSegment
                            uri.getQueryParameter("v") != null -> uri.getQueryParameter("v")
                            uri.pathSegments.contains("shorts") -> uri.pathSegments.getOrNull(uri.pathSegments.indexOf("shorts") + 1)
                            else -> null
                        }
                        if (!id.isNullOrBlank() && (host.contains("youtube.com") || host.contains("youtu.be"))) {
                            onVideo("https://www.youtube.com/embed/$id?autoplay=1&playsinline=1")
                            return true
                        }
                        return false
                    }
                }
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.tag != url) {
                view.tag = url
                view.loadUrl(url)
            }
        }
    )
}

private class AstraChromeClient(private val context: Context) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var originalSystemUi = 0
    private var originalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        val activity = context as? ComponentActivity ?: return
        val decor = activity.window.decorView as? FrameLayout ?: return
        customView = view
        customViewCallback = callback
        originalSystemUi = decor.systemUiVisibility
        originalOrientation = activity.requestedOrientation
        (view.parent as? ViewGroup)?.removeView(view)
        decor.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        decor.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun onHideCustomView() {
        val activity = context as? ComponentActivity
        val decor = activity?.window?.decorView as? FrameLayout
        customView?.let { decor?.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        if (activity != null) {
            decor?.systemUiVisibility = originalSystemUi
            activity.requestedOrientation = originalOrientation
        }
    }
}
