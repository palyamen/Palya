package com.astra.video

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val AppBack = Color(0xFF0B0D12)
private val AppPanel = Color(0xFF171B25)
private val AppAccent = Color(0xFFFF3158)
private val AppMuted = Color(0xFF9299A8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 13, 18)
        window.navigationBarColor = android.graphics.Color.rgb(11, 13, 18)
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (error: YoutubeDLException) {
            Toast.makeText(this, "Модуль загрузки не запустился: " + error.message, Toast.LENGTH_LONG).show()
        }
        setContent { AstraVideoApp() }
    }
}

private class ActiveTask(val id: String, val title: String) {
    var progress by mutableFloatStateOf(0f)
    var status by mutableStateOf("В очереди")
    var detail by mutableStateOf("")
}

private data class SystemDownload(
    val id: Long,
    val title: String,
    val status: String,
    val done: Long,
    val total: Long
)

@Composable
private fun AstraVideoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var address by remember { mutableStateOf("https://www.google.com") }
    var currentPage by remember { mutableStateOf("https://www.google.com") }
    var pageTitle by remember { mutableStateOf("Главная") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val candidates = remember { mutableStateListOf<MediaCandidate>() }
    val tasks = remember { mutableStateListOf<ActiveTask>() }

    fun startPageDownload(page: String, title: String) {
        if (tasks.any { it.status == "Скачивается" && it.title == title }) return
        val task = ActiveTask(System.currentTimeMillis().toString(), title.ifBlank { "Видео со страницы" })
        tasks.add(0, task)
        scope.launch {
            task.status = "Анализирую страницу"
            try {
                val file = DownloadEngine.downloadPage(context, page) { progress, line ->
                    scope.launch(Dispatchers.Main) {
                        task.progress = progress
                        task.status = if (progress > 0f) "Скачивается" else "Анализирую страницу"
                        task.detail = line
                    }
                }
                task.progress = 100f
                task.status = "Готово"
                task.detail = file.name
                refreshKey++
                Toast.makeText(context, "Сохранено: " + file.name, Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                task.status = "Ошибка"
                task.detail = error.message ?: "Не удалось получить видео"
            }
        }
    }

    fun startDirectDownload(
        url: String,
        userAgent: String,
        cookies: String?,
        referer: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val path = Uri.parse(url).encodedPath.orEmpty().lowercase(Locale.ROOT)
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) {
            startPageDownload(url, "Потоковое видео")
            return
        }
        try {
            val item = DownloadEngine.enqueueDirect(
                context, url, userAgent, cookies, referer, mimeType, contentDisposition
            )
            Toast.makeText(context, "Загрузка добавлена: " + item.title, Toast.LENGTH_SHORT).show()
            refreshKey++
        } catch (error: Exception) {
            Toast.makeText(context, "Не удалось начать загрузку: " + error.message, Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = selectedFile != null || tab != 0) {
        if (selectedFile != null) selectedFile = null else tab = 0
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = AppBack,
        surface = AppPanel,
        primary = AppAccent,
        onBackground = Color.White,
        onSurface = Color.White
    )) {
        if (selectedFile != null) {
            VideoPlayerScreen(file = selectedFile!!, onBack = { selectedFile = null })
        } else {
            Scaffold(
                containerColor = AppBack,
                bottomBar = {
                    NavigationBar(containerColor = AppPanel, contentColor = Color.White) {
                        NavigationBarItem(
                            selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Language, null) }, label = { Text("Браузер") }
                        )
                        NavigationBarItem(
                            selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.Download, null) }, label = { Text("Загрузки") }
                        )
                        NavigationBarItem(
                            selected = tab == 2, onClick = { tab = 2 },
                            icon = { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Медиатека") }
                        )
                        NavigationBarItem(
                            selected = tab == 3, onClick = { tab = 3 },
                            icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Настройки") }
                        )
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    AppHeader()
                    when (tab) {
                        0 -> BrowserScreen(
                            address = address,
                            currentPage = currentPage,
                            pageTitle = pageTitle,
                            candidates = candidates,
                            onAddressChange = { address = it },
                            onNavigate = { input ->
                                val trimmed = input.trim()
                                val target = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                    trimmed
                                } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
                                    "https://" + trimmed
                                } else {
                                    "https://www.google.com/search?q=" + Uri.encode(trimmed)
                                }
                                address = target
                                currentPage = target
                                candidates.clear()
                            },
                            onPageChanged = { url, title ->
                                if (url.isNotBlank()) {
                                    currentPage = url
                                    address = url
                                }
                                if (title.isNotBlank()) pageTitle = title
                            },
                            onMediaFound = { candidate ->
                                if (candidates.none { it.url == candidate.url }) candidates.add(candidate)
                            },
                            onDownloadPage = { startPageDownload(currentPage, pageTitle) },
                            onDownloadCandidate = { candidate ->
                                if (candidate.kind.contains("поток")) {
                                    startPageDownload(candidate.url, candidate.kind)
                                } else {
                                    startDirectDownload(
                                        candidate.url, "Mozilla/5.0 AstraVideo",
                                        CookieManager.getInstance().getCookie(candidate.url),
                                        currentPage, null, null
                                    )
                                }
                            },
                            onDirectDownload = { url, agent, cookies, referer, disposition, mime ->
                                startDirectDownload(url, agent, cookies, referer, disposition, mime)
                            }
                        )
                        1 -> DownloadsScreen(tasks, refreshKey, onRefresh = { refreshKey++ })
                        2 -> LibraryScreen(refreshKey) { selectedFile = it }
                        else -> SettingsScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).background(AppAccent, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("ASTRA VIDEO", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("Браузер и загрузчик видео", fontSize = 11.sp, color = AppMuted)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(
    address: String,
    currentPage: String,
    pageTitle: String,
    candidates: List<MediaCandidate>,
    onAddressChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onPageChanged: (String, String) -> Unit,
    onMediaFound: (MediaCandidate) -> Unit,
    onDownloadPage: () -> Unit,
    onDownloadCandidate: (MediaCandidate) -> Unit,
    onDirectDownload: (String, String, String?, String?, String?, String?) -> Unit
) {
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) {
                Icon(Icons.Default.ArrowForward, "Вперёд", tint = Color.White)
            }
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                placeholder = { Text("Адрес сайта или поиск", fontSize = 12.sp) },
                shape = RoundedCornerShape(14.dp)
            )
            IconButton(onClick = { onNavigate(address) }) {
                Icon(Icons.Default.Search, "Перейти", tint = AppAccent)
            }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = AppAccent
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(pageTitle.ifBlank { "Страница" }, color = Color.White, fontSize = 13.sp, maxLines = 1)
                Text("Найдено медиа: " + candidates.size, color = AppMuted, fontSize = 11.sp)
            }
            TextButton(onClick = onDownloadPage) {
                Icon(Icons.Default.Download, null, tint = AppAccent)
                Spacer(Modifier.width(4.dp))
                Text("Скачать страницу", color = AppAccent, fontSize = 12.sp)
            }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                android.webkit.WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    CookieManager.getInstance().setAcceptCookie(true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: android.webkit.WebView, newProgress: Int) {
                            progress = newProgress
                        }
                        override fun onReceivedTitle(view: android.webkit.WebView, title: String?) {
                            onPageChanged(view.url.orEmpty(), title.orEmpty())
                        }
                    }
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: android.webkit.WebView,
                            request: android.webkit.WebResourceRequest
                        ): Boolean = false

                        override fun onPageStarted(
                            view: android.webkit.WebView,
                            url: String,
                            favicon: android.graphics.Bitmap?
                        ) {
                            onPageChanged(url, view.title.orEmpty())
                        }

                        override fun onLoadResource(view: android.webkit.WebView, url: String) {
                            MediaDetector.detect(url)?.let(onMediaFound)
                        }

                        override fun onPageFinished(view: android.webkit.WebView, url: String) {
                            onPageChanged(url, view.title.orEmpty())
                            val script = """
                                (function() {
                                  var re = /\.(mp4|m4v|webm|mkv|mov|m3u8|mpd|mp3|m4a|aac|ogg|wav)(?:[?#]|$)/i;
                                  return JSON.stringify(performance.getEntriesByType('resource')
                                    .map(function(e) { return e.name; })
                                    .filter(function(name) { return re.test(name); }));
                                })();
                            """.trimIndent()
                            evaluateJavascript(script) { result ->
                                runCatching {
                                    val json = org.json.JSONTokener(result).nextValue() as? String ?: return@runCatching
                                    val array = org.json.JSONArray(json)
                                    for (index in 0 until array.length()) {
                                        MediaDetector.detect(array.optString(index))?.let(onMediaFound)
                                    }
                                }
                            }
                        }
                    }
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                        val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                        onDirectDownload(
                            downloadUrl, userAgent.orEmpty(), cookies, url,
                            contentDisposition, mimeType
                        )
                    }
                    tag = currentPage
                    loadUrl(currentPage)
                }
            },
            update = { view ->
                if (view.tag != currentPage) {
                    view.tag = currentPage
                    view.loadUrl(currentPage)
                }
            }
        )
        if (candidates.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 170.dp)
                    .background(AppPanel).padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Обнаруженные файлы", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                LazyColumn {
                    items(candidates.take(8), key = { it.url }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onDownloadCandidate(item) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (item.kind.contains("Аудио")) Icons.Default.AudioFile else Icons.Default.VideoFile,
                                null, tint = AppAccent
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.kind, color = Color.White, fontSize = 12.sp)
                                Text(item.url, color = AppMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDownloadCandidate(item) }) {
                                Icon(Icons.Default.Download, "Скачать", tint = AppAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(
    tasks: SnapshotStateList<ActiveTask>,
    refreshKey: Int,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val directDownloads = remember(refreshKey) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val values = mutableListOf<SystemDownload>()
        manager.query(DownloadManager.Query())?.use { cursor ->
            val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val doneCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (cursor.moveToNext()) {
                val status = when (cursor.getInt(statusCol)) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Готово"
                    DownloadManager.STATUS_FAILED -> "Ошибка"
                    DownloadManager.STATUS_RUNNING -> "Скачивается"
                    DownloadManager.STATUS_PENDING -> "В очереди"
                    DownloadManager.STATUS_PAUSED -> "Пауза"
                    else -> "Неизвестно"
                }
                values.add(
                    SystemDownload(
                        cursor.getLong(idCol),
                        cursor.getString(titleCol) ?: "Файл",
                        status,
                        cursor.getLong(doneCol),
                        cursor.getLong(totalCol)
                    )
                )
            }
        }
        values.reversed()
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            onRefresh()
        }
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Загрузки", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Обновить", tint = AppAccent) }
        }
        Text("Активные задачи", color = AppMuted, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f)) {
            items(tasks, key = { it.id }) { task ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        .background(AppPanel, RoundedCornerShape(14.dp)).padding(13.dp)
                ) {
                    Text(task.title, color = Color.White, fontSize = 14.sp, maxLines = 2)
                    Text(task.status, color = if (task.status == "Ошибка") AppAccent else AppMuted, fontSize = 12.sp)
                    if (task.status == "Скачивается" || task.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { task.progress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = AppAccent
                        )
                    }
                    if (task.detail.isNotBlank()) {
                        Text(task.detail, color = AppMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("Загрузки Android", color = AppMuted, fontSize = 12.sp)
            }
            items(directDownloads, key = { it.id }) { item ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        .background(AppPanel, RoundedCornerShape(14.dp)).padding(13.dp)
                ) {
                    Text(item.title, color = Color.White, fontSize = 14.sp, maxLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.status, color = AppMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(formatBytes(item.done) + " / " + formatBytes(item.total), color = AppMuted, fontSize = 10.sp)
                    }
                    if (item.status == "Скачивается" && item.total > 0) {
                        LinearProgressIndicator(
                            progress = { (item.done.toFloat() / item.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            color = AppAccent
                        )
                    }
                    if (item.status == "Ошибка") {
                        TextButton(onClick = { DownloadEngine.cancelDirect(context, item.id); onRefresh() }) {
                            Text("Удалить задачу", color = AppAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(refreshKey: Int, onOpen: (File) -> Unit) {
    val context = LocalContext.current
    val files = remember(refreshKey) { DownloadEngine.listFiles(context) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Медиатека", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(files.size.toString() + " файлов", color = AppMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Здесь появятся скачанные видео и аудио", color = AppMuted)
            }
        } else {
            LazyColumn {
                items(files, key = { it.absolutePath }) { file ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .background(AppPanel, RoundedCornerShape(14.dp))
                            .clickable { onOpen(file) }.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VideoFile, null, tint = AppAccent, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(file.length()) + " • " + file.extension.uppercase(), color = AppMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.PlayArrow, "Открыть", tint = AppAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val directory = DownloadEngine.outputDirectory(context)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Настройки", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text("Папка сохранения", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(directory.absolutePath, color = AppMuted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Text("Astra Video определяет доступные медиафайлы в браузере и использует отдельный загрузчик для страниц и потоков.", color = AppMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Text("Загружай только материалы, которые разрешено сохранять. Защищённые DRM-потоки не поддерживаются.", color = AppMuted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerScreen(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Column(Modifier.fillMaxSize().background(AppBack)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White) }
            Text(file.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
            update = { it.player = player }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return bytes.toString() + " Б"
    if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f КБ", bytes / 1024.0)
    if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0))
    return String.format(Locale.getDefault(), "%.2f ГБ", bytes / (1024.0 * 1024.0 * 1024.0))
}
