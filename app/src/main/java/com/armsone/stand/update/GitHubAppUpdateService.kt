package com.armsone.stand.update

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class GitHubAppRelease(
    val productVersion: String,
    val versionCode: Int,
    val tagName: String,
    val assetName: String,
    val apkUrl: URL,
    val assetSizeBytes: Long,
    val sha256: String = "",
    val releaseNotes: String = "",
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data class Checking(val isManual: Boolean = false) : AppUpdateState {
        companion object {
            val Automatic = Checking(isManual = false)
            val Manual = Checking(isManual = true)
        }
    }
    data class Latest(
        val currentVersionCode: Int,
        val release: GitHubAppRelease? = null,
        val message: String? = null,
    ) : AppUpdateState
    data class Available(
        val release: GitHubAppRelease,
        val message: String? = null,
    ) : AppUpdateState
    data class Downloading(val release: GitHubAppRelease, val progressPercent: Int? = null, val isAutomatic: Boolean = false) : AppUpdateState
    data class Ready(val release: GitHubAppRelease, val apkFile: File) : AppUpdateState
    data class Failed(
        val message: String,
        val canRetry: Boolean = true,
    ) : AppUpdateState
}

class GitHubAppUpdateService(
    context: Context,
    private val currentVersionCode: Int,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val downloadManager = applicationContext.getSystemService(DownloadManager::class.java)
    private val preferences = applicationContext.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("GitHubAppUpdateService"),
    )
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private val isChecking = AtomicBoolean(false)
    private var downloadJob: Job? = null
    private var activeDownloadId: Long? = null

    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
    var automaticDownloadEnabled: Boolean
        get() = preferences.getBoolean("automatic_download_enabled", true)
        set(value) { preferences.edit().putBoolean("automatic_download_enabled", value).apply() }

    fun checkForUpdate(isManual: Boolean = false) {
        val currentState = mutableState.value
        if (currentState is AppUpdateState.Downloading || currentState is AppUpdateState.Ready) {
            return
        }
        if (!isChecking.compareAndSet(false, true)) return
        mutableState.value = if (isManual) {
            AppUpdateState.Checking.Manual
        } else {
            AppUpdateState.Checking.Automatic
        }
        scope.launch {
            try {
                val result = runCatching { fetchLatestRelease() }
                val resolved = GitHubUpdatePolicy.resolveCheckState(
                    currentVersionCode = currentVersionCode,
                    releaseResult = result,
                    isManual = isManual,
                )
                mutableState.value = resolved
                if (!isManual && automaticDownloadEnabled && resolved is AppUpdateState.Available) startDownload(resolved.release, true)
            } finally {
                isChecking.set(false)
            }
        }
    }

    fun dismiss() {
        val current = mutableState.value
        if (current is AppUpdateState.Checking || current is AppUpdateState.Downloading) return
        mutableState.value = AppUpdateState.Idle
    }

    fun download(release: GitHubAppRelease) = startDownload(release, false)

    fun cancelDownload() {
        activeDownloadId?.let { downloadManager.remove(it) }; activeDownloadId = null; downloadJob?.cancel(); downloadJob = null
        val release = (mutableState.value as? AppUpdateState.Downloading)?.release
        mutableState.value = release?.let { AppUpdateState.Available(it, "다운로드를 취소했습니다. 다시 받을 수 있습니다.") } ?: AppUpdateState.Idle
    }

    private fun startDownload(release: GitHubAppRelease, automatic: Boolean) {
        if (downloadJob?.isActive == true) return
        val current = mutableState.value
        if (current !is AppUpdateState.Available || current.release != release) return
        val request = DownloadManager.Request(Uri.parse(release.apkUrl.toExternalForm())).setTitle("S.tand ${release.productVersion}").setDescription("업데이트 다운로드 중").setMimeType(APK_MIME).setAllowedOverMetered(!automatic).setAllowedOverRoaming(false).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val id = runCatching { downloadManager.enqueue(request) }.getOrElse { mutableState.value = AppUpdateState.Failed("다운로드를 시작하지 못했습니다. 다시 시도해 주세요."); return }
        activeDownloadId = id; mutableState.value = AppUpdateState.Downloading(release, 0, automatic)
        downloadJob = scope.launch {
            try { monitorDownload(id, release, automatic) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { Log.w(LOG_TAG, "Update failed", error); downloadManager.remove(id); mutableState.value = AppUpdateState.Failed("업데이트를 받거나 검증하지 못했습니다. 다시 시도해 주세요.") }
            finally { activeDownloadId = null }
        }
    }

    override fun close() {
        downloadJob?.cancel()
        scope.cancel()
    }

    private suspend fun monitorDownload(id: Long, release: GitHubAppRelease, automatic: Boolean) {
        while (true) {
            val snapshot = queryDownload(id)
            when (snapshot.first) {
                DownloadManager.STATUS_SUCCESSFUL -> { val uri = downloadManager.getUriForDownloadedFile(id) ?: throw IOException("Downloaded file unavailable"); val verified = copyAndVerify(uri, release); downloadManager.remove(id); mutableState.value = AppUpdateState.Ready(release, verified); return }
                DownloadManager.STATUS_FAILED -> throw IOException("DownloadManager failed")
                else -> mutableState.value = AppUpdateState.Downloading(release, snapshot.second, automatic)
            }
            delay(300)
        }
    }

    private fun queryDownload(id: Long): Pair<Int, Int?> = downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
        if (!cursor.moveToFirst()) throw IOException("Download disappeared")
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)); val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)); val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        status to if (total > 0) ((done * 100L) / total).toInt().coerceIn(0, 100) else null
    } ?: throw IOException("Download query failed")

    private fun copyAndVerify(uri: Uri, release: GitHubAppRelease): File {
        val directory = File(applicationContext.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }; val partial = File(directory, "${release.assetName.removeSuffix(".apk")}.partial.apk"); val destination = File(directory, release.assetName)
        partial.delete(); val digest = MessageDigest.getInstance("SHA-256"); var total = 0L
        applicationContext.contentResolver.openInputStream(uri)?.use { input -> partial.outputStream().buffered().use { output -> val buffer = ByteArray(DEFAULT_BUFFER_SIZE); while (true) { val count = input.read(buffer); if (count < 0) break; total += count; if (total > MAX_APK_BYTES) throw IOException("APK too large"); digest.update(buffer, 0, count); output.write(buffer, 0, count) } } } ?: throw IOException("Downloaded file cannot be opened")
        if (total != release.assetSizeBytes || digest.digest().joinToString("") { "%02x".format(it) } != release.sha256) { partial.delete(); throw IOException("APK digest mismatch") }
        verifyDownloadedApk(partial, release.versionCode); destination.delete(); if (!partial.renameTo(destination)) throw IOException("Cannot finalize APK"); return destination
    }

    private fun fetchLatestRelease(): GitHubAppRelease? {
        val connection = openConnection(LATEST_RELEASE_URL)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (status !in 200..299) throw IOException("GitHub returned HTTP $status")
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val text = reader.readText()
                if (text.length > MAX_RELEASE_JSON_CHARS) {
                    throw IOException("GitHub release response is too large")
                }
                text
            }
            return GitHubReleaseDecoder.decode(payload)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAndVerify(release: GitHubAppRelease): File {
        val updateDirectory = File(applicationContext.cacheDir, UPDATE_DIRECTORY).apply {
            if (!isDirectory && !mkdirs()) throw IOException("Cannot create update directory")
        }
        updateDirectory.listFiles()?.forEach { oldFile ->
            if (oldFile.name != release.assetName) oldFile.delete()
        }
        val destination = File(updateDirectory, release.assetName)
        // Android 10's PackageManager refuses to inspect an otherwise valid archive when the
        // temporary path does not end in .apk. Keep the partial marker before the extension.
        val partial = File(
            updateDirectory,
            "${release.assetName.removeSuffix(".apk")}.partial.apk",
        )
        partial.delete()

        val connection = openConnection(release.apkUrl)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("APK download returned HTTP $status")
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_APK_BYTES) throw IOException("APK is too large")
            var total = 0L
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_APK_BYTES) throw IOException("APK is too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (total <= 0L || (release.assetSizeBytes > 0L && total != release.assetSizeBytes)) {
                throw IOException("APK size does not match the release")
            }
            verifyDownloadedApk(partial, release.versionCode)
            destination.delete()
            if (!partial.renameTo(destination)) throw IOException("Cannot finalize APK download")
            return destination
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyDownloadedApk(apkFile: File, expectedVersionCode: Int) {
        val packageManager = applicationContext.packageManager
        // GET_SIGNING_CERTIFICATES returns an empty archive signer list on Samsung Android 10.
        // GET_SIGNATURES remains available from minSdk 26 and returns the actual APK certificate
        // for both installed and archive packages, which is sufficient while this app uses one
        // stable signing certificate without rotation.
        val flags = PackageManager.GET_SIGNATURES
        val archive = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: throw IOException("Android could not parse the downloaded APK")
        if (archive.packageName != applicationContext.packageName) {
            throw IOException("내려받은 APK의 앱 이름이 에스텐드와 맞지 않습니다")
        }
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            archive.versionCode.toLong()
        }
        if (archiveVersion != expectedVersionCode.toLong() || archiveVersion <= currentVersionCode) {
            throw IOException(
                "Downloaded APK version $archiveVersion does not match expected $expectedVersionCode",
            )
        }
        val installed = packageManager.getPackageInfo(applicationContext.packageName, flags)
        val installedSignatures = installed.signatures.orEmpty()
        val archiveSignatures = archive.signatures.orEmpty()
        val installedCertificateSet = installedSignatures.map { it.toCharsString() }.toSet()
        val archiveCertificateSet = archiveSignatures.map { it.toCharsString() }.toSet()
        if (installedCertificateSet.isEmpty()) {
            throw IOException("Android returned no certificate for the installed app")
        }
        if (archiveCertificateSet.isEmpty()) {
            throw IOException("Android returned no certificate for the downloaded APK")
        }
        if (installedCertificateSet != archiveCertificateSet) {
            throw IOException("Downloaded APK certificate does not match the installed app")
        }
    }

    private fun openConnection(url: URL): HttpURLConnection {
        require(url.protocol == "https")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            useCaches = false
            doInput = true
            setRequestProperty("User-Agent", "S.tand-Android/$currentVersionCode")
        }
    }

    private companion object {
        val LATEST_RELEASE_URL = URL(
            "https://api.github.com/repos/armsone/S.tand-Android/releases/latest",
        )
        const val UPDATE_DIRECTORY = "updates"
        const val NETWORK_TIMEOUT_MILLIS = 15_000
        const val MAX_RELEASE_JSON_CHARS = 1_000_000
        const val MAX_APK_BYTES = 200L * 1_024L * 1_024L
        const val APK_MIME = "application/vnd.android.package-archive"
        const val LOG_TAG = "S.tandUpdate"
    }
}

internal object GitHubReleaseDecoder {
    fun decode(payload: String): GitHubAppRelease? {
        val root = JSONObject(payload)
        if (root.optBoolean("draft", true) || root.optBoolean("prerelease", true)) return null
        val tag = root.optString("tag_name")
        val productVersion = GitHubUpdatePolicy.productVersion(tag) ?: return null
        val releaseNotes = root.optString("body")
        val versionCode = GitHubUpdatePolicy.versionCode(releaseNotes) ?: return null
        val assets = root.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            val size = asset.optLong("size", -1L)
            val digest = GitHubUpdatePolicy.sha256(asset.optString("digest")) ?: continue
            if (GitHubUpdatePolicy.isApprovedApkAsset(name, url, productVersion, size)) {
                return GitHubAppRelease(
                    productVersion = productVersion,
                    versionCode = versionCode,
                    tagName = tag,
                    assetName = name,
                    apkUrl = URL(url),
                    assetSizeBytes = size,
                    sha256 = digest,
                    releaseNotes = releaseNotes,
                )
            }
        }
        return null
    }
}

internal object GitHubUpdatePolicy {
    private val TAG_PATTERN = Regex("^android-v((?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*))$")
    private val VERSION_CODE_PATTERN = Regex("(?m)^Android-Version-Code: ([1-9]\\d*)$")
    private val DIGEST_PATTERN = Regex("^sha256:([0-9a-f]{64})$")

    fun productVersion(tagName: String): String? = TAG_PATTERN.matchEntire(tagName)
        ?.groupValues
        ?.get(1)
    fun versionCode(releaseNotes: String): Int? = VERSION_CODE_PATTERN.find(releaseNotes)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
    fun sha256(value: String): String? = DIGEST_PATTERN.matchEntire(value)?.groupValues?.get(1)

    fun isApprovedApkAsset(
        assetName: String,
        urlText: String,
        productVersion: String,
        sizeBytes: Long,
    ): Boolean {
        if (assetName != "S.tand-Android-$productVersion.apk") return false
        if (sizeBytes <= 0L || sizeBytes > 200L * 1_024L * 1_024L) return false
        val url = runCatching { URL(urlText) }.getOrNull() ?: return false
        return url.protocol == "https" &&
            url.host == "github.com" &&
            url.userInfo == null && (url.port == -1 || url.port == 443) && url.query == null && url.ref == null &&
            url.path == "/armsone/S.tand-Android/releases/download/android-v$productVersion/$assetName"
    }

    fun resolveCheckState(
        currentVersionCode: Int,
        releaseResult: Result<GitHubAppRelease?>,
        isManual: Boolean,
    ): AppUpdateState {
        return releaseResult.fold(
            onSuccess = { release ->
                when {
                    release == null -> {
                        if (isManual) {
                            AppUpdateState.Failed(
                                message = "게시된 릴리스 정보를 찾을 수 없어 최신 버전을 확인할 수 없습니다. 나중에 다시 시도해 주세요.",
                                canRetry = true,
                            )
                        } else {
                            AppUpdateState.Idle
                        }
                    }
                    release.versionCode > currentVersionCode -> {
                        AppUpdateState.Available(release)
                    }
                    else -> {
                        if (isManual) {
                            AppUpdateState.Latest(
                                currentVersionCode = currentVersionCode,
                                release = release,
                            )
                        } else {
                            AppUpdateState.Idle
                        }
                    }
                }
            },
            onFailure = {
                if (isManual) {
                    AppUpdateState.Failed(
                        message = "최신 버전을 확인하지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.",
                        canRetry = true,
                    )
                } else {
                    AppUpdateState.Idle
                }
            },
        )
    }
}
