package com.photo.starsnap.bible.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

object WorshipTime {
    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")
        .withResolverStyle(ResolverStyle.STRICT)
    private val pattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")

    fun now(): String = LocalDateTime.now().format(formatter)

    fun isValid(value: String): Boolean {
        if (!value.matches(pattern)) return false
        return try {
            LocalDateTime.parse(value, formatter)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    fun normalize(serverValue: String?): String? {
        val candidate = serverValue?.trim()?.replace(' ', 'T')?.take(16) ?: return null
        return candidate.takeIf(::isValid)
    }

    fun parseOrNow(value: String): LocalDateTime = try {
        LocalDateTime.parse(value, formatter)
    } catch (_: DateTimeParseException) {
        LocalDateTime.now()
    }

    fun format(value: LocalDateTime): String = value.format(formatter)
}
