package com.photo.starsnap.bible

import android.content.Context
import com.bible.starsnap.data.BibleApiClient
import com.photo.starsnap.bible.data.SecureCookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl

class AppContainer(context: Context) {
    private val baseUrl = "https://api.starsnap.kr/".toHttpUrl()
    val cookieJar = SecureCookieJar(context.applicationContext, baseUrl)
    val api = BibleApiClient(baseUrl, cookieJar)
}
