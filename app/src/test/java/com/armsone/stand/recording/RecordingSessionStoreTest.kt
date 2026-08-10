package com.armsone.stand.recording

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecordingSessionStoreTest {
    @Test
    fun mateModeReentryWithinThirtyMinutesResumesTheSameSession() = withDirectory { directory ->
        val store = RecordingSessionStore(directory)
        val start = instant("2026-08-10T00:00:00Z")
        val first = store.beginMateSession(start)
        assertTrue(store.endMateSession(first, start.plusSeconds(60)))

        val resumed = store.beginMateSession(start.plus(Duration.ofMinutes(30)).plusSeconds(60))
        assertEquals(first, resumed)
        assertEquals(null, store.sessions.single().endedAt)

        store.endMateSession(first, start.plus(Duration.ofMinutes(31)))
        val next = store.beginMateSession(start.plus(Duration.ofMinutes(61)).plusSeconds(1))
        assertNotEquals(first, next)
    }

    @Test
    fun legacyGroupsUseNinetyMinuteGapAndFifteenMinutePadding() = withDirectory { directory ->
        val first = clip(directory, "first.wav", "2026-08-10T01:00:00Z", 10.0)
        val within = clip(directory, "within.wav", "2026-08-10T02:30:10Z", 5.0)
        val separate = clip(directory, "separate.wav", "2026-08-10T04:00:16Z", 20.0)
        val merged = clip(directory, "ignored-selected-merged.wav", "2026-08-10T01:30:00Z", 99.0)

        val groups = RecordingSessionPolicy.inferredGroups(listOf(separate, merged, within, first))

        assertEquals(2, groups.size)
        assertEquals(listOf(first.file, within.file), groups[0].clips.map { it.file })
        assertEquals(instant("2026-08-10T00:45:00Z"), groups[0].startedAt)
        assertEquals(instant("2026-08-10T02:45:15Z"), groups[0].endedAt)
        assertEquals(listOf(separate.file), groups[1].clips.map { it.file })
        assertTrue(groups.all { it.isInferred })
    }

    @Test
    fun markerFractionIsNormalizedAndClamped() = withDirectory { directory ->
        val start = instant("2026-08-10T01:00:00Z")
        val end = start.plusSeconds(100)

        assertEquals(
            0.25,
            RecordingSessionPolicy.markerFraction(
                clip(directory, "middle.wav", start.plusSeconds(25), 1.0),
                start,
                end,
            ),
            0.000_001,
        )
        assertEquals(
            0.0,
            RecordingSessionPolicy.markerFraction(
                clip(directory, "before.wav", start.minusSeconds(5), 1.0),
                start,
                end,
            ),
            0.0,
        )
        assertEquals(
            1.0,
            RecordingSessionPolicy.markerFraction(
                clip(directory, "after.wav", end.plusSeconds(5), 1.0),
                start,
                end,
            ),
            0.0,
        )
    }

    @Test
    fun aNewStoreRecoversAnOpenSessionAtItsStartTime() = withDirectory { directory ->
        val start = instant("2026-08-10T01:00:00Z")
        val original = RecordingSessionStore(directory)
        val id = original.beginMateSession(start)

        val recovered = RecordingSessionStore(directory)

        assertEquals(id, recovered.sessions.single().id)
        assertEquals(start, recovered.sessions.single().endedAt)
        assertEquals(start, RecordingSessionStore(directory).sessions.single().endedAt)
    }

    @Test
    fun onlyManagedOriginalSupportedAudioFilesCanBeAssociated() = withDirectory { directory ->
        val store = RecordingSessionStore(directory)
        val start = instant("2026-08-10T01:00:00Z")
        val id = store.beginMateSession(start)
        val original = clip(directory, "sleep-sound-original.wav", start.plusSeconds(2), 3.0)
        val merged = clip(directory, "sleep-sound-selected-merged.wav", start.plusSeconds(3), 3.0)
        val m4a = clip(directory, "sample-embedded-snore.m4a", start.plusSeconds(4), 3.0)

        assertTrue(store.associate(original, id))
        assertFalse(store.associate(merged, id))
        assertTrue(store.associate(m4a, id))
        assertEquals(
            listOf(original.file.name, m4a.file.name),
            store.sessions.single().clipFileNames,
        )
        assertTrue(EmbeddedRecordingSamplePolicy.automaticInstallEnabled)
        assertEquals("m4a", EmbeddedRecordingSamplePolicy.sourceFormat)
        assertEquals("m4a", EmbeddedRecordingSamplePolicy.libraryFormat)
    }

    @Test
    fun unassignedClipsAreRecoveredBySessionTimeline() = withDirectory { directory ->
        val store = RecordingSessionStore(directory)
        val start = instant("2026-08-10T01:00:00Z")
        val id = store.beginMateSession(start)
        store.endMateSession(id, start.plusSeconds(60))
        val nearStart = clip(directory, "near-start.wav", start.minusSeconds(5), 1.0)
        val nearEnd = clip(directory, "near-end.wav", start.plusSeconds(65), 1.0)
        val outside = clip(directory, "outside.wav", start.plusSeconds(66), 1.0)

        assertEquals(2, store.associateUnassigned(listOf(outside, nearEnd, nearStart)))
        assertEquals(
            setOf(nearStart.file.name, nearEnd.file.name),
            store.sessions.single().clipFileNames.toSet(),
        )
    }

    @Test
    fun removingDeletedReferencesPrunesOnlyExpiredEmptySessions() = withDirectory { directory ->
        val start = instant("2026-08-10T01:00:00Z")
        val store = RecordingSessionStore(directory)
        val id = store.beginMateSession(start)
        val audio = clip(directory, "delete-me.wav", start, 1.0)
        store.associate(audio, id)
        store.endMateSession(id, start.plusSeconds(1))

        assertTrue(store.removeReferences(listOf(audio.file), start.plus(Duration.ofMinutes(31))))
        assertTrue(store.sessions.isEmpty())
        assertTrue(audio.file.exists())
    }

    @Test
    fun persistenceFailureLeavesAudioAndPreviousMetadataUntouched() = withDirectory { directory ->
        val audio = clip(directory, "preserved.wav", "2026-08-10T01:00:00Z", 1.0)
        val blockedManifest = File(directory, "blocked-manifest").apply {
            mkdir()
            File(this, "sentinel").writeText("keep")
        }
        val store = RecordingSessionStore(
            directory = directory,
            manifestFile = blockedManifest,
        )

        try {
            store.beginMateSession(audio.createdAt)
            fail("Expected metadata persistence to fail")
        } catch (_: IOException) {
            // Expected: the metadata destination is a non-empty directory.
        }

        assertTrue(audio.file.exists())
        assertTrue(File(blockedManifest, "sentinel").exists())
        assertTrue(store.sessions.isEmpty())
        assertNotNull(store.lastPersistenceError)
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun metadataRoundTripsUnicodeNamesWithoutTemporaryFiles() = withDirectory { directory ->
        val start = instant("2026-08-10T01:00:00.123Z")
        val store = RecordingSessionStore(directory)
        val id = store.beginMateSession(start)
        val audio = clip(directory, "코골이 녹음.wav", start, 1.0)
        store.associate(audio, id)
        store.endMateSession(id, start.plusSeconds(10))

        val reloaded = RecordingSessionStore(directory)

        assertEquals(listOf(audio.file.name), reloaded.sessions.single().clipFileNames)
        assertEquals(start.plusSeconds(10), reloaded.sessions.single().endedAt)
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    private fun clip(
        directory: File,
        name: String,
        createdAt: String,
        duration: Double,
    ): RecordingClip = clip(directory, name, instant(createdAt), duration)

    private fun clip(
        directory: File,
        name: String,
        createdAt: Instant,
        duration: Double,
    ): RecordingClip {
        val file = File(directory, name).apply { writeBytes(byteArrayOf(1)) }
        return RecordingClip(file, createdAt, duration)
    }

    private fun instant(value: String): Instant = Instant.parse(value)

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("stand-session-test-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
