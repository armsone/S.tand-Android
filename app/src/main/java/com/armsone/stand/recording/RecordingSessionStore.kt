package com.armsone.stand.recording

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.math.max

enum class SessionMonitoringHealth {
    UNKNOWN,
    MONITORED,
    FAILED,
    UNMONITORED;

    companion object {
        fun fromString(value: String?): SessionMonitoringHealth = when (value?.uppercase()) {
            "MONITORED" -> MONITORED
            "FAILED" -> FAILED
            "UNMONITORED" -> UNMONITORED
            else -> UNKNOWN
        }
    }
}

data class RecordingSessionMetadata(
    val id: UUID,
    val startedAt: Instant,
    val endedAt: Instant?,
    val clipFileNames: List<String>,
    val startleEvents: List<SleepStartleEvent> = emptyList(),
    val monitoringHealth: SessionMonitoringHealth = SessionMonitoringHealth.UNKNOWN,
    val monitoredDurationSeconds: Double = 0.0,
    val failureReason: String? = null,
)

data class SleepStartleEvent(
    val id: UUID,
    val startedAt: Instant,
    val endedAt: Instant?,
)

data class RecordingSessionGroup(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val clips: List<RecordingClip>,
    val isInferred: Boolean,
    val startleEvents: List<SleepStartleEvent> = emptyList(),
    val monitoringHealth: SessionMonitoringHealth = SessionMonitoringHealth.UNKNOWN,
    val monitoredDurationSeconds: Double = 0.0,
    val failureReason: String? = null,
) {
    val totalDurationSeconds: Double
        get() = clips.sumOf { it.durationSeconds }

    val insight: SleepSessionInsight
        get() = SleepSessionInsight.from(this)

    val isGenuineQuietNight: Boolean
        get() = clips.isEmpty() && (monitoringHealth == SessionMonitoringHealth.MONITORED || monitoredDurationSeconds > 0.0) && failureReason == null

    val isFailedOrUnmonitored: Boolean
        get() = clips.isEmpty() && (monitoringHealth == SessionMonitoringHealth.FAILED || failureReason != null || (monitoringHealth == SessionMonitoringHealth.UNMONITORED && !isInferred))
}

data class SleepSessionInsight(
    val sessionDuration: Duration,
    val soundCount: Int,
    val soundDurationSeconds: Double,
    val movementCount: Int,
    val activityBuckets: List<Int>,
    val busiestBucketIndex: Int?,
) {
    val eventsPerHour: Double
        get() {
            val seconds = sessionDuration.toMillis().coerceAtLeast(0L) / 1_000.0
            return if (seconds <= 0.0) 0.0 else (soundCount + movementCount) / (seconds / 3_600.0)
        }

    fun busiestRange(sessionStart: Instant): Pair<Instant, Instant>? {
        val index = busiestBucketIndex ?: return null
        val bucketMillis = sessionDuration.toMillis() / BUCKET_COUNT
        if (bucketMillis <= 0L) return null
        val start = sessionStart.plusMillis(index * bucketMillis)
        return start to start.plusMillis(bucketMillis)
    }

    companion object {
        const val BUCKET_COUNT = 12

        fun from(session: RecordingSessionGroup): SleepSessionInsight {
            val duration = Duration.between(session.startedAt, session.endedAt).coerceAtLeast(Duration.ZERO)
            val buckets = MutableList(BUCKET_COUNT) { 0 }
            val eventTimes = session.clips.map(RecordingClip::createdAt) +
                session.startleEvents.map(SleepStartleEvent::startedAt)
            eventTimes.forEach { instant ->
                val fraction = RecordingSessionPolicy.markerFraction(
                    instant = instant,
                    sessionStart = session.startedAt,
                    sessionEnd = session.endedAt,
                )
                val index = (fraction * BUCKET_COUNT).toInt().coerceIn(0, BUCKET_COUNT - 1)
                buckets[index] += 1
            }
            val maximum = buckets.maxOrNull() ?: 0
            return SleepSessionInsight(
                sessionDuration = duration,
                soundCount = session.clips.size,
                soundDurationSeconds = session.totalDurationSeconds,
                movementCount = session.startleEvents.size,
                activityBuckets = buckets,
                busiestBucketIndex = if (maximum > 0) buckets.indexOf(maximum) else null,
            )
        }
    }
}

/** iOS의 세션 경계 규칙을 Android의 WAV 녹음에 그대로 적용한 순수 정책입니다. */
object RecordingSessionPolicy {
    val mateModeResumeGap: Duration = Duration.ofMinutes(30)
    val legacyRecordingGap: Duration = Duration.ofMinutes(90)
    val legacyTimelinePadding: Duration = Duration.ofMinutes(15)

    fun inferredGroups(
        clips: Collection<RecordingClip>,
        maximumGap: Duration = legacyRecordingGap,
    ): List<RecordingSessionGroup> {
        require(!maximumGap.isNegative) { "maximumGap must not be negative" }
        val originals = clips
            .filter(::isOriginalRecording)
            .sortedWith(compareBy<RecordingClip> { it.createdAt }.thenBy { it.file.name })
        if (originals.isEmpty()) return emptyList()

        val clusters = mutableListOf(mutableListOf(originals.first()))
        originals.drop(1).forEach { clip ->
            val previous = clusters.last().last()
            val previousEnd = previous.createdAt.plusMillis(
                (previous.durationSeconds.coerceAtLeast(0.0) * 1_000.0).toLong(),
            )
            if (Duration.between(previousEnd, clip.createdAt) > maximumGap) {
                clusters += mutableListOf(clip)
            } else {
                clusters.last() += clip
            }
        }

        return clusters.map { cluster ->
            val first = cluster.first()
            val last = cluster.last()
            val startedAt = first.createdAt.minus(legacyTimelinePadding)
            val lastClipEnd = last.createdAt.plusMillis(
                (last.durationSeconds.coerceAtLeast(0.0) * 1_000.0).toLong(),
            )
            val paddedEnd = lastClipEnd.plus(legacyTimelinePadding)
            RecordingSessionGroup(
                id = "legacy-${first.file.name}",
                startedAt = startedAt,
                endedAt = maxInstant(startedAt.plusSeconds(1), paddedEnd),
                clips = cluster,
                isInferred = true,
            )
        }
    }

    fun markerFraction(
        clip: RecordingClip,
        sessionStart: Instant,
        sessionEnd: Instant,
    ): Double = markerFraction(clip.createdAt, sessionStart, sessionEnd)

    fun markerFraction(
        instant: Instant,
        sessionStart: Instant,
        sessionEnd: Instant,
    ): Double {
        val durationMillis = max(1L, Duration.between(sessionStart, sessionEnd).toMillis())
        return (
            Duration.between(sessionStart, instant).toMillis().toDouble() /
                durationMillis.toDouble()
            ).coerceIn(0.0, 1.0)
    }
}

/** iOS와 같은 M4A 내장 샘플을 변환 없이 처음 한 번 설치합니다. */
data object EmbeddedRecordingSamplePolicy {
    const val sourceFormat = "m4a"
    const val libraryFormat = "m4a"
    const val automaticInstallEnabled = true
    const val reason = "M4A 원본을 앱 내부 녹음 폴더에 그대로 설치합니다."
}

/**
 * 녹음 파일과 독립된 매이트 모드 세션 메타데이터 저장소입니다.
 *
 * 이 클래스는 WAV를 만들거나 지우지 않습니다. 모든 변경은 임시 파일을 동기화한 뒤
 * manifest로 이동하므로 저장 실패 시 기존 manifest와 실제 오디오 파일을 보존합니다.
 */
class RecordingSessionStore(
    val directory: File,
    private val now: () -> Instant = Instant::now,
    private val manifestFile: File = File(directory, MANIFEST_FILE_NAME),
) {
    private val lock = Any()
    private var storedSessions: List<RecordingSessionMetadata> = emptyList()

    @Volatile
    var lastPersistenceError: IOException? = null
        private set

    val sessions: List<RecordingSessionMetadata>
        get() = synchronized(lock) { storedSessions.map { it.copy(clipFileNames = it.clipFileNames.toList()) } }

    init {
        synchronized(lock) {
            storedSessions = loadManifest()
            recoverOpenSessionsLocked()
        }
    }

    /** 30분 이내 재진입이면 최근 매이트 세션을 다시 열고, 아니면 새 세션을 만듭니다. */
    @Throws(IOException::class)
    fun beginMateSession(at: Instant = now()): UUID = synchronized(lock) {
        var candidate = removeExpiredEmptySessions(storedSessions, at)
        candidate.lastOrNull { it.endedAt == null }?.let { open ->
            if (candidate != storedSessions) commitLocked(candidate)
            return@synchronized open.id
        }

        val recentIndex = candidate.indices
            .filter { candidate[it].endedAt != null }
            .maxByOrNull { candidate[it].endedAt!! }
        if (recentIndex != null) {
            val recent = candidate[recentIndex]
            val gap = Duration.between(recent.endedAt, at)
            if (!gap.isNegative && gap <= RecordingSessionPolicy.mateModeResumeGap) {
                candidate = candidate.toMutableList().also { sessions ->
                    sessions[recentIndex] = recent.copy(endedAt = null)
                }
                commitLocked(candidate)
                return@synchronized recent.id
            }
        }

        val session = RecordingSessionMetadata(
            id = UUID.randomUUID(),
            startedAt = at,
            endedAt = null,
            clipFileNames = emptyList(),
            startleEvents = emptyList(),
        )
        commitLocked(candidate + session)
        session.id
    }

    @Throws(IOException::class)
    fun endMateSession(
        id: UUID?,
        at: Instant = now(),
        health: SessionMonitoringHealth = SessionMonitoringHealth.UNKNOWN,
        monitoredDurationSeconds: Double = 0.0,
        failureReason: String? = null,
    ): Boolean = synchronized(lock) {
        val index = storedSessions.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized false
        val session = storedSessions[index]
        val candidate = storedSessions.toMutableList().also { sessions ->
            val endedAt = maxInstant(session.startedAt, at)
            val resolvedHealth = if (health != SessionMonitoringHealth.UNKNOWN) {
                health
            } else if (session.monitoringHealth != SessionMonitoringHealth.UNKNOWN) {
                session.monitoringHealth
            } else {
                SessionMonitoringHealth.UNKNOWN
            }
            val resolvedDuration = maxOf(session.monitoredDurationSeconds, monitoredDurationSeconds)
            val resolvedFailure = failureReason ?: session.failureReason
            sessions[index] = session.copy(
                endedAt = endedAt,
                monitoringHealth = resolvedHealth,
                monitoredDurationSeconds = resolvedDuration,
                failureReason = resolvedFailure,
                startleEvents = session.startleEvents.map { event ->
                    if (event.endedAt == null) {
                        event.copy(endedAt = maxInstant(event.startedAt, endedAt))
                    } else {
                        event
                    }
                },
            )
        }
        commitLocked(candidate)
        true
    }

    @Throws(IOException::class)
    fun recordMonitoringEvidence(
        id: UUID?,
        health: SessionMonitoringHealth,
        monitoredDurationSeconds: Double,
        failureReason: String? = null,
    ): Boolean = synchronized(lock) {
        val index = storedSessions.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized false
        val session = storedSessions[index]
        val candidate = storedSessions.toMutableList().also { sessions ->
            sessions[index] = session.copy(
                monitoringHealth = health,
                monitoredDurationSeconds = maxOf(session.monitoredDurationSeconds, monitoredDurationSeconds),
                failureReason = failureReason ?: session.failureReason,
            )
        }
        commitLocked(candidate)
        true
    }

    @Throws(IOException::class)
    fun beginStartleEvent(sessionId: UUID?, at: Instant = now()): UUID? = synchronized(lock) {
        val index = storedSessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return@synchronized null
        val session = storedSessions[index]
        session.startleEvents.lastOrNull { it.endedAt == null }?.let { return@synchronized it.id }
        val event = SleepStartleEvent(UUID.randomUUID(), at, null)
        val candidate = storedSessions.toMutableList().also { sessions ->
            sessions[index] = session.copy(startleEvents = session.startleEvents + event)
        }
        commitLocked(candidate)
        event.id
    }

    @Throws(IOException::class)
    fun endStartleEvent(id: UUID?, at: Instant = now()): Boolean = synchronized(lock) {
        if (id == null) return@synchronized false
        val sessionIndex = storedSessions.indexOfFirst { session ->
            session.startleEvents.any { it.id == id }
        }
        if (sessionIndex < 0) return@synchronized false
        val session = storedSessions[sessionIndex]
        val events = session.startleEvents.map { event ->
            if (event.id == id) event.copy(endedAt = maxInstant(event.startedAt, at)) else event
        }
        val candidate = storedSessions.toMutableList().also { sessions ->
            sessions[sessionIndex] = session.copy(startleEvents = events)
        }
        commitLocked(candidate)
        true
    }

    /** 원본 녹음 하나를 지정 세션 또는 녹음 시각과 겹치는 최근 세션에 연결합니다. */
    @Throws(IOException::class)
    fun associate(clip: RecordingClip, sessionId: UUID? = null): Boolean = synchronized(lock) {
        if (!isManagedOriginalRecording(clip)) return@synchronized false
        val name = clip.file.name
        if (storedSessions.any { name in it.clipFileNames }) return@synchronized true

        val index = if (sessionId != null) {
            storedSessions.indexOfFirst { it.id == sessionId }
        } else {
            matchingSessionIndex(storedSessions, clip)
        }
        if (index < 0) return@synchronized false

        val candidate = storedSessions.toMutableList().also { sessions ->
            val session = sessions[index]
            sessions[index] = session.copy(clipFileNames = session.clipFileNames + name)
        }
        commitLocked(candidate)
        true
    }

    /** reload 뒤 아직 연결되지 않은 원본 녹음을 세션 시각의 앞뒤 5초 허용 범위로 복구 연결합니다. */
    @Throws(IOException::class)
    fun associateUnassigned(clips: Collection<RecordingClip>): Int = synchronized(lock) {
        var candidate = storedSessions
        val assigned = candidate.flatMapTo(mutableSetOf()) { it.clipFileNames }
        var count = 0
        clips.filter(::isManagedOriginalRecording)
            .sortedBy { it.createdAt }
            .forEach { clip ->
                if (!assigned.add(clip.file.name)) return@forEach
                val index = matchingSessionIndex(candidate, clip)
                if (index < 0) return@forEach
                candidate = candidate.toMutableList().also { sessions ->
                    val session = sessions[index]
                    sessions[index] = session.copy(
                        clipFileNames = session.clipFileNames + clip.file.name,
                    )
                }
                count += 1
            }
        if (count > 0) commitLocked(candidate)
        count
    }

    /** 저장된 세션과 아직 연결되지 않은 레거시 녹음 그룹을 최신 세션부터 반환합니다. */
    fun groups(
        clips: Collection<RecordingClip>,
        referenceTime: Instant = now(),
    ): List<RecordingSessionGroup> = synchronized(lock) {
        val originals = clips.filter(::isManagedOriginalRecording)
        val clipsByName = originals.associateBy { it.file.name }
        val assignedNames = mutableSetOf<String>()
        val explicit = storedSessions.mapNotNull { session ->
            val sessionClips = session.clipFileNames
                .mapNotNull(clipsByName::get)
                .sortedBy { it.createdAt }
            val hasContentOrEvidence = sessionClips.isNotEmpty() ||
                session.startleEvents.isNotEmpty() ||
                session.monitoringHealth != SessionMonitoringHealth.UNKNOWN ||
                session.failureReason != null ||
                session.endedAt == null
            if (!hasContentOrEvidence) return@mapNotNull null
            assignedNames += sessionClips.map { it.file.name }
            val lastClipEnd = sessionClips.maxOfOrNull { clip ->
                clip.createdAt.plusMillis(
                    (clip.durationSeconds.coerceAtLeast(0.0) * 1_000.0).toLong(),
                )
            } ?: session.startedAt
            val lastStartleEnd = session.startleEvents.maxOfOrNull { event ->
                event.endedAt ?: referenceTime
            } ?: session.startedAt
            RecordingSessionGroup(
                id = "session-${session.id}",
                startedAt = session.startedAt,
                endedAt = maxInstant(
                    session.startedAt.plusSeconds(1),
                    session.endedAt ?: maxInstant(referenceTime, maxInstant(lastClipEnd, lastStartleEnd)),
                ),
                clips = sessionClips,
                isInferred = false,
                startleEvents = session.startleEvents,
                monitoringHealth = session.monitoringHealth,
                monitoredDurationSeconds = session.monitoredDurationSeconds,
                failureReason = session.failureReason,
            )
        }
        val legacy = RecordingSessionPolicy.inferredGroups(
            originals.filterNot { it.file.name in assignedNames },
        )
        (explicit + legacy).sortedByDescending { it.startedAt }
    }

    /** 삭제가 끝난 파일 이름의 참조만 정리합니다. 실제 오디오 파일에는 접근하지 않습니다. */
    @Throws(IOException::class)
    fun removeReferences(
        files: Collection<File>,
        referenceTime: Instant = now(),
    ): Boolean = synchronized(lock) {
        val names = files.mapTo(mutableSetOf()) { it.name }
        if (names.isEmpty()) return@synchronized false
        var candidate = storedSessions.map { session ->
            session.copy(clipFileNames = session.clipFileNames.filterNot(names::contains))
        }
        candidate = removeExpiredEmptySessions(candidate, referenceTime)
        if (candidate == storedSessions) return@synchronized false
        commitLocked(candidate)
        true
    }

    private fun recoverOpenSessionsLocked() {
        val recovered = storedSessions.map { session ->
            if (session.endedAt == null) {
                session.copy(
                    endedAt = session.startedAt,
                    startleEvents = session.startleEvents.map { event ->
                        if (event.endedAt == null) {
                            event.copy(endedAt = maxInstant(event.startedAt, session.startedAt))
                        } else {
                            event
                        }
                    },
                )
            } else {
                session
            }
        }
        if (recovered == storedSessions) return
        try {
            commitLocked(recovered)
        } catch (error: IOException) {
            // 런타임에서는 열린 세션을 보수적으로 닫고, 다음 변경 때 다시 저장을 시도합니다.
            storedSessions = recovered
            lastPersistenceError = error
        }
    }

    private fun matchingSessionIndex(
        sessions: List<RecordingSessionMetadata>,
        clip: RecordingClip,
    ): Int = sessions.indices.reversed().firstOrNull { index ->
        val session = sessions[index]
        val lowerBound = session.startedAt.minusSeconds(ASSOCIATION_TOLERANCE_SECONDS)
        val upperBound = (session.endedAt ?: Instant.MAX)
            .let { end ->
                if (end == Instant.MAX) end else end.plusSeconds(ASSOCIATION_TOLERANCE_SECONDS)
            }
        clip.createdAt >= lowerBound && clip.createdAt <= upperBound
    } ?: -1

    private fun isManagedOriginalRecording(clip: RecordingClip): Boolean = try {
        isOriginalRecording(clip) &&
            clip.file.isFile &&
            clip.file.canonicalFile.parentFile == directory.canonicalFile
    } catch (_: IOException) {
        false
    }

    @Throws(IOException::class)
    private fun commitLocked(candidate: List<RecordingSessionMetadata>) {
        persistManifest(candidate)
        storedSessions = candidate
        lastPersistenceError = null
    }

    private fun loadManifest(): List<RecordingSessionMetadata> {
        if (!manifestFile.isFile) return emptyList()
        return try {
            decodeManifest(manifestFile.readText(StandardCharsets.UTF_8))
        } catch (error: Exception) {
            lastPersistenceError = IOException("녹음 세션 메타데이터를 읽을 수 없습니다.", error)
            emptyList()
        }
    }

    @Throws(IOException::class)
    private fun persistManifest(sessions: List<RecordingSessionMetadata>) {
        val parent = manifestFile.parentFile
            ?: throw IOException("세션 메타데이터 폴더가 없습니다.")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("세션 메타데이터 폴더를 만들 수 없습니다.")
        }
        val temporary = File(parent, ".${manifestFile.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encodeManifest(sessions).toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    manifestFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (error: AtomicMoveNotSupportedException) {
                // 비원자적 덮어쓰기로 강등하면 정상 manifest까지 잃을 수 있으므로 실패로 둡니다.
                throw IOException(
                    "이 파일 시스템은 원자적 세션 메타데이터 교체를 지원하지 않습니다.",
                    error,
                )
            }
        } catch (error: IOException) {
            lastPersistenceError = error
            throw error
        } finally {
            temporary.delete()
        }
    }

    companion object {
        const val MANIFEST_FILE_NAME = ".recording-sessions-v1"
        private const val MANIFEST_HEADER_V1 = "S.TAND-RECORDING-SESSIONS\t1"
        private const val MANIFEST_HEADER_V2 = "S.TAND-RECORDING-SESSIONS\t2"
        private const val MANIFEST_HEADER = "S.TAND-RECORDING-SESSIONS\t3"
        private const val ASSOCIATION_TOLERANCE_SECONDS = 5L
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()

        private fun encodeManifest(sessions: List<RecordingSessionMetadata>): String = buildString {
            appendLine(MANIFEST_HEADER)
            sessions.forEach { session ->
                val names = session.clipFileNames.joinToString(",") { name ->
                    encoder.encodeToString(name.toByteArray(StandardCharsets.UTF_8))
                }
                val encodedFailureReason = session.failureReason?.let { reason ->
                    encoder.encodeToString(reason.toByteArray(StandardCharsets.UTF_8))
                }.orEmpty()
                append("S\t")
                append(session.id)
                append('\t')
                append(session.startedAt.toEpochMilli())
                append('\t')
                append(session.endedAt?.toEpochMilli()?.toString().orEmpty())
                append('\t')
                append(names)
                append('\t')
                append(session.startleEvents.joinToString(",") { event ->
                    listOf(
                        event.id.toString(),
                        event.startedAt.toEpochMilli().toString(),
                        event.endedAt?.toEpochMilli()?.toString().orEmpty(),
                    ).joinToString(":")
                })
                append('\t')
                append(session.monitoringHealth.name)
                append('\t')
                append(session.monitoredDurationSeconds)
                append('\t')
                append(encodedFailureReason)
                append('\n')
            }
        }

        private fun decodeManifest(text: String): List<RecordingSessionMetadata> {
            val lines = text.lineSequence().toList()
            val header = lines.firstOrNull()
            require(header == MANIFEST_HEADER || header == MANIFEST_HEADER_V2 || header == MANIFEST_HEADER_V1) {
                "Unknown session manifest"
            }
            return lines.drop(1).filter(String::isNotBlank).map { line ->
                val fields = line.split('\t')
                require(fields.size >= 5 && fields[0] == "S") { "Invalid session entry" }
                val names = fields[4].takeIf(String::isNotEmpty)
                    ?.split(',')
                    ?.map { encoded ->
                        String(decoder.decode(encoded), StandardCharsets.UTF_8)
                    }
                    .orEmpty()
                val startleEvents = fields.getOrNull(5)
                    ?.takeIf(String::isNotEmpty)
                    ?.split(',')
                    ?.map { encoded ->
                        val event = encoded.split(':', limit = 3)
                        require(event.size == 3) { "Invalid startle event" }
                        SleepStartleEvent(
                            id = UUID.fromString(event[0]),
                            startedAt = Instant.ofEpochMilli(event[1].toLong()),
                            endedAt = event[2].takeIf(String::isNotEmpty)
                                ?.toLong()
                                ?.let(Instant::ofEpochMilli),
                        )
                    }
                    .orEmpty()
                val health = fields.getOrNull(6)?.let(SessionMonitoringHealth::fromString) ?: SessionMonitoringHealth.UNKNOWN
                val duration = fields.getOrNull(7)?.toDoubleOrNull() ?: 0.0
                val failureReason = fields.getOrNull(8)?.takeIf(String::isNotEmpty)?.let { encoded ->
                    try {
                        String(decoder.decode(encoded), StandardCharsets.UTF_8)
                    } catch (_: RuntimeException) {
                        encoded
                    }
                }
                RecordingSessionMetadata(
                    id = UUID.fromString(fields[1]),
                    startedAt = Instant.ofEpochMilli(fields[2].toLong()),
                    endedAt = fields[3].takeIf(String::isNotEmpty)?.toLong()?.let(Instant::ofEpochMilli),
                    clipFileNames = names.distinct(),
                    startleEvents = startleEvents,
                    monitoringHealth = health,
                    monitoredDurationSeconds = duration,
                    failureReason = failureReason,
                )
            }
        }

        private fun removeExpiredEmptySessions(
            sessions: List<RecordingSessionMetadata>,
            referenceTime: Instant,
        ): List<RecordingSessionMetadata> = sessions.filterNot { session ->
            session.clipFileNames.isEmpty() &&
                session.startleEvents.isEmpty() &&
                session.monitoringHealth == SessionMonitoringHealth.UNKNOWN &&
                session.failureReason == null &&
                session.endedAt != null &&
                Duration.between(session.endedAt, referenceTime) >
                RecordingSessionPolicy.mateModeResumeGap
        }
    }
}

private fun isOriginalRecording(clip: RecordingClip): Boolean =
    clip.mediaFormat != null &&
        !clip.isMerged

private fun maxInstant(first: Instant, second: Instant): Instant =
    if (first >= second) first else second
