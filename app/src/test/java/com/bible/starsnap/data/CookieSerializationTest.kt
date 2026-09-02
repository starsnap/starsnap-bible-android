package com.bible.starsnap.data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieSerializationTest {
    @Test
    fun hostOnlySecureHttpOnlyCookieSurvivesSerialization() {
        val origin = "https://bible.starsnap.kr/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("bible-session")
            .value("opaque-test-value")
            .hostOnlyDomain(origin.host)
            .path("/")
            .secure()
            .httpOnly()
            .expiresAt(1_900_000_000_000)
            .build()

        val restored = requireNotNull(Cookie.parse(origin, cookie.toString()))

        assertEquals("bible-session", restored.name)
        assertTrue(restored.hostOnly)
        assertTrue(restored.secure)
        assertTrue(restored.httpOnly)
        assertEquals(origin.host, restored.domain)
    }

    @Test
    fun preSeparationCookiesAreExcluded() {
        val origin = "https://bible.starsnap.kr/".toHttpUrl()
        val cookies = listOf(
            Cookie.Builder().name("access-token").value("legacy").hostOnlyDomain(origin.host).build(),
            Cookie.Builder().name("bible-session").value("current").hostOnlyDomain(origin.host).build(),
        )

        assertEquals(listOf("bible-session"), cookies.onlyBibleSessionCookies().map(Cookie::name))
    }
}
