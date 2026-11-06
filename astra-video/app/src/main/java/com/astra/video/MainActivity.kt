package com.astra.video

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.URLUtil
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

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

private class ActiveTask(
    val id: String,
    val title: String,
    val url: String,
    val userAgent: String?,
    val cookies: String?,
    val referer: String?,
    val audioOnly: Boolean = false
) {
    var processId by mutableStateOf(id)
    var progress by mutableFloatStateOf(0f)
    var status by mutableStateOf("В очереди")
    var detail by mutableStateOf("")
    var pauseRequested by mutableStateOf(false)
    var cancelRequested by mutableStateOf(false)
    var job: Job? = null
}

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

    fun launchTask(task: ActiveTask) {
        task.pauseRequested = false
        task.cancelRequested = false
        task.processId = task.id + "-" + System.currentTimeMillis()
        task.status = "В очереди"
        task.job = scope.launch {
            try {
                task.status = "Анализирую источник"
                val file = DownloadEngine.download(
                    context, task.url, task.processId, task.userAgent, task.cookies,
                    task.referer, task.audioOnly
                ) { progress, line ->
                    scope.launch(Dispatchers.Main) {
                        task.progress = progress
                        task.status = if (progress > 0f) "Скачивается" else "Анализирую источник"
                        task.detail = line
                    }
                }
                task.progress = 100f
                task.status = "Готово"
                task.detail = file.name
                refreshKey++
                Toast.makeText(context, "Сохранено: " + file.name, Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                when {
                    task.pauseRequested -> task.status = "Пауза"
                    task.cancelRequested -> task.status = "Отменено"
                    else -> {
                        task.status = "Ошибка"
                        task.detail = error.message ?: "Не удалось получить медиа"
                    }
                }
            }
        }
    }

    fun enqueueDownload(
        url: String,
        title: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null,
        audioOnly: Boolean = false
    ) {
        val task = ActiveTask(
            UUID.randomUUID().toString(), title.ifBlank { "Медиафайл" },
            url, userAgent, cookies, referer, audioOnly
        )
        tasks.add(0, task)
        launchTask(task)
    }

    fun pauseTask(task: ActiveTask) {
        task.pauseRequested = true
        task.status = "Приостанавливаю"
        DownloadEngine.stop(task.processId)
        task.job?.cancel()
    }

    fun resumeTask(task: ActiveTask) {
        launchTask(task)
    }

    fun cancelTask(task: ActiveTask) {
        task.cancelRequested = true
        task.status = "Отменяю"
        DownloadEngine.stop(task.processId)
        task.job?.cancel()
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
                            onDownloadPage = { url, agent, cookies, referer -> enqueueDownload(url, pageTitle, agent, cookies, referer) },
                            onDownloadCandidate = { candidate ->
                                enqueueDownload(
                                    candidate.url, candidate.kind, "Mozilla/5.0 AstraVideo",
                                    CookieManager.getInstance().getCookie(candidate.url), currentPage
                                )
                            },
                            onDirectDownload = { url, agent, cookies, referer, disposition, mime ->
                                val suggestedName = URLUtil.guessFileName(url, disposition, mime)
                                enqueueDownload(url, suggestedName, agent, cookies, referer)
                            }
                        )
                        1 -> DownloadsScreen(
                            tasks, refreshKey,
                            onRefresh = { refreshKey++ },
                            onPause = { pauseTask(it) },
                            onResume = { resumeTask(it) },
                            onCancel = { cancelTask(it) }
                        )
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
    onDownloadPage: (String, String, String?, String?) -> Unit,
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
            TextButton(onClick = {
                onDownloadPage(
                    currentPage,
                    webView?.settings?.userAgentString.orEmpty(),
                    CookieManager.getInstance().getCookie(currentPage),
                    currentPage
                )
            }) {
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
                            view.tag = url
                            onPageChanged(url, view.title.orEmpty())
                        }

                        override fun onLoadResource(view: android.webkit.WebView, url: String) {
                            MediaDetector.detect(url)?.let(onMediaFound)
                        }

                        override fun onPageFinished(view: android.webkit.WebView, url: String) {
                            onPageChanged(url, view.title.orEmpty())
                            val script = """
                                (function() {
                                  var urls = new Set();
                                  document.querySelectorAll('video, audio, source').forEach(function(el) {
                                    if (el.currentSrc) urls.add(el.currentSrc);
                                    if (el.src) urls.add(el.src);
                                  });
                                  performance.getEntriesByType('resource').slice(-300).forEach(function(entry) {
                                    if (entry.name) urls.add(entry.name);
                                  });
                                  return JSON.stringify(Array.from(urls));
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
                            downloadUrl, userAgent.orEmpty(), cookies, webView?.url.orEmpty(),
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
    onRefresh: () -> Unit,
    onPause: (ActiveTask) -> Unit,
    onResume: (ActiveTask) -> Unit,
    onCancel: (ActiveTask) -> Unit
) {
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
        Text("До трёх одновременных задач. Незавершённые файлы можно продолжить.", color = AppMuted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Пока загрузок нет", color = AppMuted)
            }
        } else {
            LazyColumn {
                items(tasks, key = { it.id }) { task ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .background(AppPanel, RoundedCornerShape(14.dp)).padding(13.dp)
                    ) {
                        Text(task.title, color = Color.White, fontSize = 14.sp, maxLines = 2)
                        Text(task.status, color = if (task.status == "Ошибка") AppAccent else AppMuted, fontSize = 12.sp)
                        if (task.progress > 0f || task.status == "Скачивается") {
                            LinearProgressIndicator(
                                progress = { task.progress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                color = AppAccent
                            )
                            Text(task.progress.toInt().toString() + "%", color = AppMuted, fontSize = 10.sp)
                        }
                        if (task.detail.isNotBlank()) {
                            Text(task.detail, color = AppMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (task.status == "Скачивается" || task.status == "Анализирую источник" || task.status == "В очереди") {
                                TextButton(onClick = { onPause(task) }) { Text("Пауза", color = AppAccent) }
                            }
                            if (task.status == "Пауза" || task.status == "Ошибка") {
                                TextButton(onClick = { onResume(task) }) { Text("Продолжить", color = AppAccent) }
                            }
                            if (task.status != "Готово" && task.status != "Отменено") {
                                TextButton(onClick = { onCancel(task) }) { Text("Отменить", color = AppMuted) }
                            }
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
