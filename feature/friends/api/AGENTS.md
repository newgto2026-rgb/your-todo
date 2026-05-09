# :feature:friends:api 모듈 가이드

## 역할
- Friends 공개 route와 feature entry 계약.

## 규칙
- 구현 상세/ViewModel/UI 상태를 두지 않는다.
- 앱 셸과 impl 사이의 공개 내비게이션 계약만 유지한다.

## 검증
- `./gradlew :feature:friends:api:testDebugUnitTest`
