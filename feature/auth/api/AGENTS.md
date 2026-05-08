# :feature:auth:api 모듈 가이드

## 역할
- 인증 기능의 공개 진입 계약.
- 앱 셸이 인증 구현을 직접 알지 않도록 하는 최소 API.

## 규칙
- `:feature:auth:impl`에 의존하지 않는다.
- 서버 통신, Credential Manager, 화면 상태 변환 로직을 넣지 않는다.
- 앱 시작 게이트에 필요한 타입과 인터페이스만 노출한다.

## 검증
- `./gradlew :feature:auth:api:lintDebug`
