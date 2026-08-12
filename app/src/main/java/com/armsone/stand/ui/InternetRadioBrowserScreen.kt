package com.armsone.stand.ui

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.DragEvent
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.armsone.stand.model.InternetRadioBrowserAddressResult
import com.armsone.stand.model.InternetRadioBrowserPolicy

private val BrowserBackground = Color(0xFF1B1412)
private val BrowserPanel = Color.White.copy(alpha = 0.09f)
private val BrowserAccent = Color(0xFFF26C2D)

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InternetRadioBrowserScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var addressText by rememberSaveable { mutableStateOf(InternetRadioBrowserPolicy.homepage) }
    var addressFieldIsFocused by remember { mutableStateOf(false) }
    var showsFavorites by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var popupReturnAddress by remember { mutableStateOf<String?>(null) }
    var browserGeneration by remember { mutableStateOf(0) }

    fun updateNavigationState(view: WebView) {
        canGoBack = view.canGoBack()
        view.url?.takeIf(InternetRadioBrowserPolicy::isSecureWebAddress)?.let { current ->
            if (!addressFieldIsFocused) addressText = current
        }
    }

    fun loadSecureAddress(address: String) {
        showsFavorites = false
        keyboardController?.hide()
        when (val result = InternetRadioBrowserPolicy.browsingAddress(address)) {
            is InternetRadioBrowserAddressResult.Valid -> {
                errorMessage = null
                webView?.loadUrl(result.url)
            }
            is InternetRadioBrowserAddressResult.Invalid -> errorMessage = result.message
        }
    }

    fun pauseAndClose() {
        webView?.evaluateJavascript(
            "document.querySelectorAll('video,audio').forEach(function(m){m.pause();});",
            null,
        )
        webView?.onPause()
        onClose()
    }

    fun handleBackOrClose() {
        val popupAddress = popupReturnAddress
        when {
            popupAddress != null -> {
                popupReturnAddress = null
                webView?.loadUrl(popupAddress)
            }
            webView?.canGoBack() == true -> webView?.goBack()
            else -> pauseAndClose()
        }
    }

    BackHandler { handleBackOrClose() }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> {
                    webView?.evaluateJavascript(
                        "document.querySelectorAll('video,audio').forEach(function(m){m.pause();});",
                        null,
                    )
                    webView?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrowserBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BrowserToolbarButton(
                icon = if (popupReturnAddress != null || !canGoBack) {
                    Icons.Default.Close
                } else {
                    Icons.AutoMirrored.Filled.ArrowBack
                },
                label = when {
                    popupReturnAddress != null -> "팝업 닫기"
                    canGoBack -> "이전 페이지"
                    else -> "브라우저 닫기"
                },
                onClick = ::handleBackOrClose,
                onLongClick = ::pauseAndClose,
            )
            OutlinedTextField(
                value = addressText,
                onValueChange = {
                    addressText = it.take(InternetRadioBrowserPolicy.MAXIMUM_ADDRESS_LENGTH)
                    errorMessage = null
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .onFocusChanged { addressFieldIsFocused = it.isFocused },
                placeholder = { Text("웹 주소 입력", maxLines = 1) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { loadSecureAddress(addressText) }),
            )
            BrowserToolbarButton(
                icon = if (isLoading) Icons.Default.Stop else Icons.Default.Search,
                label = if (isLoading) "로딩 중지" else "주소로 이동",
                primary = true,
                onClick = {
                    if (isLoading) {
                        webView?.stopLoading()
                        isLoading = false
                        loadProgress = 0f
                    } else {
                        loadSecureAddress(addressText)
                    }
                },
                onLongClick = {
                    val copiedAddress = clipboard.getText()?.text?.trim().orEmpty()
                    if (copiedAddress.isEmpty()) {
                        errorMessage = "복사한 웹 주소가 없습니다."
                    } else {
                        addressText = copiedAddress.take(InternetRadioBrowserPolicy.MAXIMUM_ADDRESS_LENGTH)
                        loadSecureAddress(addressText)
                    }
                },
            )
            BrowserToolbarButton(
                icon = Icons.Default.Refresh,
                label = "새로고침",
                enabled = webView?.url != null,
                onClick = {
                    errorMessage = null
                    webView?.reload()
                },
            )
            BrowserToolbarButton(
                icon = if (showsFavorites) Icons.Default.Star else Icons.Outlined.StarOutline,
                label = if (showsFavorites) "즐겨찾기 닫기" else "즐겨찾기 열기",
                selected = showsFavorites,
                onClick = { showsFavorites = !showsFavorites },
            )
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = BrowserAccent,
                trackColor = BrowserPanel,
            )
        } else {
            Spacer(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.18f)))
        }

        Box(Modifier.fillMaxSize()) {
            key(browserGeneration) {
                AndroidView(
                factory = { context ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            mediaPlaybackRequiresUserGesture = true
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            setGeolocationEnabled(false)
                            safeBrowsingEnabled = true
                        }
                        isLongClickable = false
                        setOnLongClickListener { true }
                        setOnDragListener { _, event ->
                            val containsFile =
                                event.clipDescription?.hasMimeType(
                                    ClipDescription.MIMETYPE_TEXT_URILIST,
                                ) == true ||
                                    (0 until (event.clipData?.itemCount ?: 0)).any { index ->
                                        event.clipData?.getItemAt(index)?.uri != null
                                    }
                            if (containsFile && event.action == DragEvent.ACTION_DROP) {
                                errorMessage = "이 브라우저에서는 파일을 끌어놓을 수 없습니다."
                            }
                            containsFile
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        setDownloadListener { _, _, _, _, _ ->
                            errorMessage = "이 브라우저는 파일을 내려받지 않습니다."
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val address = request.url.toString()
                                if (InternetRadioBrowserPolicy.isSecureWebAddress(address)) return false
                                errorMessage = "https://로 시작하는 안전한 웹 주소만 열 수 있습니다."
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                errorMessage = null
                                isLoading = true
                                loadProgress = maxOf(loadProgress, 0.08f)
                                updateNavigationState(view)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false
                                loadProgress = 1f
                                updateNavigationState(view)
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    errorMessage = "페이지를 열지 못했습니다. ${error.description}"
                                    isLoading = false
                                    loadProgress = 0f
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView,
                                handler: SslErrorHandler,
                                error: SslError,
                            ) {
                                handler.cancel()
                                errorMessage = "안전한 연결을 확인할 수 없어 페이지를 열지 않았습니다."
                            }

                            override fun onReceivedHttpAuthRequest(
                                view: WebView,
                                handler: HttpAuthHandler,
                                host: String,
                                realm: String,
                            ) {
                                handler.cancel()
                                errorMessage = "로그인이 필요한 페이지는 열 수 없습니다."
                            }

                            override fun onReceivedClientCertRequest(
                                view: WebView,
                                request: ClientCertRequest,
                            ) {
                                request.cancel()
                            }

                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail,
                            ): Boolean {
                                errorMessage = if (detail.didCrash()) {
                                    "웹 페이지가 종료되어 브라우저를 안전하게 다시 시작했습니다."
                                } else {
                                    "웹 페이지를 다시 불러올 수 있도록 브라우저를 초기화했습니다."
                                }
                                if (webView === view) webView = null
                                browserGeneration += 1
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                isLoading = newProgress < 100
                                updateNavigationState(view)
                            }

                            override fun onPermissionRequest(request: PermissionRequest) {
                                request.deny()
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String,
                                callback: GeolocationPermissions.Callback,
                            ) {
                                callback.invoke(origin, false, false)
                            }

                            override fun onShowFileChooser(
                                webView: WebView,
                                filePathCallback: ValueCallback<Array<android.net.Uri>>,
                                fileChooserParams: FileChooserParams,
                            ): Boolean {
                                filePathCallback.onReceiveValue(null)
                                errorMessage = "이 브라우저에서는 파일을 선택할 수 없습니다."
                                return true
                            }

                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message,
                            ): Boolean {
                                val returnAddress = view.url
                                val popup = WebView(view.context).apply {
                                    settings.javaScriptEnabled = false
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            popupView: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            val target = request.url.toString()
                                            if (InternetRadioBrowserPolicy.isSecureWebAddress(target)) {
                                                popupReturnAddress = returnAddress
                                                view.loadUrl(target)
                                            } else {
                                                errorMessage = "https://로 시작하는 안전한 웹 주소만 열 수 있습니다."
                                            }
                                            popupView.destroy()
                                            return true
                                        }
                                    }
                                }
                                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        webView = this
                        loadUrl(
                            addressText.takeIf(InternetRadioBrowserPolicy::isSecureWebAddress)
                                ?: InternetRadioBrowserPolicy.homepage,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { releasedView ->
                    releasedView.evaluateJavascript(
                        "document.querySelectorAll('video,audio').forEach(function(m){m.pause();});",
                        null,
                    )
                    releasedView.stopLoading()
                    releasedView.onPause()
                    releasedView.clearHistory()
                    releasedView.clearFormData()
                    releasedView.clearCache(true)
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    releasedView.removeAllViews()
                    releasedView.destroy()
                    if (webView === releasedView) webView = null
                },
            )
            }

            if (showsFavorites) {
                BrowserFavoritesPanel(
                    onClose = { showsFavorites = false },
                    onOpen = { favorite ->
                        addressText = favorite
                        loadSecureAddress(favorite)
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else if (errorMessage != null) {
                BrowserMessagePanel(
                    message = errorMessage.orEmpty(),
                    onClose = { errorMessage = null },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
    selected: Boolean = false,
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onLongClickLabel = onLongClick?.let { "길게 누르기" },
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = when {
            primary -> BrowserAccent
            else -> BrowserPanel
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (primary) BrowserAccent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.14f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> Color.White.copy(alpha = 0.25f)
                    selected -> BrowserAccent
                    primary -> Color.White
                    else -> Color.White.copy(alpha = 0.68f)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserFavoritesPanel(
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = BrowserBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = BrowserAccent)
                Text(
                    "즐겨찾기",
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.Bold,
                )
                BrowserToolbarButton(
                    icon = Icons.Default.Clear,
                    label = "즐겨찾기 닫기",
                    onClick = onClose,
                )
            }
            InternetRadioBrowserPolicy.favorites.forEach { favorite ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            role = Role.Button,
                            onClickLabel = favorite.title,
                            onClick = { onOpen(favorite.url) },
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = BrowserPanel,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (favorite.isHomepage) Icons.Default.Home else Icons.Default.Public,
                            contentDescription = null,
                            tint = if (favorite.isHomepage) BrowserAccent else Color.White.copy(alpha = 0.58f),
                        )
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                favorite.title,
                                color = Color.White.copy(alpha = 0.88f),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                favorite.url,
                                color = Color.White.copy(alpha = 0.52f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Text(
                "웹사이트만 열며 스트리밍 주소를 자동으로 감지하거나 채널에 입력하지 않습니다.",
                color = Color.White.copy(alpha = 0.52f),
            )
        }
    }
}

@Composable
private fun BrowserMessagePanel(
    message: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(8.dp),
        color = BrowserBackground.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrowserAccent.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Public, contentDescription = null, tint = BrowserAccent)
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.82f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
            )
            BrowserToolbarButton(
                icon = Icons.Default.Close,
                label = "안내 닫기",
                onClick = onClose,
            )
        }
    }
}
