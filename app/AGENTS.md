# :app 모듈 가이드

## 역할
- 애플리케이션 셸 및 시작 구성.
- 최상위 내비게이션 호스트와 Android 진입점.
- 런타임 통합(알림, 워커, 리시버).

## 소유 범위
- `MainActivity`, `AppNavHost`, `Application` 연결.
- AndroidManifest 앱 레벨 선언.
- 앱 레벨 리소스와 알림 텍스트.

## 경계
- `feature:*:api`, `feature:*:entry`, `core:*`에 의존한다.
- 기능 내부 구현을 직접 포함하지 않는다.

## 변경 체크리스트
- 내비게이션/시작 목적지가 바뀌면 feature-entry 계약 연결을 검증한다.
- 앱 리소스는 로케일 안전성(`values`, `values-ko`)을 유지한다.
- 리마인더/워커 변경 시 런타임 권한과 채널 동작을 확인한다.

## Navigation 작업 규칙
- 내비게이션 관련 변경(라우트/백스택/오버레이/바텀시트/엔트리 데코레이터)이 포함되면 반드시 `docs/agent` 가이드와 공식 권장 패턴을 먼저 확인하고 그 기준으로 구현한다.
- `NavDisplay`는 `backStack + entryDecorators + entryProvider` 기반 구성을 우선 사용하고, 수동 `entries` 조합은 owner/scope 안정성 검증 근거가 있을 때만 사용한다.
- Overlay/BottomSheet 전환 중에도 destination `ViewModelStoreOwner` 연속성이 깨지지 않도록 구현한다. 화면별 ViewModel이 Activity 스코프로 승격되거나 재생성되지 않게 한다.
- feature는 라우트 엔트리와 화면 content를 제공하고, 앱 셸은 컨테이너/호스트/전환만 담당한다. (컨테이너와 feature 내부 모달을 중복으로 소유하지 않는다.)
- 내비게이션 변경 PR에는 최소 1개 이상 “전환 전/후 상태 유지” UI 테스트(예: 선택 날짜/필터/스크롤/입력 상태)를 포함하거나 기존 테스트를 갱신한다.
- 사용자 가시 문구를 테스트에서 검증할 때는 하드코딩 문자열 대신 리소스 기반 비교를 사용해 로케일에 독립적으로 작성한다.

## 검증
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
