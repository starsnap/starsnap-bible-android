package com.photo.starsnap.bible.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureCookieJar(
    context: Context,
    private val origin: HttpUrl,
) : CookieJar {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private var cookies: MutableList<Cookie> = load().toMutableList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != origin.host) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            this.cookies.removeAll { stored ->
                stored.expiresAt < now || cookies.any { incoming ->
                    incoming.name == stored.name &&
                        incoming.domain == stored.domain &&
                        incoming.path == stored.path
                }
            }
            this.cookies += cookies.filter { it.expiresAt >= now }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        if (url.host != origin.host) return@synchronized emptyList()
        val now = System.currentTimeMillis()
        val before = cookies.size
        cookies.removeAll { it.expiresAt < now }
        if (before != cookies.size) persist()
        cookies.filter { it.matches(url) }
    }

    fun clear() = synchronized(lock) {
        cookies.clear()
        preferences.edit().remove(COOKIES_KEY).apply()
    }

    private fun persist() {
        val json = JSONArray()
        cookies.forEach { json.put(it.toString()) }
        val encrypted = encrypt(json.toString().toByteArray(Charsets.UTF_8))
        preferences.edit().putString(COOKIES_KEY, encrypted).apply()
    }

    private fun load(): List<Cookie> {
        val encrypted = preferences.getString(COOKIES_KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(decrypt(encrypted).toString(Charsets.UTF_8))
            buildList {
                for (index in 0 until json.length()) {
                    Cookie.parse(origin, json.getString(index))?.let(::add)
                }
            }
        }.getOrElse {
            preferences.edit().remove(COOKIES_KEY).apply()
            emptyList()
        }
    }

    private fun encrypt(value: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + cipher.getOutputSize(value.size))
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(cipher.doFinal(value))
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): ByteArray {
        val payload = ByteBuffer.wrap(Base64.decode(value, Base64.NO_WRAP))
        val ivSize = payload.int
        require(ivSize in 12..16)
        val iv = ByteArray(ivSize).also(payload::get)
        val encrypted = ByteArray(payload.remaining()).also(payload::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "starsnap_bible_secure_session"
        const val COOKIES_KEY = "encrypted_cookies"
        const val KEY_ALIAS = "starsnap_bible_cookie_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
