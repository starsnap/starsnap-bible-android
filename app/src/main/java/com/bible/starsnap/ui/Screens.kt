package com.bible.starsnap.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bible.starsnap.data.BibleVerse
import com.bible.starsnap.data.WorshipTime
import java.time.LocalDateTime

@Composable
fun LaunchScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoStories,
            contentDescription = null,
            tint = BibleColors.BrandStrong,
        )
        Spacer(Modifier.height(16.dp))
        Text("StarSnap Bible", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator()
        Text("로그인 상태 확인 중", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun UnavailableScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("서버에 연결할 수 없습니다", style = MaterialTheme.typography.headlineSmall)
        Text(message, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                tint = BibleColors.BrandStrong,
            )
        }
        item {
            Text("StarSnap Bible", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Text(
                "성경 말씀을 찾고, 구절마다 묵상을 기록해보세요",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("아이디 또는 이메일") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("비밀번호") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onLogin() }),
                    )
                    state.error?.let { Feedback(it, true) }
                    Button(
                        onClick = onLogin,
                        enabled = state.username.isNotBlank() && state.password.isNotBlank() && !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSubmitting) CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        else Text("로그인")
                    }
                }
            }
        }
        item {
            Text(
                "StarSnap 계정의 아이디/이메일과 비밀번호를 사용합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleScreen(
    state: BibleUiState,
    viewModel: BibleViewModel,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("로그아웃할까요?") },
            text = {
                Text(
                    if (state.isDirty) "저장하지 않은 말씀 노트가 사라집니다."
                    else "이 기기의 로그인 세션을 종료합니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("로그아웃") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("취소") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("StarSnap Bible", fontWeight = FontWeight.Bold)
                        Text(
                            "허가된 말씀 · 비공개 노트",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { confirmLogout = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "로그아웃")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { IntroCard() }
            item { LicenseCard(state, viewModel::loadLicense) }
            item { SearchCard(state, viewModel::updateQuery, viewModel::search) }

            if (state.verses.isEmpty() && state.searchState == RequestState.Idle) {
                item { EmptyState(state.canSearch) }
            } else {
                item {
                    Text(
                        "검색 결과 ${state.verses.size}개",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                items(
                    items = state.verses,
                    key = { "${it.translationCode}:${it.bookCode}:${it.chapter}:${it.verse}" },
                ) { verse ->
                    VerseCard(verse, state.selectedVerse == verse) { viewModel.selectVerse(verse) }
                }
            }

            state.selectedVerse?.let { verse ->
                item {
                    NoteEditor(
                        state = state,
                        verse = verse,
                        onContentChange = viewModel::updateContent,
                        onWorshipAtChange = viewModel::updateWorshipAt,
                        onPickTime = {
                            showDateTimePicker(context, state.worshipAt, viewModel::updateWorshipAt)
                        },
                        onCancel = viewModel::cancelChanges,
                        onSave = viewModel::save,
                        onReload = viewModel::reloadAfterConflict,
                    )
                }
            }

            state.error?.let { item { Feedback(it, true) } }
            state.message?.let { item { Feedback(it, false) } }
        }
    }
}

@Composable
private fun IntroCard() = Card(
    colors = CardDefaults.cardColors(containerColor = BibleColors.Brand.copy(alpha = 0.28f)),
    border = BorderStroke(1.dp, BibleColors.BrandStrong),
) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.AutoStories, contentDescription = null)
        Text(
            "한 구절을 찾고, 조용히 기록하세요",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text("예배 시간과 선택한 말씀, 마음에 남은 내용을 내 계정에 비공개로 저장합니다.")
    }
}

@Composable
private fun LicenseCard(state: BibleUiState, onRetry: () -> Unit) = Card(
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("본문 이용 상태", style = MaterialTheme.typography.titleMedium)
            when (state.licenseState) {
                RequestState.Loading -> CircularProgressIndicator(modifier = Modifier.height(22.dp))
                RequestState.Error -> IconButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "상태 다시 확인")
                }
                RequestState.Idle -> Text(
                    if (state.canSearch) "이용 가능" else "허가 대기",
                    color = if (state.canSearch) BibleColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            state.license?.notice ?: "저작권 허가 상태를 확인한 뒤 본문 검색을 제공합니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.license?.providerName?.let { Text("제공 번역본: $it") }
    }
}

@Composable
private fun SearchCard(
    state: BibleUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) = Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("성경 검색", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSearch && state.searchState != RequestState.Loading,
            label = { Text("책 이름, 장·절 또는 검색어") },
            placeholder = { Text(if (state.canSearch) "예: 창세기 1:1 또는 사랑" else "허가 후 검색할 수 있습니다") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            singleLine = true,
        )
        Button(
            onClick = onSearch,
            enabled = state.canSearch && state.query.isNotBlank() && state.searchState != RequestState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.searchState == RequestState.Loading) CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            else Text("검색")
        }
        Text(
            "본문 권한이 확인된 경우에만 구절 내용을 표시합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(canSearch: Boolean) = Card {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.AutoStories, contentDescription = null)
        Text(if (canSearch) "검색어를 입력해 시작하세요" else "본문 이용 허가를 기다리고 있습니다")
        Text(
            if (canSearch) "구절을 선택하면 말씀 노트가 열립니다."
            else "허가 전에는 보호되는 성경 본문을 앱에 포함하거나 표시하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun VerseCard(verse: BibleVerse, selected: Boolean, onClick: () -> Unit) = Card(
    modifier = Modifier.semantics { this.selected = selected },
    onClick = onClick,
    colors = CardDefaults.cardColors(
        containerColor = if (selected) BibleColors.Brand.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
    ),
    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BibleColors.BrandStrong else MaterialTheme.colorScheme.outline),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(verse.reference, style = MaterialTheme.typography.titleMedium)
        Text(verse.translationName, style = MaterialTheme.typography.labelSmall)
        Text(verse.text, style = MaterialTheme.typography.bodyLarge)
        Text(verse.copyrightNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoteEditor(
    state: BibleUiState,
    verse: BibleVerse,
    onContentChange: (String) -> Unit,
    onWorshipAtChange: (String) -> Unit,
    onPickTime: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
) = Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null)
            Column(Modifier.padding(start = 10.dp)) {
                Text("말씀 노트 · 비공개", style = MaterialTheme.typography.titleLarge)
                Text(verse.reference)
            }
        }
        if (state.noteState == RequestState.Loading) {
            CircularProgressIndicator()
        } else {
            OutlinedTextField(
                value = state.worshipAt,
                onValueChange = onWorshipAtChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("예배 시간") },
                supportingText = { Text("YYYY-MM-DDTHH:mm") },
                trailingIcon = {
                    IconButton(onClick = onPickTime) {
                        Icon(Icons.Outlined.AccessTime, contentDescription = "날짜와 시간 선택")
                    }
                },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.content,
                onValueChange = onContentChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("말씀 노트 내용") },
                placeholder = { Text("감사, 질문, 오늘의 실천을 자유롭게 적어보세요.") },
                supportingText = { Text("${state.content.length} / 5,000자") },
                minLines = 6,
                maxLines = 12,
            )
            if (state.hasConflict) {
                OutlinedButton(
                    onClick = onReload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("최신 버전 불러오고 초안 유지")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = state.isDirty && state.saveState != RequestState.Loading,
                    modifier = Modifier.weight(1f),
                ) { Text("변경 취소") }
                Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.weight(1f)) {
                    if (state.saveState == RequestState.Loading) CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    else Text("비공개로 저장")
                }
            }
        }
    }
}

@Composable
private fun Feedback(message: String, error: Boolean) = Card(
    colors = CardDefaults.cardColors(containerColor = if (error) BibleColors.DangerSoft else BibleColors.SuccessSoft),
) {
    Text(
        message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) BibleColors.Danger else BibleColors.Success,
    )
}

private fun showDateTimePicker(context: Context, value: String, onSelected: (String) -> Unit) {
    val initial = WorshipTime.parseOrNow(value)
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onSelected(WorshipTime.format(LocalDateTime.of(year, month + 1, day, hour, minute)))
                },
                initial.hour,
                initial.minute,
                false,
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
