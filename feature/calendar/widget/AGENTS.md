# :feature:calendar:widget 모듈 가이드

## 역할
- Android 홈 화면용 Calendar 위젯 구현.
- Glance 위젯 UI, 위젯 전용 presenter/state, receiver, widget update 연결을 소유한다.

## 경계
- `feature:calendar:api`, `core:domain`, `core:model`에 의존한다.
- `feature:calendar:impl`과 `app`에 의존하지 않는다.
- 앱 화면 Compose UI를 직접 재사용하지 않는다.

## 규칙
- 위젯은 현재 월 캘린더 표시와 날짜 탭 앱 진입까지만 담당한다.
- Todo 상세 표시/수정/완료/삭제는 앱 Calendar 화면으로 위임한다.
- 날짜 계산과 표시 모델 변환은 presenter/date grid로 분리해 단위 테스트한다.
- 사용자 노출 문자열은 `values`, `values-ko` 리소스로 관리한다.
- 위젯 갱신 실패는 앱 기능 실패로 전파하지 않는다.

## 검증
- `./gradlew :feature:calendar:widget:testDebugUnitTest`
- `./gradlew :feature:calendar:widget:lintDebug`
