package com.bible.starsnap.data

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieSerializationTest {
    @Test
    fun hostOnlySecureHttpOnlyCookieSurvivesSerialization() {
        val origin = "https://api.starsnap.kr/".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("refresh-token")
            .value("opaque-test-value")
            .hostOnlyDomain(origin.host)
            .path("/")
            .secure()
            .httpOnly()
            .expiresAt(1_900_000_000_000)
            .build()

        val restored = requireNotNull(Cookie.parse(origin, cookie.toString()))

        assertEquals("refresh-token", restored.name)
        assertTrue(restored.hostOnly)
        assertTrue(restored.secure)
        assertTrue(restored.httpOnly)
        assertEquals(origin.host, restored.domain)
    }
}
