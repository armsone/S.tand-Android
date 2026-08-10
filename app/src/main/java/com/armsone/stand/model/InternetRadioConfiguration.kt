package com.armsone.stand.model

import java.net.URI

data class InternetRadioConfiguration(
    val displayName: String,
    val streamUrl: String,
) {
    fun normalizedOrNull(): InternetRadioConfiguration? {
        val name = displayName.trim().ifEmpty { "인터넷 라디오" }
        val url = streamUrl.trim()
        if (name.length > 30 || url.isEmpty() || url.length > 2_048) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() ||
            uri.userInfo != null
        ) return null
        return copy(displayName = name, streamUrl = url)
    }

    companion object {
        fun validationMessage(displayName: String, streamUrl: String): String? {
            val name = displayName.trim()
            val url = streamUrl.trim()
            if (name.length > 30) return "라디오 이름이 너무 깁니다."
            if (url.isEmpty()) return "라디오 주소를 입력해 주세요."
            if (url.length > 2_048) return "라디오 주소가 너무 깁니다."
            val uri = runCatching { URI(url) }.getOrNull()
                ?: return "서버 주소를 확인해 주세요."
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return "https://로 시작하는 안전한 스트림 주소만 사용할 수 있습니다."
            }
            if (uri.host.isNullOrBlank()) return "서버 주소를 확인해 주세요."
            if (uri.userInfo != null) {
                return "아이디나 비밀번호가 포함된 주소는 저장할 수 없습니다."
            }
            return null
        }
    }
}
