package com.bible.starsnap.ui

import com.bible.starsnap.data.ApiException
import com.bible.starsnap.data.BibleGateway
import com.bible.starsnap.data.BibleLicenseStatus
import com.bible.starsnap.data.BibleMeditation
import com.bible.starsnap.data.BibleSlice
import com.bible.starsnap.data.BibleVerse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BibleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun licenseRecheckClearsProtectedStateBeforeNetworkReturns() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val model = BibleViewModel(gateway)
        gateway.licenseResponses += CompletableDeferred(activeLicense())
        model.loadLicense()
        advanceUntilIdle()
        model.updateQuery("synthetic")
        gateway.searchResponse = BibleSlice(listOf(verse("A")), 0, 30, true)
        model.search()
        advanceUntilIdle()
        assertEquals(1, model.state.value.verses.size)

        gateway.licenseResponses += CompletableDeferred()
        model.loadLicense()

        assertTrue(model.state.value.verses.isEmpty())
        assertNull(model.state.value.selectedVerse)
        assertEquals(RequestState.Loading, model.state.value.licenseState)
    }

    @Test
    fun selectionWhileNoteLoadsKeepsCurrentVerse() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val model = BibleViewModel(gateway)
        gateway.licenseResponses += CompletableDeferred(activeLicense())
        model.loadLicense()
        advanceUntilIdle()
        val verseA = verse("A")
        val verseB = verse("B")
        val noteA = CompletableDeferred<BibleMeditation?>()
        gateway.noteResponses[verseA.id] = noteA

        model.selectVerse(verseA)
        runCurrent()
        model.selectVerse(verseB)
        runCurrent()
        noteA.complete(meditation("A", 0, "note A"))
        advanceUntilIdle()

        assertEquals(verseA, model.state.value.selectedVerse)
        assertEquals("note A", model.state.value.content)
        assertTrue(model.state.value.error?.contains("불러오거나 저장") == true)
    }

    @Test
    fun foregroundRecheckKeepsDraftWhenLicenseStaysActive() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val model = BibleViewModel(gateway)
        gateway.licenseResponses += CompletableDeferred(activeLicense())
        model.loadLicense()
        advanceUntilIdle()
        val verse = verse("A")
        gateway.noteResponses[verse.id] = CompletableDeferred(null)
        model.selectVerse(verse)
        advanceUntilIdle()
        model.updateContent("작성 중인 초안")
        gateway.licenseResponses += CompletableDeferred(activeLicense())

        model.onForeground()
        advanceUntilIdle()

        assertEquals("작성 중인 초안", model.state.value.content)
        assertEquals(verse, model.state.value.selectedVerse)
        assertTrue(model.state.value.canSearch)
    }

    @Test
    fun dirtyDraftBlocksNewVerseSelection() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val model = BibleViewModel(gateway)
        gateway.licenseResponses += CompletableDeferred(activeLicense())
        model.loadLicense()
        advanceUntilIdle()
        val verseA = verse("A")
        val verseB = verse("B")
        gateway.noteResponses[verseA.id] = CompletableDeferred(null)
        model.selectVerse(verseA)
        advanceUntilIdle()
        model.updateContent("저장 전 초안")

        model.selectVerse(verseB)

        assertEquals(verseA, model.state.value.selectedVerse)
        assertEquals("저장 전 초안", model.state.value.content)
        assertTrue(model.state.value.error?.contains("저장하지 않은") == true)
    }

    @Test
    fun selectingLaterVerseLoadsCanonicalRange() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val model = BibleViewModel(gateway)
        gateway.licenseResponses += CompletableDeferred(activeLicense())
        model.loadLicense()
        advanceUntilIdle()
        val start = verse("A")
        gateway.noteResponses[start.id] = CompletableDeferred(null)
        model.selectVerse(start)
        advanceUntilIdle()

        model.selectVerse(start.copy(verse = 3))
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), model.state.value.selectedVerses.map { it.verse })
        assertEquals(3, model.state.value.selectedEndVerse)
        assertTrue(model.state.value.isDirty)
    }

    @Test
    fun conflictKeepsDraftAndReloadsLatestVersionBeforeRetry() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val model = BibleViewModel(gateway)
        gateway.licenseResponses += CompletableDeferred(activeLicense())
        model.loadLicense()
        advanceUntilIdle()
        val verse = verse("A")
        gateway.noteResponses[verse.id] = CompletableDeferred(meditation("A", 1, "server old"))
        model.selectVerse(verse)
        advanceUntilIdle()
        model.updateContent("my draft")
        gateway.saveError = ApiException(409, "conflict")

        model.save()
        advanceUntilIdle()

        assertTrue(model.state.value.hasConflict)
        assertEquals("my draft", model.state.value.content)
        assertFalse(model.state.value.canSave)

        gateway.noteResponses[verse.id] = CompletableDeferred(meditation("A", 2, "server latest"))
        gateway.saveError = null
        model.reloadAfterConflict()
        advanceUntilIdle()

        assertFalse(model.state.value.hasConflict)
        assertEquals("my draft", model.state.value.content)
        assertEquals("server latest", model.state.value.savedContent)
        assertEquals(2L, model.state.value.meditation?.version)
    }

    @Test
    fun sessionChangeCancelsAndClearsAllSensitiveState() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val model = BibleViewModel(gateway)
        gateway.licenseResponses += CompletableDeferred(activeLicense())
        model.loadLicense()
        advanceUntilIdle()
        model.updateQuery("private")

        model.clearForSessionChange()

        assertEquals(BibleUiState(), model.state.value)
    }

    @Test
    fun protectedEndpoint503ClearsTextAndRechecksLicense() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val model = BibleViewModel(gateway)
        gateway.licenseResponses += CompletableDeferred(activeLicense())
        model.loadLicense()
        advanceUntilIdle()
        model.updateQuery("synthetic")
        gateway.searchResponse = BibleSlice(listOf(verse("A")), 0, 30, true)
        model.search()
        advanceUntilIdle()
        assertEquals(1, model.state.value.verses.size)

        gateway.searchError = ApiException(503, "license unavailable")
        gateway.licenseResponses += CompletableDeferred(
            activeLicense().copy(
                phase = "paused",
                searchAvailable = false,
                textDisplayAllowed = false,
            ),
        )
        model.search()
        advanceUntilIdle()

        assertTrue(model.state.value.verses.isEmpty())
        assertFalse(model.state.value.canSearch)
        assertEquals("paused", model.state.value.license?.phase)
    }

    private class FakeGateway : BibleGateway {
        val licenseResponses = ArrayDeque<CompletableDeferred<BibleLicenseStatus>>()
        var searchResponse = BibleSlice(emptyList(), 0, 30, true)
        var searchError: Throwable? = null
        val noteResponses = mutableMapOf<String, CompletableDeferred<BibleMeditation?>>()
        var saveError: Throwable? = null

        override suspend fun validateSession() = false
        override suspend fun login(username: String, password: String) = Unit
        override suspend fun logout() = Unit
        override suspend fun licenseStatus() = licenseResponses.removeFirst().await()
        override suspend fun searchVerses(query: String, page: Int): BibleSlice {
            searchError?.let { throw it }
            return searchResponse
        }
        override suspend fun verseRange(start: BibleVerse, endVerse: Int) =
            (start.verse..endVerse).map { start.copy(verse = it, text = "synthetic-$it") }
        override suspend fun meditationByVerse(verse: BibleVerse) =
            noteResponses.getValue(verse.id).await()

        override suspend fun saveMeditation(
            verse: BibleVerse,
            content: String,
            worshipAt: String,
            endVerse: Int,
            current: BibleMeditation?,
        ): BibleMeditation {
            saveError?.let { throw it }
            return meditation(verse.bookCode, (current?.version ?: -1) + 1, content)
        }
    }

    private companion object {
        fun activeLicense() = BibleLicenseStatus(
            phase = "active",
            searchAvailable = true,
            textDisplayAllowed = true,
            notice = "synthetic active license",
            providerName = "Synthetic",
            expiresOn = "2099-01-01",
        )

        fun verse(code: String) = BibleVerse(
            translationCode = "TEST",
            translationName = "Synthetic",
            copyrightNotice = "Test data only",
            bookCode = code,
            bookName = "Synthetic book $code",
            chapter = 1,
            verse = 1,
            text = "synthetic-$code",
        )

        fun meditation(id: String, version: Long, content: String) = BibleMeditation(
            id = id,
            bookCode = id,
            chapter = 1,
            verse = 1,
            content = content,
            version = version,
            createdAt = "2026-09-01T09:00:00",
            modifiedAt = "2026-09-01T09:00:00",
            worshipAt = "2026-09-01T09:00:00",
            endVerse = 1,
        )
    }
}
