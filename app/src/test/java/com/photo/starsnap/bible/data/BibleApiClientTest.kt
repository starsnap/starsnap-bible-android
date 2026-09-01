package com.photo.starsnap.bible.data

import com.bible.starsnap.data.BibleApiClient
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.google.gson.Gson
import okhttp3.CookieJar
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
            current = null,
        )
        client.saveMeditation(
            verse = syntheticVerse(),
            content = "updated note",
            worshipAt = "2026-09-02T09:10",
            current = created,
        )

        val create = server.takeRequest()
        val update = server.takeRequest()
        val createJson = Gson().fromJson(create.body.readUtf8(), CreateMeditationRequest::class.java)
        val updateJson = Gson().fromJson(update.body.readUtf8(), UpdateMeditationRequest::class.java)

        assertEquals("POST", create.method)
        assertEquals("2026-09-01T19:30", createJson.worshipAt)
        assertEquals("PATCH", update.method)
        assertEquals(0L, updateJson.expectedVersion)
        assertEquals("2026-09-02T09:10", updateJson.worshipAt)
    }

    @Test
    fun loginIgnoresResponseBodyAndSendsUsernameOrEmailType() = runTest {
        server.enqueue(json("not-json"))
        server.enqueue(json("""{"expiredAt":"2026-09-01T10:00:00"}"""))

        client.login("person@example.com", "secret")
        client.login("nickname", "secret")

        val email = Gson().fromJson(server.takeRequest().body.readUtf8(), LoginRequest::class.java)
        val username = Gson().fromJson(server.takeRequest().body.readUtf8(), LoginRequest::class.java)
        assertEquals("EMAIL", email.loginType)
        assertEquals("USERNAME", username.loginType)
    }

    @Test
    fun concurrentUnauthorizedRequestsShareOneRefresh() = runTest {
        val protectedCount = AtomicInteger(0)
        val refreshCount = AtomicInteger(0)
        val initialRequests = CountDownLatch(2)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl?.encodedPath) {
                    "/api/auth/refresh" -> {
                        refreshCount.incrementAndGet()
                        json("{}")
                    }
                    "/api/bible/license/status" -> {
                        val count = protectedCount.incrementAndGet()
                        if (count <= 2) {
                            initialRequests.countDown()
                            initialRequests.await(5, TimeUnit.SECONDS)
                            json("""{"message":"expired"}""", status = 401)
                        } else {
                            json("""{"phase":"pending","searchAvailable":false,"textDisplayAllowed":false,"notice":"pending","providerName":null,"expiresOn":null}""")
                        }
                    }
                    else -> json("""{"message":"not found"}""", status = 404)
                }
            }
        }

        listOf(
            async { client.licenseStatus() },
            async { client.licenseStatus() },
        ).awaitAll()

        assertEquals(1, refreshCount.get())
        assertEquals(4, protectedCount.get())
    }

    @Test
    fun oldSessionUnauthorizedResponseIsNeverReplayedAfterEpochChange() = runTest {
        val initialSeen = CountDownLatch(1)
        val releaseUnauthorized = CountDownLatch(1)
        val refreshCount = AtomicInteger(0)
        val protectedCount = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl?.encodedPath) {
                    "/api/auth/refresh" -> {
                        refreshCount.incrementAndGet()
                        json("{}")
                    }
                    "/api/bible/license/status" -> {
                        protectedCount.incrementAndGet()
                        initialSeen.countDown()
                        releaseUnauthorized.await(5, TimeUnit.SECONDS)
                        json("""{"message":"expired"}""", status = 401)
                    }
                    else -> json("""{"message":"not found"}""", status = 404)
                }
            }
        }

        val oldRequest = async { runCatching { client.licenseStatus() } }
        runCurrent()
        assertEquals(true, initialSeen.await(5, TimeUnit.SECONDS))
        client.invalidateSession()
        releaseUnauthorized.countDown()
        val result = oldRequest.await()

        assertTrue(result.isFailure)
        assertEquals(0, refreshCount.get())
        assertEquals(1, protectedCount.get())
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
        """{"id":"$id","bookCode":"TST","chapter":1,"verse":1,"content":"private note","version":$version,"createdAt":"2026-09-01T19:30:00","modifiedAt":"2026-09-01T19:30:00","worshipAt":"2026-09-01T19:30:00"}"""

    private fun json(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
