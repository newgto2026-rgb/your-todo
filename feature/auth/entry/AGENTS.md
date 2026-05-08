# :feature:auth:entry 모듈 가이드

## 역할
- 앱 조합을 위해 인증 기능 진입 구현을 바인딩하는 연결 모듈.

## 규칙
- DI/와이어링 관심사만 둔다.
- 로그인 화면, Credential Manager, ViewModel 로직을 넣지 않는다.

## 검증
- `./gradlew :feature:auth:entry:lintDebug`
