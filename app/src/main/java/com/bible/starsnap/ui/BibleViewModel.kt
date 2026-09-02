package com.bible.starsnap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bible.starsnap.data.ApiException
import com.bible.starsnap.data.BibleGateway
import com.bible.starsnap.data.BibleLicenseStatus
import com.bible.starsnap.data.BibleMeditation
import com.bible.starsnap.data.BibleVerse
import com.bible.starsnap.data.WorshipTime
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
    val selectedVerses: List<BibleVerse> = emptyList(),
    val selectedEndVerse: Int? = null,
    val savedEndVerse: Int? = null,
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
    val editorIsDirty: Boolean get() = content != savedContent || worshipAt != savedWorshipAt
    val isDirty: Boolean get() = editorIsDirty || selectedEndVerse != savedEndVerse
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
    private var rangeJob: Job? = null
    private var saveJob: Job? = null

    fun loadLicense() = verifyLicense(preserveProtectedState = false)

    fun onForeground() {
        if (_state.value.licenseState != RequestState.Loading) {
            verifyLicense(preserveProtectedState = true)
        }
    }

    private fun verifyLicense(preserveProtectedState: Boolean) {
        val generation = invalidateProtectedRequests()
        _state.update {
            if (preserveProtectedState) {
                it.copy(
                    licenseState = RequestState.Loading,
                    license = null,
                    searchState = RequestState.Idle,
                    noteState = RequestState.Idle,
                    saveState = RequestState.Idle,
                    error = null,
                    message = null,
                )
            } else {
                BibleUiState(licenseState = RequestState.Loading)
            }
        }
        licenseJob = viewModelScope.launch {
            runApi { api.licenseStatus() }
                .onSuccess { license ->
                    if (generation != protectedGeneration) return@onSuccess
                    _state.update { current ->
                        if (license.phase == "active" && license.searchAvailable && license.textDisplayAllowed) {
                            current.copy(licenseState = RequestState.Idle, license = license)
                        } else {
                            BibleUiState(licenseState = RequestState.Idle, license = license)
                        }
                    }
                }
                .onFailure {
                    if (generation != protectedGeneration) return@onFailure
                    _state.value = BibleUiState(
                        licenseState = RequestState.Error,
                        error = "성경 본문 이용 상태를 확인하지 못했습니다.",
                    )
                }
        }
    }

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
        val current = _state.value
        if (current.noteState == RequestState.Loading || current.saveState == RequestState.Loading) {
            _state.update { it.copy(error = "말씀 노트를 불러오거나 저장하는 중입니다. 잠시 후 다시 선택해 주세요.") }
            return
        }
        val start = current.selectedVerse
        val changesStart = start == null || start.bookCode != verse.bookCode ||
            start.chapter != verse.chapter || verse.verse < start.verse
        if ((changesStart && current.isDirty) || (!changesStart && current.editorIsDirty)) {
            _state.update { it.copy(error = "저장하지 않은 노트가 있습니다. 먼저 저장하거나 변경을 취소해 주세요.") }
            return
        }
        if (start != null && start.bookCode == verse.bookCode && start.chapter == verse.chapter && verse.verse >= start.verse) {
            if (current.selectedEndVerse != verse.verse) selectRange(start, verse.verse)
            return
        }

        val generation = protectedGeneration
        noteJob?.cancel()
        rangeJob?.cancel()
        saveJob?.cancel()
        val now = WorshipTime.now()
        _state.update {
            it.copy(
                selectedVerse = verse,
                selectedVerses = listOf(verse),
                selectedEndVerse = verse.verse,
                savedEndVerse = verse.verse,
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
                    val endVerse = meditation?.endVerse ?: verse.verse
                    val selectedVerses = if (endVerse > verse.verse) {
                        runApi { api.verseRange(verse, endVerse) }.getOrElse {
                            _state.update { current -> current.copy(error = "저장된 QT 구간을 불러오지 못했습니다.") }
                            listOf(verse)
                        }
                    } else {
                        listOf(verse)
                    }
                    if (!matchesSelection(generation, verse)) return@onSuccess
                    _state.update {
                        it.copy(
                            noteState = RequestState.Idle,
                            meditation = meditation,
                            selectedVerses = selectedVerses,
                            selectedEndVerse = endVerse,
                            savedEndVerse = endVerse,
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

    private fun selectRange(start: BibleVerse, endVerse: Int) {
        val generation = protectedGeneration
        rangeJob?.cancel()
        _state.update { it.copy(noteState = RequestState.Loading, error = null, message = null) }
        rangeJob = viewModelScope.launch {
            runApi { api.verseRange(start, endVerse) }
                .onSuccess { verses ->
                    if (!matchesSelection(generation, start)) return@onSuccess
                    _state.update {
                        it.copy(
                            noteState = RequestState.Idle,
                            selectedVerses = verses,
                            selectedEndVerse = endVerse,
                        )
                    }
                }
                .onFailure {
                    if (handleLicenseUnavailable(it)) return@onFailure
                    if (!matchesSelection(generation, start)) return@onFailure
                    _state.update { current ->
                        current.copy(noteState = RequestState.Idle, error = "선택한 QT 구간을 불러오지 못했습니다.")
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
        val current = _state.value
        val verse = current.selectedVerse
        _state.update {
            it.copy(
                content = it.savedContent,
                worshipAt = it.savedWorshipAt,
                selectedEndVerse = it.savedEndVerse,
                error = null,
                message = "변경 사항을 취소했습니다.",
            )
        }
        val endVerse = current.savedEndVerse ?: return
        if (verse != null && endVerse > verse.verse) selectRange(verse, endVerse)
    }

    fun save() {
        val current = _state.value
        val verse = current.selectedVerse ?: return
        val endVerse = current.selectedEndVerse ?: return
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
            runApi { api.saveMeditation(verse, content, worshipAt, endVerse, current.meditation) }
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
                            selectedEndVerse = saved.endVerse ?: endVerse,
                            savedEndVerse = saved.endVerse ?: endVerse,
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
                            savedEndVerse = latest?.endVerse ?: verse.verse,
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
        rangeJob?.cancel()
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
