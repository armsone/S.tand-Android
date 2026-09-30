package com.armsone.stand.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.ui.components.standFocusable
import com.armsone.stand.ui.components.standPanelSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val JS_PAUSE_COMMAND = """
    (function() {
        try {
            document.querySelectorAll('video,audio').forEach(function(m) { m.pause(); });
        } catch (_) {}
        var iframe = document.querySelector('iframe#player') || document.querySelector('iframe[src*="youtube.com"]');
        if (iframe && iframe.contentWindow && iframe.src) {
            try {
                var origin = new URL(iframe.src).origin;
                iframe.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: 'pauseVideo',
                    args: []
                }), origin);
            } catch (_) {}
        }
    })();
"""

private const val JS_RESUME_COMMAND = """
    (function() {
        var iframe = document.querySelector('iframe#player') || document.querySelector('iframe[src*="youtube.com"]');
        if (iframe && iframe.contentWindow && iframe.src) {
            try {
                var origin = new URL(iframe.src).origin;
                iframe.contentWindow.postMessage(JSON.stringify({
                    event: 'command',
                    func: 'playVideo',
                    args: []
                }), origin);
            } catch (_) {}
        }
    })();
"""

private const val JS_SITE_READINESS_POLL = """
    (function() {
        var hasQueue = !!document.querySelector('#queueList button');
        var hasPlayer = !!document.querySelector('iframe#player');
        if (hasQueue && hasPlayer && window.StandPpabangBridge && typeof window.StandPpabangBridge.onSiteEvent === 'function') {
            window.StandPpabangBridge.onSiteEvent('ready');
        }
    })();
"""

class PpabangSessionCallbacks(
    val leaseToken: Long,
    val isResumed: () -> Boolean,
    val reportPlaybackState: (PpabangPlaybackState, String?) -> Unit,
    val getUiState: () -> StandUiState,
    val launchJob: (suspend () -> Unit) -> Job,
)

private class PpabangBridge(
    private val holder: PpabangWebViewHolder,
) {
    @JavascriptInterface
    fun onPlayerState(state: Int) {
        holder.onPlayerState(state)
    }

    @JavascriptInterface
    fun onCoverStatus(status: String) {
        holder.onCoverStatus(status)
    }

    @JavascriptInterface
    fun onSiteEvent(event: String) {
        holder.onSiteEvent(event)
    }
}

private fun createWebViewClient(holder: PpabangWebViewHolder): WebViewClient {
    return object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            if (!request.isForMainFrame) return false
            val allowed = request.url.scheme == "https" && request.url.host == PpabangPolicy.HOST
            if (!allowed) holder.stopDocument()
            return !allowed
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            holder.pageGeneration += 1
            val session = holder.activeSession
            if (!holder.documentStopped &&
                session?.isResumed() == true &&
                !holder.isLocallyPausedForAppSwitch && !holder.isManualPaused
            ) {
                holder.reportPlaybackState(PpabangPlaybackState.LOADING, null)
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (holder.isTrustedPage(view) && !holder.documentStopped) {
                val generation = holder.pageGeneration
                view.evaluateJavascript(PpabangPolicy.INJECTED_SETUP_JS) {
                    val liveSession = holder.activeSession
                    if (liveSession?.isResumed() == true &&
                        !holder.isLocallyPausedForAppSwitch && !holder.isManualPaused
                    ) {
                        view.evaluateJavascript(JS_SITE_READINESS_POLL, null)
                        holder.deliverPendingCommand(view, generation, liveSession.leaseToken)
                    }
                }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                holder.documentStopped = true
                holder.pendingCommand = null
                holder.reportPlaybackState(
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
            holder.reportPlaybackState(
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
            holder.onRenderProcessGone(view)
            holder.reportPlaybackState(
                PpabangPlaybackState.FAILED,
                "웹 뷰가 재설정되었습니다",
            )
            return true
        }
    }
}

private fun createWebChromeClient(): WebChromeClient {
    return object : WebChromeClient() {
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
}

/**
 * Screen-owned holder that reuses a single WebView across portrait/landscape branch
 * switches without reloading YouTube player state, with lease token detachment protection
 * and persistent ownership of runtime state and live callback routing.
 */
class PpabangWebViewHolder {
    private val handler = Handler(Looper.getMainLooper())
    var webView: WebView? = null
        private set

    private var detachRunnable: Runnable? = null
    var activeLeaseToken: Long = 0L
        private set
    private var nextLeaseToken: Long = 1L
    var activeSession: PpabangSessionCallbacks? = null
        private set

    var loadedCategory: PpabangCategory? = null
    var documentStopped: Boolean = false
    var isManualPaused: Boolean = false
    var isLocallyPausedForAppSwitch: Boolean = false
    var pendingCommand: PpabangCommand? = null
    var pageGeneration: Int = 0
    var requestedConfirmationJob: Job? = null

    fun newLeaseToken(): Long = nextLeaseToken++

    fun bindSession(session: PpabangSessionCallbacks) {
        activeSession = session
        activeLeaseToken = session.leaseToken
    }

    fun unbindSession(leaseToken: Long) {
        if (activeLeaseToken == leaseToken) {
            activeSession = null
            requestedConfirmationJob?.cancel()
            requestedConfirmationJob = null
        }
    }

    fun acquireWebView(context: Context, factory: (Context) -> WebView): WebView =
        acquireWebView(context, newLeaseToken(), factory)

    fun acquireWebView(context: Context, leaseToken: Long, factory: (Context) -> WebView): WebView {
        detachRunnable?.let {
            handler.removeCallbacks(it)
            detachRunnable = null
        }
        activeLeaseToken = leaseToken
        val existing = webView
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val newView = factory(context)
        webView = newView
        return newView
    }

    fun releaseWithGrace(view: WebView, graceMs: Long = 150L, onRealDispose: (WebView) -> Unit) =
        releaseWithGrace(activeLeaseToken, view, graceMs, onRealDispose)

    fun releaseWithGrace(leaseToken: Long, view: WebView, graceMs: Long = 150L, onRealDispose: (WebView) -> Unit) {
        if (leaseToken != activeLeaseToken) {
            return
        }
        detachRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            detachRunnable = null
            if (activeLeaseToken == leaseToken && webView === view) {
                disposeNow(onRealDispose)
            }
        }
        detachRunnable = r
        handler.postDelayed(r, graceMs)
    }

    fun isTrustedPage(view: WebView? = webView): Boolean {
        val url = view?.url ?: return false
        val uri = android.net.Uri.parse(url)
        return uri.scheme == "https" && uri.host == PpabangPolicy.HOST
    }

    fun executeJs(js: String) {
        val view = webView ?: return
        if (isTrustedPage(view) && !documentStopped) {
            view.evaluateJavascript(js, null)
        }
    }

    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    fun reportPlaybackState(state: PpabangPlaybackState, message: String?) {
        activeSession?.reportPlaybackState?.invoke(state, message)
    }

    fun stopDocument() {
        requestedConfirmationJob?.cancel()
        requestedConfirmationJob = null
        documentStopped = true
        isLocallyPausedForAppSwitch = false
        isManualPaused = false
        pendingCommand = null
        pageGeneration += 1
        webView?.let { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
        }
        reportPlaybackState(PpabangPlaybackState.IDLE, null)
    }

    fun pausePlayback(isManual: Boolean = false) {
        if (documentStopped) return
        requestedConfirmationJob?.cancel()
        requestedConfirmationJob = null
        if (isManual) {
            isManualPaused = true
        } else {
            isLocallyPausedForAppSwitch = true
        }
        pendingCommand = null
        executeJs(JS_PAUSE_COMMAND)
        webView?.onPause()
        if (isManual) {
            reportPlaybackState(PpabangPlaybackState.PAUSED, null)
        }
    }

    fun startRequestedConfirmation(generation: Int) {
        requestedConfirmationJob?.cancel()
        val session = activeSession ?: return
        requestedConfirmationJob = session.launchJob {
            delay(4_000L)
            if (generation == pageGeneration && !documentStopped && !isManualPaused) {
                val currentState = session.getUiState()
                if (currentState.ppabangPlaybackState == PpabangPlaybackState.REQUESTED) {
                    session.reportPlaybackState(
                        PpabangPlaybackState.AUTOPLAY_BLOCKED,
                        "화면을 터치하여 재생을 시작하세요",
                    )
                }
            }
        }
    }

    fun deliverPendingCommand(leaseToken: Long, attempt: Int = 0) {
        val view = webView ?: return
        deliverPendingCommand(view, pageGeneration, leaseToken, attempt)
    }

    fun deliverPendingCommand(view: WebView, generation: Int, leaseToken: Long, attempt: Int = 0) {
        val session = activeSession ?: return
        if (leaseToken != activeLeaseToken || webView !== view || documentStopped || generation != pageGeneration || !isTrustedPage(view)) return
        if (!session.isResumed() || isLocallyPausedForAppSwitch || isManualPaused) {
            return
        }
        val command = pendingCommand ?: return
        val script = when (command) {
            PpabangCommand.NEXT -> PpabangPolicy.JS_NEXT_COMMAND
            PpabangCommand.RESUME -> JS_RESUME_COMMAND
            else -> PpabangPolicy.JS_PLAY_COMMAND
        }
        view.evaluateJavascript(
            "(function(){if(!document.querySelector('#queueList button') || !document.querySelector('iframe#player')) return false; " +
                script + "; return true;})()",
        ) { ready ->
            val currentSession = activeSession ?: return@evaluateJavascript
            if (leaseToken != activeLeaseToken || webView !== view || documentStopped || generation != pageGeneration) return@evaluateJavascript
            if (!currentSession.isResumed() || isLocallyPausedForAppSwitch || isManualPaused) {
                return@evaluateJavascript
            }
            if (ready == "true") {
                pendingCommand = null
                if (command == PpabangCommand.PLAY || command == PpabangCommand.RESUME || command == PpabangCommand.NEXT) {
                    currentSession.reportPlaybackState(PpabangPlaybackState.REQUESTED, null)
                    startRequestedConfirmation(generation)
                }
            } else if (attempt < 40) { // 40 attempts * 500ms = 20s readiness timeout
                view.postDelayed({ deliverPendingCommand(view, generation, leaseToken, attempt + 1) }, 500)
            } else {
                pendingCommand = null
                currentSession.reportPlaybackState(PpabangPlaybackState.FAILED, PpabangPolicy.PLAYER_UNAVAILABLE_MESSAGE)
            }
        }
    }

    fun onPlayerState(playerState: Int) {
        val view = webView ?: return
        view.post {
            if (webView !== view || !isTrustedPage(view) || documentStopped) return@post
            val session = activeSession ?: return@post
            val isForeground = session.isResumed()
            if (!isForeground || isLocallyPausedForAppSwitch || isManualPaused) {
                if (playerState == 1) {
                    executeJs(JS_PAUSE_COMMAND)
                }
                return@post
            }
            when (playerState) {
                1 -> {
                    requestedConfirmationJob?.cancel()
                    session.reportPlaybackState(PpabangPlaybackState.PLAYING, null)
                }
                2 -> session.reportPlaybackState(PpabangPlaybackState.PAUSED, null)
                3 -> session.reportPlaybackState(PpabangPlaybackState.BUFFERING, null)
                else -> Unit
            }
        }
    }

    fun onCoverStatus(coverStatus: String) {
        val view = webView ?: return
        view.post {
            if (webView !== view || !isTrustedPage(view) || documentStopped) return@post
            val session = activeSession ?: return@post
            val isForeground = session.isResumed()
            if (!isForeground || isLocallyPausedForAppSwitch || isManualPaused) return@post
            when (coverStatus) {
                "BLOCKED" -> session.reportPlaybackState(
                    PpabangPlaybackState.AUTOPLAY_BLOCKED,
                    "화면을 터치하여 재생을 시작하세요",
                )
                "EMPTY" -> session.reportPlaybackState(
                    PpabangPlaybackState.FAILED,
                    PpabangPolicy.EMPTY_LIST_MESSAGE,
                )
                "START_COVER" -> {
                    if (session.getUiState().ppabangPlaybackState != PpabangPlaybackState.PLAYING) {
                        session.reportPlaybackState(PpabangPlaybackState.IDLE, null)
                    }
                }
            }
        }
    }

    fun onSiteEvent(siteEvent: String) {
        val view = webView ?: return
        view.post {
            if (webView !== view || !isTrustedPage(view) || documentStopped) return@post
            val session = activeSession ?: return@post
            when (siteEvent) {
                "ready" -> {
                    if (pendingCommand != null &&
                        !documentStopped &&
                        !isManualPaused &&
                        !isLocallyPausedForAppSwitch &&
                        session.isResumed()
                    ) {
                        deliverPendingCommand(view, pageGeneration, session.leaseToken)
                    }
                }
                "empty" -> {
                    session.reportPlaybackState(PpabangPlaybackState.FAILED, PpabangPolicy.EMPTY_LIST_MESSAGE)
                }
                "emptyCleared" -> {
                    if (session.getUiState().ppabangPlaybackState != PpabangPlaybackState.PLAYING) {
                        session.reportPlaybackState(PpabangPlaybackState.IDLE, null)
                    }
                }
            }
        }
    }

    fun onRenderProcessGone(view: WebView) {
        if (webView === view) {
            webView = null
        }
    }

    fun disposeNow(onRealDispose: ((WebView) -> Unit)? = null) {
        detachRunnable?.let {
            handler.removeCallbacks(it)
            detachRunnable = null
        }
        requestedConfirmationJob?.cancel()
        requestedConfirmationJob = null
        activeSession = null
        loadedCategory = null
        documentStopped = true
        pendingCommand = null
        isManualPaused = false
        isLocallyPausedForAppSwitch = false
        val v = webView
        webView = null
        if (v != null) {
            (v.parent as? ViewGroup)?.removeView(v)
            if (onRealDispose != null) {
                onRealDispose(v)
            } else {
                v.removeJavascriptInterface(PpabangPolicy.JS_BRIDGE_NAME)
                v.stopLoading()
                v.loadUrl("about:blank")
                v.onPause()
                v.removeAllViews()
                v.destroy()
            }
        }
    }
}

@Composable
fun rememberPpabangWebViewHolder(): PpabangWebViewHolder {
    val holder = remember { PpabangWebViewHolder() }
    DisposableEffect(holder) {
        onDispose {
            holder.disposeNow { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.removeJavascriptInterface(PpabangPolicy.JS_BRIDGE_NAME)
                view.stopLoading()
                view.loadUrl("about:blank")
                view.onPause()
                view.removeAllViews()
                view.destroy()
            }
        }
    }
    return holder
}

/**
 * Exact category emoji mapping matching iPhone contract:
 * ccm🙏 ballad🎤 girlgroup💃 legends🏆 crossEdit🎬 golfHorizontal🏌️ golfVertical⛳
 * game🎮 mukbang🍜 camping⛺ travel✈️ lounge☕ bedroom🌙 other▶️
 */
fun getExactCategoryEmoji(category: PpabangCategory): String = when (category.id) {
    "ccm" -> "🙏"
    "ballad" -> "🎤"
    "girlgroup" -> "💃"
    "legends" -> "🏆"
    "crossEdit", "cross-edit", "cross_edit" -> "🎬"
    "golfHorizontal" -> "🏌️"
    "golfVertical" -> "⛳"
    "game" -> "🎮"
    "mukbang" -> "🍜"
    "camping" -> "⛺"
    "travel" -> "✈️"
    "lounge" -> "☕"
    "bedroom" -> "🌙"
    else -> "▶️"
}

/**
 * 3 vertical cards (98x66, gap 7):
 * 1. icon-only playpause
 * 2. icon-only next
 * 3. category emoji 18 + label 12
 */
@Composable
fun PpabangControlCards(
    playbackState: PpabangPlaybackState,
    currentCategory: PpabangCategory,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onCategoryClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPause: (() -> Unit)? = null,
    backgroundOpacity: Float = 1f,
) {
    val isPlaying = playbackState == PpabangPlaybackState.PLAYING
    Column(
        modifier = modifier.width(98.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Card 1: icon-only playpause
        Surface(
            modifier = Modifier
                .size(width = 98.dp, height = 66.dp)
                .standFocusable(shape = RoundedCornerShape(16.dp))
                .standPanelSurface(
                    isDimmed = false,
                    cornerRadius = 16.dp,
                    splitGap = 2.dp,
                )
                .clickable {
                    if (isPlaying) {
                        onPause?.invoke() ?: onStop()
                    } else {
                        onPlay()
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = if (isPlaying) "일시정지" else "재생"
                },
            color = Color.Transparent,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Card 2: icon-only next
        Surface(
            modifier = Modifier
                .size(width = 98.dp, height = 66.dp)
                .standFocusable(shape = RoundedCornerShape(16.dp))
                .standPanelSurface(
                    isDimmed = false,
                    cornerRadius = 16.dp,
                    splitGap = 2.dp,
                )
                .clickable { onNext() }
                .semantics {
                    role = Role.Button
                    contentDescription = "다음 영상"
                },
            color = Color.Transparent,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Card 3: category emoji 18 + label 12
        Surface(
            modifier = Modifier
                .size(width = 98.dp, height = 66.dp)
                .standFocusable(shape = RoundedCornerShape(16.dp))
                .standPanelSurface(
                    isDimmed = false,
                    cornerRadius = 16.dp,
                    splitGap = 2.dp,
                )
                .clickable { onCategoryClick() }
                .semantics {
                    role = Role.Button
                    contentDescription = "${currentCategory.title} 카테고리 선택"
                },
            color = Color.Transparent,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = getExactCategoryEmoji(currentCategory),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentCategory.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Reusable composable control helper for caller wiring.
 */
@Composable
fun PpabangPlayerControls(
    state: StandUiState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onSelectCategoryClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPause: (() -> Unit)? = null,
    backgroundOpacity: Float = 1f,
) {
    PpabangControlCards(
        playbackState = state.ppabangPlaybackState,
        currentCategory = state.ppabangCategory,
        onPlay = onPlay,
        onStop = onStop,
        onNext = onNext,
        onCategoryClick = onSelectCategoryClick,
        modifier = modifier,
        onPause = onPause,
        backgroundOpacity = backgroundOpacity,
    )
}

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
    webViewHolder: PpabangWebViewHolder? = null,
    onPause: (() -> Unit)? = null,
    onOpenCategoryPicker: (() -> Unit)? = null,
    showControls: Boolean = true,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val videoWidth = if (isTelevision) 160.dp else 216.dp
        val controlsWidth = 98.dp
        val gap = 8.dp
        val shouldShowControls = showControls && maxWidth >= (videoWidth + gap + controlsWidth)
        val totalWidth = if (shouldShowControls) videoWidth + gap + controlsWidth else videoWidth
        val controlsHeight = (66 * 3 + 7 * 2).dp // 212.dp
        val totalHeight = if (shouldShowControls) maxOf(videoWidth, controlsHeight) else videoWidth
        val maxY = with(density) { (maxHeight - totalHeight).toPx().coerceAtLeast(0f) }
        DisposableEffect(Unit) { onDispose { onFrameChanged(Rect.Zero) } }
        PpabangInlinePlayer(
            state = state,
            isTelevision = isTelevision,
            isPortrait = isPortrait,
            commandFlow = commandFlow,
            onPlay = onPlay,
            onStop = onStop,
            onNext = onNext,
            onSelectCategory = onSelectCategory,
            onClose = onClose,
            onPlaybackStateChanged = onPlaybackStateChanged,
            modifier = Modifier
                .offset {
                    IntOffset(
                        0, // bottomLEFT: videoLEFT, controlsRIGHT
                        maxY.roundToInt(),
                    )
                }
                .width(totalWidth)
                .onGloballyPositioned { onFrameChanged(it.boundsInWindow()) },
            webViewHolder = webViewHolder,
            onPause = onPause,
            onOpenCategoryPicker = onOpenCategoryPicker,
            showControls = shouldShowControls,
        )
    }
}

/**
 * Inline visible video player for Ppabang.
 * Video: 216 square (200 viewport), TV: 160 square.
 * Controls: 3 vertical cards 98x66 with gap 7 to the right of video.
 * Supports safe parent detachment/re-attachment via screen-owned PpabangWebViewHolder.
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
    webViewHolder: PpabangWebViewHolder? = null,
    onPause: (() -> Unit)? = null,
    onOpenCategoryPicker: (() -> Unit)? = null,
    showControls: Boolean = true,
) {
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val grayscalePaint = remember {
        Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
    }
    val holder = webViewHolder ?: rememberPpabangWebViewHolder()
    val leaseToken = remember { holder.newLeaseToken() }
    val currentState by rememberUpdatedState(state)
    val reportState by rememberUpdatedState(onPlaybackStateChanged)
    var showCategoryDialogInternal by remember { mutableStateOf(false) }

    val session = remember(leaseToken) {
        PpabangSessionCallbacks(
            leaseToken = leaseToken,
            isResumed = { lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
            reportPlaybackState = { st, msg -> reportState(st, msg) },
            getUiState = { currentState },
            launchJob = { block -> coroutineScope.launch { block() } },
        )
    }

    DisposableEffect(leaseToken) {
        holder.bindSession(session)
        onDispose {
            holder.unbindSession(leaseToken)
        }
    }

    LaunchedEffect(commandFlow) {
        commandFlow.collect { command ->
            when (command) {
                PpabangCommand.PLAY -> {
                    holder.isLocallyPausedForAppSwitch = false
                    holder.isManualPaused = false
                    if (holder.documentStopped) {
                        holder.documentStopped = false
                        holder.pendingCommand = PpabangCommand.PLAY
                        holder.loadedCategory = currentState.ppabangCategory
                        holder.webView?.loadUrl(currentState.ppabangCategory.url)
                    } else {
                        holder.pendingCommand = PpabangCommand.PLAY
                        holder.deliverPendingCommand(leaseToken)
                    }
                }
                PpabangCommand.STOP -> {
                    holder.stopDocument()
                }
                PpabangCommand.NEXT -> {
                    holder.isLocallyPausedForAppSwitch = false
                    holder.isManualPaused = false
                    if (holder.documentStopped) {
                        holder.documentStopped = false
                        holder.pendingCommand = PpabangCommand.NEXT
                        holder.loadedCategory = currentState.ppabangCategory
                        holder.webView?.loadUrl(currentState.ppabangCategory.url)
                    } else {
                        holder.pendingCommand = PpabangCommand.NEXT
                        holder.deliverPendingCommand(leaseToken)
                    }
                }
                PpabangCommand.PAUSE -> {
                    holder.pausePlayback(isManual = true)
                }
                PpabangCommand.RESUME -> {
                    if (!holder.documentStopped) {
                        holder.isLocallyPausedForAppSwitch = false
                        holder.isManualPaused = false
                        holder.pendingCommand = PpabangCommand.RESUME
                        holder.deliverPendingCommand(leaseToken)
                    }
                }
            }
        }
    }

    LaunchedEffect(state.ppabangCategory) {
        val target = state.ppabangCategory
        if (holder.loadedCategory != target) {
            holder.loadedCategory = target
            holder.documentStopped = false
            holder.isLocallyPausedForAppSwitch = false
            holder.isManualPaused = false
            holder.pendingCommand = PpabangCommand.PLAY
            holder.webView?.let { wv ->
                reportState(PpabangPlaybackState.LOADING, null)
                wv.loadUrl(target.url)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    holder.isLocallyPausedForAppSwitch = false
                    holder.webView?.onResume()
                    // Do not auto-resume if user explicitly manually paused
                    if (!holder.isManualPaused && holder.pendingCommand != null && !holder.documentStopped) {
                        holder.deliverPendingCommand(leaseToken)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    holder.pausePlayback(isManual = false)
                }
                Lifecycle.Event.ON_STOP -> {
                    holder.pausePlayback(isManual = false)
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Video box: 216 square (200 viewport), TV: 160 square
        Surface(
            modifier = Modifier.size(playerSide),
            color = lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                0.30f,
            ).copy(alpha = backgroundOpacity),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f * backgroundOpacity),
            ),
        ) {
            Box(Modifier.padding(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(videoSide)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Transparent)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .clickable {
                            // Touch emptyState to retry if in empty state or autoplay blocked
                            if (currentState.ppabangPlaybackState == PpabangPlaybackState.AUTOPLAY_BLOCKED) {
                                holder.executeJs(PpabangPolicy.JS_PLAY_COMMAND)
                            } else if (currentState.ppabangPlaybackState == PpabangPlaybackState.FAILED) {
                                holder.executeJs("var es = document.getElementById('emptyState'); if (es) es.click();")
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { context ->
                            val configureNewView = { ctx: Context ->
                                WebView(ctx).apply {
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
                                        isFocusable = false
                                        isFocusableInTouchMode = false
                                        setOnTouchListener { _, _ -> true }
                                    }

                                    addJavascriptInterface(
                                        PpabangBridge(holder),
                                        PpabangPolicy.JS_BRIDGE_NAME,
                                    )

                                    webViewClient = createWebViewClient(holder)
                                    webChromeClient = createWebChromeClient()

                                    holder.loadedCategory = state.ppabangCategory
                                    if (state.ppabangPlaybackState != PpabangPlaybackState.IDLE) {
                                        loadUrl(state.ppabangCategory.url)
                                    } else {
                                        loadUrl("about:blank")
                                    }
                                }
                            }

                            val view = holder.acquireWebView(context, leaseToken, configureNewView)
                            view.webViewClient = createWebViewClient(holder)
                            view.webChromeClient = createWebChromeClient()
                            if (isTelevision) {
                                view.isFocusable = false
                                view.isFocusableInTouchMode = false
                                view.setOnTouchListener { _, _ -> true }
                            } else {
                                view.setOnTouchListener(null)
                            }
                            view
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            val isGrayscale = state.settings.displayTheme == StandDisplayTheme.GRAYSCALE
                            view.setLayerType(
                                if (isGrayscale) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
                                if (isGrayscale) grayscalePaint else null,
                            )
                        },
                        onRelease = { releasedView ->
                            holder.releaseWithGrace(leaseToken, releasedView, graceMs = 150L) { viewToDestroy ->
                                (viewToDestroy.parent as? ViewGroup)?.removeView(viewToDestroy)
                                viewToDestroy.removeJavascriptInterface(PpabangPolicy.JS_BRIDGE_NAME)
                                viewToDestroy.stopLoading()
                                viewToDestroy.loadUrl("about:blank")
                                viewToDestroy.onPause()
                                viewToDestroy.removeAllViews()
                                viewToDestroy.destroy()
                            }
                        },
                    )
                }
            }
        }

        // Controls to the RIGHT of video
        if (showControls) {
            PpabangControlCards(
                playbackState = currentState.ppabangPlaybackState,
                currentCategory = currentState.ppabangCategory,
                onPlay = onPlay,
                onStop = onStop,
                onNext = onNext,
                onCategoryClick = {
                    if (onOpenCategoryPicker != null) {
                        onOpenCategoryPicker()
                    } else {
                        showCategoryDialogInternal = true
                    }
                },
                onPause = onPause ?: { holder.pausePlayback(isManual = true) },
                backgroundOpacity = backgroundOpacity,
            )
        }
    }

    if (showCategoryDialogInternal) {
        PpabangCategoryDialog(
            currentCategory = currentState.ppabangCategory,
            categories = currentState.ppabangCategories,
            onSelect = { category ->
                showCategoryDialogInternal = false
                onSelectCategory(category)
            },
            onDismiss = { showCategoryDialogInternal = false },
        )
    }
}

/**
 * 3-column cards category picker dialog:
 * minHeight 82, gap 10, radius 16, padding 20, emoji 18, label 12 semibold.
 * Header 50 / section 16: "빠방 재생목록" / "원하는 음악과 영상을 골라 주세요."
 * Height: 50 + 16 + rows * 82 + max(0, rows - 1) * 10 + 40 with scroll.
 * Selected: full color accent, others grayscale/dim with semantics and D-pad.
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
    val rows = (categories.size + 2) / 3
    val calculatedContentHeight = 50.dp + 16.dp + (rows * 82).dp + (maxOf(0, rows - 1) * 10).dp + 40.dp
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedCategoryIndex) {
        if (selectedCategoryIndex >= 0) {
            selectedCategoryFocusRequester.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 460.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1E1E22),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = calculatedContentHeight.coerceAtMost(560.dp))
                    .verticalScroll(scrollState)
                    .padding(20.dp),
            ) {
                // Header: 50dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "빠방 재생목록",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
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

                // Section: 16dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "원하는 음악과 영상을 골라 주세요",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3-column cards
                val chunkedCategories = remember(categories) { categories.chunked(3) }
                val selectedAccent = MaterialTheme.colorScheme.primary
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    chunkedCategories.forEach { rowCategories ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowCategories.forEach { category ->
                                val isSelected = category == currentCategory
                                var isFocused by remember(category) { mutableStateOf(false) }
                                val emoji = getExactCategoryEmoji(category)

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 82.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .then(
                                            if (isSelected) {
                                                Modifier.focusRequester(selectedCategoryFocusRequester)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .clickable { onSelect(category) }
                                        .standFocusable(shape = RoundedCornerShape(16.dp))
                                        .semantics {
                                            role = Role.Button
                                            this.selected = isSelected
                                            contentDescription = "${category.title} 채널 ${if (isSelected) "선택됨" else "선택"}"
                                        },
                                    color = when {
                                        isFocused -> selectedAccent.copy(alpha = 0.55f)
                                        isSelected -> selectedAccent.copy(alpha = 0.85f)
                                        else -> Color.White.copy(alpha = 0.06f)
                                    },
                                    border = when {
                                        isFocused -> androidx.compose.foundation.BorderStroke(2.5.dp, selectedAccent.copy(alpha = 0.9f))
                                        isSelected -> androidx.compose.foundation.BorderStroke(1.5.dp, selectedAccent)
                                        else -> androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 6.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = 18.sp,
                                            lineHeight = 22.sp,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = category.title,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                            // Pad remaining cells in last row if fewer than 3 items
                            val emptySlots = 3 - rowCategories.size
                            repeat(emptySlots) {
                                Spacer(modifier = Modifier.weight(1f))
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
    PAUSE,
    RESUME,
}
