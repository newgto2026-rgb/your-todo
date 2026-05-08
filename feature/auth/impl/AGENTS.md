# :feature:auth:impl 모듈 가이드

## 역할
- 인증 기능 구현: 로그인 화면, 인증 게이트 ViewModel, Credential Manager 연동.

## 규칙
- UI는 `UiState + ViewModel + 이벤트` 구조를 유지한다.
- 서버/저장소 직접 구현은 하지 않고 `core:domain` 유스케이스만 호출한다.
- Google Credential 획득은 인증 기능의 플랫폼 연동으로 격리하고, 서버 로그인/세션 저장은 domain/data 계약을 통해 수행한다.
- 사용자 노출 문자열은 `values`, `values-ko` 리소스로 관리한다.

## 검증
- `./gradlew :feature:auth:impl:testDebugUnitTest`
- `./gradlew :feature:auth:impl:lintDebug`
