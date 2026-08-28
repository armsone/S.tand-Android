package com.armsone.stand.model

import java.net.URI
import java.util.UUID

sealed interface RadioDecodeResult {
    data class Success(val channels: List<InternetRadioConfiguration>) : RadioDecodeResult
    data class Failure(val message: String) : RadioDecodeResult
}

data class RadioImportPreview(
    val importedChannels: List<InternetRadioConfiguration>,
    val duplicateChannels: List<InternetRadioConfiguration>,
    val newChannels: List<InternetRadioConfiguration>,
    val currentChannels: List<InternetRadioConfiguration>,
    val availableSlots: Int,
    val addableChannels: List<InternetRadioConfiguration>,
    val overflowChannels: List<InternetRadioConfiguration>,
) {
    val canAddAll: Boolean get() = newChannels.isNotEmpty() && overflowChannels.isEmpty()
    val isAllDuplicates: Boolean get() = importedChannels.isNotEmpty() && newChannels.isEmpty()
    val hasUnencryptedStreams: Boolean get() = importedChannels.any { it.isUnencrypted }
    val isFull: Boolean get() = availableSlots == 0
}

object InternetRadioImportPolicy {
    fun evaluate(
        currentChannels: List<InternetRadioConfiguration>,
        importedChannels: List<InternetRadioConfiguration>,
    ): RadioImportPreview {
        val currentNormalized = currentChannels.mapNotNull { it.normalizedOrNull() }
        val reservedIds = currentNormalized.mapTo(mutableSetOf()) { it.id }
        val importedNormalized = buildList {
            val seenUrls = mutableSetOf<String>()
            for (ch in importedChannels) {
                var norm = ch.normalizedOrNull() ?: continue
                val urlKey = radioUrlKey(norm.streamUrl)
                if (seenUrls.add(urlKey)) {
                    if (!reservedIds.add(norm.id)) {
                        norm = norm.copy(id = UUID.randomUUID().toString())
                        reservedIds.add(norm.id)
                    }
                    add(norm)
                }
            }
        }.take(AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT)

        val currentUrlSet = currentNormalized.map { radioUrlKey(it.streamUrl) }.toSet()
        val duplicates = importedNormalized.filter { radioUrlKey(it.streamUrl) in currentUrlSet }
        val newChannels = importedNormalized.filter { radioUrlKey(it.streamUrl) !in currentUrlSet }
        val availableSlots = (AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT - currentNormalized.size).coerceAtLeast(0)
        val addableChannels = newChannels.take(availableSlots)
        val overflowChannels = newChannels.drop(availableSlots)

        return RadioImportPreview(
            importedChannels = importedNormalized,
            duplicateChannels = duplicates,
            newChannels = newChannels,
            currentChannels = currentNormalized,
            availableSlots = availableSlots,
            addableChannels = addableChannels,
            overflowChannels = overflowChannels,
        )
    }

    fun applyAdd(
        currentChannels: List<InternetRadioConfiguration>,
        channelsToAdd: List<InternetRadioConfiguration>,
    ): List<InternetRadioConfiguration> {
        val result = currentChannels.toMutableList()
        val currentUrls = result.map { radioUrlKey(it.streamUrl) }.toMutableSet()
        val currentIds = result.mapTo(mutableSetOf()) { it.id }
        for (ch in channelsToAdd) {
            var norm = ch.normalizedOrNull() ?: continue
            if (result.size >= AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT) break
            if (currentUrls.add(radioUrlKey(norm.streamUrl))) {
                if (!currentIds.add(norm.id)) {
                    norm = norm.copy(id = UUID.randomUUID().toString())
                    currentIds.add(norm.id)
                }
                result.add(norm)
            }
        }
        return result
    }

    fun applyReplace(
        importedChannels: List<InternetRadioConfiguration>,
    ): List<InternetRadioConfiguration> {
        return buildList {
            val seenUrls = mutableSetOf<String>()
            for (ch in importedChannels) {
                val norm = ch.normalizedOrNull() ?: continue
                if (size >= AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT) break
                if (seenUrls.add(radioUrlKey(norm.streamUrl))) {
                    add(norm)
                }
            }
        }
    }

    internal fun radioUrlKey(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return value.trim()
        return buildString {
            append(uri.scheme?.lowercase().orEmpty())
            append("://")
            append(uri.host?.lowercase().orEmpty())
            if (uri.port >= 0) append(":${uri.port}")
            append(uri.rawPath.orEmpty())
            if (uri.rawQuery != null) append("?${uri.rawQuery}")
        }
    }
}

object InternetRadioCodec {
    const val FORMAT_NAME = "s.tand-radio"
    const val CURRENT_VERSION = 1
    const val MAX_PAYLOAD_BYTES = 128 * 1024 // 128 KiB
    const val DEFAULT_FILE_NAME = "S.tand-Radio.standradio.json"

    fun encode(
        channels: List<InternetRadioConfiguration>,
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): String {
        val validChannels = channels.mapNotNull { it.normalizedOrNull() }
            .take(AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT)
        val channelJsonList = validChannels.map { channel ->
            """    {
      "id": ${quote(channel.id)},
      "displayName": ${quote(channel.displayName)},
      "streamUrl": ${quote(channel.streamUrl)}
    }"""
        }
        return buildString {
            append("{\n")
            append("  \"format\": \"$FORMAT_NAME\",\n")
            append("  \"version\": $CURRENT_VERSION,\n")
            append("  \"exportedAt\": $exportedAtMillis,\n")
            append("  \"channels\": [\n")
            append(channelJsonList.joinToString(",\n"))
            if (channelJsonList.isNotEmpty()) append("\n")
            append("  ]\n")
            append("}\n")
        }
    }

    fun decode(rawBytes: ByteArray): RadioDecodeResult {
        if (rawBytes.size > MAX_PAYLOAD_BYTES) {
            return RadioDecodeResult.Failure("파일 크기가 제한(128KB)을 초과했습니다.")
        }
        if (rawBytes.isEmpty()) {
            return RadioDecodeResult.Failure("파일 내용이 비어 있습니다.")
        }
        return decode(rawBytes.toString(Charsets.UTF_8))
    }

    fun decode(jsonString: String): RadioDecodeResult {
        if (jsonString.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            return RadioDecodeResult.Failure("파일 크기가 제한(128KB)을 초과했습니다.")
        }
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            return RadioDecodeResult.Failure("파일 내용이 비어 있습니다.")
        }

        val root = try {
            SimpleJsonParser.parse(trimmed) as? Map<*, *>
                ?: return RadioDecodeResult.Failure("라디오 파일 형식이 올바르지 않습니다.")
        } catch (e: Exception) {
            return RadioDecodeResult.Failure("라디오 파일 내용을 읽을 수 없습니다.")
        }

        val format = root["format"] as? String
        if (format != FORMAT_NAME) {
            return RadioDecodeResult.Failure("지원하지 않는 라디오 파일 형식입니다.")
        }

        val version = (root["version"] as? Number)?.toInt()
            ?: return RadioDecodeResult.Failure("버전 정보가 없습니다.")
        if (version != CURRENT_VERSION) {
            return RadioDecodeResult.Failure("지원하지 않는 버전(${version})입니다.")
        }

        val rawChannels = root["channels"] as? List<*>
            ?: return RadioDecodeResult.Failure("라디오 채널 목록을 찾을 수 없습니다.")

        val parsedChannels = mutableListOf<InternetRadioConfiguration>()
        val seenUrls = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()

        for (item in rawChannels) {
            val channelMap = item as? Map<*, *> ?: continue
            val name = (channelMap["displayName"] as? String)?.trim().orEmpty()
            val url = (channelMap["streamUrl"] as? String)?.trim().orEmpty()
            val requestedId = (channelMap["id"] as? String)?.trim().orEmpty()

            if (url.isEmpty()) continue
            val config = InternetRadioConfiguration(
                displayName = name.ifEmpty { "인터넷 라디오" },
                streamUrl = url,
                id = requestedId.takeIf(::isSafeChannelId) ?: UUID.randomUUID().toString(),
            ).normalizedOrNull() ?: continue

            val normalizedUrlKey = InternetRadioImportPolicy.radioUrlKey(config.streamUrl)
            if (!seenUrls.add(normalizedUrlKey)) continue
            val uniqueConfig = if (seenIds.add(config.id)) {
                config
            } else {
                config.copy(id = UUID.randomUUID().toString()).also { seenIds.add(it.id) }
            }
            parsedChannels.add(uniqueConfig)
            if (parsedChannels.size >= AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT) {
                break
            }
        }

        if (parsedChannels.isEmpty()) {
            return RadioDecodeResult.Failure("가져올 수 있는 유효한 라디오 채널이 없습니다.")
        }

        return RadioDecodeResult.Success(parsedChannels)
    }

    private fun quote(string: String): String {
        val escaped = buildString {
            for (c in string) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (c.code < 0x20) {
                            append(String.format("\\u%04x", c.code))
                        } else {
                            append(c)
                        }
                    }
                }
            }
        }
        return "\"$escaped\""
    }

    private fun isSafeChannelId(value: String): Boolean =
        value.length in 1..128 && value.all { it.isLetterOrDigit() || it in "-_.:" }

    /**
     * Dependency-free, lightweight, standalone JSON parser that operates consistently on both
     * Android runtime and pure JVM test environments without stub issues.
     */
    internal object SimpleJsonParser {
        fun parse(json: String): Any? = Parser(json).parseDocument()

        private class Parser(private val json: String) {
            private var index = 0

            fun parseDocument(): Any? {
                val result = parseValue()
                skipWhitespace()
                require(index == json.length) { "Unexpected trailing content at $index" }
                return result
            }

            private fun skipWhitespace() {
                while (index < json.length && json[index].isWhitespace()) index++
            }

            private fun parseString(): String {
                require(index < json.length && json[index] == '"')
                index++
                val sb = StringBuilder()
                while (index < json.length) {
                    val c = json[index++]
                    if (c == '"') return sb.toString()
                    if (c == '\\') {
                        require(index < json.length) { "Unterminated escape" }
                        when (val next = json[index++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(index + 4 <= json.length) { "Invalid unicode escape" }
                                val hex = json.substring(index, index + 4)
                                index += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw IllegalArgumentException("Invalid escape at $index")
                        }
                    } else {
                        require(c.code >= 0x20) { "Control character in string" }
                        sb.append(c)
                    }
                }
                throw IllegalArgumentException("Unterminated string")
            }

            private fun parseNumber(): Number {
                val start = index
                while (index < json.length && (json[index].isDigit() || json[index] == '-' || json[index] == '+' || json[index] == '.' || json[index] == 'e' || json[index] == 'E')) {
                    index++
                }
                val numStr = json.substring(start, index)
                require(numStr.isNotEmpty()) { "Invalid number" }
                return if (numStr.contains('.') || numStr.contains('e') || numStr.contains('E')) {
                    numStr.toDouble()
                } else {
                    numStr.toLong()
                }
            }

            private fun parseValue(): Any? {
                skipWhitespace()
                require(index < json.length) { "Unexpected end of input" }
                return when (val c = json[index]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    't', 'f' -> {
                        if (json.startsWith("true", index)) {
                            index += 4
                            true
                        } else if (json.startsWith("false", index)) {
                            index += 5
                            false
                        } else {
                            throw IllegalArgumentException("Unexpected token at $index")
                        }
                    }
                    'n' -> {
                        if (json.startsWith("null", index)) {
                            index += 4
                            null
                        } else {
                            throw IllegalArgumentException("Unexpected token at $index")
                        }
                    }
                    else -> {
                        if (c == '-' || c.isDigit()) {
                            parseNumber()
                        } else {
                            throw IllegalArgumentException("Unexpected char '$c' at $index")
                        }
                    }
                }
            }

            private fun parseObject(): Map<String, Any?> {
                index++ // Skip '{'
                val map = LinkedHashMap<String, Any?>()
                skipWhitespace()
                if (index < json.length && json[index] == '}') {
                    index++
                    return map
                }
                while (index < json.length) {
                    skipWhitespace()
                    if (index >= json.length || json[index] != '"') {
                        throw IllegalArgumentException("Expected string key in object at $index")
                    }
                    val key = parseString()
                    skipWhitespace()
                    if (index >= json.length || json[index] != ':') {
                        throw IllegalArgumentException("Expected ':' at $index")
                    }
                    index++ // Skip ':'
                    val value = parseValue()
                    map[key] = value
                    skipWhitespace()
                    if (index < json.length && json[index] == ',') {
                        index++
                    } else if (index < json.length && json[index] == '}') {
                        index++
                        break
                    } else {
                        throw IllegalArgumentException("Expected ',' or '}' at $index")
                    }
                }
                return map
            }

            private fun parseArray(): List<Any?> {
                index++ // Skip '['
                val list = mutableListOf<Any?>()
                skipWhitespace()
                if (index < json.length && json[index] == ']') {
                    index++
                    return list
                }
                while (index < json.length) {
                    val value = parseValue()
                    list.add(value)
                    skipWhitespace()
                    if (index < json.length && json[index] == ',') {
                        index++
                    } else if (index < json.length && json[index] == ']') {
                        index++
                        break
                    } else {
                        throw IllegalArgumentException("Expected ',' or ']' at $index")
                    }
                }
                return list
            }

        }
    }
}
