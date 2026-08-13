package com.armsone.stand.boyiso

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import com.armsone.stand.model.EnvironmentDisplayMode
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BoyisoRole(val wireValue: String, val title: String, val description: String) {
    VIEWER(
        wireValue = MonitoringService.ROLE_HOST,
        title = "볼사람",
        description = "말할사람 기기의 소리와 연결 상태를 확인합니다.",
    ),
    SPEAKER(
        wireValue = MonitoringService.ROLE_GUEST,
        title = "말할사람",
        description = "이 기기에서 소리를 감지해 볼사람 기기로 전달합니다.",
    ),
    ;

    companion object {
        fun fromWireValue(value: String?): BoyisoRole =
            entries.firstOrNull { it.wireValue == value } ?: VIEWER
    }
}

data class BoyisoConfiguration(
    val role: BoyisoRole = BoyisoRole.VIEWER,
    val roomId: String = "",
    val roomKey: String = "",
    val canInvite: Boolean = false,
    val deviceName: String = "",
) {
    val hasRoom: Boolean
        get() = roomId.isNotBlank() && roomKey.length >= BoyisoManager.MINIMUM_ROOM_KEY_LENGTH
}

data class BoyisoDevice(
    val id: String,
    val name: String,
    val role: BoyisoRole,
    val batteryPercent: Int?,
    val monitoring: Boolean,
    val lastSeenMillis: Long,
    val displayMode: EnvironmentDisplayMode?,
    val sessionActive: Boolean,
)

data class BoyisoEventSummary(
    val sourceName: String,
    val kind: String,
    val detail: String,
    val path: String,
    val timestampMillis: Long,
)

data class BoyisoState(
    val configuration: BoyisoConfiguration,
    val running: Boolean = false,
    val lanConnectionCount: Int = 0,
    val bluetoothConnectionCount: Int = 0,
    val internetConnectionCount: Int = 0,
    val devices: List<BoyisoDevice> = emptyList(),
    val microphoneMonitoring: Boolean = false,
    val hadConnectedDevice: Boolean = false,
    val issueMessage: String? = null,
    val latestEvent: BoyisoEventSummary? = null,
) {
    val homeStatusText: String
        get() = if (running) configuration.role.title else "연결 안 됨"

    val statusText: String
        get() = when {
            !running -> "설정 필요"
            issueMessage != null -> issueMessage
            configuration.role == BoyisoRole.SPEAKER && microphoneMonitoring -> "말할 준비됨"
            configuration.role == BoyisoRole.SPEAKER -> "마이크 대기"
            devices.none { it.role == BoyisoRole.SPEAKER } -> "말할사람 연결 대기"
            else -> "말할사람 ${devices.count { it.role == BoyisoRole.SPEAKER }}대 연결"
        }
}

class BoyisoManager(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(BoyisoState(configuration = loadConfiguration()))
    val state: StateFlow<BoyisoState> = _state.asStateFlow()
    private var localDisplayMode: EnvironmentDisplayMode = EnvironmentDisplayMode.OBJECT
    private var localSessionActive: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MonitoringService.ACTION_STATE -> receiveState(intent)
                MonitoringService.ACTION_EVENT -> receiveEvent(intent)
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(MonitoringService.ACTION_STATE)
            addAction(MonitoringService.ACTION_EVENT)
        }
        ContextCompat.registerReceiver(
            applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun updateConfiguration(configuration: BoyisoConfiguration) {
        if (_state.value.running) return
        val normalized = configuration.copy(
            roomId = configuration.roomId.trim(),
            roomKey = configuration.roomKey.trim(),
            deviceName = configuration.deviceName.trim().take(MAXIMUM_DEVICE_NAME_LENGTH),
        )
        saveConfiguration(normalized)
        _state.value = _state.value.copy(configuration = normalized, issueMessage = null)
    }

    fun createRoom() {
        updateConfiguration(
            _state.value.configuration.copy(
                roomId = randomToken(12),
                roomKey = randomToken(32),
                canInvite = true,
            ),
        )
    }

    fun acceptInvitation(uri: Uri): Boolean {
        if (uri.scheme != INVITE_SCHEME || uri.host != INVITE_HOST) return false
        if (uri.getQueryParameter(INVITE_VERSION_QUERY) != PROTOCOL_VERSION) return false
        val roomId = uri.getQueryParameter(INVITE_ROOM_QUERY).orEmpty()
        val roomKey = uri.getQueryParameter(INVITE_KEY_QUERY).orEmpty()
        if (roomId.isBlank() || roomKey.length < MINIMUM_ROOM_KEY_LENGTH) return false
        updateConfiguration(
            _state.value.configuration.copy(
                roomId = roomId,
                roomKey = roomKey,
                canInvite = false,
            ),
        )
        return true
    }

    fun invitationUri(): Uri? {
        val configuration = _state.value.configuration
        if (!configuration.hasRoom || !configuration.canInvite) return null
        return Uri.Builder()
            .scheme(INVITE_SCHEME)
            .authority(INVITE_HOST)
            .appendQueryParameter(INVITE_VERSION_QUERY, PROTOCOL_VERSION)
            .appendQueryParameter(INVITE_ROOM_QUERY, configuration.roomId)
            .appendQueryParameter(INVITE_KEY_QUERY, configuration.roomKey)
            .build()
    }

    fun start() {
        val configuration = _state.value.configuration
        require(configuration.hasRoom) { "QR로 돌봄 공간을 만들거나 참여해 주세요." }
        require(configuration.deviceName.isNotBlank()) { "이 기기의 이름을 입력해 주세요." }
        saveConfiguration(configuration)
        val intent = Intent(applicationContext, MonitoringService::class.java)
            .setAction(MonitoringService.ACTION_START)
            .putExtra(MonitoringService.EXTRA_ROLE, configuration.role.wireValue)
            .putExtra(MonitoringService.EXTRA_ROOM_ID, configuration.roomId)
            .putExtra(MonitoringService.EXTRA_ROOM_CODE, configuration.roomKey)
            .putExtra(MonitoringService.EXTRA_SOURCE_NAME, configuration.deviceName)
            .putExtra(MonitoringService.EXTRA_DISPLAY_MODE, localDisplayMode.wireValue)
            .putExtra(MonitoringService.EXTRA_SESSION_ACTIVE, localSessionActive)
        ContextCompat.startForegroundService(applicationContext, intent)
    }

    fun updateLocalStandState(mode: EnvironmentDisplayMode, sessionActive: Boolean) {
        localDisplayMode = mode
        localSessionActive = sessionActive
        if (!_state.value.running) return
        applicationContext.startService(
            Intent(applicationContext, MonitoringService::class.java)
                .setAction(MonitoringService.ACTION_UPDATE_STAND_STATE)
                .putExtra(MonitoringService.EXTRA_DISPLAY_MODE, mode.wireValue)
                .putExtra(MonitoringService.EXTRA_SESSION_ACTIVE, sessionActive),
        )
    }

    fun sendMovement() {
        if (!_state.value.running) return
        applicationContext.startService(
            Intent(applicationContext, MonitoringService::class.java)
                .setAction(MonitoringService.ACTION_MOVEMENT),
        )
    }

    fun stop() {
        applicationContext.startService(
            Intent(applicationContext, MonitoringService::class.java)
                .setAction(MonitoringService.ACTION_STOP),
        )
    }

    fun leaveRoom() {
        stop()
        val clearedConfiguration = _state.value.configuration.copy(
            roomId = "",
            roomKey = "",
            canInvite = false,
        )
        saveConfiguration(clearedConfiguration)
        _state.value = BoyisoState(configuration = clearedConfiguration)
    }

    fun sendTokTok() {
        if (!_state.value.running) return
        applicationContext.startService(
            Intent(applicationContext, MonitoringService::class.java)
                .setAction(MonitoringService.ACTION_TOKTOK),
        )
    }

    override fun close() {
        runCatching { applicationContext.unregisterReceiver(receiver) }
    }

    private fun receiveState(intent: Intent) {
        val role = BoyisoRole.fromWireValue(intent.getStringExtra("role"))
        val currentConfiguration = _state.value.configuration
        val names = intent.getStringArrayListExtra("sourceNames").orEmpty()
        val roles = intent.getStringArrayListExtra("sourceRoles").orEmpty()
        val displayModes = intent.getStringArrayListExtra("sourceDisplayModes").orEmpty()
        val ids = intent.getStringArrayListExtra("sourceIds").orEmpty()
        val batteries = intent.getIntegerArrayListExtra("sourceBatteries").orEmpty()
        val monitoring = intent.getBooleanArrayExtra("sourceMonitoring") ?: booleanArrayOf()
        val sessionActive = intent.getBooleanArrayExtra("sourceSessionActive") ?: booleanArrayOf()
        val lastSeen = intent.getLongArrayExtra("sourceLastSeen") ?: longArrayOf()
        val devices = ids.mapIndexed { index, id ->
            BoyisoDevice(
                id = id,
                name = names.getOrNull(index).orEmpty().ifBlank { "말할사람 기기" },
                role = BoyisoRole.fromWireValue(roles.getOrNull(index)),
                batteryPercent = batteries.getOrNull(index)?.takeIf { it >= 0 },
                monitoring = monitoring.getOrNull(index) ?: false,
                lastSeenMillis = lastSeen.getOrNull(index) ?: 0L,
                displayMode = displayModes.getOrNull(index)?.toEnvironmentDisplayModeOrNull(),
                sessionActive = sessionActive.getOrNull(index) ?: false,
            )
        }
        _state.value = _state.value.copy(
            configuration = currentConfiguration.copy(role = role),
            running = intent.getBooleanExtra("running", false),
            lanConnectionCount = intent.getIntExtra("lanCount", 0),
            bluetoothConnectionCount = intent.getIntExtra("bleCount", 0),
            internetConnectionCount = intent.getIntExtra("internetCount", 0),
            devices = devices,
            microphoneMonitoring = intent.getBooleanExtra("monitoring", false),
            hadConnectedDevice = intent.getBooleanExtra("hadConnectedDevice", false),
            issueMessage = intent.getStringExtra("error")?.takeIf(String::isNotBlank),
        )
    }

    private fun receiveEvent(intent: Intent) {
        _state.value = _state.value.copy(
            latestEvent = BoyisoEventSummary(
                sourceName = intent.getStringExtra("sourceName").orEmpty(),
                kind = intent.getStringExtra("kind").orEmpty(),
                detail = intent.getStringExtra("detail").orEmpty(),
                path = intent.getStringExtra("path").orEmpty(),
                timestampMillis = intent.getLongExtra("timestamp", System.currentTimeMillis()),
            ),
        )
    }

    private fun loadConfiguration(): BoyisoConfiguration {
        val defaultName = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter(String::isNotBlank)
            .joinToString(" ")
        return BoyisoConfiguration(
            role = BoyisoRole.fromWireValue(preferences.getString(KEY_ROLE, null)),
            roomId = preferences.getString(KEY_ROOM_ID, "").orEmpty(),
            roomKey = preferences.getString(KEY_ROOM_KEY, "").orEmpty(),
            canInvite = preferences.getBoolean(KEY_CAN_INVITE, false),
            deviceName = preferences.getString(KEY_DEVICE_NAME, defaultName).orEmpty(),
        )
    }

    private fun saveConfiguration(configuration: BoyisoConfiguration) {
        preferences.edit()
            .putString(KEY_ROLE, configuration.role.wireValue)
            .putString(KEY_ROOM_ID, configuration.roomId)
            .putString(KEY_ROOM_KEY, configuration.roomKey)
            .putBoolean(KEY_CAN_INVITE, configuration.canInvite)
            .putString(KEY_DEVICE_NAME, configuration.deviceName)
            .apply()
    }

    companion object {
        @JvmStatic
        @Volatile
        var isAppVisible: Boolean = false

        const val MINIMUM_ROOM_KEY_LENGTH = 32
        private const val MAXIMUM_DEVICE_NAME_LENGTH = 32
        private const val PREFERENCES = "boyiso"
        private const val KEY_ROLE = "role"
        private const val KEY_ROOM_ID = "room_id"
        private const val KEY_ROOM_KEY = "room_key"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_CAN_INVITE = "can_invite"
        private const val INVITE_SCHEME = "stand"
        private const val INVITE_HOST = "boyiso"
        private const val INVITE_VERSION_QUERY = "v"
        private const val INVITE_ROOM_QUERY = "room"
        private const val INVITE_KEY_QUERY = "key"
        private const val PROTOCOL_VERSION = "2"
        private val secureRandom = SecureRandom()

        private fun randomToken(byteCount: Int): String {
            val bytes = ByteArray(byteCount).also(secureRandom::nextBytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}

private val EnvironmentDisplayMode.wireValue: String
    get() = when (this) {
        EnvironmentDisplayMode.MATE -> "mate"
        EnvironmentDisplayMode.OBJECT -> "object"
    }

private fun String.toEnvironmentDisplayModeOrNull(): EnvironmentDisplayMode? = when (this) {
    "mate" -> EnvironmentDisplayMode.MATE
    "object" -> EnvironmentDisplayMode.OBJECT
    else -> null
}
