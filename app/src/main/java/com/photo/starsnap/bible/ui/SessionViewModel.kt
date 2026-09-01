package com.photo.starsnap.bible.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photo.starsnap.bible.data.ApiException
import com.photo.starsnap.bible.data.BibleGateway
import com.photo.starsnap.bible.data.SecureCookieJar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data object Authenticated : SessionState
    data class Unavailable(val message: String) : SessionState
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

class SessionViewModel(
    private val api: BibleGateway,
    private val cookieJar: SecureCookieJar,
) : ViewModel() {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    init {
        bootstrap()
    }

    fun updateUsername(value: String) {
        _login.value = _login.value.copy(username = value, error = null)
    }

    fun updatePassword(value: String) {
        _login.value = _login.value.copy(password = value, error = null)
    }

    fun bootstrap() = viewModelScope.launch {
        _state.value = SessionState.Loading
        runCatching { api.refreshSession() }
            .onSuccess { refreshed ->
                if (refreshed) {
                    _state.value = SessionState.Authenticated
                } else {
                    cookieJar.clear()
                    api.invalidateSession()
                    _state.value = SessionState.SignedOut
                }
            }
            .onFailure {
                _state.value = SessionState.Unavailable("서버에 연결하지 못했습니다.")
            }
    }

    fun login() = viewModelScope.launch {
        val username = _login.value.username.trim()
        val password = _login.value.password
        if (username.isEmpty() || password.isEmpty() || _login.value.isSubmitting) return@launch

        _login.value = _login.value.copy(isSubmitting = true, error = null)
        cookieJar.clear()
        api.invalidateSession()
        runCatching { api.login(username, password) }
            .onSuccess {
                _login.value = LoginUiState()
                _state.value = SessionState.Authenticated
            }
            .onFailure { error ->
                cookieJar.clear()
                _login.value = _login.value.copy(
                    isSubmitting = false,
                    error = if (error is ApiException && error.statusCode in setOf(400, 401)) {
                        "아이디 또는 비밀번호를 다시 확인해 주세요."
                    } else {
                        "로그인하지 못했습니다. 네트워크 연결을 확인해 주세요."
                    },
                )
                _state.value = SessionState.SignedOut
            }
    }

    fun logout() = viewModelScope.launch {
        _state.value = SessionState.Loading
        runCatching { api.logout() }
        cookieJar.clear()
        api.invalidateSession()
        _state.value = SessionState.SignedOut
    }

    fun expireSession() {
        cookieJar.clear()
        api.invalidateSession()
        _login.value = LoginUiState(error = "로그인이 만료되었습니다. 다시 로그인해 주세요.")
        _state.value = SessionState.SignedOut
    }
}
