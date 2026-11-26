package com.example.safari27

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Start Page",
    val url: String = "https://www.apple.com",
    val group: String = "General"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Safari27App()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Safari27App() {
    val tabs = remember {
        mutableStateListOf(
            BrowserTab(title = "Apple", url = "https://www.apple.com", group = "Featured"),
            BrowserTab(title = "Google", url = "https://www.google.com", group = "Search")
        )
    }
    var activeTabIndex by remember { mutableIntStateOf(0) }
    val currentTab = tabs.getOrNull(activeTabIndex) ?: tabs.first()

    var addressInput by remember(currentTab.id, currentTab.url) {
        mutableStateOf(formatDisplayUrl(currentTab.url))
    }
    var isEditingUrl by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // iOS 27 Feature States
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showPageMenu by remember { mutableStateOf(false) }
    var liquidGlassOpacity by remember { mutableFloatStateOf(0.78f) } // iOS 27 Liquid Glass Slider
    var adaptiveDarkMode by remember { mutableStateOf(false) } // iOS 27.3 Adaptive Dark Mode
    var verticalTabsMode by remember { mutableStateOf(true) } // iOS 27.3 Vertical Tabs
    var customScriptPrompt by remember { mutableStateOf("") }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = showTabSwitcher || showPageMenu || canGoBack) {
        when {
            showPageMenu -> showPageMenu = false
            showTabSwitcher -> showTabSwitcher = false
            canGoBack -> webViewRef?.goBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (adaptiveDarkMode) Color(0xFF0B0C0E) else Color(0xFFF2F2F7))
    ) {
        // 1. Main WebView Canvas
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // iOS Slim Progress Indicator
            if (pageProgress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = Color(0xFF007AFF),
                    trackColor = Color.Transparent
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString =
                                "Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Mobile/15E148 Safari/604.1"
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress / 100f
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank() && activeTabIndex in tabs.indices) {
                                    tabs[activeTabIndex] = tabs[activeTabIndex].copy(title = title)
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                url?.let {
                                    if (activeTabIndex in tabs.indices) {
                                        tabs[activeTabIndex] = tabs[activeTabIndex].copy(url = it)
                                    }
                                    if (!isEditingUrl) addressInput = formatDisplayUrl(it)
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                if (adaptiveDarkMode) {
                                    applyAdaptiveDarkMode(view, true)
                                }
                            }
                        }
                        loadUrl(currentTab.url)
                        webViewRef = this
                    }
                },
                update = { view ->
                    if (view.url != currentTab.url && !isEditingUrl) {
                        view.loadUrl(currentTab.url)
                    }
                }
            )
        }

        // 2. iOS 27 Floating Liquid Glass Bottom Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Liquid Glass Capsule URL Bar
            LiquidGlassSurface(
                opacity = liquidGlassOpacity,
                darkMode = adaptiveDarkMode,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Page Settings / Extensions Icon (iOS 27 Left Capsule Button)
                    IconButton(
                        onClick = { showPageMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Page Settings",
                            tint = if (adaptiveDarkMode) Color.White else Color(0xFF1C1C1E),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Centered Domain / Search Input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = if (adaptiveDarkMode) Color.White else Color(0xFF1C1C1E),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = if (isEditingUrl) TextAlign.Start else TextAlign.Center
                            ),
                            cursorBrush = SolidColor(Color(0xFF007AFF)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val resolved = resolveUrlOrSearch(addressInput)
                                    tabs[activeTabIndex] = tabs[activeTabIndex].copy(url = resolved)
                                    webViewRef?.loadUrl(resolved)
                                    isEditingUrl = false
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { state ->
                                    isEditingUrl = state.isFocused
                                    addressInput = if (state.isFocused) {
                                        currentTab.url
                                    } else {
                                        formatDisplayUrl(currentTab.url)
                                    }
                                }
                        )
                    }

                    // Reload / Stop Button
                    IconButton(
                        onClick = {
                            if (pageProgress in 0.01f..0.99f) webViewRef?.stopLoading()
                            else webViewRef?.reload()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (pageProgress in 0.01f..0.99f) Icons.Rounded.Close else Icons.Rounded.Refresh,
                            contentDescription = "Reload",
                            tint = if (adaptiveDarkMode) Color.White else Color(0xFF1C1C1E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom Action Controls Pill
            LiquidGlassSurface(
                opacity = liquidGlassOpacity,
                darkMode = adaptiveDarkMode,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconColor = if (adaptiveDarkMode) Color(0xFF0A84FF) else Color(0xFF007AFF)
                    val disabledColor = Color.Gray.copy(alpha = 0.45f)

                    IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack) iconColor else disabledColor
                        )
                    }
                    IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) iconColor else disabledColor
                        )
                    }
                    IconButton(onClick = {
                        tabs.add(BrowserTab(title = "New Tab", url = "https://www.google.com"))
                        activeTabIndex = tabs.lastIndex
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = "New Tab", tint = iconColor)
                    }
                    IconButton(onClick = { showTabSwitcher = true }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CropSquare, contentDescription = "Tabs", tint = iconColor)
                            Text(
                                text = "${tabs.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = iconColor
                            )
                        }
                    }
                }
            }
        }

        // 3. iOS 27.3 Vertical & Card Tab Switcher Overlay
        AnimatedVisibility(
            visible = showTabSwitcher,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (verticalTabsMode) "Vertical Tabs (${tabs.size})" else "Tab Overview (${tabs.size})",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { verticalTabsMode = !verticalTabsMode }) {
                                Text(
                                    text = if (verticalTabsMode) "Compact" else "Vertical",
                                    color = Color(0xFF0A84FF)
                                )
                            }
                            TextButton(onClick = { showTabSwitcher = false }) {
                                Text("Done", color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(tabs) { index, tab ->
                            val isSelected = index == activeTabIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (isSelected) Color(0xFF1C1C1E) else Color(0xFF141416)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable {
                                        activeTabIndex = index
                                        showTabSwitcher = false
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatDisplayUrl(tab.url),
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                                if (tabs.size > 1) {
                                    IconButton(onClick = {
                                        tabs.removeAt(index)
                                        if (activeTabIndex >= tabs.size) {
                                            activeTabIndex = tabs.lastIndex
                                        }
                                    }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Close Tab", tint = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            tabs.add(BrowserTab(title = "New Tab", url = "https://www.google.com"))
                            activeTabIndex = tabs.lastIndex
                            showTabSwitcher = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Tab", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 4. iOS 27 Page Settings & Custom Extension Sheet
        if (showPageMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showPageMenu = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(14.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                        .clickable(enabled = false) {}
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "iOS 27 Safari Controls",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Adaptive Dark Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Adaptive Dark Mode", color = Color.White)
                        Switch(
                            checked = adaptiveDarkMode,
                            onCheckedChange = { enabled ->
                                adaptiveDarkMode = enabled
                                applyAdaptiveDarkMode(webViewRef, enabled)
                            }
                        )
                    }

                    // Liquid Glass Translucency Slider
                    Column {
                        Text(
                            "Liquid Glass Opacity: ${(liquidGlassOpacity * 100).toInt()}%",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        Slider(
                            value = liquidGlassOpacity,
                            onValueChange = { liquidGlassOpacity = it },
                            valueRange = 0.35f..0.95f
                        )
                    }

                    // iOS 27 "Create an Extension" Quick Page Styler
                    OutlinedTextField(
                        value = customScriptPrompt,
                        onValueChange = { customScriptPrompt = it },
                        placeholder = { Text("Describe page style (e.g., large text, sepia)...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Button(
                        onClick = {
                            injectCustomPageStyle(webViewRef, customScriptPrompt)
                            showPageMenu = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Apply Custom Page Extension")
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassSurface(
    opacity: Float,
    darkMode: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val baseColor = if (darkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val rimGradient = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (darkMode) 0.28f else 0.85f),
            Color.White.copy(alpha = 0.08f)
        )
    )
    Box(
        modifier = modifier
            .shadow(elevation = 14.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(baseColor.copy(alpha = opacity))
            .border(BorderStroke(1.dp, rimGradient), shape = shape),
        content = content
    )
}

private fun formatDisplayUrl(rawUrl: String): String {
    return rawUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore("/")
        .ifBlank { rawUrl }
}

private fun resolveUrlOrSearch(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
    }
}

private fun applyAdaptiveDarkMode(webView: WebView?, enable: Boolean) {
    val js = if (enable) {
        """
        (function() {
            let style = document.getElementById('safari27-dark');
            if (!style) {
                style = document.createElement('style');
                style.id = 'safari27-dark';
                style.innerHTML = 'html { filter: invert(90%) hue-rotate(180deg) !important; background: #111 !important; } img, video { filter: invert(100%) hue-rotate(180deg) !important; }';
                document.head.appendChild(style);
            }
        })();
        """.trimIndent()
    } else {
        "document.getElementById('safari27-dark')?.remove();"
    }
    webView?.evaluateJavascript(js, null)
}

private fun injectCustomPageStyle(webView: WebView?, prompt: String) {
    val rule = when {
        prompt.contains("large", ignoreCase = true) -> "body, p, span { font-size: 20px !important; line-height: 1.6 !important; }"
        prompt.contains("sepia", ignoreCase = true) -> "html { filter: sepia(65%) !important; }"
        prompt.contains("grayscale", ignoreCase = true) -> "html { filter: grayscale(100%) !important; }"
        else -> "body { font-family: -apple-system, sans-serif !important; letter-spacing: 0.3px !important; }"
    }
    val js = """
        (function() {
            let s = document.createElement('style');
            s.innerHTML = `$rule`;
            document.head.appendChild(s);
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}
