package com.armsone.stand.model

import java.net.URI
import java.util.UUID

data class InternetRadioConfiguration(
    val displayName: String,
    val streamUrl: String,
    val id: String = UUID.randomUUID().toString(),
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
        val stableID = id.trim().takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        return copy(displayName = name, streamUrl = url, id = stableID)
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

object InternetRadioReconnectPolicy {
    private val DelaySeconds = listOf(1, 2, 4, 8, 15)

    fun delaySeconds(attempt: Int): Int? = DelaySeconds.getOrNull(attempt.coerceAtLeast(0))
}

object InternetRadioMutationPolicy {
    fun shouldStopForSave(
        activeChannelID: String?,
        previous: InternetRadioConfiguration?,
        updated: InternetRadioConfiguration,
    ): Boolean = previous != null &&
        activeChannelID == previous.id &&
        previous.streamUrl != updated.streamUrl

    fun shouldStopForDelete(activeChannelID: String?, deletedChannelID: String): Boolean =
        activeChannelID == deletedChannelID

    fun selectedChannelIDAfterSave(
        currentSelectedChannelID: String?,
        editedChannelID: String?,
        savedChannelID: String,
    ): String = if (editedChannelID == null) savedChannelID else currentSelectedChannelID ?: savedChannelID
}

object RadioShareImportPolicy {
    fun validatedUrlOrNull(sharedText: String?): String? {
        val value = sharedText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return InternetRadioConfiguration("인터넷 라디오", value)
            .normalizedOrNull()
            ?.streamUrl
    }
}
