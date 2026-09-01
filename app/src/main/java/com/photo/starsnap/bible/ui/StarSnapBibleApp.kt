package com.photo.starsnap.bible.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StarSnapBibleApp(
    sessionViewModel: SessionViewModel,
    bibleViewModel: BibleViewModel,
) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val login by sessionViewModel.login.collectAsStateWithLifecycle()
    val bible by bibleViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(bible.sessionExpired) {
        if (bible.sessionExpired) {
            bibleViewModel.clearSessionExpiredFlag()
            bibleViewModel.clearForSessionChange()
            sessionViewModel.expireSession()
        }
    }

    when (val current = session) {
        SessionState.Loading -> LaunchScreen()
        SessionState.SignedOut -> {
            LaunchedEffect(Unit) { bibleViewModel.clearForSessionChange() }
            LoginScreen(
                state = login,
                onUsernameChange = sessionViewModel::updateUsername,
                onPasswordChange = sessionViewModel::updatePassword,
                onLogin = sessionViewModel::login,
            )
        }
        SessionState.Authenticated -> {
            LaunchedEffect(Unit) { bibleViewModel.loadLicense() }
            BibleScreen(
                state = bible,
                viewModel = bibleViewModel,
                onLogout = {
                    bibleViewModel.clearForSessionChange()
                    sessionViewModel.logout()
                },
            )
        }
        is SessionState.Unavailable -> UnavailableScreen(
            message = current.message,
            onRetry = sessionViewModel::bootstrap,
        )
    }
}
