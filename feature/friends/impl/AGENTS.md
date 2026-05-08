# :feature:friends:impl 모듈 가이드

## 역할
- Friends 화면 구현: Composable, ViewModel, UI state, side effect.

## 규칙
- `UiState + Action + SideEffect + ViewModel` 구조를 유지한다.
- 사용자 노출 텍스트는 리소스에서 가져온다.
- Composable은 상태 렌더링과 이벤트 전달만 담당한다.
- 친구 요청/수락/거절/끊기 정책은 서버/domain/use case 결과에 따른다.

## 검증
- `./gradlew :feature:friends:impl:testDebugUnitTest`
- `./gradlew :feature:friends:impl:lintDebug`
