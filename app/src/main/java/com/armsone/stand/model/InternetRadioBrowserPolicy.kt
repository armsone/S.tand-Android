package com.armsone.stand.model

import java.net.URI
import java.net.URLEncoder

data class InternetRadioBrowserFavorite(
    val title: String,
    val url: String,
    val isHomepage: Boolean = false,
)

sealed interface InternetRadioBrowserAddressResult {
    data class Valid(val url: String) : InternetRadioBrowserAddressResult
    data class Invalid(val message: String) : InternetRadioBrowserAddressResult
}

object InternetRadioBrowserPolicy {
    const val MAXIMUM_ADDRESS_LENGTH = 2_048

    val favorites = listOf(
        InternetRadioBrowserFavorite("Google", "https://www.google.com/", isHomepage = true),
        InternetRadioBrowserFavorite("한국 라디오", "https://radio.bsod.kr/"),
        InternetRadioBrowserFavorite(
            "내가 사랑하는 인터넷 라디오",
            "https://blog.naver.com/armsone/224388181252",
        ),
        InternetRadioBrowserFavorite("FMSTREAM", "https://fmstream.org/"),
        InternetRadioBrowserFavorite("Radio Browser", "https://www.radio-browser.info/"),
    )

    val homepage: String = favorites.first { it.isHomepage }.url

    fun browsingAddress(rawInput: String): InternetRadioBrowserAddressResult {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            return InternetRadioBrowserAddressResult.Invalid("웹 주소를 입력해 주세요.")
        }
        if (input.length > MAXIMUM_ADDRESS_LENGTH) {
            return InternetRadioBrowserAddressResult.Invalid("웹 주소가 너무 깁니다.")
        }

        val explicitScheme = runCatching { URI(input).scheme }.getOrNull()
        val candidate = when {
            !explicitScheme.isNullOrEmpty() -> input
            input.none(Char::isWhitespace) && input.contains('.') -> "https://$input"
            else -> {
                @Suppress("DEPRECATION")
                val query = URLEncoder.encode(input, "UTF-8")
                "https://www.google.com/search?q=$query"
            }
        }
        return if (isSecureWebAddress(candidate)) {
            InternetRadioBrowserAddressResult.Valid(candidate)
        } else {
            InternetRadioBrowserAddressResult.Invalid(
                "https://로 시작하는 안전한 웹 주소만 열 수 있습니다.",
            )
        }
    }

    fun isSecureWebAddress(rawAddress: String?): Boolean {
        val address = rawAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (address.length > MAXIMUM_ADDRESS_LENGTH) return false
        val uri = runCatching { URI(address) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }
}
