package com.armsone.stand.model

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Ppabang (빠방) category metadata. The available list is owned by the server so
 * additions and removals do not require an Android or Google TV app update.
 */
data class PpabangCategory(val id: String) {
    val title: String
        get() = when (id) {
            "golfVertical" -> "세로 골프"
            "golfHorizontal" -> "가로 골프"
            "camping" -> "캠핑"
            "girlgroup" -> "아이돌 뮤비"
            "legends" -> "경연"
            "ballad" -> "가요톱텐"
            "game" -> "게임"
            "mukbang" -> "먹방"
            "travel" -> "여행"
            "ccm" -> "CCM"
            "lounge" -> "라운지"
            "bedroom" -> "베드룸"
            else -> id.replace('-', ' ').replace('_', ' ')
        }

    val url: String get() = "${PpabangPolicy.BASE_URL}?category=$id&standSession=${System.currentTimeMillis()}"

    companion object {
        val DEFAULT = PpabangCategory("ccm")
        val fallbackCategories = listOf(
            "golfVertical", "golfHorizontal", "camping", "girlgroup", "legends", "ballad",
            "ccm", "lounge", "bedroom",
        ).map(::PpabangCategory)

        fun fromId(id: String?): PpabangCategory =
            id?.takeIf(String::isNotBlank)?.let(::PpabangCategory) ?: DEFAULT

        fun next(current: PpabangCategory, categories: List<PpabangCategory>): PpabangCategory {
            val available = categories.ifEmpty { fallbackCategories }
            val index = available.indexOf(current)
            return available[Math.floorMod(index + 1, available.size)]
        }
    }
}

object PpabangCatalog {
    /** Returns only categories that currently have playable videos. */
    fun fetchCategories(): List<PpabangCategory> {
        val connection = (URL("${PpabangPolicy.BASE_URL}api/catalog/status").openConnection() as HttpURLConnection)
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            if (connection.responseCode !in 200..299) return emptyList()
            val categories = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optJSONObject("categories") ?: return emptyList()
            categories.keys().asSequence()
                .filter { id -> (categories.optJSONObject(id)?.optInt("count", 0) ?: 0) > 0 }
                .map(::PpabangCategory)
                .sortedBy { it.title }
                .toList()
        } finally {
            connection.disconnect()
        }
    }
}

enum class PpabangPlaybackState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    STOPPED,
    AUTOPLAY_BLOCKED,
    FAILED;

    val displayText: String
        get() = when (this) {
            IDLE -> "대기"
            LOADING -> "연결 중"
            PLAYING -> "재생 중"
            PAUSED -> "일시 정지"
            STOPPED -> "정지"
            AUTOPLAY_BLOCKED -> "터치 필요"
            FAILED -> "연결 실패"
        }
}

object PpabangPolicy {
    const val BASE_URL = "https://ppabang.net/"
    const val HOST = "ppabang.net"
    const val JS_BRIDGE_NAME = "StandPpabangBridge"
    const val MINIMUM_VIEWPORT_SIZE_DP = 200

    /**
     * Minimal presentation CSS that preserves playerFrame, startCover, resumeCover,
     * emptyState, YouTube iframe official controls and branding, with a viewport
     * guaranteed to be at least 200x200 CSS pixels.
     */
    val INJECTED_CSS = """
        html, body {
            margin: 0 !important;
            padding: 0 !important;
            width: 100% !important;
            height: 100% !important;
            overflow: hidden !important;
            background: transparent !important;
        }
        .brand-bar, .queue, .player-help, .queue-note, .skip-toast, .panel-share, #shareCategory, #connectGoogle {
            display: none !important;
        }
        .shorts-shell, .stage {
            margin: 0 !important;
            padding: 0 !important;
            width: 100% !important;
            height: 100% !important;
            max-width: none !important;
            max-height: none !important;
            display: flex !important;
            align-items: center !important;
            justify-content: center !important;
            box-sizing: border-box !important;
        }
        .player-frame {
            width: 100% !important;
            height: 100% !important;
            min-width: 200px !important;
            min-height: 200px !important;
            max-width: none !important;
            max-height: none !important;
            border-radius: 0 !important;
            margin: 0 !important;
            box-shadow: none !important;
            position: relative !important;
        }
        .video-surface, .video-surface iframe, #player {
            width: 100% !important;
            height: 100% !important;
            min-width: 200px !important;
            min-height: 200px !important;
        }
        .stage { position: relative !important; inset: 0 !important; min-height: 0 !important; }
        .player-frame { display: block !important; border: 0 !important; aspect-ratio: auto !important; }
        .video-surface { position: absolute !important; inset: 0 !important; flex: none !important; aspect-ratio: auto !important; }
        .player-toolbar { display: none !important; }
        .player-frame, .video-surface { background: transparent !important; }
        .start-cover, .resume-cover, .empty-state {
            z-index: 10 !important;
        }
    """.trimIndent()

    /**
     * Injected script to setup CSS and bridge YouTube iframe postMessage commands
     * and DOM cover states.
     */
    val INJECTED_SETUP_JS = """
        (function() {
            if (location.origin !== 'https://ppabang.net') return;
            if (!document.getElementById('stand-ppabang-style')) {
                var style = document.createElement('style');
                style.id = 'stand-ppabang-style';
                style.textContent = ${escapedJsString(INJECTED_CSS)};
                document.head.appendChild(style);
            }

            if (!window.__standPpabangInitialized) {
                window.__standPpabangInitialized = true;

                window.addEventListener('message', function(event) {
                    var frame = document.querySelector('iframe#player');
                    if (!frame || event.source !== frame.contentWindow ||
                        (event.origin !== 'https://www.youtube.com' && event.origin !== 'https://www.youtube-nocookie.com')) {
                        return;
                    }
                    try {
                        var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                        if (data && data.event === 'infoDelivery' && data.info && data.info.playerState !== undefined) {
                            if (window.StandPpabangBridge && typeof window.StandPpabangBridge.onPlayerState === 'function') {
                                window.StandPpabangBridge.onPlayerState(Number(data.info.playerState));
                            }
                        }
                    } catch (_) {}
                });

                function checkCovers() {
                    var resumeCover = document.getElementById('resumeCover');
                    var startCover = document.getElementById('startCover');
                    var emptyState = document.getElementById('emptyState');
                    if (window.StandPpabangBridge) {
                        if (resumeCover && !resumeCover.hidden && resumeCover.offsetParent !== null) {
                            window.StandPpabangBridge.onCoverStatus('BLOCKED');
                        } else if (startCover && !startCover.hidden && startCover.offsetParent !== null) {
                            window.StandPpabangBridge.onCoverStatus('START_COVER');
                        } else if (emptyState && !emptyState.hidden && emptyState.offsetParent !== null) {
                            window.StandPpabangBridge.onCoverStatus('EMPTY');
                        } else {
                            window.StandPpabangBridge.onCoverStatus('CLEAR');
                        }
                    }
                }

                checkCovers();
                var observer = new MutationObserver(function() {
                    checkCovers();
                });
                observer.observe(document.body, {
                    attributes: true,
                    subtree: true,
                    attributeFilter: ['hidden', 'style', 'class']
                });
            }
        })();
    """.trimIndent()

    /**
     * JS command to trigger native Play:
     * Clicks #startCover or #resumeCover if visible, or sends YouTube iframe playVideo postMessage.
     */
    val JS_PLAY_COMMAND = """
        (function() {
            var sc = document.getElementById('startCover');
            if (sc && !sc.hidden && sc.offsetParent !== null) {
                sc.click();
                return;
            }
            var rc = document.getElementById('resumeCover');
            if (rc && !rc.hidden && rc.offsetParent !== null) {
                rc.click();
                return;
            }
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
    """.trimIndent()

    /**
     * JS command to trigger native STOP (clear/reset):
     * Pauses all audio/video and posts stopVideo / pauseVideo to YouTube iframe.
     */
    val JS_STOP_COMMAND = """
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
                        func: 'stopVideo',
                        args: []
                    }), origin);
                    iframe.contentWindow.postMessage(JSON.stringify({
                        event: 'command',
                        func: 'pauseVideo',
                        args: []
                    }), origin);
                } catch (_) {}
            }
        })();
    """.trimIndent()

    /**
     * JS command to trigger next track through site's existing #nextButton logic.
     */
    val JS_NEXT_COMMAND = """
        (function() {
            var nb = document.getElementById('nextButton');
            if (nb) {
                nb.click();
            }
        })();
    """.trimIndent()

    private fun escapedJsString(value: String): String =
        "\"" + value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "") + "\""
}
