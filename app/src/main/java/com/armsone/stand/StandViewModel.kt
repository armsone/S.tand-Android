package com.armsone.stand

import android.app.Application
import android.os.SystemClock
import android.provider.Settings as AndroidSystemSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.armsone.stand.audio.AudioDetectorConfiguration
import com.armsone.stand.audio.AudioMonitor
import com.armsone.stand.audio.AudioMonitorState
import com.armsone.stand.data.SettingsRepository
import com.armsone.stand.model.AmbientLightPolicy
import com.armsone.stand.model.AppSettings
import com.armsone.stand.model.BatteryProtectionPolicy
import com.armsone.stand.model.BoyisoStartleLightingPolicy
import com.armsone.stand.model.BoyisoStartleLightingProfile
import com.armsone.stand.model.EnvironmentDisplayMode
import com.armsone.stand.model.FaceDownLightingPolicy
import com.armsone.stand.model.LampPhase
import com.armsone.stand.model.LampTorchLightingPolicy
import com.armsone.stand.model.InternetRadioMutationPolicy
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.SimplifiedBrightnessModePolicy
import com.armsone.stand.model.StandDisplayTheme
import com.armsone.stand.model.StandExperienceMode
import com.armsone.stand.model.StandModePreference
import com.armsone.stand.model.StartleActivationPolicy
import com.armsone.stand.model.SleepCareMonitoringPolicy
import com.armsone.stand.model.StandAutomaticDimmingPolicy
import com.armsone.stand.platform.AmbientLightReading
import com.armsone.stand.platform.AmbientCameraBrightnessService
import com.armsone.stand.platform.AmbientCameraModePolicy
import com.armsone.stand.platform.AmbientCameraPolicy
import com.armsone.stand.platform.AmbientCameraState
import com.armsone.stand.platform.BatteryMonitor
import com.armsone.stand.platform.DeviceBatteryState
import com.armsone.stand.platform.DeviceSensorMonitor
import com.armsone.stand.platform.DeviceSensorMonitoringMode
import com.armsone.stand.platform.DeviceSensorMonitoringPolicy
import com.armsone.stand.platform.DisplayBrightnessPolicy
import com.armsone.stand.platform.TorchController
import com.armsone.stand.platform.InternetRadioPlayer
import com.armsone.stand.platform.InternetRadioState
import com.armsone.stand.recording.RecordingClip
import com.armsone.stand.recording.RecordingMergeKind
import com.armsone.stand.recording.RecordingMergeService
import com.armsone.stand.recording.RecordingRepository
import com.armsone.stand.recording.RecordingSessionGroup
import com.armsone.stand.recording.RecordingSessionStore
import com.armsone.stand.ui.StandUiState
import com.armsone.stand.ui.WeatherUiState
import com.armsone.stand.weather.WeatherAvailability
import com.armsone.stand.weather.WeatherService
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class StandViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val recordingRepository = RecordingRepository(application)
    private val recordingSessionStore = RecordingSessionStore(recordingRepository.directory)
    private val recordingMergeService = RecordingMergeService(application)
    private val audioMonitor = AudioMonitor(application, recordingRepository)
    private val weatherService = WeatherService(application)
    private val torchController = TorchController(application)
    private val ambientCamera = AmbientCameraBrightnessService(application)
    private val batteryMonitor = BatteryMonitor(application, ::onBatteryChanged)
    private val internetRadioPlayer = InternetRadioPlayer(application)
    private val sensorMonitor = DeviceSensorMonitor(
        context = application,
        onAmbientLightChanged = ::onAmbientLightChanged,
        onMovement = ::onDeviceMovement,
        onFaceDownChanged = ::onFaceDownChanged,
    )

    private val mutableUiState = MutableStateFlow(
        StandUiState(settings = settingsRepository.settings.value),
    )
    val uiState: StateFlow<StandUiState> = mutableUiState.asStateFlow()
    private val mutableLocalMovementEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val localMovementEvents: SharedFlow<Unit> = mutableLocalMovementEvents.asSharedFlow()
    val recordings: StateFlow<List<RecordingClip>> = recordingRepository.recordings
    private val mutableRecordingSessionGroups = MutableStateFlow<List<RecordingSessionGroup>>(
        emptyList(),
    )
    val recordingSessionGroups: StateFlow<List<RecordingSessionGroup>> =
        mutableRecordingSessionGroups.asStateFlow()

    private val monitorMutex = Mutex()
    private val foreground = AtomicBoolean(false)
    private val recordingOperationRunning = AtomicBoolean(false)
    private var microphonePermissionGranted = false
    private var locationPermissionGranted = false
    private var cameraPermissionGranted = false
    private var didAutomaticallyStart = false
    private var monitoringPausedForPlayback = false
    private var boyisoSpeakerActive = false
    private var batteryProtectionLatched = false
    private var movementTriggeredLamp = false
    private var boyisoStartleLightingProfile: BoyisoStartleLightingProfile? = null
    private var mateModeEnteredAtElapsedRealtimeMillis: Long? = null
    private var pendingModeTarget: EnvironmentDisplayMode? = null
    private var activeRecordingSessionId: UUID? = null
    private var activeStartleEventId: UUID? = null
    private var brightnessAdjustmentActive = false
    private var brightnessPreference = StandModePreference.AUTOMATIC
    private var suppressNextSettingsEnvironmentRefresh = false

    private var lampJob: Job? = null
    private var brightnessTapJob: Job? = null
    private var brightnessEndpointLockJob: Job? = null
    private var modeTransitionJob: Job? = null
    private var ambientCameraSamplingJob: Job? = null

    init {
        val initialSettings = settingsRepository.settings.value
        audioMonitor.configure(
            AudioDetectorConfiguration(soundThresholdDB = initialSettings.soundThresholdDB),
        )
        audioMonitor.setRecordingEnabled(initialSettings.recordingEnabled)

        audioMonitor.onClap = {
            viewModelScope.launch {
                val state = mutableUiState.value
                if (state.isSessionActive &&
                    state.environmentMode == EnvironmentDisplayMode.MATE &&
                    state.settings.multiStimulusWakeEnabled
                ) {
                    activateLamp(triggeredByMovement = true)
                }
            }
        }
        audioMonitor.onMovement = {
            viewModelScope.launch {
                val state = mutableUiState.value
                if (state.isSessionActive &&
                    state.environmentMode == EnvironmentDisplayMode.MATE &&
                    state.settings.multiStimulusWakeEnabled
                ) {
                    mutableLocalMovementEvents.tryEmit(Unit)
                    activateLamp(triggeredByMovement = true)
                }
            }
        }
        audioMonitor.onClipSaved = { savedFile ->
            val clips = recordingRepository.reload()
            val savedClip = clips.firstOrNull { clip ->
                runCatching { clip.file.canonicalFile == savedFile.canonicalFile }
                    .getOrDefault(false)
            }
            if (savedClip != null) {
                runCatching {
                    recordingSessionStore.associate(savedClip, activeRecordingSessionId)
                }
            }
            refreshRecordingSessionGroups(clips)
        }

        settingsRepository.settings
            .onEach(::onSettingsChanged)
            .launchIn(viewModelScope)

        recordingRepository.recordings
            .onEach { clips ->
                mutableUiState.update { it.copy(recordingCount = clips.size) }
                refreshRecordingSessionGroups(clips)
            }
            .launchIn(viewModelScope)

        combine(
            audioMonitor.state,
            audioMonitor.normalizedLevel,
            audioMonitor.isWritingClip,
            audioMonitor.errorMessage,
        ) { state, level, writing, error ->
            AudioPresentation(
                running = state is AudioMonitorState.Monitoring,
                level = level.toFloat(),
                writing = writing,
                error = error ?: (state as? AudioMonitorState.Error)?.message,
            )
        }.onEach { audio ->
            mutableUiState.update { current ->
                current.copy(
                    audioRunning = audio.running,
                    audioLevel = audio.level,
                    isWritingClip = audio.writing,
                    audioMessage = audio.error ?: permissionAudioMessage(current),
                )
            }
        }.launchIn(viewModelScope)

        combine(
            audioMonitor.effectiveSoundThresholdDB,
            audioMonitor.noiseCalibrationProgress,
        ) { threshold, progress -> threshold to progress.toFloat() }
            .onEach { (threshold, progress) ->
                mutableUiState.update {
                    it.copy(
                        effectiveSoundThresholdDB = threshold,
                        noiseCalibrationProgress = progress,
                    )
                }
            }
            .launchIn(viewModelScope)

        combine(
            weatherService.weather,
            weatherService.locationName,
            weatherService.availability,
        ) { weather, locationName, availability ->
            val weatherUi = weather?.let {
                WeatherUiState(
                    temperatureCelsius = it.temperatureCelsius,
                    apparentTemperatureCelsius = it.apparentTemperatureCelsius,
                    precipitationMillimeters = it.precipitationMillimeters,
                    weatherCode = it.weatherCode,
                    isDay = it.isDay,
                    locationName = locationName,
                )
            }
            weatherUi to weatherMessage(availability)
        }.onEach { (weather, message) ->
            mutableUiState.update { it.copy(weather = weather, weatherMessage = message) }
        }.launchIn(viewModelScope)

        batteryMonitor.state
            .onEach(::onBatteryChanged)
            .launchIn(viewModelScope)

        torchController.state
            .onEach { torchState ->
                mutableUiState.update { it.copy(torchAvailable = torchState.isAvailable) }
            }
            .launchIn(viewModelScope)

        ambientCamera.state
            .onEach { cameraState ->
                mutableUiState.update { it.copy(ambientCameraState = cameraState) }
            }
            .launchIn(viewModelScope)

        internetRadioPlayer.state
            .onEach { radioState ->
                mutableUiState.update { it.copy(internetRadioState = radioState) }
                syncSleepCareMonitoring()
            }
            .launchIn(viewModelScope)

        internetRadioPlayer.volume
            .onEach { volume ->
                mutableUiState.update { it.copy(internetRadioVolume = volume) }
            }
            .launchIn(viewModelScope)
    }

    fun onAppForeground(
        hasMicrophonePermission: Boolean,
        hasLocationPermission: Boolean,
        hasCameraPermission: Boolean,
        mayAutomaticallyStart: Boolean,
    ) {
        foreground.set(true)
        setPermissions(
            hasMicrophonePermission = hasMicrophonePermission,
            hasLocationPermission = hasLocationPermission,
            hasCameraPermission = hasCameraPermission,
        )
        batteryMonitor.start()
        onBatteryChanged(batteryMonitor.state.value)

        if (!didAutomaticallyStart && mayAutomaticallyStart) {
            didAutomaticallyStart = true
            startNightSession()
        } else if (mutableUiState.value.isSessionActive) {
            syncDeviceSensorMonitoring()
            seedAmbientBrightnessFallback()
            refreshEnvironmentMode(immediate = true)
            syncRecordingSessionForDisplayMode()
            syncSleepCareMonitoring()
            activateLamp(triggeredByMovement = false)
        }
        weatherService.refreshIfNeeded(locationPermissionGranted)
        syncAmbientCameraSampling()
    }

    fun onAppBackground() {
        val keepRunning = mutableUiState.value.settings.backgroundModeEnabled &&
            mutableUiState.value.isSessionActive
        foreground.set(keepRunning)
        if (keepRunning) return
        internetRadioPlayer.stop()
        lampJob?.cancel()
        brightnessTapJob?.cancel()
        brightnessEndpointLockJob?.cancel()
        modeTransitionJob?.cancel()
        ambientCameraSamplingJob?.cancel()
        ambientCamera.cancel()
        pendingModeTarget = null
        movementTriggeredLamp = false
        boyisoStartleLightingProfile = null
        brightnessAdjustmentActive = false
        mutableUiState.update {
            it.copy(
                lampIntensity = 0f,
                lampPhase = LampPhase.OFF,
                experienceMode = modeExperience(it.environmentMode),
            )
        }
        sensorMonitor.stop()
        torchController.turnOff()
        syncRecordingSessionForDisplayMode(forceClosed = true)
        syncSleepCareMonitoring()
    }

    fun setPermissions(
        hasMicrophonePermission: Boolean,
        hasLocationPermission: Boolean,
        hasCameraPermission: Boolean,
    ) {
        microphonePermissionGranted = hasMicrophonePermission
        locationPermissionGranted = hasLocationPermission
        cameraPermissionGranted = hasCameraPermission
        mutableUiState.update { current ->
            current.copy(
                audioMessage = permissionAudioMessage(current),
                hasMicrophonePermission = hasMicrophonePermission,
                hasApproximateLocationPermission = hasLocationPermission,
                hasCameraPermission = hasCameraPermission,
            )
        }
        syncSleepCareMonitoring()
        syncTorch()
        if (foreground.get()) {
            weatherService.refreshIfNeeded(locationPermissionGranted)
        }
        ambientCamera.setEnabled(
            enabled = mutableUiState.value.settings.cameraAmbientSensingEnabled,
            hasPermission = cameraPermissionGranted,
        )
        syncAmbientCameraSampling()
    }

    fun startNightSession() {
        val battery = batteryMonitor.state.value
        if (battery.shouldProtect) {
            batteryProtectionLatched = true
            mutableUiState.update {
                it.copy(
                    isSessionActive = false,
                    batteryProtectionActive = true,
                    controlsVisible = true,
                )
            }
            return
        }

        batteryProtectionLatched = false
        mutableUiState.update {
            it.copy(
                isSessionActive = true,
                batteryProtectionActive = false,
                controlsVisible = true,
            )
        }
        if (foreground.get()) {
            syncDeviceSensorMonitoring()
            seedAmbientBrightnessFallback()
        }
        refreshEnvironmentMode(immediate = true)
        syncRecordingSessionForDisplayMode()
        syncSleepCareMonitoring()
        activateLamp(triggeredByMovement = false)
        weatherService.refreshIfNeeded(locationPermissionGranted)
        syncAmbientCameraSampling()
    }

    fun stopNightSession() {
        lampJob?.cancel()
        brightnessTapJob?.cancel()
        brightnessEndpointLockJob?.cancel()
        modeTransitionJob?.cancel()
        movementTriggeredLamp = false
        boyisoStartleLightingProfile = null
        brightnessAdjustmentActive = false
        pendingModeTarget = null
        mutableUiState.update {
            it.copy(
                isSessionActive = false,
                lampIntensity = 0f,
                lampPhase = LampPhase.OFF,
                controlsVisible = true,
                experienceMode = modeExperience(it.environmentMode),
            )
        }
        sensorMonitor.stop()
        torchController.turnOff()
        syncRecordingSessionForDisplayMode(forceClosed = true)
        syncSleepCareMonitoring()
        ambientCameraSamplingJob?.cancel()
        ambientCamera.cancel()
    }

    fun toggleNightSession() {
        if (mutableUiState.value.isSessionActive) stopNightSession() else startNightSession()
    }

    fun onScreenTap() {
        toggleObjectMateMode()
    }

    fun beginBrightnessAdjustment() {
        val state = mutableUiState.value
        if (!state.isSessionActive) return
        brightnessTapJob?.cancel()
        brightnessEndpointLockJob?.cancel()
        lampJob?.cancel()
        movementTriggeredLamp = false
        boyisoStartleLightingProfile = null
        finishStartleEvent()
        brightnessAdjustmentActive = true
        brightnessPreference = state.settings.modePreference
        torchController.turnOff()
    }

    fun updateBrightnessLevel(requestedLevel: Float) {
        if (!brightnessAdjustmentActive || !mutableUiState.value.isSessionActive) return
        val adjustment = SimplifiedBrightnessModePolicy.stabilizedAdjustment(
            requestedLevel = requestedLevel,
            currentPreference = brightnessPreference,
        )
        brightnessPreference = adjustment.preference
        applyBrightnessPreview(adjustment.level, adjustment.preference)

        val endpointPreference = when (adjustment.level) {
            1f -> StandModePreference.OBJECT
            0f -> StandModePreference.MATE
            else -> null
        }
        if (endpointPreference == null || adjustment.preference == endpointPreference) {
            brightnessEndpointLockJob?.cancel()
            brightnessEndpointLockJob = null
        } else if (brightnessEndpointLockJob == null) {
            brightnessEndpointLockJob = viewModelScope.launch {
                delay(SimplifiedBrightnessModePolicy.ENDPOINT_LOCK_DELAY_MILLIS)
                val currentLevel = mutableUiState.value.lampIntensity
                val stillAtEndpoint = if (endpointPreference == StandModePreference.OBJECT) {
                    currentLevel >= 1f
                } else {
                    currentLevel <= 0f
                }
                if (stillAtEndpoint) {
                    brightnessPreference = endpointPreference
                    applyBrightnessPreview(currentLevel, endpointPreference)
                    persistBrightness(currentLevel, endpointPreference)
                }
                brightnessEndpointLockJob = null
            }
        }
    }

    fun endBrightnessAdjustment() {
        if (!brightnessAdjustmentActive) return
        brightnessAdjustmentActive = false
        val state = mutableUiState.value
        persistBrightness(state.lampIntensity, brightnessPreference)
        syncRecordingSessionForDisplayMode()
        syncSleepCareMonitoring()
        syncAmbientCameraSampling()
    }

    private fun toggleObjectMateMode() {
        val state = mutableUiState.value
        if (!state.isSessionActive) return
        brightnessTapJob?.cancel()
        brightnessEndpointLockJob?.cancel()
        lampJob?.cancel()
        movementTriggeredLamp = false
        boyisoStartleLightingProfile = null
        brightnessAdjustmentActive = false
        val target = SimplifiedBrightnessModePolicy.tapLevel(state.environmentMode)
        applyBrightnessPreview(target, StandModePreference.AUTOMATIC)
        persistBrightness(target, StandModePreference.AUTOMATIC)
        syncTorch()
        syncAmbientCameraSampling()
    }

    private fun applyBrightnessPreview(level: Float, preference: StandModePreference) {
        val normalized = SimplifiedBrightnessModePolicy.clamped(level)
        val mode = SimplifiedBrightnessModePolicy.mode(normalized, preference)
        val previousMode = mutableUiState.value.environmentMode
        mutableUiState.update { current ->
            current.copy(
                lampIntensity = normalized,
                lampPhase = if (normalized <= 0f) LampPhase.OFF else LampPhase.HOLDING,
                environmentMode = mode,
                experienceMode = modeExperience(mode),
            )
        }
        if (previousMode != mode) {
            recordEnvironmentModeTransition(previousMode, mode)
            syncDeviceSensorMonitoring()
            syncRecordingSessionForDisplayMode()
            syncSleepCareMonitoring()
        }
    }

    private fun persistBrightness(level: Float, preference: StandModePreference) {
        suppressNextSettingsEnvironmentRefresh = true
        settingsRepository.update { current ->
            current.copy(
                lampIntensity = SimplifiedBrightnessModePolicy.clamped(level),
                modePreference = preference,
            )
        }
    }

    fun toggleTheme() {
        settingsRepository.update { current ->
            current.copy(displayTheme = current.displayTheme.next())
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsRepository.update(transform)
    }

    fun toggleInternetRadio() {
        val selectedID = mutableUiState.value.settings.selectedInternetRadioId ?: return
        toggleInternetRadio(selectedID)
    }

    fun updateInternetRadioVolume(level: Float) {
        internetRadioPlayer.updateVolume(level)
    }

    fun updateClockScale(scale: Float) {
        settingsRepository.update { current ->
            current.copy(clockScale = com.armsone.stand.model.HomeClockScalePolicy.clamped(scale))
        }
    }

    fun toggleInternetRadio(channelID: String) {
        val channel = mutableUiState.value.settings.internetRadioChannels
            .firstOrNull { it.id == channelID } ?: return
        when (internetRadioPlayer.state.value) {
            InternetRadioState.Idle,
            is InternetRadioState.Failed,
            -> {
                selectInternetRadio(channelID)
                internetRadioPlayer.play(channel)
            }
            is InternetRadioState.Loading,
            is InternetRadioState.Playing,
            is InternetRadioState.Reconnecting,
            -> {
                val activeID = when (val state = internetRadioPlayer.state.value) {
                    is InternetRadioState.Loading -> state.channelID
                    is InternetRadioState.Playing -> state.channelID
                    is InternetRadioState.Reconnecting -> state.channelID
                    else -> null
                }
                if (activeID == channelID) {
                    internetRadioPlayer.stop()
                } else {
                    selectInternetRadio(channelID)
                    internetRadioPlayer.play(channel)
                }
            }
        }
    }

    fun saveInternetRadio(displayName: String, streamUrl: String): String? {
        return saveInternetRadioChannel(
            channelID = mutableUiState.value.settings.selectedInternetRadioId,
            displayName = displayName,
            streamUrl = streamUrl,
        )
    }

    fun saveInternetRadioChannel(
        channelID: String?,
        displayName: String,
        streamUrl: String,
    ): String? {
        val error = com.armsone.stand.model.InternetRadioConfiguration.validationMessage(
            displayName,
            streamUrl,
        )
        if (error != null) return error
        val configuration = com.armsone.stand.model.InternetRadioConfiguration(
            displayName,
            streamUrl,
        ).normalizedOrNull() ?: return "라디오 주소를 확인해 주세요."
        val currentSettings = mutableUiState.value.settings
        val existing = currentSettings.internetRadioChannels.firstOrNull { it.id == channelID }
        if (existing == null &&
            currentSettings.internetRadioChannels.size >=
            AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT
        ) {
            return "라디오 채널은 최대 2개까지 저장할 수 있습니다."
        }
        val savedConfiguration = existing?.let { configuration.copy(id = it.id) } ?: configuration
        if (
            InternetRadioMutationPolicy.shouldStopForSave(
                activeChannelID = activeInternetRadioChannelID(internetRadioPlayer.state.value),
                previous = existing,
                updated = savedConfiguration,
            )
        ) {
            internetRadioPlayer.stop()
        }
        settingsRepository.update { current ->
            val existingIndex = current.internetRadioChannels.indexOfFirst { it.id == channelID }
            if (existingIndex < 0 &&
                current.internetRadioChannels.size >=
                AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT
            ) {
                return@update current
            }
            val saved = if (existingIndex >= 0) savedConfiguration else configuration
            val channels = current.internetRadioChannels.toMutableList().apply {
                if (existingIndex >= 0) {
                    set(existingIndex, saved)
                } else if (size < AppSettings.MAXIMUM_INTERNET_RADIO_CHANNEL_COUNT) {
                    add(saved)
                }
            }
            val selectedID = InternetRadioMutationPolicy.selectedChannelIDAfterSave(
                currentSelectedChannelID = current.selectedInternetRadioId,
                editedChannelID = channelID,
                savedChannelID = saved.id,
            )
            val selected = channels.firstOrNull { it.id == selectedID } ?: saved
            current.copy(
                internetRadio = selected,
                internetRadioChannels = channels,
                selectedInternetRadioId = selected.id,
            )
        }
        return null
    }

    fun deleteInternetRadio() {
        mutableUiState.value.settings.selectedInternetRadioId?.let(::deleteInternetRadioChannel)
    }

    fun deleteInternetRadioChannel(channelID: String) {
        if (
            InternetRadioMutationPolicy.shouldStopForDelete(
                activeChannelID = activeInternetRadioChannelID(internetRadioPlayer.state.value),
                deletedChannelID = channelID,
            )
        ) {
            internetRadioPlayer.stop()
        }
        settingsRepository.update { current ->
            val remaining = current.internetRadioChannels.filterNot {
                it.id == channelID
            }
            val selected = remaining.firstOrNull { it.id == current.selectedInternetRadioId }
                ?: remaining.firstOrNull()
            current.copy(
                internetRadio = selected,
                internetRadioChannels = remaining,
                selectedInternetRadioId = selected?.id,
            )
        }
    }

    fun selectInternetRadio(channelID: String) {
        settingsRepository.update { current ->
            val selected = current.internetRadioChannels.firstOrNull { it.id == channelID }
                ?: return@update current
            current.copy(internetRadio = selected, selectedInternetRadioId = selected.id)
        }
    }

    fun moveInternetRadioChannel(channelID: String, destinationIndex: Int) {
        settingsRepository.update { current ->
            val sourceIndex = current.internetRadioChannels.indexOfFirst { it.id == channelID }
            if (sourceIndex < 0 || destinationIndex !in current.internetRadioChannels.indices ||
                sourceIndex == destinationIndex
            ) return@update current
            val reordered = current.internetRadioChannels.toMutableList()
            val channel = reordered.removeAt(sourceIndex)
            reordered.add(destinationIndex, channel)
            current.copy(internetRadioChannels = reordered)
        }
    }

    private fun activeInternetRadioChannelID(state: InternetRadioState): String? = when (state) {
        is InternetRadioState.Loading -> state.channelID
        is InternetRadioState.Playing -> state.channelID
        is InternetRadioState.Reconnecting -> state.channelID
        is InternetRadioState.Failed -> state.channelID
        InternetRadioState.Idle -> null
    }

    fun setCameraAmbientSensingEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(cameraAmbientSensingEnabled = enabled) }
        ambientCamera.setEnabled(enabled, cameraPermissionGranted)
        if (enabled && cameraPermissionGranted) measureAmbientCameraBrightness()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        ambientCamera.onPermissionResult(granted)
        if (granted && mutableUiState.value.settings.cameraAmbientSensingEnabled) {
            measureAmbientCameraBrightness()
            syncAmbientCameraSampling()
        }
    }

    fun measureAmbientCameraBrightness() {
        val state = mutableUiState.value
        if (!foreground.get() || !state.settings.cameraAmbientSensingEnabled) return
        torchController.turnOff()
        ambientCamera.measureOnce(preferBackCamera = state.isFaceDown) { reading ->
            if (reading != null) {
                val hasLightSensor = sensorMonitor.state.value.lightSensorAvailable
                mutableUiState.update { current ->
                    current.copy(
                        ambientCameraBrightness = reading.value,
                        normalizedAmbientLight = if (hasLightSensor) {
                            current.normalizedAmbientLight
                        } else {
                            reading.value
                        },
                    )
                }
                if (!hasLightSensor) {
                    modeTransitionJob?.cancel()
                    pendingModeTarget = null
                    refreshEnvironmentMode(immediate = false)
                }
            }
            syncTorch()
        }
    }

    fun restoreRecommendedSettings() {
        settingsRepository.restoreRecommendedValues()
    }

    fun setSoundThreshold(value: Float) {
        settingsRepository.update { current ->
            current.copy(soundThresholdDB = value.coerceIn(-55f, -18f))
        }
    }

    fun cycleModePreference() {
        val next = when (mutableUiState.value.settings.modePreference) {
            StandModePreference.AUTOMATIC -> StandModePreference.OBJECT
            StandModePreference.OBJECT -> StandModePreference.MATE
            StandModePreference.MATE -> StandModePreference.AUTOMATIC
        }
        settingsRepository.update { it.copy(modePreference = next) }
    }

    fun setModePreference(preference: StandModePreference) {
        settingsRepository.update { current -> current.copy(modePreference = preference) }
    }

    fun toggleTorchEnabled() {
        settingsRepository.update { it.copy(torchEnabled = !it.torchEnabled) }
        syncTorch()
    }

    fun toggleOrientationLock(isPortrait: Boolean) {
        settingsRepository.update { current ->
            current.copy(
                orientationPreference = if (
                    current.orientationPreference == OrientationPreference.AUTOMATIC
                ) {
                    if (isPortrait) {
                        OrientationPreference.PORTRAIT
                    } else {
                        OrientationPreference.LANDSCAPE
                    }
                } else {
                    OrientationPreference.AUTOMATIC
                },
            )
        }
    }

    fun refreshWeather(force: Boolean = true) {
        weatherService.refreshIfNeeded(locationPermissionGranted, force)
    }

    fun onSystemDisplayBrightnessChanged() {
        if (foreground.get() && seedAmbientBrightnessFallback()) {
            refreshEnvironmentMode(immediate = false)
        }
    }

    fun pauseMonitoringForPlayback() {
        monitoringPausedForPlayback = true
        syncSleepCareMonitoring()
    }

    fun resumeMonitoringAfterPlayback() {
        monitoringPausedForPlayback = false
        syncSleepCareMonitoring()
    }

    fun setBoyisoSpeakerActive(active: Boolean) {
        if (boyisoSpeakerActive == active) return
        boyisoSpeakerActive = active
        syncSleepCareMonitoring()
    }

    fun activateBoyisoStartle(kind: String = "movement", detail: String? = null) {
        val state = mutableUiState.value
        if (!state.isSessionActive || state.environmentMode != EnvironmentDisplayMode.MATE) return
        val requestedProfile = BoyisoStartleLightingPolicy.profile(kind, detail)
        val activeProfile = boyisoStartleLightingProfile
        if (activeProfile != null &&
            !(activeProfile == BoyisoStartleLightingProfile.GENTLE &&
                requestedProfile == BoyisoStartleLightingProfile.STRONG)
        ) return
        activateLamp(
            triggeredByMovement = true,
            bypassStartleDelay = true,
            boyisoProfile = requestedProfile,
        )
    }

    fun deleteRecording(clip: RecordingClip): Boolean {
        val deleted = recordingRepository.delete(clip)
        if (deleted) {
            runCatching { recordingSessionStore.removeReferences(listOf(clip.file)) }
            refreshRecordingSessionGroups(recordingRepository.recordings.value)
        }
        return deleted
    }

    fun deleteRecordings(clips: List<RecordingClip>) {
        runRecordingOperation {
            val deletedFiles = clips.distinctBy { it.file.canonicalPath }
                .filter { recordingRepository.delete(it) }
                .map(RecordingClip::file)
            if (deletedFiles.isNotEmpty()) {
                runCatching { recordingSessionStore.removeReferences(deletedFiles) }
            }
            val reloaded = recordingRepository.reload()
            refreshRecordingSessionGroups(reloaded)
            if (deletedFiles.size == clips.distinctBy { it.file.canonicalPath }.size) {
                "선택한 녹음 ${deletedFiles.size}개를 삭제했습니다."
            } else {
                "일부 녹음을 삭제하지 못했습니다."
            }
        }
    }

    fun deleteAllRecordings() {
        runRecordingOperation {
            val before = recordingRepository.recordings.value
            val succeeded = recordingRepository.deleteAll()
            val deletedFiles = before.map(RecordingClip::file).filterNot { it.exists() }
            if (deletedFiles.isNotEmpty()) {
                runCatching { recordingSessionStore.removeReferences(deletedFiles) }
            }
            val remaining = recordingRepository.reload()
            refreshRecordingSessionGroups(remaining)
            if (succeeded && remaining.isEmpty()) {
                "저장된 잠소리를 모두 삭제했습니다."
            } else {
                "일부 녹음을 삭제하지 못했습니다."
            }
        }
    }

    fun mergeRecordings(clips: List<RecordingClip>, deleteSources: Boolean) {
        mergeRecordingsInternal(
            clips = clips,
            kind = RecordingMergeKind.SELECTED,
            deleteSources = deleteSources,
        )
    }

    fun mergeToday(deleteSources: Boolean) {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val clips = recordingRepository.recordings.value.filter { clip ->
            !clip.isMerged && clip.createdAt.atZone(zoneId).toLocalDate() == today
        }
        mergeRecordingsInternal(
            clips = clips,
            kind = RecordingMergeKind.TODAY,
            deleteSources = deleteSources,
        )
    }

    fun clearRecordingOperationMessage() {
        mutableUiState.update { it.copy(recordingOperationMessage = null) }
    }

    private fun mergeRecordingsInternal(
        clips: List<RecordingClip>,
        kind: RecordingMergeKind,
        deleteSources: Boolean,
    ) {
        runRecordingOperation {
            val sources = clips.filterNot(RecordingClip::isMerged)
                .distinctBy { it.file.canonicalPath }
            val merged = recordingMergeService.merge(sources, kind)
            val deletedFiles = if (deleteSources) {
                sources.filter { recordingRepository.delete(it) }.map(RecordingClip::file)
            } else {
                emptyList()
            }
            if (deletedFiles.isNotEmpty()) {
                runCatching { recordingSessionStore.removeReferences(deletedFiles) }
            }
            val reloaded = recordingRepository.reload()
            refreshRecordingSessionGroups(reloaded)
            when {
                !deleteSources -> "${sources.size}개 녹음을 합쳤습니다. 원본은 그대로 두었습니다."
                deletedFiles.size == sources.size ->
                    "${sources.size}개 녹음을 합치고 원본을 삭제했습니다."
                else -> "합본 ${merged.file.name}은 만들었지만 원본 일부를 삭제하지 못했습니다."
            }
        }
    }

    private fun runRecordingOperation(operation: () -> String) {
        if (!recordingOperationRunning.compareAndSet(false, true)) return
        mutableUiState.update {
            it.copy(
                recordingOperationInProgress = true,
                recordingOperationMessage = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val message = runCatching(operation).fold(
                onSuccess = { it },
                onFailure = { error -> error.message ?: "녹음 작업을 완료하지 못했습니다." },
            )
            recordingOperationRunning.set(false)
            mutableUiState.update {
                it.copy(
                    recordingOperationInProgress = false,
                    recordingOperationMessage = message,
                )
            }
        }
    }

    private fun onSettingsChanged(settings: AppSettings) {
        audioMonitor.configure(
            AudioDetectorConfiguration(soundThresholdDB = settings.soundThresholdDB),
        )
        audioMonitor.setRecordingEnabled(settings.recordingEnabled)
        mutableUiState.update { it.copy(settings = settings) }
        weatherService.setLocationEnabled(settings.weatherLocationEnabled)
        if (foreground.get() && settings.weatherLocationEnabled) {
            weatherService.refreshIfNeeded(locationPermissionGranted, force = true)
        }
        ambientCamera.setEnabled(settings.cameraAmbientSensingEnabled, cameraPermissionGranted)
        if (suppressNextSettingsEnvironmentRefresh) {
            suppressNextSettingsEnvironmentRefresh = false
        } else {
            refreshEnvironmentMode(immediate = settings.modePreference != StandModePreference.AUTOMATIC)
        }
        syncSleepCareMonitoring()
        syncTorch()
        syncAmbientCameraSampling()
    }

    private fun onAmbientLightChanged(reading: AmbientLightReading) {
        mutableUiState.update {
            it.copy(
                normalizedAmbientLight = reading.normalizedBrightness,
                rawAmbientLux = reading.rawLux,
            )
        }
        if (mutableUiState.value.settings.ambientSensingEnabled) {
            refreshEnvironmentMode(immediate = false)
        }
    }

    private fun seedAmbientBrightnessFallback(): Boolean {
        if (sensorMonitor.state.value.lightSensorAvailable) return false
        val cameraReading = ambientCamera.reading.value
        if (
            mutableUiState.value.settings.cameraAmbientSensingEnabled &&
            AmbientCameraPolicy.isFresh(cameraReading, SystemClock.elapsedRealtimeNanos())
        ) {
            mutableUiState.update {
                it.copy(
                    normalizedAmbientLight = cameraReading?.value,
                    rawAmbientLux = null,
                    ambientCameraBrightness = cameraReading?.value,
                )
            }
            return true
        }
        val systemBrightness = runCatching {
            AndroidSystemSettings.System.getInt(
                getApplication<Application>().contentResolver,
                AndroidSystemSettings.System.SCREEN_BRIGHTNESS,
                -1,
            )
        }.getOrDefault(-1)
        val normalized = DisplayBrightnessPolicy.normalized(systemBrightness) ?: return false
        mutableUiState.update {
            it.copy(
                normalizedAmbientLight = normalized,
                rawAmbientLux = null,
            )
        }
        return true
    }

    private fun syncAmbientCameraSampling() {
        ambientCameraSamplingJob?.cancel()
        ambientCameraSamplingJob = null
        val state = mutableUiState.value
        if (
            !foreground.get() ||
            !state.isSessionActive ||
            !state.settings.cameraAmbientSensingEnabled ||
            state.settings.modePreference != StandModePreference.AUTOMATIC ||
            sensorMonitor.state.value.lightSensorAvailable ||
            !cameraPermissionGranted
        ) {
            return
        }
        ambientCameraSamplingJob = viewModelScope.launch {
            if (!AmbientCameraPolicy.isFresh(
                    ambientCamera.reading.value,
                    SystemClock.elapsedRealtimeNanos(),
                )
            ) {
                measureAmbientCameraBrightness()
            }
            while (true) {
                delay(AMBIENT_CAMERA_SAMPLE_INTERVAL_MILLIS)
                if (ambientCamera.state.value != AmbientCameraState.MEASURING) {
                    measureAmbientCameraBrightness()
                }
            }
        }
    }

    private fun onDeviceMovement() {
        val state = mutableUiState.value
        if (state.isSessionActive &&
            state.environmentMode == EnvironmentDisplayMode.MATE &&
            state.settings.multiStimulusWakeEnabled
        ) {
            mutableLocalMovementEvents.tryEmit(Unit)
            activateLamp(triggeredByMovement = true)
        }
    }

    private fun onFaceDownChanged(isFaceDown: Boolean) {
        val state = mutableUiState.value
        if (state.isFaceDown == isFaceDown) return
        if (isFaceDown && !FaceDownLightingPolicy.shouldBlackout(state.isSessionActive, true)) return
        mutableUiState.update { it.copy(isFaceDown = isFaceDown) }
        if (isFaceDown) {
            torchController.turnOff()
        } else {
            refreshEnvironmentMode(immediate = false)
            syncTorch()
        }
    }

    private fun onBatteryChanged(battery: DeviceBatteryState) {
        if (battery.shouldProtect) {
            pauseForLowBattery()
        } else if (BatteryProtectionPolicy.shouldClearProtection(
                wasProtecting = batteryProtectionLatched,
                shouldProtectNow = battery.shouldProtect,
            )
        ) {
            batteryProtectionLatched = false
        }
        mutableUiState.update {
            it.copy(
                batteryLevel = battery.levelFraction,
                isCharging = battery.isCharging,
                batteryProtectionActive = batteryProtectionLatched,
            )
        }
    }

    private fun pauseForLowBattery() {
        if (batteryProtectionLatched && !mutableUiState.value.isSessionActive) return
        batteryProtectionLatched = true
        lampJob?.cancel()
        modeTransitionJob?.cancel()
        ambientCameraSamplingJob?.cancel()
        movementTriggeredLamp = false
        boyisoStartleLightingProfile = null
        pendingModeTarget = null
        mutableUiState.update {
            it.copy(
                isSessionActive = false,
                lampIntensity = 0f,
                lampPhase = LampPhase.OFF,
                controlsVisible = true,
                batteryProtectionActive = true,
                experienceMode = modeExperience(it.environmentMode),
            )
        }
        sensorMonitor.stop()
        torchController.turnOff()
        syncRecordingSessionForDisplayMode(forceClosed = true)
        syncSleepCareMonitoring()
    }

    private fun refreshEnvironmentMode(immediate: Boolean) {
        val state = mutableUiState.value
        val settings = state.settings
        val normalizedBrightness = if (settings.ambientSensingEnabled) {
            state.normalizedAmbientLight
        } else {
            null
        }
        val usesCameraFallback = settings.modePreference == StandModePreference.AUTOMATIC &&
            settings.ambientSensingEnabled &&
            settings.cameraAmbientSensingEnabled &&
            !sensorMonitor.state.value.lightSensorAvailable
        val fallbackBrightness = if (usesCameraFallback) {
            settings.lampIntensity
        } else {
            normalizedBrightness
        }
        val fallbackTarget = if (
            settings.modePreference == StandModePreference.AUTOMATIC &&
            fallbackBrightness == null
        ) {
            state.environmentMode
        } else {
            AmbientLightPolicy.targetMode(
                preference = settings.modePreference,
                normalizedBrightness = fallbackBrightness ?: 1f,
                threshold = settings.brightnessModeThreshold,
            )
        }
        val target = if (usesCameraFallback) {
            AmbientCameraModePolicy.targetMode(
                current = state.environmentMode,
                fallback = fallbackTarget,
                reading = ambientCamera.reading.value,
                nowNanos = SystemClock.elapsedRealtimeNanos(),
            )
        } else {
            fallbackTarget
        }

        if (target == state.environmentMode) {
            modeTransitionJob?.cancel()
            modeTransitionJob = null
            pendingModeTarget = null
            return
        }

        val forced = settings.modePreference != StandModePreference.AUTOMATIC
        if (immediate || forced || !state.isSessionActive) {
            applyEnvironmentMode(target)
            return
        }
        if (pendingModeTarget == target) return

        modeTransitionJob?.cancel()
        pendingModeTarget = target
        modeTransitionJob = viewModelScope.launch {
            delay(
                AmbientLightPolicy.confirmationDelayMillis(
                    current = state.environmentMode,
                    target = target,
                ),
            )
            if (pendingModeTarget == target) {
                pendingModeTarget = null
                applyEnvironmentMode(target)
            }
        }
    }

    private fun applyEnvironmentMode(mode: EnvironmentDisplayMode) {
        val previous = mutableUiState.value.environmentMode
        modeTransitionJob?.cancel()
        modeTransitionJob = null
        pendingModeTarget = null
        if (mode == EnvironmentDisplayMode.OBJECT) {
            movementTriggeredLamp = false
            boyisoStartleLightingProfile = null
        }
        mutableUiState.update {
            it.copy(
                environmentMode = mode,
                experienceMode = currentExperience(mode, it.lampPhase),
            )
        }
        recordEnvironmentModeTransition(previous, mode)
        syncDeviceSensorMonitoring()
        syncRecordingSessionForDisplayMode()
        syncSleepCareMonitoring()
        if (previous != mode && mutableUiState.value.isSessionActive) {
            activateLamp(triggeredByMovement = false)
        }
    }

    private fun activateLamp(
        triggeredByMovement: Boolean,
        bypassStartleDelay: Boolean = false,
        boyisoProfile: BoyisoStartleLightingProfile? = null,
    ) {
        val state = mutableUiState.value
        if (!state.isSessionActive || batteryProtectionLatched) return
        if (triggeredByMovement && !bypassStartleDelay && !StartleActivationPolicy.canActivate(
                mateModeEnteredAtMillis = mateModeEnteredAtElapsedRealtimeMillis,
                nowMillis = SystemClock.elapsedRealtime(),
            )
        ) {
            return
        }
        lampJob?.cancel()
        if (triggeredByMovement) {
            if (activeRecordingSessionId == null) syncRecordingSessionForDisplayMode()
            if (activeStartleEventId == null) {
                activeStartleEventId = runCatching {
                    recordingSessionStore.beginStartleEvent(activeRecordingSessionId)
                }.getOrNull()
            }
        } else {
            finishStartleEvent()
        }
        movementTriggeredLamp = triggeredByMovement
        boyisoStartleLightingProfile = if (triggeredByMovement) boyisoProfile else null
        val restingIntensity = state.settings.lampIntensity
        val maximumIntensity = if (triggeredByMovement) {
            boyisoProfile?.peakIntensity
                ?: max(state.settings.lampIntensity, SimplifiedBrightnessModePolicy.OBJECT_TAP_LEVEL)
        } else {
            state.settings.lampIntensity
        }
        val initialIntensity = boyisoProfile?.startingIntensity ?: maximumIntensity
        mutableUiState.update {
            val phase = if (initialIntensity <= 0f) LampPhase.OFF else LampPhase.HOLDING
            it.copy(
                lampIntensity = initialIntensity,
                lampPhase = phase,
                experienceMode = currentExperience(it.environmentMode, phase),
            )
        }
        syncTorch()

        if (boyisoProfile != null) {
            lampJob = viewModelScope.launch {
                val startedAt = SystemClock.elapsedRealtime()
                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    val complete = elapsed >= boyisoProfile.totalMillis
                    val intensity = BoyisoStartleLightingPolicy.intensityAt(
                        profile = boyisoProfile,
                        elapsedMillis = elapsed,
                        restingIntensity = restingIntensity,
                    )
                    mutableUiState.update {
                        it.copy(
                            lampIntensity = intensity,
                            lampPhase = if (complete) {
                                if (restingIntensity <= 0f) LampPhase.OFF else LampPhase.HOLDING
                            } else {
                                LampPhase.FADING
                            },
                            experienceMode = if (complete) {
                                modeExperience(it.environmentMode)
                            } else {
                                currentExperience(it.environmentMode, LampPhase.FADING)
                            },
                        )
                    }
                    syncTorch()
                    if (complete) {
                        movementTriggeredLamp = false
                        boyisoStartleLightingProfile = null
                        finishStartleEvent()
                        torchController.turnOff()
                        break
                    }
                    delay(LAMP_FRAME_MILLIS)
                }
            }
            return
        }

        val shouldFade = triggeredByMovement || StandAutomaticDimmingPolicy.shouldFade(
                automaticDimmingEnabled = state.settings.automaticDimmingEnabled,
                environmentMode = state.environmentMode,
            )
        if (!shouldFade) {
            return
        }

        lampJob = viewModelScope.launch {
            delay((state.settings.holdDurationSeconds * 1_000).toLong())
            if (mutableUiState.value.environmentMode != EnvironmentDisplayMode.MATE) return@launch
            val startedAt = SystemClock.elapsedRealtime()
            val durationMillis = max(100L, (state.settings.fadeDurationSeconds * 1_000).toLong())
            val targetIntensity = if (triggeredByMovement) state.settings.lampIntensity else 0f
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val progress = (elapsed.toFloat() / durationMillis).coerceIn(0f, 1f)
                val intensity = targetIntensity +
                    (maximumIntensity - targetIntensity) * (1f - progress)
                val phase = when {
                    progress < 1f -> LampPhase.FADING
                    targetIntensity <= 0f -> LampPhase.OFF
                    else -> LampPhase.HOLDING
                }
                mutableUiState.update {
                    it.copy(
                        lampIntensity = intensity,
                        lampPhase = phase,
                        experienceMode = currentExperience(it.environmentMode, phase),
                    )
                }
                syncTorch()
                if (progress >= 1f) {
                    movementTriggeredLamp = false
                    boyisoStartleLightingProfile = null
                    finishStartleEvent()
                    torchController.turnOff()
                    mutableUiState.update {
                        it.copy(experienceMode = modeExperience(it.environmentMode))
                    }
                    break
                }
                delay(LAMP_FRAME_MILLIS)
            }
        }
    }

    private fun recordEnvironmentModeTransition(
        previous: EnvironmentDisplayMode,
        current: EnvironmentDisplayMode,
    ) {
        mateModeEnteredAtElapsedRealtimeMillis = StartleActivationPolicy.entryTimeAfterTransition(
            previous = previous,
            current = current,
            existingEntryTimeMillis = mateModeEnteredAtElapsedRealtimeMillis,
            nowMillis = SystemClock.elapsedRealtime(),
        )
    }

    private fun dimLampNow() {
        val state = mutableUiState.value
        if (!state.isSessionActive || state.lampPhase == LampPhase.OFF) return
        lampJob?.cancel()
        val startingIntensity = state.lampIntensity
        movementTriggeredLamp = false
        boyisoStartleLightingProfile = null
        finishStartleEvent()
        lampJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            while (true) {
                val progress = (
                    (SystemClock.elapsedRealtime() - startedAt).toFloat() /
                        MANUAL_DIM_DURATION_MILLIS
                    ).coerceIn(0f, 1f)
                mutableUiState.update {
                    it.copy(
                        lampIntensity = startingIntensity * (1f - progress),
                        lampPhase = if (progress >= 1f) LampPhase.OFF else LampPhase.FADING,
                        experienceMode = modeExperience(it.environmentMode),
                    )
                }
                syncTorch()
                if (progress >= 1f) {
                    torchController.turnOff()
                    break
                }
                delay(LAMP_FRAME_MILLIS)
            }
        }
    }

    private fun currentExperience(
        mode: EnvironmentDisplayMode,
        phase: LampPhase,
    ): StandExperienceMode = if (movementTriggeredLamp && phase != LampPhase.OFF) {
        StandExperienceMode.STARTLED
    } else {
        modeExperience(mode)
    }

    private fun modeExperience(mode: EnvironmentDisplayMode): StandExperienceMode =
        if (mode == EnvironmentDisplayMode.OBJECT) {
            StandExperienceMode.OBJECT
        } else {
            StandExperienceMode.MATE
        }

    private fun syncSleepCareMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            monitorMutex.withLock {
                val state = mutableUiState.value
                val shouldMonitor = foreground.get() &&
                    state.settings.soundSensingEnabled &&
                    SleepCareMonitoringPolicy.shouldMonitor(
                        isSessionActive = state.isSessionActive,
                        environmentMode = state.environmentMode,
                    ) &&
                    !monitoringPausedForPlayback &&
                    !boyisoSpeakerActive &&
                    internetRadioPlayer.state.value !is InternetRadioState.Loading &&
                    internetRadioPlayer.state.value !is InternetRadioState.Playing &&
                    internetRadioPlayer.state.value !is InternetRadioState.Reconnecting &&
                    !state.batteryProtectionActive &&
                    microphonePermissionGranted
                if (shouldMonitor) audioMonitor.start() else audioMonitor.stop()
            }
        }
    }

    private fun syncRecordingSessionForDisplayMode(
        forceClosed: Boolean = false,
        at: Instant = Instant.now(),
    ) {
        val state = mutableUiState.value
        val shouldBeOpen = !forceClosed &&
            foreground.get() &&
            state.isSessionActive &&
            state.environmentMode == EnvironmentDisplayMode.MATE

        if (shouldBeOpen) {
            if (activeRecordingSessionId == null) {
                activeRecordingSessionId = runCatching {
                    recordingSessionStore.beginMateSession(at)
                }.getOrNull()
            }
        } else {
            finishStartleEvent(at)
            val sessionId = activeRecordingSessionId ?: return
            val ended = runCatching {
                recordingSessionStore.endMateSession(sessionId, at)
            }.getOrDefault(false)
            if (ended) activeRecordingSessionId = null
        }
        refreshRecordingSessionGroups(recordingRepository.recordings.value)
    }

    private fun finishStartleEvent(at: Instant = Instant.now()) {
        val eventId = activeStartleEventId ?: return
        val ended = runCatching {
            recordingSessionStore.endStartleEvent(eventId, at)
        }.getOrDefault(false)
        if (ended) activeStartleEventId = null
        refreshRecordingSessionGroups(recordingRepository.recordings.value)
    }

    private fun refreshRecordingSessionGroups(clips: List<RecordingClip>) {
        runCatching { recordingSessionStore.associateUnassigned(clips) }
        mutableRecordingSessionGroups.value = recordingSessionStore.groups(clips)
    }

    private fun syncTorch() {
        val state = mutableUiState.value
        if (!foreground.get() ||
            !cameraPermissionGranted ||
            !state.isSessionActive ||
            !FaceDownLightingPolicy.allowsTorch(state.isFaceDown) ||
            state.environmentMode != EnvironmentDisplayMode.MATE ||
            state.lampPhase == LampPhase.OFF
        ) {
            torchController.turnOff()
            return
        }

        val boyisoProfile = boyisoStartleLightingProfile
        if (boyisoProfile != null) {
            val level = BoyisoStartleLightingPolicy.torchLevel(
                profile = boyisoProfile,
                torchEnabled = state.settings.torchEnabled,
                roomIsDark = AmbientCameraModePolicy.isRecentlyDark(
                    ambientCamera.reading.value,
                    SystemClock.elapsedRealtimeNanos(),
                ),
                supportsStrengthControl = torchController.state.value.maximumStrengthLevel > 1,
            )
            if (level <= 0.0) torchController.turnOff() else torchController.setLevel(level)
            return
        }
        val maximumLevel = LampTorchLightingPolicy.maximumLevel(
            torchEnabled = state.settings.torchEnabled,
            isMovementTriggered = movementTriggeredLamp,
            roomIsDark = AmbientCameraModePolicy.isRecentlyDark(
                ambientCamera.reading.value,
                SystemClock.elapsedRealtimeNanos(),
            ),
            environmentMode = state.environmentMode,
        )
        if (maximumLevel <= 0.0) {
            torchController.turnOff()
            return
        }
        val visualMaximum = state.settings.lampIntensity.coerceAtLeast(0.01f)
        val fadeProgress = (state.lampIntensity / visualMaximum).coerceIn(0f, 1f)
        torchController.setLevel(maximumLevel * fadeProgress)
    }

    private fun syncDeviceSensorMonitoring() {
        when (
            DeviceSensorMonitoringPolicy.mode(
                isForeground = foreground.get(),
                isSessionActive = mutableUiState.value.isSessionActive,
                environmentMode = mutableUiState.value.environmentMode,
            )
        ) {
            DeviceSensorMonitoringMode.STOPPED -> sensorMonitor.stop()
            DeviceSensorMonitoringMode.AMBIENT_ONLY -> sensorMonitor.startAmbientOnly()
            DeviceSensorMonitoringMode.SLEEP_CARE -> sensorMonitor.startSleepCare()
        }
    }

    private fun permissionAudioMessage(state: StandUiState): String? =
        if (state.settings.soundSensingEnabled &&
            state.isSessionActive &&
            state.environmentMode == EnvironmentDisplayMode.MATE &&
            !microphonePermissionGranted
        ) {
            "마이크 권한이 없어 소리 감지를 사용할 수 없습니다."
        } else {
            null
        }

    private fun weatherMessage(availability: WeatherAvailability): String? = when (availability) {
        WeatherAvailability.IDLE -> "현재 위치 날씨를 준비 중"
        WeatherAvailability.REQUESTING_LOCATION -> "대략적 위치 확인 중"
        WeatherAvailability.LOADING -> "현재 날씨 불러오는 중"
        WeatherAvailability.AVAILABLE -> null
        WeatherAvailability.LOCATION_DENIED -> "위치 권한이 없어 날씨를 표시할 수 없습니다."
        WeatherAvailability.PROVIDER_UNAVAILABLE -> "현재 위치를 확인할 수 없습니다."
        WeatherAvailability.OFFLINE -> "네트워크 연결 후 날씨를 다시 확인해 주세요."
        WeatherAvailability.FAILED -> "날씨를 불러오지 못했습니다."
        WeatherAvailability.CLOSED -> null
    }

    override fun onCleared() {
        lampJob?.cancel()
        brightnessTapJob?.cancel()
        brightnessEndpointLockJob?.cancel()
        modeTransitionJob?.cancel()
        audioMonitor.close()
        sensorMonitor.close()
        batteryMonitor.close()
        torchController.close()
        weatherService.close()
        ambientCamera.close()
        internetRadioPlayer.close()
        super.onCleared()
    }

    private data class AudioPresentation(
        val running: Boolean,
        val level: Float,
        val writing: Boolean,
        val error: String?,
    )

    companion object {
        private const val LAMP_FRAME_MILLIS = 50L
        private const val MANUAL_DIM_DURATION_MILLIS = 1_500f
        private const val AMBIENT_CAMERA_SAMPLE_INTERVAL_MILLIS = 45_000L
    }
}
