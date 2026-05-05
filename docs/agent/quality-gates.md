# 품질 게이트

## 목적
- 테스트/커버리지/검증 커맨드를 한곳에 모아 작업 타입별로 빠르게 적용한다.

## 적용 조건 (When to load)
- 구현 변경 후 검증 단계
- 테스트 범위 또는 커버리지 기준 확인이 필요할 때

## 공통 기준
- use case/ViewModel 단위 테스트를 우선한다.
- 핵심 사용자 플로우에는 UI 테스트를 추가한다.
- Flow 테스트에는 Turbine을 사용하고 공용 fake/rule은 `core:testing`을 재사용한다.
- 테스트는 구현 세부가 아닌 동작 중심으로 작성한다.
- non-view 레이어 커버리지 최소 80%를 확인한다.
- pre-push 훅에서 변경 모듈 `lintDebug`를 우선 실행하며, Gradle/CI/훅 변경 시 `./gradlew lint` 전체를 실행한다.

## 작업 타입별 체크리스트

### 구현(Feature)
- 관련 모듈 단위 테스트 실행
- 영향 범위 린트 실행
- 필요 시 루트 통합 테스트 실행

### 버그 수정(Bugfix)
- 재현 테스트(또는 실패 케이스) 추가
- 수정 후 회귀 테스트 실행
- 영향 모듈 최소 범위 검증

### 리팩터링(Refactor)
- 공개 계약(API/인터페이스) 호환성 확인
- 동작 동일성 테스트 강화
- 성능/재구성 영향 점검(Compose 관련 변경 시)

## 검증 명령어
- 앱 빌드: `./gradlew assembleDebug`
- 단위 테스트: `./gradlew testDebugUnitTest`
- 단일 테스트: `./gradlew testDebugUnitTest --tests "com.example.MyTest"`
- 계측 테스트: `./gradlew connectedDebugAndroidTest`
- 모듈 단위 테스트: `./gradlew :<module>:testDebugUnitTest`
- 모듈 린트: `./gradlew :<module>:lintDebug`
