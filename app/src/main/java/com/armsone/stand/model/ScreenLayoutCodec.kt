package com.armsone.stand.model

/**
 * Versioned, dependency-free persistence format for one screen orientation's panel layout.
 *
 * The format is intentionally private to this codec. Unsupported versions or structurally invalid
 * payloads return a fresh copy of the supplied orientation-specific fallback.
 */
object ScreenLayoutCodec {
    private const val FORMAT_VERSION = "S.tand-layout-v1"
    private const val MAX_ENCODED_LENGTH = 4_096

    private const val CLOCK = "clock"
    private const val SECONDS = "seconds"
    private const val WEATHER_ICON = "weatherIcon"
    private const val WEATHER_TEMPERATURE = "weatherTemperature"
    private const val WEATHER_CONDITION = "weatherCondition"
    private const val DATE = "date"
    private const val STATUS = "status"
    private const val BRIGHTNESS_RULE = "brightnessRule"
    private const val BATTERY = "battery"
    private const val RADIO = "radio"
    private const val SECONDARY_RADIO = "secondaryRadio"
    private const val RADIOS_GROUPED = "radiosGrouped"
    private const val WEATHER_GROUP_IDS = "weatherGroupIds"
    private const val CONTROL_ORDER = "controlOrder"

    private val requiredKeys = setOf(
        CLOCK,
        WEATHER_ICON,
        WEATHER_TEMPERATURE,
        WEATHER_CONDITION,
        DATE,
        STATUS,
        BRIGHTNESS_RULE,
        BATTERY,
        WEATHER_GROUP_IDS,
        CONTROL_ORDER,
    )
    private val allowedKeys = requiredKeys + setOf(RADIO, SECONDS, SECONDARY_RADIO, RADIOS_GROUPED)

    fun encode(layout: StandScreenLayout): String {
        val normalized = layout.copy()
        return buildString {
            append(FORMAT_VERSION)
            appendField(CLOCK, normalized.clock.encoded())
            appendField(SECONDS, normalized.seconds.encoded())
            appendField(WEATHER_ICON, normalized.weatherIcon.encoded())
            appendField(WEATHER_TEMPERATURE, normalized.weatherTemperature.encoded())
            appendField(WEATHER_CONDITION, normalized.weatherCondition.encoded())
            appendField(DATE, normalized.date.encoded())
            appendField(STATUS, normalized.status.encoded())
            appendField(BRIGHTNESS_RULE, normalized.brightnessRule.encoded())
            appendField(BATTERY, normalized.battery.encoded())
            appendField(RADIO, normalized.radio.encoded())
            appendField(SECONDARY_RADIO, normalized.secondaryRadio.encoded())
            appendField(RADIOS_GROUPED, normalized.radiosGrouped.toString())
            appendField(WEATHER_GROUP_IDS, normalized.weatherGroupIds.joinToString(","))
            appendField(
                CONTROL_ORDER,
                normalized.controlOrder.joinToString(",") { it.rawValue },
            )
        }
    }

    fun decodeOrDefault(
        encoded: String?,
        fallback: StandScreenLayout,
    ): StandScreenLayout {
        val safeFallback = fallback.copy()
        if (encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_LENGTH) return safeFallback

        return decode(encoded) ?: safeFallback
    }

    private fun decode(encoded: String): StandScreenLayout? {
        val parts = encoded.split(FIELD_SEPARATOR)
        if (parts.firstOrNull() != FORMAT_VERSION) return null

        val fields = LinkedHashMap<String, String>()
        for (part in parts.drop(1)) {
            val separatorIndex = part.indexOf(KEY_VALUE_SEPARATOR)
            if (separatorIndex <= 0) return null

            val key = part.substring(0, separatorIndex)
            val value = part.substring(separatorIndex + 1)
            if (key !in allowedKeys || fields.put(key, value) != null) return null
        }
        if (!fields.keys.containsAll(requiredKeys)) return null

        val clock = fields[CLOCK]?.toPanelTransform() ?: return null
        val seconds = fields[SECONDS]?.toPanelTransform()
        val weatherIcon = fields[WEATHER_ICON]?.toPanelTransform() ?: return null
        val weatherTemperature = fields[WEATHER_TEMPERATURE]?.toPanelTransform() ?: return null
        val weatherCondition = fields[WEATHER_CONDITION]?.toPanelTransform() ?: return null
        val date = fields[DATE]?.toPanelTransform() ?: return null
        val status = fields[STATUS]?.toPanelTransform() ?: return null
        val brightnessRule = fields[BRIGHTNESS_RULE]?.toPanelTransform() ?: return null
        val battery = fields[BATTERY]?.toPanelTransform() ?: return null
        val radio = fields[RADIO]?.toPanelTransform()
        val secondaryRadio = fields[SECONDARY_RADIO]?.toPanelTransform()
        val radiosGrouped = fields[RADIOS_GROUPED]?.let { value ->
            when (value) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        val weatherGroupIds = fields[WEATHER_GROUP_IDS]?.toGroupIds() ?: return null
        val controlOrder = StandControlKind.normalizedRawOrder(
            fields[CONTROL_ORDER]
                ?.split(VALUE_SEPARATOR)
                ?.filter { it.isNotEmpty() },
        )

        return StandScreenLayout(
            clock = clock,
            seconds = seconds ?: PanelTransform(x = 0.27f, y = 0.036f),
            weatherIcon = weatherIcon,
            weatherTemperature = weatherTemperature,
            weatherCondition = weatherCondition,
            date = date,
            status = status,
            brightnessRule = brightnessRule,
            battery = battery,
            radio = radio ?: PanelTransform(x = 0f, y = 0.28f),
            secondaryRadio = secondaryRadio ?: PanelTransform(
                x = -0.26f,
                y = 0.215f,
                scale = 0.75f,
            ),
            radiosGrouped = radiosGrouped ?: false,
            weatherGroupIds = weatherGroupIds,
            controlOrder = controlOrder,
        )
    }

    private fun StringBuilder.appendField(key: String, value: String) {
        append(FIELD_SEPARATOR)
        append(key)
        append(KEY_VALUE_SEPARATOR)
        append(value)
    }

    private fun PanelTransform.encoded(): String = "$x,$y,$scale"

    private fun String.toPanelTransform(): PanelTransform? {
        val values = split(VALUE_SEPARATOR)
        if (values.size != TRANSFORM_VALUE_COUNT) return null
        val x = values[0].toFiniteFloatOrNull() ?: return null
        val y = values[1].toFiniteFloatOrNull() ?: return null
        val scale = values[2].toFiniteFloatOrNull() ?: return null
        return PanelTransform(x = x, y = y, scale = scale)
    }

    private fun String.toGroupIds(): List<Int>? {
        val values = split(VALUE_SEPARATOR)
        if (values.size != WeatherPiece.entries.size) return null
        return values.map { it.toIntOrNull() ?: return null }
    }

    private fun String.toFiniteFloatOrNull(): Float? =
        toFloatOrNull()?.takeIf { it.isFinite() }

    private const val FIELD_SEPARATOR = '|'
    private const val KEY_VALUE_SEPARATOR = '='
    private const val VALUE_SEPARATOR = ','
    private const val TRANSFORM_VALUE_COUNT = 3
}
