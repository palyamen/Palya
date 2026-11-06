package com.astra.video

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import android.content.Context
import android.content.Intent

private val Back = Color(0xFF0B0D12)
private val Panel = Color(0xFF171B25)
private val Accent = Color(0xFFFF3158)
private val Muted = Color(0xFF9299A8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 13, 18)
        window.navigationBarColor = android.graphics.Color.rgb(11, 13, 18)
        setContent { AstraVideoApp() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AstraVideoApp() {
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }

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
                    else -> {
                        Column(Modifier.fillMaxSize().padding(22.dp)) {
                            Text("Загрузки", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            InfoCard("MP4 и MP3", "Раздел подготовлен для файлов, которые доступны для скачивания или экспорта с разрешения правообладателя.")
                            Spacer(Modifier.height(12.dp))
                            InfoCard("Важно", "YouTube не предоставляет обычным сторонним приложениям прямую загрузку чужих роликов. Для офлайн-просмотра YouTube используй официальную функцию загрузки, а собственные видео можно экспортировать через YouTube Studio.")
                            Spacer(Modifier.height(12.dp))
                            Text("Здесь появится список сохранённых файлов.", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
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
                settings.userAgentString = settings.userAgentString + " AstraVideo/1.0"
                webChromeClient = WebChromeClient()
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
