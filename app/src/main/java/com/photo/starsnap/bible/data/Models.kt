package com.photo.starsnap.bible.data

data class LoginRequest(
    val username: String,
    val password: String,
    val loginType: String,
)

data class BibleLicenseStatus(
    val phase: String,
    val searchAvailable: Boolean,
    val textDisplayAllowed: Boolean,
    val notice: String,
    val providerName: String?,
    val expiresOn: String?,
)

data class BibleVerse(
    val translationCode: String,
    val translationName: String,
    val copyrightNotice: String,
    val bookCode: String,
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
) {
    val id: String get() = "$translationCode:$bookCode:$chapter:$verse"
    val reference: String get() = "$bookName $chapter:$verse"
}

data class BibleSlice(
    val content: List<BibleVerse>,
    val number: Int,
    val size: Int,
    val last: Boolean,
)

data class BibleMeditation(
    val id: String,
    val bookCode: String,
    val chapter: Int,
    val verse: Int,
    val content: String,
    val version: Long,
    val createdAt: String,
    val modifiedAt: String,
    val worshipAt: String?,
)

data class CreateMeditationRequest(
    val bookCode: String,
    val chapter: Int,
    val verse: Int,
    val content: String,
    val worshipAt: String,
)

data class UpdateMeditationRequest(
    val content: String,
    val expectedVersion: Long,
    val worshipAt: String,
)

data class ErrorEnvelope(
    val message: String? = null,
    val error: String? = null,
    val detail: String? = null,
)

class ApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)
