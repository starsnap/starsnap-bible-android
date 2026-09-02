package com.bible.starsnap.data

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.google.gson.Gson
import okhttp3.CookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BibleApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: BibleApiClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = BibleApiClient(server.url("/"), CookieJar.NO_COOKIES)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun licenseStatusUsesProtectedBibleEndpoint() = runTest {
        server.enqueue(
            json(
                """{"phase":"pending","searchAvailable":false,"textDisplayAllowed":false,"notice":"permission pending","providerName":null,"expiresOn":null}""",
            ),
        )

        val status = client.licenseStatus()
        val request = server.takeRequest()

        assertEquals("pending", status.phase)
        assertEquals("/api/bible/license/status?translationCode=NKRV", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun searchEncodesQueryAndDecodesSyntheticResult() = runTest {
        server.enqueue(
            json(
                """{"content":[{"translationCode":"TEST","translationName":"Synthetic","copyrightNotice":"Test data only","bookCode":"TST","bookName":"Synthetic book","chapter":1,"verse":1,"text":"synthetic-test-text"}],"number":0,"size":30,"last":true}""",
            ),
        )

        val result = client.searchVerses("테스트 말씀")
        val request = server.takeRequest()

        assertEquals("테스트 말씀", request.requestUrl?.queryParameter("query"))
        assertEquals("synthetic-test-text", result.content.single().text)
    }

    @Test
    fun missingMeditationReturnsNull() = runTest {
        server.enqueue(json("""{"message":"not found"}""", status = 404))

        val meditation = client.meditationByVerse(syntheticVerse())

        assertNull(meditation)
        assertEquals("/api/bible/meditations/by-verse", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun createAndUpdateCarryLocalWorshipMinuteAndVersion() = runTest {
        server.enqueue(json(meditationJson(id = "note-1", version = 0)))
        server.enqueue(json(meditationJson(id = "note-1", version = 1)))

        val created = client.saveMeditation(
            verse = syntheticVerse(),
            content = "private note",
            worshipAt = "2026-09-01T19:30",
            endVerse = 3,
            current = null,
        )
        client.saveMeditation(
            verse = syntheticVerse(),
            content = "updated note",
            worshipAt = "2026-09-02T09:10",
            endVerse = 4,
            current = created,
        )

        val create = server.takeRequest()
        val update = server.takeRequest()
        val createJson = Gson().fromJson(create.body.readUtf8(), CreateMeditationRequest::class.java)
        val updateJson = Gson().fromJson(update.body.readUtf8(), UpdateMeditationRequest::class.java)

        assertEquals("POST", create.method)
        assertEquals("2026-09-01T19:30", createJson.worshipAt)
        assertEquals(3, createJson.endVerse)
        assertEquals("PATCH", update.method)
        assertEquals(0L, updateJson.expectedVersion)
        assertEquals("2026-09-02T09:10", updateJson.worshipAt)
        assertEquals(4, updateJson.endVerse)
    }

    @Test
    fun rangeUsesCanonicalRangeEndpoint() = runTest {
        server.enqueue(json("""[${verseJson(1)},${verseJson(2)}]"""))

        val result = client.verseRange(syntheticVerse(), 2)
        val request = server.takeRequest()

        assertEquals(2, result.size)
        assertEquals("/api/bible/verses/range", request.requestUrl?.encodedPath)
        assertEquals("1", request.requestUrl?.queryParameter("verse"))
        assertEquals("2", request.requestUrl?.queryParameter("endVerse"))
    }

    @Test
    fun loginUsesStandaloneBibleAccountEndpoint() = runTest {
        server.enqueue(json("""{"userId":"user-1","username":"reader1"}"""))

        client.login("reader1", "secret")

        val request = server.takeRequest()
        val login = Gson().fromJson(request.body.readUtf8(), LoginRequest::class.java)
        assertEquals("/api/bible/auth/login", request.requestUrl?.encodedPath)
        assertEquals("reader1", login.username)
        assertEquals("secret", login.password)
    }

    @Test
    fun validStandaloneBibleSessionIsAccepted() = runTest {
        server.enqueue(json("""{"userId":"user-1","username":"reader1"}"""))

        assertTrue(client.validateSession())
        assertEquals("/api/bible/auth/session", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun expiredStandaloneBibleSessionIsRejectedWithoutRetry() = runTest {
        server.enqueue(json("""{"message":"Authentication required"}""", status = 401))

        assertEquals(false, client.validateSession())
        assertEquals(1, server.requestCount)
    }

    private fun syntheticVerse() = BibleVerse(
        translationCode = "TEST",
        translationName = "Synthetic",
        copyrightNotice = "Test data only",
        bookCode = "TST",
        bookName = "Synthetic book",
        chapter = 1,
        verse = 1,
        text = "synthetic-test-text",
    )

    private fun meditationJson(id: String, version: Long) =
        """{"id":"$id","bookCode":"TST","chapter":1,"verse":1,"endVerse":1,"content":"private note","version":$version,"createdAt":"2026-09-01T19:30:00","modifiedAt":"2026-09-01T19:30:00","worshipAt":"2026-09-01T19:30:00"}"""

    private fun verseJson(verse: Int) =
        """{"translationCode":"TEST","translationName":"Synthetic","copyrightNotice":"Test data only","bookCode":"TST","bookName":"Synthetic book","chapter":1,"verse":$verse,"text":"synthetic-$verse"}"""

    private fun json(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
