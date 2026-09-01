package com.photo.starsnap.bible.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photo.starsnap.bible.data.ApiException
import com.photo.starsnap.bible.data.BibleGateway
import com.photo.starsnap.bible.data.BibleLicenseStatus
import com.photo.starsnap.bible.data.BibleMeditation
import com.photo.starsnap.bible.data.BibleVerse
import com.photo.starsnap.bible.data.WorshipTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RequestState { Idle, Loading, Error }

data class BibleUiState(
    val licenseState: RequestState = RequestState.Loading,
    val license: BibleLicenseStatus? = null,
    val query: String = "",
    val searchState: RequestState = RequestState.Idle,
    val verses: List<BibleVerse> = emptyList(),
    val selectedVerse: BibleVerse? = null,
    val noteState: RequestState = RequestState.Idle,
    val meditation: BibleMeditation? = null,
    val content: String = "",
    val worshipAt: String = "",
    val savedContent: String = "",
    val savedWorshipAt: String = "",
    val saveState: RequestState = RequestState.Idle,
    val error: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
    val hasConflict: Boolean = false,
    val conflictDraft: String? = null,
) {
    val canSearch: Boolean
        get() = license?.phase == "active" &&
            license.searchAvailable &&
            license.textDisplayAllowed
    val isDirty: Boolean get() = content != savedContent || worshipAt != savedWorshipAt
    val canSave: Boolean
        get() = selectedVerse != null && content.trim().isNotEmpty() &&
            WorshipTime.isValid(worshipAt) && isDirty && !hasConflict && saveState != RequestState.Loading
}

class BibleViewModel(private val api: BibleGateway) : ViewModel() {
    private val _state = MutableStateFlow(BibleUiState())
    val state: StateFlow<BibleUiState> = _state.asStateFlow()

    private var protectedGeneration = 0L
    private var licenseJob: Job? = null
    private var searchJob: Job? = null
    private var noteJob: Job? = null
    private var saveJob: Job? = null

    fun loadLicense() {
        val generation = invalidateProtectedRequests()
        _state.update {
            it.copy(
                licenseState = RequestState.Loading,
                license = null,
                searchState = RequestState.Idle,
                verses = emptyList(),
                selectedVerse = null,
                noteState = RequestState.Idle,
                meditation = null,
                content = "",
                worshipAt = "",
                savedContent = "",
                savedWorshipAt = "",
                saveState = RequestState.Idle,
                hasConflict = false,
                conflictDraft = null,
                error = null,
                message = null,
            )
        }
        licenseJob = viewModelScope.launch {
            runApi { api.licenseStatus() }
                .onSuccess { license ->
                    if (generation != protectedGeneration) return@onSuccess
                    _state.update {
                        it.copy(licenseState = RequestState.Idle, license = license)
                    }
                }
                .onFailure {
                    if (generation != protectedGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            licenseState = RequestState.Error,
                            license = null,
                            error = "성경 본문 이용 상태를 확인하지 못했습니다.",
                        )
                    }
                }
        }
    }

    fun onForeground() = loadLicense()

    fun updateQuery(value: String) {
        _state.update { it.copy(query = value, error = null, message = null) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (!_state.value.canSearch || query.isEmpty()) return
        val generation = protectedGeneration
        searchJob?.cancel()
        _state.update { it.copy(searchState = RequestState.Loading, error = null, message = null) }
        searchJob = viewModelScope.launch {
            runApi { api.searchVerses(query) }
                .onSuccess { response ->
                    if (generation != protectedGeneration || !_state.value.canSearch) return@onSuccess
                    _state.update { it.copy(searchState = RequestState.Idle, verses = response.content) }
                }
                .onFailure {
                    if (handleLicenseUnavailable(it)) return@onFailure
                    if (generation != protectedGeneration) return@onFailure
                    _state.update {
                        it.copy(searchState = RequestState.Error, error = "성경 구절을 검색하지 못했습니다.")
                    }
                }
        }
    }

    fun selectVerse(verse: BibleVerse) {
        val generation = protectedGeneration
        noteJob?.cancel()
        saveJob?.cancel()
        val now = WorshipTime.now()
        _state.update {
            it.copy(
                selectedVerse = verse,
                noteState = RequestState.Loading,
                meditation = null,
                content = "",
                savedContent = "",
                worshipAt = now,
                savedWorshipAt = now,
                saveState = RequestState.Idle,
                hasConflict = false,
                conflictDraft = null,
                error = null,
                message = null,
            )
        }
        noteJob = viewModelScope.launch {
            runApi { api.meditationByVerse(verse) }
                .onSuccess { meditation ->
                    if (!matchesSelection(generation, verse)) return@onSuccess
                    val content = meditation?.content.orEmpty()
                    val worshipAt = WorshipTime.normalize(meditation?.worshipAt) ?: now
                    _state.update {
                        it.copy(
                            noteState = RequestState.Idle,
                            meditation = meditation,
                            content = content,
                            savedContent = content,
                            worshipAt = worshipAt,
                            savedWorshipAt = worshipAt,
                        )
                    }
                }
                .onFailure {
                    if (handleLicenseUnavailable(it)) return@onFailure
                    if (!matchesSelection(generation, verse)) return@onFailure
                    _state.update {
                        it.copy(
                            noteState = RequestState.Error,
                            error = "저장된 말씀 노트를 불러오지 못했습니다.",
                        )
                    }
                }
        }
    }

    fun updateContent(value: String) {
        if (value.length <= 5_000) {
            _state.update { it.copy(content = value, error = null, message = null) }
        }
    }

    fun updateWorshipAt(value: String) {
        _state.update { it.copy(worshipAt = value, error = null, message = null) }
    }

    fun cancelChanges() {
        _state.update {
            it.copy(
                content = it.savedContent,
                worshipAt = it.savedWorshipAt,
                error = null,
                message = "변경 사항을 취소했습니다.",
            )
        }
    }

    fun save() {
        val current = _state.value
        val verse = current.selectedVerse ?: return
        val content = current.content.trim()
        val worshipAt = current.worshipAt.trim()
        if (content.isEmpty()) {
            _state.update { it.copy(error = "말씀 노트 내용을 입력해 주세요.") }
            return
        }
        if (!WorshipTime.isValid(worshipAt)) {
            _state.update { it.copy(error = "예배 시간을 다시 확인해 주세요.") }
            return
        }

        val generation = protectedGeneration
        saveJob?.cancel()
        _state.update { it.copy(saveState = RequestState.Loading, error = null, message = null) }
        saveJob = viewModelScope.launch {
            runApi { api.saveMeditation(verse, content, worshipAt, current.meditation) }
                .onSuccess { saved ->
                    if (!matchesSelection(generation, verse)) return@onSuccess
                    val savedTime = WorshipTime.normalize(saved.worshipAt) ?: worshipAt
                    _state.update {
                        it.copy(
                            meditation = saved,
                            content = saved.content,
                            savedContent = saved.content,
                            worshipAt = savedTime,
                            savedWorshipAt = savedTime,
                        saveState = RequestState.Idle,
                        hasConflict = false,
                        conflictDraft = null,
                        message = "말씀 노트를 비공개로 저장했습니다.",
                        )
                    }
                }
                .onFailure { error ->
                    if (handleLicenseUnavailable(error)) return@onFailure
                    if (!matchesSelection(generation, verse)) return@onFailure
                    val conflict = error is ApiException && error.statusCode == 409
                    _state.update {
                        it.copy(
                            saveState = RequestState.Error,
                            hasConflict = conflict,
                            conflictDraft = if (conflict) content else it.conflictDraft,
                            error = if (conflict) {
                                "다른 기기에서 노트가 변경되었습니다. 최신 버전을 불러와 초안을 다시 확인해 주세요."
                            } else {
                                "말씀 노트를 저장하지 못했습니다. 작성 내용은 화면에 남아 있습니다."
                            },
                        )
                    }
                }
        }
    }

    fun clearSessionExpiredFlag() {
        _state.update { it.copy(sessionExpired = false) }
    }

    fun clearForSessionChange() {
        invalidateProtectedRequests()
        _state.value = BibleUiState()
    }

    fun reloadAfterConflict() {
        val current = _state.value
        val verse = current.selectedVerse ?: return
        val draft = current.conflictDraft ?: current.content
        val editedWorshipAt = current.worshipAt
        val generation = protectedGeneration
        noteJob?.cancel()
        saveJob?.cancel()
        _state.update { it.copy(noteState = RequestState.Loading, error = null, message = null) }
        noteJob = viewModelScope.launch {
            runApi { api.meditationByVerse(verse) }
                .onSuccess { latest ->
                    if (!matchesSelection(generation, verse)) return@onSuccess
                    val latestContent = latest?.content.orEmpty()
                    val latestWorshipAt = WorshipTime.normalize(latest?.worshipAt) ?: editedWorshipAt
                    _state.update {
                        it.copy(
                            noteState = RequestState.Idle,
                            meditation = latest,
                            content = draft,
                            savedContent = latestContent,
                            worshipAt = editedWorshipAt,
                            savedWorshipAt = latestWorshipAt,
                            hasConflict = false,
                            conflictDraft = null,
                            saveState = RequestState.Idle,
                            message = "최신 버전을 불러왔습니다. 초안을 확인한 뒤 다시 저장해 주세요.",
                        )
                    }
                }
                .onFailure {
                    if (handleLicenseUnavailable(it)) return@onFailure
                    if (!matchesSelection(generation, verse)) return@onFailure
                    _state.update {
                        it.copy(noteState = RequestState.Error, error = "최신 말씀 노트를 불러오지 못했습니다.")
                    }
                }
        }
    }

    private fun invalidateProtectedRequests(): Long {
        protectedGeneration += 1
        licenseJob?.cancel()
        searchJob?.cancel()
        noteJob?.cancel()
        saveJob?.cancel()
        return protectedGeneration
    }

    private fun matchesSelection(generation: Long, verse: BibleVerse): Boolean =
        generation == protectedGeneration && _state.value.selectedVerse?.id == verse.id

    private fun handleLicenseUnavailable(error: Throwable): Boolean {
        if (error !is ApiException || error.statusCode != 503) return false
        loadLicense()
        return true
    }

    private suspend fun <T> runApi(block: suspend () -> T): Result<T> {
        val result = try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
        val error = result.exceptionOrNull()
        if (error is ApiException && error.statusCode == 401) {
            _state.update { it.copy(sessionExpired = true) }
        }
        return result
    }
}
