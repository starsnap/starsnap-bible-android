# StarSnap Bible Android

성경 저작권 허가 상태를 서버에서 확인한 뒤 구절을 검색하고, 예배 시간과 비공개 말씀 노트를 기록하는 Android 전용 앱입니다.

## 원칙

- API: `https://api.starsnap.kr`
- Android API 36, Kotlin, Jetpack Compose, JDK 21
- 권한은 `INTERNET`만 사용
- HttpOnly 인증 쿠키는 Android Keystore AES-GCM으로 암호화해 저장
- 보호되는 성경 본문은 앱 번들, 테스트 fixture, 로컬 DB, 오프라인 캐시에 포함하지 않음
- 서버 라이선스 상태가 `pending` 또는 `paused`면 검색 결과와 선택 본문을 메모리에서 즉시 제거

## 현재 배포 차단 사항

StarSnap 백엔드는 사용자당 refresh 세션을 하나만 저장합니다. 이 앱에서 로그인하면 기존 SNS 웹·Android·iOS 세션이 만료될 수 있으므로, 사용자+세션 단위 다중 로그인 구조가 배포되기 전에는 Play Store에 출시하지 않습니다.

## 검증

```powershell
.\gradlew.bat test lintDebug assembleDebug bundleRelease
```

서명 키, 서비스 계정, 사용자 자격 증명은 저장소에 포함하지 않습니다.
