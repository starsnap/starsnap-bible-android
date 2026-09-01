package com.photo.starsnap.bible.data

import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class BibleApiClient(
    private val baseUrl: HttpUrl,
    cookieJar: CookieJar,
    private val gson: Gson = Gson(),
) : BibleGateway {
    private val refreshLock = Any()
    private val sessionEpoch = AtomicLong(0)
    private val refreshVersion = AtomicLong(0)
    private val refreshClient = baseClient(cookieJar).build()
    private val client = baseClient(cookieJar)
        .addInterceptor { chain ->
            val tagged = chain.request().newBuilder()
                .tag(
                    SessionGenerationTag::class.java,
                    SessionGenerationTag(
                        epoch = sessionEpoch.get(),
                        refreshVersion = refreshVersion.get(),
                    ),
                )
                .build()
            chain.proceed(tagged)
        }
        .authenticator(SessionAuthenticator())
        .build()

    override suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        refreshRequest().use {
            it.isSuccessful.also { success -> if (success) refreshVersion.incrementAndGet() }
        }
    }

    override suspend fun login(username: String, password: String) {
        val body = gson.toJson(
            LoginRequest(
                username = username,
                password = password,
                loginType = if ('@' in username) "EMAIL" else "USERNAME",
            ),
        )
        requestEmpty(
            method = "POST",
            path = "/api/auth/login",
            body = body,
        )
        refreshVersion.incrementAndGet()
    }

    override suspend fun logout() {
        requestEmpty(method = "POST", path = "/api/auth/logout")
        refreshVersion.incrementAndGet()
    }

    override fun invalidateSession() {
        sessionEpoch.incrementAndGet()
        refreshVersion.set(0)
    }

    override suspend fun licenseStatus(): BibleLicenseStatus = request(
        method = "GET",
        path = "/api/bible/license/status",
        query = mapOf("translationCode" to "NKRV"),
        responseClass = BibleLicenseStatus::class.java,
    )

    override suspend fun searchVerses(query: String, page: Int): BibleSlice = request(
        method = "GET",
        path = "/api/bible/verses",
        query = mapOf(
            "translationCode" to "NKRV",
            "query" to query,
            "page" to page.toString(),
            "size" to "30",
        ),
        responseClass = BibleSlice::class.java,
    )

    override suspend fun meditationByVerse(verse: BibleVerse): BibleMeditation? = try {
        request(
            method = "GET",
            path = "/api/bible/meditations/by-verse",
            query = mapOf(
                "bookCode" to verse.bookCode,
                "chapter" to verse.chapter.toString(),
                "verse" to verse.verse.toString(),
            ),
            responseClass = BibleMeditation::class.java,
        )
    } catch (error: ApiException) {
        if (error.statusCode == 404) null else throw error
    }

    override suspend fun saveMeditation(
        verse: BibleVerse,
        content: String,
        worshipAt: String,
        current: BibleMeditation?,
    ): BibleMeditation = if (current == null) {
        request(
            method = "POST",
            path = "/api/bible/meditations",
            body = gson.toJson(
                CreateMeditationRequest(
                    bookCode = verse.bookCode,
                    chapter = verse.chapter,
                    verse = verse.verse,
                    content = content,
                    worshipAt = worshipAt,
                ),
            ),
            responseClass = BibleMeditation::class.java,
        )
    } else {
        request(
            method = "PATCH",
            path = "/api/bible/meditations/${current.id}",
            body = gson.toJson(
                UpdateMeditationRequest(
                    content = content,
                    expectedVersion = current.version,
                    worshipAt = worshipAt,
                ),
            ),
            responseClass = BibleMeditation::class.java,
        )
    }

    private suspend fun requestEmpty(method: String, path: String, body: String? = null) {
        withContext(Dispatchers.IO) {
            val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE) ?: EMPTY_JSON_BODY
            val request = requestBuilder(path).method(method, requestBody).build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw response.toException()
            }
        }
    }

    private suspend fun <T> request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
        responseClass: Class<T>,
    ): T = withContext(Dispatchers.IO) {
        val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
        val request = requestBuilder(path, query)
            .method(method, requestBody)
            .build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw response.toException()
            val payload = response.body?.string().orEmpty()
            runCatching { gson.fromJson(payload, responseClass) }
                .getOrElse { throw ApiException(response.code, "서버 응답을 읽지 못했습니다.") }
        }
    }

    private fun requestBuilder(path: String, query: Map<String, String> = emptyMap()): Request.Builder {
        val resolved = baseUrl.resolve(path.removePrefix("/"))
            ?: throw IllegalArgumentException("Invalid API path")
        val url = resolved.newBuilder().apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
    }

    private fun refreshRequest(): Response {
        val url = baseUrl.resolve("api/auth/refresh")
            ?: throw IllegalArgumentException("Invalid refresh URL")
        val request = Request.Builder()
            .url(url)
            .patch(EMPTY_JSON_BODY)
            .header("Accept", "application/json")
            .build()
        return refreshClient.newCall(request).execute()
    }

    private fun Response.toException(): ApiException {
        val payload = body?.string().orEmpty()
        val envelope = runCatching { gson.fromJson(payload, ErrorEnvelope::class.java) }.getOrNull()
        val message = envelope?.message ?: envelope?.detail ?: envelope?.error ?: when (code) {
            401 -> "로그인이 만료되었습니다."
            403 -> "이 작업을 수행할 권한이 없습니다."
            else -> "요청을 완료하지 못했습니다."
        }
        return ApiException(code, message)
    }

    private fun baseClient(cookieJar: CookieJar) = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)

    private inner class SessionAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.url.encodedPath in AUTHENTICATOR_EXCLUDED_PATHS) return null
            if (responseCount(response) >= 2) return null

            return synchronized(refreshLock) {
                val requestGeneration = response.request
                    .tag(SessionGenerationTag::class.java)
                if (requestGeneration != null && requestGeneration.epoch != sessionEpoch.get()) {
                    return@synchronized null
                }
                if (requestGeneration != null && requestGeneration.refreshVersion != refreshVersion.get()) {
                    return@synchronized response.request.newBuilder().build()
                }
                refreshRequest().use { refresh ->
                    if (refresh.isSuccessful) {
                        refreshVersion.incrementAndGet()
                        response.request.newBuilder().build()
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count += 1
            prior = prior.priorResponse
        }
        return count
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_JSON_BODY = ByteArray(0).toRequestBody(JSON_MEDIA_TYPE)
        val AUTHENTICATOR_EXCLUDED_PATHS = setOf(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/refresh",
        )
    }

    private data class SessionGenerationTag(
        val epoch: Long,
        val refreshVersion: Long,
    )
}
