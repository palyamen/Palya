package com.astra.video

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONTokener

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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    url: String,
    modifier: Modifier = Modifier,
    onMediaFound: (MediaCandidate) -> Unit,
    onDirectDownload: (String, String, String?, String?, String?) -> Unit,
    onPageChanged: (String, String) -> Unit,
    onProgress: (Int) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptCookie(true)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        onProgress(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView, title: String?) {
                        onPageChanged(view.url.orEmpty(), title.orEmpty())
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return false
                    }

                    override fun onPageStarted(view: WebView, pageUrl: String, favicon: android.graphics.Bitmap?) {
                        onPageChanged(pageUrl, view.title.orEmpty())
                    }

                    override fun onLoadResource(view: WebView, resourceUrl: String) {
                        MediaDetector.detect(resourceUrl)?.let(onMediaFound)
                    }

                    override fun onPageFinished(view: WebView, pageUrl: String) {
                        onPageChanged(pageUrl, view.title.orEmpty())
                        val script = """
                            (function() {
                              var re = /\.(mp4|m4v|webm|mkv|mov|m3u8|mpd|mp3|m4a|aac|ogg|wav)(?:[?#]|$)/i;
                              return JSON.stringify(performance.getEntriesByType('resource')
                                .map(function(e) { return e.name; })
                                .filter(function(name) { return re.test(name); }));
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(script) { result ->
                            runCatching {
                                val json = JSONTokener(result).nextValue() as? String ?: return@runCatching
                                val array = JSONArray(json)
                                for (index in 0 until array.length()) {
                                    MediaDetector.detect(array.optString(index))?.let(onMediaFound)
                                }
                            }
                        }
                    }
                }

                setDownloadListener(DownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                    val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                    onDirectDownload(
                        downloadUrl,
                        userAgent.orEmpty(),
                        cookies,
                        url,
                        mimeType
                    )
                })
                tag = url
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.tag != url) {
                webView.tag = url
                webView.loadUrl(url)
            }
        }
    )
}
