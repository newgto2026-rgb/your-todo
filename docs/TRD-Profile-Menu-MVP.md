# Goal
- PRD-107의 Profile Menu MVP를 Android 앱 셸에서 구현한다.
- 프로필 아이콘 클릭 시 오른쪽 drawer를 열고, 읽기 전용 계정 정보, 닉네임 복사, 앱/알림 설정, 정책/버전 정보, 로그아웃을 제공한다.
- 닉네임 변경, 서버 API, DB/DataStore 스키마 변경은 포함하지 않는다.

# Architecture
- `:app`이 profile menu drawer, clipboard, settings intent, policy URL fallback, logout orchestration을 소유한다.
- `:core:ui`는 `YourTodoAppHeader` / `YourTodoBrandHeader`의 stateless profile click callback만 제공한다.
- `:feature:*:impl`은 drawer 상태나 로그아웃 로직을 갖지 않고 `AppRouteActions.openProfileMenu()`만 호출한다.
- `SignOutUseCase`를 재사용해 push token 삭제와 로컬 세션 삭제를 수행한다.
- `AuthGate`는 세션 변화를 감지해 로그인 화면 전환을 담당한다.

# Module Changes
- `:core:ui`
  - 프로필 아바타를 48dp 터치 영역의 버튼으로 확장한다.
  - `onProfileClick` 기본 no-op 콜백을 추가한다.
- `:core:ui` navigation
  - `AppRouteActions.openProfileMenu()` 기본 no-op을 추가한다.
- `:feature:todo:impl`
  - Todo 상단 헤더에 `onProfileClick`을 전달한다.
- `:feature:calendar:impl`
  - Calendar 상단 헤더에 `onProfileClick`을 전달한다.
- `:feature:friends:impl`
  - Friends 상단 헤더에 `onProfileClick`을 전달한다.
- `:app`
  - `AppProfileMenuViewModel`로 세션 표시와 로그아웃 진행 상태를 관리한다.
  - `AppProfileMenuDrawer`로 오른쪽 slide-in drawer와 로그아웃 확인 다이얼로그를 제공한다.
  - 앱 문자열은 `values`, `values-ko`에 둔다.

# State
- `Closed`: drawer 닫힘.
- `Open`: drawer 열림, 세션 정보 표시.
- `CopySuccess`: 닉네임 복사 후 snackbar 표시.
- `LogoutConfirming`: 로그아웃 확인 다이얼로그 표시.
- `LogoutInProgress`: 로그아웃 실행 중, 중복 클릭 방지.
- `LogoutFailed`: drawer 유지, 실패 snackbar 표시.
- `SignedOut`: 세션 삭제 후 `AuthGate`로 위임.

# Edge Cases
- 닉네임이 비어 있으면 fallback 문구를 표시하고 복사 액션은 비활성화한다.
- 이메일이 비어 있으면 fallback 문구를 표시한다.
- Privacy/Terms URL이 비어 있으면 외부 브라우저를 열지 않고 준비 중 snackbar를 표시한다.
- notification settings intent 실패 시 앱 상세 설정으로 fallback한다.
- logout 중에는 drawer 닫기/로그아웃 확인 중복 실행을 제한한다.

# Acceptance Criteria
- Todo, Calendar, Friends 화면의 프로필 버튼이 profile menu drawer를 연다.
- drawer는 오른쪽에서 slide-in 되고 scrim, 닫기 버튼, back으로 닫힌다.
- drawer는 현재 세션의 닉네임과 이메일을 읽기 전용으로 표시한다.
- 닉네임 변경 UI는 노출하지 않는다.
- 닉네임 복사 성공 시 snackbar가 표시된다.
- 로그아웃 클릭 시 확인 다이얼로그가 먼저 표시된다.
- 로그아웃 확인 전에는 세션 삭제가 발생하지 않는다.
- 로그아웃 진행 중 중복 실행이 막힌다.
- 로그아웃 성공은 `AuthGate` 세션 전환으로 처리된다.
- 로그아웃 실패 시 drawer를 유지하고 오류 snackbar를 표시한다.

# Verification
- `./gradlew :core:ui:compileDebugKotlin :app:compileDebugKotlin :feature:todo:impl:compileDebugKotlin :feature:calendar:impl:compileDebugKotlin :feature:friends:impl:compileDebugKotlin`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`
- `./gradlew :core:ui:lintDebug`
- `./gradlew :feature:todo:impl:lintDebug`
- `./gradlew :feature:calendar:impl:lintDebug`
- `./gradlew :feature:friends:impl:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`
