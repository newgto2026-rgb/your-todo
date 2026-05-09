# :feature:friends:entry 모듈 가이드

## 역할
- Friends 구현을 앱 셸에 Hilt multibinding으로 연결한다.

## 규칙
- 앱 셸 구현 상세를 참조하지 않는다.
- `api`와 `impl` 연결만 담당한다.

## 검증
- `./gradlew :feature:friends:entry:lintDebug`
