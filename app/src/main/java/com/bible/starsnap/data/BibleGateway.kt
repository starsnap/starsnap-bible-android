package com.bible.starsnap.data

interface BibleGateway {
    suspend fun refreshSession(): Boolean
    suspend fun login(username: String, password: String)
    suspend fun logout()
    fun invalidateSession()
    suspend fun licenseStatus(): BibleLicenseStatus
    suspend fun searchVerses(query: String, page: Int = 0): BibleSlice
    suspend fun meditationByVerse(verse: BibleVerse): BibleMeditation?
    suspend fun saveMeditation(
        verse: BibleVerse,
        content: String,
        worshipAt: String,
        current: BibleMeditation?,
    ): BibleMeditation
}
