package com.example.safaribrowser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

data class FavoriteSite(
    val name: String,
    val url: String,
    val letter: String,
    val color: Color
)

val defaultFavorites = listOf(
    FavoriteSite("Apple", "https://apple.com", "", Color(0xFF1C1C1E)),
    FavoriteSite("Google", "https://google.com", "G", Color(0xFF4285F4)),
    FavoriteSite("YouTube", "https://youtube.com", "▶", Color(0xFFFF0000)),
    FavoriteSite("Wikipedia", "https://wikipedia.org", "W", Color(0xFF3366CC)),
    FavoriteSite("GitHub", "https://github.com", "GH", Color(0xFF24292F)),
    FavoriteSite("X", "https://x.com", "X", Color(0xFF000000)),
    FavoriteSite("Reddit", "https://reddit.com", "R", Color(0xFFFF4500)),
    FavoriteSite("News", "https://google.com", "N", Color(0xFF34A853))
)

class SafariTab(
    val id: Long = System.nanoTime(),
    initialUrl: String = ""
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("Start Page")
    var progress by mutableFloatStateOf(0f)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var previewBitmap by mutableStateOf<Bitmap?>(null)
    var webView: WebView? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                IOSSafari27App()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IOSSafari27App() {
    val tabs = remember { mutableStateListOf(SafariTab()) }
    var activeIndex by remember { mutableIntStateOf(0) }
    var showTabGrid by remember { mutableStateOf(false) }
    var isBarMinimized by remember { mutableStateOf(false) }
    var isEditingUrl by remember { mutableStateOf(false) }

    val currentTab = tabs.getOrNull(activeIndex) ?: tabs.first()
    var urlInput by remember(currentTab.id, currentTab.url) { mutableStateOf(currentTab.url) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    fun extractDomain(rawUrl: String): String {
        if (rawUrl.isBlank()) return "Search or enter website"
        return try {
            val host = Uri.parse(rawUrl).host ?: rawUrl
            host.removePrefix("www.").removePrefix("m.")
        } catch (e: Exception) {
            rawUrl
        }
    }

    fun captureTabSnapshot(tab: SafariTab) {
        val wv = tab.webView ?: return
        if (wv.width > 0 && wv.height > 0) {
            try {
                val bmp = Bitmap.createBitmap(wv.width / 2, wv.height / 2, Bitmap.Config.RGB_565)
                val canvas = Canvas(bmp)
                canvas.scale(0.5f, 0.5f)
                wv.draw(canvas)
                tab.previewBitmap = bmp
            } catch (_: Exception) {
            }
        }
    }

    fun navigateTo(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val destination = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://google.com/search?q=${Uri.encode(trimmed)}"
        }
        currentTab.url = destination
        urlInput = destination
        isEditingUrl = false
        isBarMinimized = false
        focusManager.clearFocus()
        currentTab.webView?.loadUrl(destination)
    }

    BackHandler(enabled = showTabGrid || isEditingUrl || currentTab.canGoBack || currentTab.url.isNotEmpty()) {
        when {
            isEditingUrl -> {
                isEditingUrl = false
                urlInput = currentTab.url
                focusManager.clearFocus()
            }
            showTabGrid -> showTabGrid = false
            currentTab.canGoBack -> currentTab.webView?.goBack()
            currentTab.url.isNotEmpty() -> {
                currentTab.url = ""
                currentTab.title = "Start Page"
                urlInput = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
    ) {
        if (!showTabGrid) {
            if (currentTab.url.isEmpty()) {
                SafariStartPage(
                    onSelectSite = { siteUrl -> navigateTo(siteUrl) }
                )
            } else {
                key(currentTab.id) {
                    AndroidView(
                        factory = { ctx ->
                            currentTab.webView ?: WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                        val delta = scrollY - oldScrollY
                                        if (delta > 18 && scrollY > 120 && !isEditingUrl) {
                                            isBarMinimized = true
                                        } else if (delta < -14 || scrollY <= 40) {
                                            isBarMinimized = false
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        currentTab.isLoading = true
                                        url?.let {
                                            currentTab.url = it
                                            if (!isEditingUrl) urlInput = it
                                        }
                                        currentTab.canGoBack = view?.canGoBack() ?: false
                                        currentTab.canGoForward = view?.canGoForward() ?: false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        currentTab.isLoading = false
                                        url?.let {
                                            currentTab.url = it
                                            if (!isEditingUrl) urlInput = it
                                        }
                                        view?.title?.takeIf { it.isNotBlank() }?.let {
                                            currentTab.title = it
                                        }
