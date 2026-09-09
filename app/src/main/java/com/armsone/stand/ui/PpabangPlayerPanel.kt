package com.armsone.stand.ui

import android.view.ViewConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.lerp
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.armsone.stand.model.PpabangCategory
import com.armsone.stand.model.PpabangPlaybackState
import com.armsone.stand.model.PpabangPolicy
import com.armsone.stand.ui.components.standFocusable
import com.armsone.stand.ui.components.standPanelSurface
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PpabangFloatingPlayer(
    anchorFrame: Rect,
    state: StandUiState,
    isTelevision: Boolean,
    isPortrait: Boolean,
    commandFlow: SharedFlow<PpabangCommand>,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onSelectCategory: (PpabangCategory) -> Unit,
    onClose: () -> Unit,
    onPlaybackStateChanged: (PpabangPlaybackState, String?) -> Unit,
    modifier: Modifier = Modifier,
    onFrameChanged: (Rect) -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val width = if (isTelevision) 160.dp else 216.dp
        val maxX = with(density) { (maxWidth - width).toPx().coerceAtLeast(0f) }
        val maxY = with(density) { (maxHeight - width).toPx().coerceAtLeast(0f) }
        DisposableEffect(Unit) { onDispose { onFrameChanged(Rect.Zero) } }
        PpabangInlinePlayer(
            state, isTelevision, isPortrait, commandFlow, onPlay, onStop, onNext,
            onSelectCategory, onClose, onPlaybackStateChanged,
            modifier = Modifier.offset {
                IntOffset(
                    (if (isTelevision) 0f else maxX).roundToInt(),
                    maxY.roundToInt(),
                )
            }
                .width(width).onGloballyPositioned { onFrameChanged(it.boundsInWindow()) },
        )
    }
}

private class PpabangBridge(
    private val onState: (Int) -> Unit,
    private val onCover: (String) -> Unit,
) {
    @JavascriptInterface
    fun onPlayerState(state: Int) {
        onState(state)
    }

    @JavascriptInterface
    fun onCoverStatus(status: String) {
        onCover(status)
    }
}

/**
 * Inline visible video player for Ppabang, adjacent to clock/home music area.
 * Keeps phone/tablet YouTube controls usable; TV uses a smaller display-only preview.
 * Supports phone, tablet, and TV D-pad.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PpabangInlinePlayer(
    state: StandUiState,
    isTelevision: Boolean,
    isPortrait: Boolean,
    commandFlow: SharedFlow<PpabangCommand>,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onSelectCategory: (PpabangCategory) -> Unit,
    onClose: () -> Unit,
    onPlaybackStateChanged: (PpabangPlaybackState, String?) -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable () -> Unit = {},
    backgroundOpacity: Float = 1f,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadedCategory by remember { mutableStateOf<PpabangCategory?>(null) }
    val currentState by rememberUpdatedState(state)
    val reportState by rememberUpdatedState(onPlaybackStateChanged)
    var documentStopped by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<PpabangCommand?>(PpabangCommand.PLAY) }
    var pageGeneration by remember { mutableStateOf(0) }

    fun isTrustedPage(view: WebView): Boolean {
        val uri = android.net.Uri.parse(view.url ?: return false)
        return uri.scheme == "https" && uri.host == PpabangPolicy.HOST
    }

    fun stopDocument() {
        documentStopped = true
        pendingCommand = null
        pageGeneration += 1
        webViewRef?.let { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
        }
        reportState(PpabangPlaybackState.STOPPED, null)
    }

    fun executeJs(js: String) {
        webViewRef?.post {
            webViewRef?.takeIf { isTrustedPage(it) && !documentStopped }
                ?.evaluateJavascript(js, null)
        }
    }

    fun deliverPendingCommand(view: WebView, generation: Int, attempt: Int = 0) {
        if (webViewRef !== view || documentStopped || generation != pageGeneration || !isTrustedPage(view)) return
        val command = pendingCommand ?: return
        val script = if (command == PpabangCommand.NEXT) PpabangPolicy.JS_NEXT_COMMAND else PpabangPolicy.JS_PLAY_COMMAND
        view.evaluateJavascript(
            "(function(){if(!document.querySelector('#queueList button') || !document.querySelector('iframe#player')) return false; " +
                script + "; return true;})()",
        ) { ready ->
            if (webViewRef !== view || documentStopped || generation != pageGeneration) return@evaluateJavascript
            if (ready == "true") {
                pendingCommand = null
            } else if (attempt < 60) {
                view.postDelayed({ deliverPendingCommand(view, generation, attempt + 1) }, 500)
            } else {
                pendingCommand = null
                reportState(PpabangPlaybackState.FAILED, "재생 준비가 지연됩니다. 재생 버튼을 다시 눌러 주세요.")
            }
        }
    }

    LaunchedEffect(commandFlow) {
        commandFlow.collect { command ->
            when (command) {
                PpabangCommand.PLAY -> {
                    if (documentStopped) {
                        documentStopped = false
                        pendingCommand = PpabangCommand.PLAY
                        webViewRef?.loadUrl(currentState.ppabangCategory.url)
                    } else {
                        pendingCommand = PpabangCommand.PLAY
                        webViewRef?.let { deliverPendingCommand(it, pageGeneration) }
                    }
                }
                PpabangCommand.STOP -> {
                    stopDocument()
                }
                PpabangCommand.NEXT -> {
                    if (documentStopped) {
                        documentStopped = false
                        pendingCommand = PpabangCommand.NEXT
                        webViewRef?.loadUrl(currentState.ppabangCategory.url)
                    } else {
                        pendingCommand = PpabangCommand.NEXT
                        webViewRef?.let { deliverPendingCommand(it, pageGeneration) }
                    }
                }
            }
        }
    }

    LaunchedEffect(state.ppabangCategory) {
        val target = state.ppabangCategory
        if (loadedCategory != target) {
            loadedCategory = target
            documentStopped = false
            pendingCommand = PpabangCommand.PLAY
            webViewRef?.let { wv ->
                onPlaybackStateChanged(PpabangPlaybackState.LOADING, null)
                wv.loadUrl(target.url)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    webViewRef?.onResume()
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    stopDocument()
                    webViewRef?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val playerSide = if (isTelevision) 160.dp else 216.dp
    val videoSide = playerSide - 16.dp
    Surface(
        modifier = modifier.width(playerSide).height(playerSide),
        color = lerp(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary, 0.30f).copy(alpha = backgroundOpacity),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f * backgroundOpacity)),
    ) {
        Box(Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .size(videoSide)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                                setGeolocationEnabled(false)
                                safeBrowsingEnabled = true
                            }
                            isLongClickable = false
                            setOnLongClickListener { true }
                            if (isTelevision) {
                                // TV remote actions stay on the 빠방 panel, not this display-only video.
                                isFocusable = false
                                isFocusableInTouchMode = false
                                setOnTouchListener { _, _ -> true }
                            }

                            addJavascriptInterface(
                                PpabangBridge(
                                    onState = { playerState ->
                                        post {
                                        if (webViewRef !== this || !isTrustedPage(this) || documentStopped ||
                                            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@post
                                        val newState = when (playerState) {
                                            1 -> PpabangPlaybackState.PLAYING
                                            2 -> PpabangPlaybackState.PAUSED
                                            3 -> PpabangPlaybackState.LOADING
                                            else -> null
                                        }
                                        if (newState != null) {
                                            reportState(newState, null)
                                        }
                                        }
                                    },
                                    onCover = { coverStatus ->
                                        post {
                                        if (webViewRef !== this || !isTrustedPage(this) || documentStopped) return@post
                                        when (coverStatus) {
                                            "BLOCKED" -> onPlaybackStateChanged(
                                                PpabangPlaybackState.AUTOPLAY_BLOCKED,
                                                "화면을 터치하여 재생을 시작하세요",
                                            )
                                            "EMPTY" -> onPlaybackStateChanged(
                                                PpabangPlaybackState.FAILED,
                                                "재생 가능한 영상이 없습니다",
                                            )
                                            "START_COVER" -> {
                                                if (currentState.ppabangPlaybackState != PpabangPlaybackState.PLAYING) {
                                                    reportState(PpabangPlaybackState.IDLE, null)
                                                }
                                            }
                                        }
                                        }
                                    },
                                ),
                                PpabangPolicy.JS_BRIDGE_NAME,
                            )

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    if (!request.isForMainFrame) return false
                                    val allowed = request.url.scheme == "https" && request.url.host == PpabangPolicy.HOST
                                    if (!allowed) stopDocument()
                                    return !allowed
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    pageGeneration += 1
                                    if (!documentStopped) reportState(PpabangPlaybackState.LOADING, null)
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    if (isTrustedPage(view) && !documentStopped) {
                                        val generation = pageGeneration
                                        view.evaluateJavascript(PpabangPolicy.INJECTED_SETUP_JS) {
                                            deliverPendingCommand(view, generation)
                                        }
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (request.isForMainFrame) {
                                        documentStopped = true
                                        pendingCommand = null
                                        onPlaybackStateChanged(
                                            PpabangPlaybackState.FAILED,
                                            "페이지를 불러오지 못했습니다",
                                        )
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView,
                                    handler: SslErrorHandler,
                                    error: SslError,
                                ) {
                                    handler.cancel()
                                    onPlaybackStateChanged(
                                        PpabangPlaybackState.FAILED,
                                        "안전한 연결을 확인할 수 없습니다",
                                    )
                                }

                                override fun onReceivedHttpAuthRequest(
                                    view: WebView,
                                    handler: HttpAuthHandler,
                                    host: String,
                                    realm: String,
                                ) {
                                    handler.cancel()
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
                                    if (webViewRef === view) webViewRef = null
                                    onPlaybackStateChanged(
                                        PpabangPlaybackState.FAILED,
                                        "웹 뷰가 재설정되었습니다",
                                    )
                                    return true
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest) {
                                    request.deny()
                                }

                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String,
                                    callback: GeolocationPermissions.Callback,
                                ) {
                                    callback.invoke(origin, false, false)
                                }
                            }

                            webViewRef = this
                            loadedCategory = state.ppabangCategory
                            loadUrl(state.ppabangCategory.url)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { releasedView ->
                        if (webViewRef === releasedView) webViewRef = null
                        releasedView.removeJavascriptInterface(PpabangPolicy.JS_BRIDGE_NAME)
                        releasedView.stopLoading()
                        releasedView.loadUrl("about:blank")
                        releasedView.onPause()
                        releasedView.removeAllViews()
                        releasedView.destroy()
                    },
                )

            }
        }
    }


}

/**
 * Accessible dialog listing the server's current categories with TV D-pad focusability.
 */
@Composable
fun PpabangCategoryDialog(
    currentCategory: PpabangCategory,
    categories: List<PpabangCategory>,
    onSelect: (PpabangCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedCategoryFocusRequester = remember { FocusRequester() }
    val selectedCategoryIndex = categories.indexOf(currentCategory)
    val categoryListState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedCategoryIndex.coerceAtLeast(0),
    )
    LaunchedEffect(selectedCategoryIndex, categories.size) {
        if (selectedCategoryIndex >= 0) {
            // A selected item below LazyColumn's initial viewport has no attached
            // FocusRequester yet. Make it visible before assigning D-pad focus.
            categoryListState.scrollToItem(selectedCategoryIndex)
            snapshotFlow {
                categoryListState.layoutInfo.visibleItemsInfo.any { it.index == selectedCategoryIndex }
            }.first { it }
            selectedCategoryFocusRequester.requestFocus()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        // A long-press release must not be interpreted as an immediate outside tap.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1E1E22),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "빠방 카테고리 선택",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .standFocusable(shape = CircleShape)
                            .semantics {
                                role = Role.Button
                                contentDescription = "닫기"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }

                Text(
                    text = "원하는 음악 영상 채널을 선택하세요 (총 ${categories.size}개 채널)",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 380.dp),
                    state = categoryListState,
                ) {
                    items(categories) { category ->
                        val isSelected = category == currentCategory
                        var isFocused by remember(category) { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.focusRequester(selectedCategoryFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { onSelect(category) }
                                .standFocusable(shape = RoundedCornerShape(12.dp))
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "${category.title} 채널 ${if (isSelected) "선택됨" else "선택"}"
                                },
                            color = when {
                                isFocused -> Color(0xFFFF5722).copy(alpha = 0.48f)
                                isSelected -> Color(0xFFFF5722).copy(alpha = 0.25f)
                                else -> Color.White.copy(alpha = 0.05f)
                            },
                            border = if (isFocused) {
                                androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFFFB27A))
                            } else {
                                null
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = category.title,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFFF7043),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class PpabangCommand {
    PLAY,
    STOP,
    NEXT,
}
