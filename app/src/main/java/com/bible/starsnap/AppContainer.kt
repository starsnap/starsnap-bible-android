package com.bible.starsnap

import android.content.Context
import com.bible.starsnap.data.BibleApiClient
import com.bible.starsnap.data.SecureCookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl

class AppContainer(context: Context) {
    private val baseUrl = "https://bible.starsnap.kr/".toHttpUrl()
    val cookieJar = SecureCookieJar(context.applicationContext, baseUrl)
    val api = BibleApiClient(baseUrl, cookieJar)
}
