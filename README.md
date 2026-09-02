# StarSnap Bible Android

성경 저작권 허가 상태를 서버에서 확인한 뒤 구절을 검색하고, 예배 시간과 비공개 말씀 노트를 기록하는 Android 전용 앱입니다.

## 원칙

- API: `https://bible.starsnap.kr`
- Android API 36, Kotlin, Jetpack Compose, JDK 21
- 권한은 `INTERNET`만 사용
- Bible 전용 `bible-session` HttpOnly 쿠키는 Android Keystore AES-GCM으로 암호화해 저장
- 보호되는 성경 본문은 앱 번들, 테스트 fixture, 로컬 DB, 오프라인 캐시에 포함하지 않음
- 서버 라이선스 상태가 `pending` 또는 `paused`면 검색 결과와 선택 본문을 메모리에서 즉시 제거

## 계정 분리

로그인·세션·말씀 노트는 Bible 전용 API와 PostgreSQL만 사용합니다. SNS 계정, access/refresh 토큰, FCM 토큰과 사용자 데이터는 읽거나 변경하지 않습니다.

## 검증

```powershell
.\gradlew.bat test lintDebug assembleDebug bundleRelease
```

서명 키, 서비스 계정, 사용자 자격 증명은 저장소에 포함하지 않습니다.
