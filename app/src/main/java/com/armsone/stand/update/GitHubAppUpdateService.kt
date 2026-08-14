package com.armsone.stand.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class GitHubAppRelease(
    val versionCode: Int,
    val tagName: String,
    val assetName: String,
    val apkUrl: URL,
    val assetSizeBytes: Long,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(
        val release: GitHubAppRelease,
        val message: String? = null,
    ) : AppUpdateState
    data class Downloading(val release: GitHubAppRelease) : AppUpdateState
    data class Ready(val release: GitHubAppRelease, val apkFile: File) : AppUpdateState
}

class GitHubAppUpdateService(
    context: Context,
    private val currentVersionCode: Int,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("GitHubAppUpdateService"),
    )
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private val checkStarted = AtomicBoolean(false)

    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    fun checkForUpdate() {
        if (!checkStarted.compareAndSet(false, true)) return
        mutableState.value = AppUpdateState.Checking
        scope.launch {
            mutableState.value = runCatching { fetchLatestRelease() }
                .getOrNull()
                ?.takeIf { it.versionCode > currentVersionCode }
                ?.let(AppUpdateState::Available)
                ?: AppUpdateState.Idle
        }
    }

    fun download(release: GitHubAppRelease) {
        val current = mutableState.value
        if (current !is AppUpdateState.Available || current.release != release) return
        mutableState.value = AppUpdateState.Downloading(release)
        scope.launch {
            val result = runCatching { downloadAndVerify(release) }
            mutableState.value = result.fold(
                onSuccess = { AppUpdateState.Ready(release, it) },
                onFailure = {
                    Log.w(
                        LOG_TAG,
                        "GitHub update download or verification failed: ${it.message}",
                        it,
                    )
                    AppUpdateState.Available(
                        release = release,
                        message = "업데이트를 받지 못했습니다. 인터넷 연결을 확인해 주세요.",
                    )
                },
            )
        }
    }

    override fun close() {
        scope.cancel()
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
        const val LOG_TAG = "S.tandUpdate"
    }
}

internal object GitHubReleaseDecoder {
    fun decode(payload: String): GitHubAppRelease? {
        val root = JSONObject(payload)
        if (root.optBoolean("draft", true) || root.optBoolean("prerelease", true)) return null
        val tag = root.optString("tag_name")
        val versionCode = GitHubUpdatePolicy.versionCode(tag) ?: return null
        val assets = root.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            val size = asset.optLong("size", -1L)
            if (GitHubUpdatePolicy.isApprovedApkAsset(name, url, versionCode, size)) {
                return GitHubAppRelease(
                    versionCode = versionCode,
                    tagName = tag,
                    assetName = name,
                    apkUrl = URL(url),
                    assetSizeBytes = size,
                )
            }
        }
        return null
    }
}

internal object GitHubUpdatePolicy {
    private val TAG_PATTERN = Regex("^android-v([1-9]\\d*)$")

    fun versionCode(tagName: String): Int? = TAG_PATTERN.matchEntire(tagName)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

    fun isApprovedApkAsset(
        assetName: String,
        urlText: String,
        versionCode: Int,
        sizeBytes: Long,
    ): Boolean {
        if (assetName != "S.tand-Android-v$versionCode.apk") return false
        if (sizeBytes <= 0L || sizeBytes > 200L * 1_024L * 1_024L) return false
        val url = runCatching { URL(urlText) }.getOrNull() ?: return false
        return url.protocol == "https" &&
            url.host == "github.com" &&
            url.path.startsWith(
                "/armsone/S.tand-Android/releases/download/android-v$versionCode/",
            ) &&
            url.path.endsWith("/$assetName")
    }
}
