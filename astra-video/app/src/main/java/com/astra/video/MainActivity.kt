package com.astra.video

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Row(
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
                            YoutubeWebView(url = videoUrl, onVideo = { videoUrl = it }, modifier = Modifier.fillMaxSize())
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

@Composable
private fun DownloadsScreen() {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    val downloads = remember(refreshKey) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val result = mutableListOf<Pair<String, String>>()
        val cursor = manager.query(DownloadManager.Query())
        cursor?.use {
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            while (it.moveToNext()) {
                val title = if (titleCol >= 0) it.getString(titleCol) ?: "Файл" else "Файл"
                val status = when (it.getInt(statusCol)) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Готово"
                    DownloadManager.STATUS_FAILED -> "Ошибка загрузки"
                    DownloadManager.STATUS_RUNNING -> "Загружается"
                    DownloadManager.STATUS_PENDING -> "В очереди"
                    DownloadManager.STATUS_PAUSED -> "Приостановлено"
                    else -> "Неизвестно"
                }
                result.add(title to status)
            }
        }
        result
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Загрузки", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Сохраняй MP4, MP3 и другие файлы по прямой ссылке, если у тебя есть право на их скачивание.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(18.dp))
        Text("Прямая ссылка на файл", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InputPanel(value = url, onValueChange = { url = it }, hint = "https://сайт.ru/video.mp4")
        Spacer(Modifier.height(12.dp))
        Text("Имя файла (необязательно)", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        InputPanel(value = fileName, onValueChange = { fileName = it }, hint = "Моё видео.mp4")
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val parsed = Uri.parse(url.trim())
                if ((parsed.scheme == "https" || parsed.scheme == "http") && !parsed.host.isNullOrBlank()) {
                    try {
                        val guessed = parsed.lastPathSegment?.takeIf { it.contains('.') } ?: ("astra_video_" + System.currentTimeMillis())
                        val safeName = fileName.trim().ifBlank { guessed }
                            .map { if (it.isLetterOrDigit() || it in " ._-") it else '_' }.joinToString("")
                        val request = DownloadManager.Request(parsed)
                            .setTitle(safeName)
                            .setDescription("Загрузка через Astra Video")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setAllowedOverMetered(true)
                            .setAllowedOverRoaming(false)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        manager.enqueue(request)
                        Toast.makeText(context, "Загрузка добавлена", Toast.LENGTH_SHORT).show()
                        url = ""
                        fileName = ""
                        refreshKey++
                    } catch (e: Exception) {
                        Toast.makeText(context, "Не удалось начать загрузку", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Введи корректную http/https-ссылку", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("Скачать файл")
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
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        .background(Panel, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.first, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text(item.second, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Это загрузчик файлов по прямым ссылкам. Он не извлекает видео или аудио из YouTube. Для роликов YouTube используй официальное офлайн-сохранение или экспорт собственных видео.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
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
