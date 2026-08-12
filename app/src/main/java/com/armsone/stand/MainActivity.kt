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
import android.provider.Settings
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
import com.armsone.stand.model.EnvironmentDisplayMode
import com.armsone.stand.model.LampPhase
import com.armsone.stand.model.OrientationPreference
import com.armsone.stand.model.RadioShareImportPolicy
import com.armsone.stand.model.StandExperienceMode
import com.armsone.stand.model.ScreenLayoutCodec
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.ClockHourMode
import com.armsone.stand.recording.RecordingClip
import com.armsone.stand.ui.RecordingsScreen
import com.armsone.stand.ui.AppUpdateDialog
import com.armsone.stand.ui.ScreenEditorScreen
import com.armsone.stand.ui.SettingsScreen
import com.armsone.stand.ui.InternetRadioScreen
import com.armsone.stand.ui.InternetRadioBrowserScreen
import com.armsone.stand.ui.InternetRadioManagementScreen
import com.armsone.stand.ui.StandHomeScreen
import com.armsone.stand.ui.StandUiState
import com.armsone.stand.ui.theme.STandTheme
import com.armsone.stand.update.AppUpdateState
import com.armsone.stand.update.GitHubAppUpdateService
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val standViewModel: StandViewModel by viewModels()
    private val pendingRadioShareUrl = MutableStateFlow<String?>(null)
    private val appUpdateService by lazy {
        GitHubAppUpdateService(this, BuildConfig.VERSION_CODE)
    }
    private var pendingUpdateInstallFile: File? = null

    private var cameraPermissionAttempted = false
    private var cameraPermissionQueued = false
    private var pendingPermissionRequest: PermissionRequest? = null
    private var startSessionAfterPermissionSequence = false
    private val permissionSequenceRemaining = ArrayDeque<PermissionRequest>()
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
        }

        when {
            completedRequest == PermissionRequest.CAMERA -> cameraPermissionQueued = false
        }
        if (startSessionAfterPermissionSequence) {
            continuePermissionReviewSequence()
        } else {
            launchQueuedCameraPermissionIfNeeded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraPermissionAttempted = savedInstanceState
            ?.getBoolean(STATE_CAMERA_PERMISSION_ATTEMPTED)
            ?: false
        cameraPermissionQueued = savedInstanceState
            ?.getBoolean(STATE_CAMERA_PERMISSION_QUEUED)
            ?: false
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
        acceptRadioShareDraft(intent)

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
    }

    override fun onStart() {
        super.onStart()
        startObservingSystemBrightness()
        val hasMicrophonePermission = hasPermission(Manifest.permission.RECORD_AUDIO)
        val hasLocationPermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasCameraPermission = hasPermission(Manifest.permission.CAMERA)
        standViewModel.onAppForeground(
            hasMicrophonePermission = hasMicrophonePermission,
            hasLocationPermission = hasLocationPermission,
            hasCameraPermission = hasCameraPermission,
            mayAutomaticallyStart = hasMicrophonePermission &&
                hasLocationPermission &&
                hasCameraPermission,
        )

        val state = standViewModel.uiState.value
        applySessionWindowState(state.isSessionActive)
        applyOrientationPreference(state.settings.orientationPreference)
    }

    override fun onStop() {
        stopObservingSystemBrightness()
        standViewModel.onAppBackground()
        restoreTransientWindowState()
        super.onStop()
    }

    override fun onDestroy() {
        appUpdateService.close()
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
        outState.putBoolean(STATE_CAMERA_PERMISSION_ATTEMPTED, cameraPermissionAttempted)
        outState.putBoolean(STATE_CAMERA_PERMISSION_QUEUED, cameraPermissionQueued)
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
        super.onSaveInstanceState(outState)
    }

    @Composable
    private fun STandApp() {
        val state by standViewModel.uiState.collectAsStateWithLifecycle()
        val recordings by standViewModel.recordings.collectAsStateWithLifecycle()
        val recordingSessionGroups by
            standViewModel.recordingSessionGroups.collectAsStateWithLifecycle()
        val sharedRadioUrl by pendingRadioShareUrl.collectAsStateWithLifecycle()
        val appUpdateState by appUpdateService.state.collectAsStateWithLifecycle()
        var ignoredUpdateVersion by rememberSaveable { mutableStateOf<Int?>(null) }
        var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
        var secondaryReturnDestination by rememberSaveable {
            mutableStateOf(AppDestination.HOME)
        }
        var browserReturnDestination by rememberSaveable {
            mutableStateOf(AppDestination.SETTINGS)
        }
        var radioEditorChannelID by rememberSaveable { mutableStateOf<String?>(null) }
        var editorIsPortrait by rememberSaveable { mutableStateOf(true) }
        var editorDraftEncoded by rememberSaveable { mutableStateOf<String?>(null) }
        var editorClockFontName by rememberSaveable { mutableStateOf<String?>(null) }
        var editorClockHourModeName by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(sharedRadioUrl) {
            if (sharedRadioUrl != null) {
                secondaryReturnDestination = AppDestination.HOME
                radioEditorChannelID = null
                destination = AppDestination.RADIO
            }
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
                else -> AppDestination.HOME
            }
        }

        LaunchedEffect(state.isSessionActive) {
            applySessionWindowState(state.isSessionActive)
        }
        LaunchedEffect(state.settings.orientationPreference) {
            applyOrientationPreference(state.settings.orientationPreference)
        }
        LaunchedEffect(torchUseRequested(state)) {
            if (torchUseRequested(state)) requestCameraPermissionForTorch()
        }
        LaunchedEffect(appUpdateState) {
            val ready = appUpdateState as? AppUpdateState.Ready ?: return@LaunchedEffect
            if (ignoredUpdateVersion != ready.release.versionCode) {
                requestUpdateInstall(ready.apkFile)
            }
        }

        STandTheme(displayTheme = state.settings.displayTheme) {
            when (destination) {
                AppDestination.HOME -> StandHomeScreen(
                    state = state,
                    onScreenTap = standViewModel::onScreenTap,
                    onToggleTheme = standViewModel::toggleTheme,
                    onOpenEditor = ::openScreenEditor,
                    onBrightnessAdjustmentStarted = standViewModel::beginBrightnessAdjustment,
                    onBrightnessLevelChanged = standViewModel::updateBrightnessLevel,
                    onBrightnessAdjustmentFinished = standViewModel::endBrightnessAdjustment,
                    onInternetRadioVolumeChanged = standViewModel::updateInternetRadioVolume,
                    onClockScaleChanged = standViewModel::updateClockScale,
                    onToggleTorch = standViewModel::toggleTorchEnabled,
                    onCycleMode = standViewModel::cycleModePreference,
                    onToggleSession = {
                        if (state.isSessionActive) {
                            standViewModel.toggleNightSession()
                        } else {
                            reviewPermissionsAndStartSession()
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
                    onToggleRadio = standViewModel::toggleInternetRadio,
                    onEditRadio = { channelID ->
                        radioEditorChannelID = channelID
                        secondaryReturnDestination = AppDestination.HOME
                        destination = AppDestination.RADIO
                    },
                )

                AppDestination.SETTINGS -> SettingsScreen(
                    state = state,
                    onUpdate = standViewModel::updateSettings,
                    onToggleMode = standViewModel::onScreenTap,
                    onRestoreRecommended = standViewModel::restoreRecommendedSettings,
                    onToggleInternetRadio = standViewModel::toggleInternetRadio,
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
                    onBack = { destination = AppDestination.HOME },
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

            val updateVersion = when (val update = appUpdateState) {
                is AppUpdateState.Available -> update.release.versionCode
                is AppUpdateState.Downloading -> update.release.versionCode
                is AppUpdateState.Ready -> update.release.versionCode
                AppUpdateState.Checking,
                AppUpdateState.Idle,
                -> null
            }
            if (updateVersion != null && ignoredUpdateVersion != updateVersion) {
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
                    onLater = { ignoredUpdateVersion = updateVersion },
                )
            }
        }
    }

    private fun reviewPermissionsAndStartSession() {
        if (pendingPermissionRequest != null) {
            showToast("진행 중인 권한 요청을 먼저 완료해 주세요.")
            return
        }

        permissionSequenceRemaining.clear()
        listOf(
            PermissionRequest.CAMERA,
            PermissionRequest.MICROPHONE,
            PermissionRequest.LOCATION,
        ).filterNot { request -> hasPermission(permissionFor(request)) }
            .forEach(permissionSequenceRemaining::addLast)

        if (permissionSequenceRemaining.isEmpty()) {
            standViewModel.toggleNightSession()
            return
        }

        startSessionAfterPermissionSequence = true
        if (permissionSequenceRemaining.contains(PermissionRequest.CAMERA)) {
            cameraPermissionAttempted = true
        }
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
        if (shouldStartSession && !standViewModel.uiState.value.isSessionActive) {
            standViewModel.toggleNightSession()
        }
        launchQueuedCameraPermissionIfNeeded()
    }

    private fun requestCameraPermissionForTorch() {
        if (hasPermission(Manifest.permission.CAMERA) || cameraPermissionAttempted) return
        cameraPermissionAttempted = true

        if (pendingPermissionRequest != null) {
            cameraPermissionQueued = true
        } else {
            launchPermission(
                request = PermissionRequest.CAMERA,
                permission = Manifest.permission.CAMERA,
            )
        }
    }

    private fun launchQueuedCameraPermissionIfNeeded() {
        if (!cameraPermissionQueued) return
        cameraPermissionQueued = false

        if (hasPermission(Manifest.permission.CAMERA)) return
        if (!torchUseRequested(standViewModel.uiState.value)) {
            cameraPermissionAttempted = false
            return
        }
        launchPermission(
            request = PermissionRequest.CAMERA,
            permission = Manifest.permission.CAMERA,
        )
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
        if (request == PermissionRequest.CAMERA) cameraPermissionAttempted = true
        launchPermission(
            request = request,
            permission = permission,
        )
    }

    private fun launchPermission(
        request: PermissionRequest,
        permission: String,
    ) {
        if (pendingPermissionRequest != null) {
            if (request == PermissionRequest.CAMERA) cameraPermissionQueued = true
            return
        }

        pendingPermissionRequest = request
        runCatching { permissionLauncher.launch(permission) }
            .onFailure {
                pendingPermissionRequest = null
                syncViewModelPermissions()
                if (startSessionAfterPermissionSequence) {
                    continuePermissionReviewSequence()
                } else when {
                    request == PermissionRequest.CAMERA -> {
                        cameraPermissionQueued = false
                        cameraPermissionAttempted = false
                    }
                    else -> launchQueuedCameraPermissionIfNeeded()
                }
            }
    }

    private fun permissionFor(request: PermissionRequest): String = when (request) {
        PermissionRequest.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        PermissionRequest.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        PermissionRequest.CAMERA -> Manifest.permission.CAMERA
    }

    private fun syncViewModelPermissions() {
        standViewModel.setPermissions(
            hasMicrophonePermission = hasPermission(Manifest.permission.RECORD_AUDIO),
            hasLocationPermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
            hasCameraPermission = hasPermission(Manifest.permission.CAMERA),
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

    private fun applyOrientationPreference(preference: OrientationPreference) {
        val orientation = when (preference) {
            OrientationPreference.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationPreference.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
                ClipDescription("S.tand 녹음", arrayOf(mimeType)),
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

    private fun torchUseRequested(state: StandUiState): Boolean =
        state.isSessionActive &&
            state.environmentMode == EnvironmentDisplayMode.MATE &&
            state.lampPhase != LampPhase.OFF &&
            (state.settings.torchEnabled ||
                state.experienceMode == StandExperienceMode.STARTLED)

    private enum class AppDestination {
        HOME,
        SETTINGS,
        RECORDINGS,
        EDITOR,
        RADIO,
        RADIO_MANAGEMENT,
        BROWSER,
    }

    private enum class PermissionRequest { MICROPHONE, LOCATION, CAMERA }

    companion object {
        const val EXTRA_RADIO_SHARE_URL = "com.armsone.stand.extra.RADIO_SHARE_URL"
        private const val AI_SHOT_URI = "hanclip://aishot"
        private const val DEFAULT_AUDIO_MIME_TYPE = "audio/*"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val STATE_CAMERA_PERMISSION_ATTEMPTED = "camera_permission_attempted"
        private const val STATE_CAMERA_PERMISSION_QUEUED = "camera_permission_queued"
        private const val STATE_PENDING_PERMISSION = "pending_permission"
        private const val STATE_START_SESSION_AFTER_PERMISSION_SEQUENCE =
            "start_session_after_permission_sequence"
        private const val STATE_PERMISSION_SEQUENCE_REMAINING =
            "permission_sequence_remaining"
    }
}
