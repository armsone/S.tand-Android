package com.armsone.stand

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.MediaPlayer
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationAttributes
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armsone.stand.model.LampPhase
import com.armsone.stand.model.EnvironmentDisplayMode
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.PermissionReminderPolicy
import com.armsone.stand.model.RadioShareImportPolicy
import com.armsone.stand.model.ScreenLayoutCodec
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.model.ExternalMusicService
import com.armsone.stand.model.StandModePreference
import com.armsone.stand.model.TvUiModePolicy
import com.armsone.stand.boyiso.BoyisoManager
import com.armsone.stand.boyiso.BoyisoQrCode
import com.armsone.stand.boyiso.BoyisoRole
import com.armsone.stand.boyiso.BoyisoStartlePolicy
import com.armsone.stand.recording.RecordingClip
import com.armsone.stand.ui.RecordingsScreen
import com.armsone.stand.ui.AppUpdateDialog
import com.armsone.stand.ui.ScreenEditorScreen
import com.armsone.stand.ui.SettingsScreen
import com.armsone.stand.ui.BoyisoScreen
import com.armsone.stand.ui.InternetRadioScreen
import com.armsone.stand.ui.InternetRadioBrowserScreen
import com.armsone.stand.ui.InternetRadioManagementScreen
import com.armsone.stand.ui.StandHomeScreen
import com.armsone.stand.ui.TokTokGreetingOverlay
import com.armsone.stand.ui.CryingChildAlertOverlay
import com.armsone.stand.ui.WalkieCallAlertOverlay
import com.armsone.stand.ui.ClockFontOptionsScreen
import com.armsone.stand.ui.FontLicenseDetailScreen
import com.armsone.stand.ui.FontLicensesScreen
import com.armsone.stand.ui.theme.STandTheme
import com.armsone.stand.update.AppUpdateState
import com.armsone.stand.update.GitHubAppUpdateService
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val standViewModel: StandViewModel by viewModels()
    private val pendingRadioShareUrl = MutableStateFlow<String?>(null)
    private val boyisoManagerDelegate = lazy { BoyisoManager(this) }
    private val boyisoManager by boyisoManagerDelegate
    private val pendingBoyisoInvitationVersion = MutableStateFlow(0L)
    private val appUpdateService by lazy {
        GitHubAppUpdateService(this, BuildConfig.VERSION_CODE)
    }
    private var pendingUpdateInstallFile: File? = null
    private var pendingBoyisoStart = false

    private var pendingPermissionRequest: PermissionRequest? = null
    private var startSessionAfterPermissionSequence = false
    private val permissionSequenceRemaining = ArrayDeque<PermissionRequest>()
    private var showPermissionReviewOnThisLaunch by mutableStateOf(false)
    private var observingSystemBrightness = false
    private val systemBrightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            standViewModel.onSystemDisplayBrightnessChanged()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val completedRequest = pendingPermissionRequest
        pendingPermissionRequest = null
        syncViewModelPermissions()
        if (completedRequest == PermissionRequest.CAMERA) {
            standViewModel.onCameraPermissionResult(granted)
            if (granted && startSessionAfterPermissionSequence) {
                standViewModel.setCameraAmbientSensingEnabled(true)
            }
        }

        if (startSessionAfterPermissionSequence) {
            continuePermissionReviewSequence()
        }
    }

    private val boyisoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (!pendingBoyisoStart) return@registerForActivityResult
        pendingBoyisoStart = false
        val configuration = boyisoManager.state.value.configuration
        if (
            configuration.role == BoyisoRole.SPEAKER &&
            !hasPermission(Manifest.permission.RECORD_AUDIO)
        ) {
            showToast("말할 사람에는 마이크 권한이 필요합니다.")
        } else {
            startBoyiso()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPermissionRequest = savedInstanceState
            ?.getString(STATE_PENDING_PERMISSION)
            ?.let { name -> PermissionRequest.entries.firstOrNull { it.name == name } }
        startSessionAfterPermissionSequence = savedInstanceState
            ?.getBoolean(STATE_START_SESSION_AFTER_PERMISSION_SEQUENCE)
            ?: false
        savedInstanceState
            ?.getStringArrayList(STATE_PERMISSION_SEQUENCE_REMAINING)
            .orEmpty()
            .mapNotNull { name -> PermissionRequest.entries.firstOrNull { it.name == name } }
            .forEach(permissionSequenceRemaining::addLast)
        val isFirstActivityInProcess = processPermissionReviewDecision == null
        val processPermissionReview = permissionReviewForCurrentProcess()
        val restoredPermissionReview = savedInstanceState
            ?.takeIf { state -> state.containsKey(STATE_SHOW_PERMISSION_REVIEW_ON_THIS_LAUNCH) }
            ?.getBoolean(STATE_SHOW_PERMISSION_REVIEW_ON_THIS_LAUNCH)
        showPermissionReviewOnThisLaunch = PermissionReminderPolicy.activityVisibility(
            isFirstActivityInProcess = isFirstActivityInProcess,
            processDecision = processPermissionReview,
            restoredVisibility = restoredPermissionReview,
        )
        acceptRadioShareDraft(intent)
        acceptBoyisoNotificationEvent(intent)
        acceptBoyisoInvitation(intent)

        setContent {
            STandApp()
        }
        appUpdateService.checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        val pendingFile = pendingUpdateInstallFile ?: return
        if (canRequestPackageInstalls()) {
            window.decorView.post { requestUpdateInstall(pendingFile) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptRadioShareDraft(intent)
        acceptBoyisoNotificationEvent(intent)
        acceptBoyisoInvitation(intent)
    }

    override fun onStart() {
        super.onStart()
        val isTelevision = TvUiModePolicy.isTelevision(resources.configuration)
        BoyisoManager.isAppVisible = true
        startObservingSystemBrightness()
        val hasMicrophonePermission = !isTelevision && hasPermission(Manifest.permission.RECORD_AUDIO)
        val hasLocationPermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasCameraPermission = if (isTelevision) false else hasPermission(Manifest.permission.CAMERA)
        if ((isTelevision && hasLocationPermission) ||
            (!isTelevision && hasMicrophonePermission && hasLocationPermission && hasCameraPermission)
        ) {
            showPermissionReviewOnThisLaunch = false
            processPermissionReviewDecision = false
            clearPermissionReminderSchedule()
        }
        standViewModel.onAppForeground(
            hasMicrophonePermission = hasMicrophonePermission,
            hasLocationPermission = hasLocationPermission,
            hasCameraPermission = hasCameraPermission,
            mayAutomaticallyStart = !showPermissionReviewOnThisLaunch ||
                (isTelevision && hasLocationPermission) ||
                (!isTelevision && hasMicrophonePermission && hasLocationPermission && hasCameraPermission),
        )
        val state = standViewModel.uiState.value
        if (isTelevision && state.settings.modePreference != StandModePreference.OBJECT) {
            standViewModel.setModePreference(StandModePreference.OBJECT)
        }

        applySessionWindowState(state.isSessionActive || state.isExternalMusicModeActive)
        applyOrientationPreference(state.settings.orientationPreference)
    }

    override fun onStop() {
        BoyisoManager.isAppVisible = false
        stopObservingSystemBrightness()
        standViewModel.onAppBackground()
        restoreTransientWindowState()
        super.onStop()
    }

    override fun onDestroy() {
        appUpdateService.close()
        if (boyisoManagerDelegate.isInitialized()) boyisoManager.close()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            val state = standViewModel.uiState.value
            applySessionWindowState(state.isSessionActive)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingPermissionRequest?.let {
            outState.putString(STATE_PENDING_PERMISSION, it.name)
        }
        outState.putBoolean(
            STATE_START_SESSION_AFTER_PERMISSION_SEQUENCE,
            startSessionAfterPermissionSequence,
        )
        outState.putStringArrayList(
            STATE_PERMISSION_SEQUENCE_REMAINING,
            ArrayList(permissionSequenceRemaining.map { it.name }),
        )
        outState.putBoolean(
            STATE_SHOW_PERMISSION_REVIEW_ON_THIS_LAUNCH,
            showPermissionReviewOnThisLaunch,
        )
        super.onSaveInstanceState(outState)
    }

    @Composable
    private fun STandApp() {
        val state by standViewModel.uiState.collectAsStateWithLifecycle()
        val recordings by standViewModel.recordings.collectAsStateWithLifecycle()
        val recordingSessionGroups by
            standViewModel.recordingSessionGroups.collectAsStateWithLifecycle()
        val sharedRadioUrl by pendingRadioShareUrl.collectAsStateWithLifecycle()
        val boyisoInvitationVersion by
            pendingBoyisoInvitationVersion.collectAsStateWithLifecycle()
        val boyisoState by boyisoManager.state.collectAsStateWithLifecycle()
        val latestStandState by rememberUpdatedState(state)
        val latestBoyisoState by rememberUpdatedState(boyisoState)
        val appUpdateState by appUpdateService.state.collectAsStateWithLifecycle()
        var ignoredUpdateVersion by rememberSaveable { mutableStateOf<Int?>(null) }
        var automaticUpdateDownloadEnabled by rememberSaveable { mutableStateOf(appUpdateService.automaticDownloadEnabled) }
        var lastShownTokTokTimestamp by rememberSaveable { mutableStateOf(0L) }
        var lastHandledBoyisoStartleTimestamp by rememberSaveable { mutableStateOf(0L) }
        var lastCryingEventTimestamp by rememberSaveable { mutableStateOf(0L) }
        var urgentChimeSequence by rememberSaveable { mutableStateOf(0L) }
        var activeTokTokSender by remember { mutableStateOf<String?>(null) }
        var activeCryingChildSender by remember { mutableStateOf<String?>(null) }
        var activeWalkieSender by remember { mutableStateOf<String?>(null) }
        var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
        var secondaryReturnDestination by rememberSaveable {
            mutableStateOf(AppDestination.HOME)
        }
        var browserReturnDestination by rememberSaveable {
            mutableStateOf(AppDestination.SETTINGS)
        }
        var boyisoReturnDestination by rememberSaveable {
            mutableStateOf(AppDestination.HOME)
        }
        var radioEditorChannelID by rememberSaveable { mutableStateOf<String?>(null) }
        var editorIsPortrait by rememberSaveable { mutableStateOf(true) }
        var editorDraftEncoded by rememberSaveable { mutableStateOf<String?>(null) }
        var editorClockFontName by rememberSaveable { mutableStateOf<String?>(null) }
        var editorClockHourModeName by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedFontLicenseName by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(sharedRadioUrl) {
            if (sharedRadioUrl != null) {
                secondaryReturnDestination = AppDestination.HOME
                radioEditorChannelID = null
                destination = AppDestination.RADIO
            }
        }
        LaunchedEffect(boyisoInvitationVersion) {
            if (boyisoInvitationVersion > 0L) {
                boyisoReturnDestination = AppDestination.HOME
                destination = AppDestination.BOYISO
            }
        }
        LaunchedEffect(boyisoState.running, boyisoState.configuration.role) {
            standViewModel.setBoyisoSpeakerActive(
                boyisoState.running && boyisoState.configuration.role == BoyisoRole.SPEAKER,
            )
        }
        LaunchedEffect(
            boyisoState.running,
            state.environmentMode,
            state.isSessionActive,
        ) {
            boyisoManager.updateLocalStandState(
                mode = state.environmentMode,
                sessionActive = state.isSessionActive,
            )
        }
        LaunchedEffect(Unit) {
            standViewModel.localMovementEvents.collect {
                val currentBoyiso = latestBoyisoState
                val currentStand = latestStandState
                if (currentBoyiso.running && BoyisoStartlePolicy.shouldRelayMovement(
                        localRole = currentBoyiso.configuration.role,
                        localSessionActive = currentStand.isSessionActive,
                        localMode = currentStand.environmentMode,
                        connectedDevices = currentBoyiso.devices,
                    )
                ) {
                    standViewModel.activateBoyisoStartle(kind = "movement")
                    boyisoManager.sendMovement()
                }
            }
        }
        LaunchedEffect(boyisoState.latestEvent) {
            val event = boyisoState.latestEvent ?: return@LaunchedEffect
            if (event.kind == "toktok" && event.timestampMillis > lastShownTokTokTimestamp) {
                lastShownTokTokTimestamp = event.timestampMillis
                activeTokTokSender = event.sourceName.ifBlank { "연결된 사람" }
                val player = MediaPlayer.create(this@MainActivity, R.raw.boyiso_toktok)
                try {
                    vibrateTokTok()
                    player?.start()
                    delay(3_000)
                } finally {
                    player?.release()
                    activeTokTokSender = null
                }
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    "BoyisoActivity",
                    "received kind=${event.kind} detail=${event.detail} ts=${event.timestampMillis} " +
                        "lastHandled=$lastHandledBoyisoStartleTimestamp",
                )
            }
            if (event.timestampMillis <= lastHandledBoyisoStartleTimestamp) {
                return@LaunchedEffect
            }
            val isSpeakerSoundForViewer = event.kind == "sound" &&
                BoyisoStartlePolicy.shouldActivateForSound(
                    localRole = boyisoState.configuration.role,
                    localSessionActive = state.isSessionActive,
                    localMode = state.environmentMode,
                )
            val isLargeSpeakerSound = isSpeakerSoundForViewer &&
                BoyisoStartlePolicy.shouldShowCryingChild(event)
            val isSharedMovement = event.kind == "movement" &&
                BoyisoStartlePolicy.shouldActivateForMovement(
                    localSessionActive = state.isSessionActive,
                    localMode = state.environmentMode,
                )
            val isWalkieEvent = event.kind == "walkie" && event.role == BoyisoRole.WALKIE
            val shouldWakeForWalkie = BoyisoStartlePolicy.shouldActivateForWalkie(
                event = event,
                localSessionActive = state.isSessionActive,
                localMode = state.environmentMode,
                multiStimulusWakeEnabled = state.settings.multiStimulusWakeEnabled,
            )
            if (BuildConfig.DEBUG) {
                Log.d(
                    "BoyisoActivity",
                    "event kind=${event.kind} detail=${event.detail} ts=${event.timestampMillis} " +
                        "role=${boyisoState.configuration.role.wireValue} active=${state.isSessionActive} " +
                        "mode=${state.environmentMode} visible=${BoyisoManager.isAppVisible} " +
                        "sound=$isSpeakerSoundForViewer movement=$isSharedMovement " +
                        "alreadyHandled=${event.timestampMillis <= lastHandledBoyisoStartleTimestamp}",
                )
            }
            if (isSpeakerSoundForViewer || isSharedMovement || shouldWakeForWalkie) {
                lastHandledBoyisoStartleTimestamp = event.timestampMillis
                standViewModel.activateBoyisoStartle(kind = event.kind, detail = event.detail)
            }
            if (isWalkieEvent) {
                urgentChimeSequence = event.timestampMillis
                activeWalkieSender = event.sourceName.ifBlank { "무전기" }
                try {
                    delay(WALKIE_ALERT_VISIBLE_MILLIS)
                } finally {
                    activeWalkieSender = null
                }
            }
            if (isLargeSpeakerSound) {
                if (
                    lastCryingEventTimestamp == 0L ||
                    event.timestampMillis - lastCryingEventTimestamp > CRYING_ALERT_GAP_MILLIS
                ) {
                    urgentChimeSequence = event.timestampMillis
                }
                lastCryingEventTimestamp = event.timestampMillis
                activeCryingChildSender = event.sourceName.ifBlank { "말할 사람" }
                try {
                    delay(CRYING_ALERT_VISIBLE_MILLIS)
                } finally {
                    activeCryingChildSender = null
                }
            }
        }
        LaunchedEffect(urgentChimeSequence) {
            if (urgentChimeSequence > 0L) playBoyisoChimeTwice()
        }

        fun openScreenEditor() {
            editorIsPortrait = resources.configuration.orientation !=
                Configuration.ORIENTATION_LANDSCAPE
            val layout = if (editorIsPortrait) {
                state.settings.portraitLayout.copy()
            } else {
                state.settings.landscapeLayout.copy()
            }
            editorDraftEncoded = ScreenLayoutCodec.encode(layout)
            editorClockFontName = state.settings.clockFont.name
            editorClockHourModeName = state.settings.clockHourMode.name
            destination = AppDestination.EDITOR
        }

        BackHandler(enabled = destination != AppDestination.HOME) {
            editorDraftEncoded = null
            editorClockFontName = null
            editorClockHourModeName = null
            if (destination == AppDestination.RADIO) pendingRadioShareUrl.value = null
            destination = when (destination) {
                AppDestination.RECORDINGS,
                AppDestination.RADIO,
                AppDestination.RADIO_MANAGEMENT,
                -> secondaryReturnDestination
                AppDestination.BROWSER -> browserReturnDestination
                AppDestination.BOYISO -> boyisoReturnDestination
                AppDestination.CLOCK_FONTS,
                AppDestination.FONT_LICENSES,
                -> AppDestination.SETTINGS
                AppDestination.FONT_LICENSE_DETAIL -> AppDestination.FONT_LICENSES
                else -> AppDestination.HOME
            }
        }

        LaunchedEffect(state.isSessionActive, state.isExternalMusicModeActive) {
            applySessionWindowState(state.isSessionActive || state.isExternalMusicModeActive)
        }
        LaunchedEffect(state.settings.orientationPreference) {
            applyOrientationPreference(state.settings.orientationPreference)
        }
        STandTheme(displayTheme = state.settings.displayTheme) {
            when (destination) {
                AppDestination.HOME -> StandHomeScreen(
                    state = state,
                    showPermissionReview = showPermissionReviewOnThisLaunch,
                    onScreenTap = standViewModel::onScreenTap,
                    onToggleTheme = standViewModel::toggleTheme,
                    onOpenEditor = ::openScreenEditor,
                    onBrightnessAdjustmentStarted = standViewModel::beginBrightnessAdjustment,
                    onBrightnessLevelChanged = standViewModel::updateBrightnessLevel,
                    onBrightnessAdjustmentFinished = standViewModel::endBrightnessAdjustment,
                    readSystemVolume = ::currentSystemMusicVolume,
                    onSystemVolumeChanged = ::updateSystemMusicVolume,
                    onClockScaleChanged = standViewModel::updateClockScale,
                    onToggleTorch = standViewModel::toggleTorchEnabled,
                    onCycleMode = standViewModel::cycleModePreference,
                    onToggleSession = {
                        if (state.isSessionActive) {
                            standViewModel.toggleNightSession()
                        } else if (showPermissionReviewOnThisLaunch) {
                            reviewPermissionsAndStartSession()
                        } else {
                            standViewModel.toggleNightSession()
                        }
                    },
                    onToggleOrientation = {
                        standViewModel.toggleOrientationLock(
                            isPortrait = resources.configuration.orientation !=
                                Configuration.ORIENTATION_LANDSCAPE,
                        )
                    },
                    onOpenRecordings = {
                        secondaryReturnDestination = AppDestination.HOME
                        destination = AppDestination.RECORDINGS
                    },
                    onOpenAiShot = ::openAiShot,
                    onOpenSettings = { destination = AppDestination.SETTINGS },
                    onOpenBoyiso = {
                        boyisoReturnDestination = AppDestination.HOME
                        destination = AppDestination.BOYISO
                    },
                    boyisoStatus = boyisoState.homeStatusText,
                    boyisoCanSendTokTok = boyisoState.running,
                    onSendBoyisoTokTok = if (boyisoState.configuration.role == BoyisoRole.WALKIE) {
                        boyisoManager::sendWalkiePress
                    } else {
                        boyisoManager::sendTokTok
                    },
                    onToggleRadio = standViewModel::toggleInternetRadio,
                    onOpenExternalMusic = ::openExternalMusic,
                    onEndExternalMusic = standViewModel::endExternalMusicMode,
                    onEditRadio = { channelID ->
                        radioEditorChannelID = channelID
                        secondaryReturnDestination = AppDestination.HOME
                        destination = AppDestination.RADIO
                    },
                    onRegisterRadio = {
                        radioEditorChannelID = null
                        secondaryReturnDestination = AppDestination.HOME
                        destination = AppDestination.RADIO
                    },
                    onCheckUpdate = {
                        ignoredUpdateVersion = null
                        appUpdateService.checkForUpdate(isManual = true)
                    },
                )

                AppDestination.SETTINGS -> SettingsScreen(
                    state = state,
                    onUpdate = standViewModel::updateSettings,
                    onModePreferenceSelected = standViewModel::setModePreference,
                    onRestoreRecommended = standViewModel::restoreRecommendedSettings,
                    onToggleInternetRadio = standViewModel::toggleInternetRadio,
                    onOpenExternalMusic = ::openExternalMusic,
                    onEndExternalMusic = standViewModel::endExternalMusicMode,
                    onAssignHomeMusicChannel = standViewModel::assignHomeMusicChannel,
                    onSaveInternetRadio = standViewModel::saveInternetRadioChannel,
                    onDeleteInternetRadio = standViewModel::deleteInternetRadioChannel,
                    onManageInternetRadios = {
                        secondaryReturnDestination = AppDestination.SETTINGS
                        destination = AppDestination.RADIO_MANAGEMENT
                    },
                    onOpenInternetRadioBrowser = {
                        browserReturnDestination = AppDestination.SETTINGS
                        destination = AppDestination.BROWSER
                    },
                    onOpenRecordings = {
                        secondaryReturnDestination = AppDestination.SETTINGS
                        destination = AppDestination.RECORDINGS
                    },
                    onOpenBoyiso = {
                        boyisoReturnDestination = AppDestination.SETTINGS
                        destination = AppDestination.BOYISO
                    },
                    onOpenClockFonts = { destination = AppDestination.CLOCK_FONTS },
                    onOpenFontLicenses = { destination = AppDestination.FONT_LICENSES },
                    boyisoStatus = boyisoState.statusText,
                    onRequestMicrophonePermission = {
                        retryPermission(PermissionRequest.MICROPHONE)
                    },
                    onRequestApproximateLocationPermission = {
                        retryPermission(PermissionRequest.LOCATION)
                    },
                    onRequestCameraPermission = {
                        retryPermission(PermissionRequest.CAMERA)
                    },
                    onCameraAmbientSensingChanged = { enabled ->
                        standViewModel.setCameraAmbientSensingEnabled(enabled)
                        if (enabled && !hasPermission(Manifest.permission.CAMERA)) {
                            retryPermission(PermissionRequest.CAMERA)
                        }
                    },
                    onOpenAppSettings = ::openAppSettings,
                    automaticUpdateDownloadEnabled = automaticUpdateDownloadEnabled,
                    onAutomaticUpdateDownloadChanged = {
                        automaticUpdateDownloadEnabled = it
                        appUpdateService.automaticDownloadEnabled = it
                    },
                    onCheckForUpdates = {
                        ignoredUpdateVersion = null
                        appUpdateService.checkForUpdate(isManual = true)
                    },
                    onBack = { destination = AppDestination.HOME },
                )

                AppDestination.CLOCK_FONTS -> ClockFontOptionsScreen(
                    selectedFont = state.settings.clockFont,
                    onFontSelected = { font ->
                        standViewModel.updateSettings { current -> current.copy(clockFont = font) }
                    },
                    onBack = { destination = AppDestination.SETTINGS },
                )

                AppDestination.FONT_LICENSES -> FontLicensesScreen(
                    onOpenLicense = { font ->
                        selectedFontLicenseName = font.name
                        destination = AppDestination.FONT_LICENSE_DETAIL
                    },
                    onBack = { destination = AppDestination.SETTINGS },
                )

                AppDestination.FONT_LICENSE_DETAIL -> FontLicenseDetailScreen(
                    font = selectedFontLicenseName
                        ?.let { name -> ClockFontChoice.entries.firstOrNull { it.name == name } }
                        ?: ClockFontChoice.PRETENDARD,
                    onBack = { destination = AppDestination.FONT_LICENSES },
                )

                AppDestination.BOYISO -> BoyisoScreen(
                    state = boyisoState,
                    invitationUri = boyisoManager.invitationUri()?.toString(),
                    onUpdateConfiguration = boyisoManager::updateConfiguration,
                    onCreateRoom = boyisoManager::createRoom,
                    onScanInvitation = ::scanBoyisoInvitation,
                    onShareInvitation = ::shareBoyisoInvitation,
                    onStart = ::requestBoyisoStart,
                    onLeaveRoom = ::leaveBoyisoRoom,
                    onTokTok = boyisoManager::sendTokTok,
                    onWalkie = boyisoManager::sendWalkiePress,
                    onBack = { destination = boyisoReturnDestination },
                )

                AppDestination.RECORDINGS -> {
                    DisposableEffect(Unit) {
                        standViewModel.pauseMonitoringForPlayback()
                        onDispose(standViewModel::resumeMonitoringAfterPlayback)
                    }
                    RecordingsScreen(
                        recordings = recordings,
                        sessionGroups = recordingSessionGroups,
                        isBusy = state.recordingOperationInProgress,
                        message = state.recordingOperationMessage,
                        onMessageDismiss = standViewModel::clearRecordingOperationMessage,
                        onBack = { destination = secondaryReturnDestination },
                        onDelete = { clip ->
                            if (!standViewModel.deleteRecording(clip)) {
                                showToast("녹음을 삭제하지 못했습니다.")
                            }
                        },
                        onShare = ::shareRecording,
                        onMergeSelected = standViewModel::mergeRecordings,
                        onMergeToday = standViewModel::mergeToday,
                        onDeleteSelected = standViewModel::deleteRecordings,
                        onDeleteAll = standViewModel::deleteAllRecordings,
                    )
                }

                AppDestination.RADIO -> InternetRadioScreen(
                    configuration = if (sharedRadioUrl == null) {
                        state.settings.internetRadioChannels.firstOrNull {
                            it.id == radioEditorChannelID
                        }
                    } else {
                        null
                    },
                    initialUrl = sharedRadioUrl,
                    onSave = { name, url ->
                        standViewModel.saveInternetRadioChannel(
                            if (sharedRadioUrl == null) radioEditorChannelID else null,
                            name,
                            url,
                        )
                    },
                    onDelete = {
                        radioEditorChannelID?.let(standViewModel::deleteInternetRadioChannel)
                    },
                    onOpenBrowser = {
                        browserReturnDestination = AppDestination.RADIO
                        destination = AppDestination.BROWSER
                    },
                    onBack = {
                        pendingRadioShareUrl.value = null
                        radioEditorChannelID = null
                        destination = secondaryReturnDestination
                    },
                )

                AppDestination.RADIO_MANAGEMENT -> InternetRadioManagementScreen(
                    state = state,
                    onToggle = standViewModel::toggleInternetRadio,
                    onAdd = {
                        radioEditorChannelID = null
                        secondaryReturnDestination = AppDestination.RADIO_MANAGEMENT
                        destination = AppDestination.RADIO
                    },
                    onEdit = { channelID ->
                        radioEditorChannelID = channelID
                        secondaryReturnDestination = AppDestination.RADIO_MANAGEMENT
                        destination = AppDestination.RADIO
                    },
                    onDelete = standViewModel::deleteInternetRadioChannel,
                    onMove = standViewModel::moveInternetRadioChannel,
                    onOpenBrowser = {
                        browserReturnDestination = AppDestination.RADIO_MANAGEMENT
                        destination = AppDestination.BROWSER
                    },
                    onBack = { destination = secondaryReturnDestination },
                )

                AppDestination.BROWSER -> InternetRadioBrowserScreen(
                    onClose = { destination = browserReturnDestination },
                )

                AppDestination.EDITOR -> {
                    val fallback = if (editorIsPortrait) {
                        state.settings.portraitLayout.copy()
                    } else {
                        state.settings.landscapeLayout.copy()
                    }
                    val draft = editorDraftEncoded?.let { encoded ->
                        ScreenLayoutCodec.decodeOrDefault(encoded, fallback)
                    } ?: fallback
                    val draftClockFont = editorClockFontName
                        ?.let { saved -> ClockFontChoice.entries.firstOrNull { it.name == saved } }
                        ?: state.settings.clockFont
                    val draftClockHourMode = editorClockHourModeName
                        ?.let { saved -> ClockHourMode.entries.firstOrNull { it.name == saved } }
                        ?: state.settings.clockHourMode
                    ScreenEditorScreen(
                        state = state,
                        layout = draft,
                        clockFont = draftClockFont,
                        clockHourMode = draftClockHourMode,
                        isPortrait = editorIsPortrait,
                        onLayoutChange = { editorDraftEncoded = ScreenLayoutCodec.encode(it) },
                        onClockFontChange = { editorClockFontName = it.name },
                        onClockHourModeChange = { editorClockHourModeName = it.name },
                        onManageRadios = {
                            secondaryReturnDestination = AppDestination.EDITOR
                            destination = AppDestination.RADIO_MANAGEMENT
                        },
                        onSave = { saved, savedClockFont, savedClockHourMode ->
                            standViewModel.updateSettings { current ->
                                if (editorIsPortrait) {
                                    current.copy(
                                        portraitLayout = saved,
                                        clockFont = savedClockFont,
                                        clockHourMode = savedClockHourMode,
                                    )
                                } else {
                                    current.copy(
                                        landscapeLayout = saved,
                                        clockFont = savedClockFont,
                                        clockHourMode = savedClockHourMode,
                                    )
                                }
                            }
                            editorDraftEncoded = null
                            editorClockFontName = null
                            editorClockHourModeName = null
                            destination = AppDestination.HOME
                        },
                        onCancel = {
                            editorDraftEncoded = null
                            editorClockFontName = null
                            editorClockHourModeName = null
                            destination = AppDestination.HOME
                        },
                    )
                }
            }

            val shouldShowUpdateDialog = when (val update = appUpdateState) {
                is AppUpdateState.Idle -> false
                is AppUpdateState.Checking -> update.isManual
                is AppUpdateState.Available -> ignoredUpdateVersion != update.release.versionCode
                is AppUpdateState.Downloading -> true
                is AppUpdateState.Ready -> ignoredUpdateVersion != update.release.versionCode
                is AppUpdateState.Latest -> true
                is AppUpdateState.Failed -> true
            }
            if (shouldShowUpdateDialog) {
                AppUpdateDialog(
                    state = appUpdateState,
                    onDownload = {
                        (appUpdateState as? AppUpdateState.Available)?.let { available ->
                            appUpdateService.download(available.release)
                        }
                    },
                    onInstall = {
                        (appUpdateState as? AppUpdateState.Ready)?.let { ready ->
                            requestUpdateInstall(ready.apkFile)
                        }
                    },
                    onCancel = appUpdateService::cancelDownload,
                    onRetry = {
                        ignoredUpdateVersion = null
                        appUpdateService.checkForUpdate(isManual = true)
                    },
                    onLater = {
                        when (val update = appUpdateState) {
                            is AppUpdateState.Available -> ignoredUpdateVersion = update.release.versionCode
                            is AppUpdateState.Ready -> ignoredUpdateVersion = update.release.versionCode
                            else -> Unit
                        }
                        appUpdateService.dismiss()
                    },
                )
            }
            activeTokTokSender?.let { sender ->
                TokTokGreetingOverlay(senderName = sender)
            }
            activeWalkieSender?.let { sender ->
                WalkieCallAlertOverlay(senderName = sender)
            }
            activeCryingChildSender?.let { sender ->
                CryingChildAlertOverlay(senderName = sender)
            }
        }
    }

    private fun reviewPermissionsAndStartSession() {
        if (pendingPermissionRequest != null) {
            showToast("진행 중인 권한 요청을 먼저 완료해 주세요.")
            return
        }

        val isTelevision = TvUiModePolicy.isTelevision(resources.configuration)
        permissionSequenceRemaining.clear()
        val requiredRequests = if (isTelevision) {
            listOf(PermissionRequest.LOCATION)
        } else {
            listOf(
                PermissionRequest.CAMERA,
                PermissionRequest.MICROPHONE,
                PermissionRequest.LOCATION,
            )
        }
        requiredRequests.filterNot { request -> hasPermission(permissionFor(request)) }
            .forEach(permissionSequenceRemaining::addLast)

        if (permissionSequenceRemaining.isEmpty()) {
            standViewModel.toggleNightSession()
            return
        }

        startSessionAfterPermissionSequence = true
        continuePermissionReviewSequence()
    }

    private fun continuePermissionReviewSequence() {
        if (pendingPermissionRequest != null) return

        while (permissionSequenceRemaining.isNotEmpty()) {
            val request = permissionSequenceRemaining.removeFirst()
            val permission = permissionFor(request)
            if (hasPermission(permission)) continue
            launchPermission(request = request, permission = permission)
            return
        }

        syncViewModelPermissions()
        val shouldStartSession = startSessionAfterPermissionSequence
        startSessionAfterPermissionSequence = false
        showPermissionReviewOnThisLaunch = false
        processPermissionReviewDecision = false
        if (shouldStartSession && !standViewModel.uiState.value.isSessionActive) {
            standViewModel.toggleNightSession()
        }
    }

    private fun retryPermission(request: PermissionRequest) {
        val permission = permissionFor(request)
        if (hasPermission(permission)) {
            syncViewModelPermissions()
            showToast("이미 허용된 권한입니다.")
            return
        }
        if (pendingPermissionRequest != null) {
            showToast("진행 중인 권한 요청을 먼저 완료해 주세요.")
            return
        }
        launchPermission(
            request = request,
            permission = permission,
        )
    }

    private fun launchPermission(
        request: PermissionRequest,
        permission: String,
    ) {
        if (pendingPermissionRequest != null) return

        pendingPermissionRequest = request
        runCatching { permissionLauncher.launch(permission) }
            .onFailure {
                pendingPermissionRequest = null
                syncViewModelPermissions()
                if (startSessionAfterPermissionSequence) {
                    continuePermissionReviewSequence()
                }
            }
    }

    private fun permissionFor(request: PermissionRequest): String = when (request) {
        PermissionRequest.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        PermissionRequest.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        PermissionRequest.CAMERA -> Manifest.permission.CAMERA
    }

    private fun schedulePermissionReviewForLaunch(): Boolean {
        val preferences = getSharedPreferences(
            PERMISSION_REMINDER_PREFERENCES,
            MODE_PRIVATE,
        )
        val isTelevision = TvUiModePolicy.isTelevision(resources.configuration)
        val requiredPermissions = TvUiModePolicy.filterLaunchPermissions(
            isTelevision = isTelevision,
            permissions = listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
        val hasMissingPermission = requiredPermissions.any { permission -> !hasPermission(permission) }
        val remaining = if (preferences.contains(KEY_LAUNCHES_UNTIL_PERMISSION_REMINDER)) {
            preferences.getInt(KEY_LAUNCHES_UNTIL_PERMISSION_REMINDER, 1)
        } else {
            null
        }
        val nextInterval = Random.nextInt(
            from = PermissionReminderPolicy.MINIMUM_LAUNCH_INTERVAL,
            until = PermissionReminderPolicy.MAXIMUM_LAUNCH_INTERVAL + 1,
        )
        val decision = PermissionReminderPolicy.decide(
            hasMissingPermission = hasMissingPermission,
            launchesUntilReminder = remaining,
            nextRandomInterval = nextInterval,
        )

        preferences.edit().apply {
            decision.launchesUntilNextReminder?.let { launches ->
                putInt(KEY_LAUNCHES_UNTIL_PERMISSION_REMINDER, launches)
            } ?: remove(KEY_LAUNCHES_UNTIL_PERMISSION_REMINDER)
        }.apply()
        return decision.shouldShow
    }

    private fun permissionReviewForCurrentProcess(): Boolean {
        processPermissionReviewDecision?.let { decision -> return decision }
        return schedulePermissionReviewForLaunch().also { decision ->
            processPermissionReviewDecision = decision
        }
    }

    private fun clearPermissionReminderSchedule() {
        getSharedPreferences(PERMISSION_REMINDER_PREFERENCES, MODE_PRIVATE)
            .edit()
            .remove(KEY_LAUNCHES_UNTIL_PERMISSION_REMINDER)
            .apply()
    }

    private fun syncViewModelPermissions() {
        val isTelevision = TvUiModePolicy.isTelevision(resources.configuration)
        standViewModel.setPermissions(
            hasMicrophonePermission = !isTelevision && hasPermission(Manifest.permission.RECORD_AUDIO),
            hasLocationPermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
            hasCameraPermission = !isTelevision && hasPermission(Manifest.permission.CAMERA),
        )
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startObservingSystemBrightness() {
        if (observingSystemBrightness) return
        runCatching {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                systemBrightnessObserver,
            )
        }.onSuccess {
            observingSystemBrightness = true
        }
    }

    private fun stopObservingSystemBrightness() {
        if (!observingSystemBrightness) return
        runCatching { contentResolver.unregisterContentObserver(systemBrightnessObserver) }
        observingSystemBrightness = false
    }

    private fun applySessionWindowState(isSessionActive: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isSessionActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    private fun openExternalMusic(service: ExternalMusicService) {
        val launchIntent = packageManager.getLaunchIntentForPackage(service.packageName)
        if (launchIntent != null) {
            standViewModel.beginExternalMusicMode(service)
            showToast("로그인·재생 후 S.tand로 돌아오세요.")
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }

        standViewModel.markExternalMusicUnavailable(service)
        showToast("${service.displayName} 앱을 먼저 설치해 주세요.")
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=${service.packageName}".toUri(),
        )
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=${service.packageName}".toUri(),
        )
        runCatching { startActivity(marketIntent) }
            .recoverCatching { startActivity(webIntent) }
    }

    private fun applyOrientationPreference(preference: OrientationPreference) {
        val isTelevision = TvUiModePolicy.isTelevision(resources.configuration)
        val orientation = when {
            isTelevision -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            preference == OrientationPreference.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            preference == OrientationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            preference == OrientationPreference.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != orientation) requestedOrientation = orientation
    }

    private fun restoreTransientWindowState() {
        applySessionWindowState(isSessionActive = false)
    }

    private fun openAiShot() {
        val intent = Intent(Intent.ACTION_VIEW, AI_SHOT_URI.toUri())
        runCatching { startActivity(intent) }
            .onFailure { showToast("AiShot을 실행할 수 없습니다.") }
    }

    private fun shareRecording(clip: RecordingClip) {
        if (!clip.file.isFile) {
            showToast("녹음 파일을 찾을 수 없습니다.")
            return
        }

        val contentUri = runCatching {
            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                clip.file,
            )
        }.getOrElse {
            showToast("녹음 파일을 공유할 수 없습니다.")
            return
        }

        val mimeType = clip.mediaFormat?.mimeType ?: DEFAULT_AUDIO_MIME_TYPE
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData(
                ClipDescription("에스텐드 녹음", arrayOf(mimeType)),
                ClipData.Item(contentUri),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "녹음 공유")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(chooser) }
            .onFailure { showToast("녹음 파일을 공유할 앱이 없습니다.") }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun acceptRadioShareDraft(intent: Intent?) {
        pendingRadioShareUrl.value = RadioShareImportPolicy.validatedUrlOrNull(
            intent?.getStringExtra(EXTRA_RADIO_SHARE_URL),
        )
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        runCatching { startActivity(intent) }
            .onFailure { showToast("앱 권한 설정을 열 수 없습니다.") }
    }

    private fun requestUpdateInstall(apkFile: File) {
        if (!apkFile.isFile) {
            showToast("업데이트 파일을 찾을 수 없습니다.")
            return
        }
        if (!canRequestPackageInstalls()) {
            pendingUpdateInstallFile = apkFile
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.fromParts("package", packageName, null),
            )
            runCatching { startActivity(intent) }
                .onFailure {
                    pendingUpdateInstallFile = null
                    showToast("업데이트 설치 권한 설정을 열 수 없습니다.")
                }
            return
        }

        val contentUri = runCatching {
            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                apkFile,
            )
        }.getOrElse {
            showToast("업데이트 파일을 열 수 없습니다.")
            return
        }
        pendingUpdateInstallFile = null
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure { showToast("Android 설치 화면을 열 수 없습니다.") }
    }

    private fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            packageManager.canRequestPackageInstalls()

    private fun vibrateTokTok() {
        val audioManager = getSystemService(AudioManager::class.java)
        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 80, 70, 120), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION),
            )
        } else {
            vibrator.vibrate(effect)
        }
    }

    private suspend fun playBoyisoChimeTwice() {
        repeat(2) {
            val player = MediaPlayer.create(this, R.raw.boyiso_toktok) ?: return
            try {
                player.start()
                delay(BOYISO_CHIME_INTERVAL_MILLIS)
            } finally {
                player.release()
            }
        }
    }

    private fun acceptBoyisoInvitation(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "stand" || uri.host != "boyiso") return
        if (boyisoManager.acceptInvitation(uri)) {
            showToast("보이소 QR을 확인했습니다.")
        }
        pendingBoyisoInvitationVersion.value += 1L
    }

    private fun acceptBoyisoNotificationEvent(intent: Intent?) {
        val eventIntent = intent ?: return
        if (eventIntent.action !in setOf(
                "com.armsone.stand.boyiso.OPEN_DETECTION",
                "com.armsone.stand.boyiso.OPEN_WALKIE",
            )
        ) return
        boyisoManager.receiveNotificationEvent(eventIntent)
    }

    private fun scanBoyisoInvitation() {
        val isTelevision = TvUiModePolicy.isTelevision(resources.configuration)
        if (isTelevision) {
            showToast("TV에서는 카메라 촬영을 지원하지 않습니다. 스마트폰으로 TV의 QR코드를 스캔해 주세요.")
            return
        }
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val uri = barcode.rawValue?.let(Uri::parse)
                if (uri != null && boyisoManager.acceptInvitation(uri)) {
                    pendingBoyisoInvitationVersion.value += 1L
                    showToast("같은 보이소 공간에 참여했습니다.")
                } else {
                    showToast("보이소 초대 QR이 아닙니다.")
                }
            }
            .addOnFailureListener { showToast("QR을 읽지 못했습니다. 다시 시도해 주세요.") }
    }

    private fun shareBoyisoInvitation() {
        val uri = boyisoManager.invitationUri() ?: return
        val imageFile = runCatching {
            val shareDirectory = File(cacheDir, "boyiso-share").also { directory ->
                check(directory.exists() || directory.mkdirs())
            }
            File(shareDirectory, "boyiso-invitation.png").also { file ->
                FileOutputStream(file, false).use { output ->
                    check(
                        BoyisoQrCode.create(uri.toString()).compress(
                            android.graphics.Bitmap.CompressFormat.PNG,
                            100,
                            output,
                        ),
                    )
                }
            }
        }.getOrElse {
            showToast("QR 사진을 만들지 못했습니다.")
            return
        }
        val contentUri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
        }.getOrElse {
            showToast("QR 사진을 공유할 수 없습니다.")
            return
        }
        val mimeType = "image/png"
        val share = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "보이소에서 이 QR 사진을 찍고 같은 공간에 들어오세요.")
            clipData = ClipData(
                ClipDescription("보이소 초대 QR", arrayOf(mimeType)),
                ClipData.Item(contentUri),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(share, "QR 사진 보내기")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(chooser) }
            .onFailure { showToast("QR 사진을 보낼 앱이 없습니다.") }
    }

    private fun requestBoyisoStart() {
        val configuration = boyisoManager.state.value.configuration
        val missing = buildList {
            if (
                configuration.role == BoyisoRole.SPEAKER &&
                !hasPermission(Manifest.permission.RECORD_AUDIO)
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                }
                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                }
            } else if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isEmpty()) {
            startBoyiso()
        } else {
            pendingBoyisoStart = true
            boyisoPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startBoyiso() {
        val speaking = boyisoManager.state.value.configuration.role == BoyisoRole.SPEAKER
        standViewModel.setBoyisoSpeakerActive(speaking)
        runCatching(boyisoManager::start).onFailure { error ->
            standViewModel.setBoyisoSpeakerActive(false)
            showToast(error.message ?: "보이소 연결을 시작하지 못했습니다.")
        }
    }

    private fun stopBoyiso() {
        boyisoManager.stop()
        standViewModel.setBoyisoSpeakerActive(false)
    }

    private fun leaveBoyisoRoom() {
        boyisoManager.leaveRoom()
        standViewModel.setBoyisoSpeakerActive(false)
    }

    private fun currentSystemMusicVolume(): Float {
        val audioManager = getSystemService(AudioManager::class.java)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum
    }

    private fun updateSystemMusicVolume(level: Float) {
        val audioManager = getSystemService(AudioManager::class.java)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (level.coerceIn(0f, 1f) * maximum).roundToInt(),
            0,
        )
    }

    private enum class AppDestination {
        HOME,
        SETTINGS,
        RECORDINGS,
        EDITOR,
        RADIO,
        RADIO_MANAGEMENT,
        BROWSER,
        BOYISO,
        CLOCK_FONTS,
        FONT_LICENSES,
        FONT_LICENSE_DETAIL,
    }

    private enum class PermissionRequest { MICROPHONE, LOCATION, CAMERA }

    companion object {
        const val EXTRA_RADIO_SHARE_URL = "com.armsone.stand.extra.RADIO_SHARE_URL"
        private const val AI_SHOT_URI = "hanclip://aishot"
        private const val DEFAULT_AUDIO_MIME_TYPE = "audio/*"
        private const val BOYISO_CHIME_INTERVAL_MILLIS = 1_250L
        private const val CRYING_ALERT_VISIBLE_MILLIS = 3_000L
        private const val WALKIE_ALERT_VISIBLE_MILLIS = 3_000L
        private const val CRYING_ALERT_GAP_MILLIS = 2_500L
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val STATE_PENDING_PERMISSION = "pending_permission"
        private const val STATE_START_SESSION_AFTER_PERMISSION_SEQUENCE =
            "start_session_after_permission_sequence"
        private const val STATE_PERMISSION_SEQUENCE_REMAINING =
            "permission_sequence_remaining"
        private const val STATE_SHOW_PERMISSION_REVIEW_ON_THIS_LAUNCH =
            "show_permission_review_on_this_launch"
        private const val PERMISSION_REMINDER_PREFERENCES = "permission_reminder"
        private const val KEY_LAUNCHES_UNTIL_PERMISSION_REMINDER =
            "launches_until_permission_reminder"
        private var processPermissionReviewDecision: Boolean? = null
    }
}
