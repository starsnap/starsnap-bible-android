package com.bible.starsnap.data

import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.TimeUnit

class BibleApiClient(
    private val baseUrl: HttpUrl,
    cookieJar: CookieJar,
    private val gson: Gson = Gson(),
) : BibleGateway {
    private val client = baseClient(cookieJar).build()

    override suspend fun validateSession(): Boolean = try {
        requestEmpty(method = "GET", path = "/api/bible/auth/session")
        true
    } catch (error: ApiException) {
        if (error.statusCode == 401) false else throw error
    }

    override suspend fun login(username: String, password: String) {
        val body = gson.toJson(
            LoginRequest(
                username = username,
                password = password,
            ),
        )
        requestEmpty(
            method = "POST",
            path = "/api/bible/auth/login",
            body = body,
        )
    }

    override suspend fun logout() {
        requestEmpty(method = "POST", path = "/api/bible/auth/logout")
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

    override suspend fun verseRange(start: BibleVerse, endVerse: Int): List<BibleVerse> = request(
        method = "GET",
        path = "/api/bible/verses/range",
        query = mapOf(
            "translationCode" to start.translationCode,
            "bookCode" to start.bookCode,
            "chapter" to start.chapter.toString(),
            "verse" to start.verse.toString(),
            "endVerse" to endVerse.toString(),
        ),
        responseClass = Array<BibleVerse>::class.java,
    ).toList()

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
        endVerse: Int,
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
                    endVerse = endVerse,
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
                    endVerse = endVerse,
                ),
            ),
            responseClass = BibleMeditation::class.java,
        )
    }

    private suspend fun requestEmpty(method: String, path: String, body: String? = null) {
        withContext(Dispatchers.IO) {
            val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
                ?: if (method == "GET") null else EMPTY_JSON_BODY
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
    }
}
