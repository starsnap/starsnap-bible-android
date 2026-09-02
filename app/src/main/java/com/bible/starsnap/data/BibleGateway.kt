package com.bible.starsnap.data

interface BibleGateway {
    suspend fun validateSession(): Boolean
    suspend fun login(username: String, password: String)
    suspend fun logout()
    suspend fun licenseStatus(): BibleLicenseStatus
    suspend fun searchVerses(query: String, page: Int = 0): BibleSlice
    suspend fun verseRange(start: BibleVerse, endVerse: Int): List<BibleVerse>
    suspend fun meditationByVerse(verse: BibleVerse): BibleMeditation?
    suspend fun saveMeditation(
        verse: BibleVerse,
        content: String,
        worshipAt: String,
        endVerse: Int,
        current: BibleMeditation?,
    ): BibleMeditation
}
