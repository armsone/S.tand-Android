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
            "crossEdit", "cross-edit", "cross_edit" -> "교차편집"
            "ballad" -> "가요톱텐"
            "hiphop" -> "힙합"
            "game" -> "게임"
            "mukbang" -> "먹방"
            "travel" -> "여행"
            "ccm" -> "CCM"
            "lounge" -> "라운지"
            "bedroom" -> "베드룸"
            "amv" -> "AMV"
            else -> id.replace('-', ' ').replace('_', ' ')
        }

    val emoji: String
        get() = when (id) {
            "golfVertical" -> "⛳"
            "golfHorizontal" -> "🏌️"
            "camping" -> "⛺"
            "girlgroup" -> "💃"
            "legends" -> "🏆"
            "ballad" -> "🎤"
            "game" -> "🎮"
            "mukbang" -> "🍜"
            "travel" -> "✈️"
            "ccm" -> "🙏"
            "lounge" -> "☕"
            "bedroom" -> "🌙"
            "crossEdit", "cross-edit", "cross_edit" -> "🎬"
            else -> "▶️"
        }

    val url: String get() = "${PpabangPolicy.BASE_URL}?category=$id&standSession=${System.currentTimeMillis()}"

    companion object {
        val DEFAULT = PpabangCategory("ccm")
        val fallbackCategories = listOf(
            "ccm", "ballad", "girlgroup", "legends", "crossEdit", "hiphop",
            "golfHorizontal", "golfVertical", "game", "mukbang", "camping",
            "travel", "lounge", "bedroom", "amv",
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
            val websiteOrder = PpabangCategory.fallbackCategories
                .mapIndexed { index, category -> category.id to index }.toMap()
            categories.keys().asSequence()
                .filter { id -> (categories.optJSONObject(id)?.optInt("count", 0) ?: 0) > 0 }
                .map(::PpabangCategory)
                .sortedWith(
                    compareBy<PpabangCategory> { websiteOrder[it.id] ?: Int.MAX_VALUE }
                        .thenBy { it.id },
                )
                .toList()
        } finally {
            connection.disconnect()
        }
    }
}

enum class PpabangPlaybackState {
    IDLE,
    LOADING,
    READY,
    REQUESTED,
    PLAYING,
    PAUSED,
    BUFFERING,
    AUTOPLAY_BLOCKED,
    FAILED;

    val displayText: String
        get() = when (this) {
            IDLE -> "대기 중"
            LOADING -> "불러오는 중"
            READY -> "재생 준비됨"
            REQUESTED -> "재생 요청 중"
            PLAYING -> "재생 중"
            PAUSED -> "일시 정지"
            BUFFERING -> "버퍼링 중"
            AUTOPLAY_BLOCKED -> "영상을 탭해 시작"
            FAILED -> "연결 실패"
        }
}

object PpabangPolicy {
    const val BASE_URL = "https://ppabang.net/"
    const val HOST = "ppabang.net"
    const val JS_BRIDGE_NAME = "StandPpabangBridge"
    const val MINIMUM_VIEWPORT_SIZE_DP = 200
    const val EMPTY_LIST_MESSAGE = "재생할 수 있는 영상이 없습니다. 영상 화면을 탭하면 목록을 다시 불러옵니다."
    const val PLAYER_UNAVAILABLE_MESSAGE = "플레이어를 준비하지 못했습니다. 정지 후 다시 재생해 주세요."

    /**
     * Exact presentation CSS from iOS PpabangPlayer:
     * Hides brand-mark img, start-cover img, empty-state img, queue, etc.
     */
    val INJECTED_CSS = """
        html,body{margin:0!important;padding:0!important;width:100%!important;height:100%!important;overflow:hidden!important;background:transparent!important;}
        .brand-bar,.queue,.player-help{display:none!important;}
        .brand-mark img,.start-cover img,.empty-state img{display:none!important;}
        .empty-state{cursor:pointer!important;padding:16px!important;}
        .empty-state strong{font-size:17px!important;}
        .shorts-shell,.stage,#playerFrame,.video-surface{margin:0!important;padding:0!important;width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;min-width:0!important;min-height:0!important;box-sizing:border-box!important;}
        .shorts-shell,.stage{display:block!important;}
        #playerFrame{border-radius:0!important;aspect-ratio:auto!important;}
        .stage{position:relative!important;top:0!important;inset:0!important;min-height:0!important;}
        #playerFrame{position:relative!important;inset:0!important;border:0!important;box-shadow:none!important;display:block!important;}
        .video-surface{position:absolute!important;inset:0!important;flex:none!important;aspect-ratio:auto!important;}
        .player-toolbar{display:none!important;}
        #playerFrame,.video-surface{background:transparent!important;}
        #player{width:100%!important;height:100%!important;min-width:0!important;min-height:0!important;}
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
