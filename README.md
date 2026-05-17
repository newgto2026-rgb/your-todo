# YourTodo

이 프로젝트의 목적은 **AI를 활용한 개발에 대한 인사이트를 얻는 것**이다.

YourTodo는 Android Todo 앱이지만, 단순히 할 일 관리 앱을 만드는 데서 끝나지 않는다. 이 저장소는 AI가 제품 기획, 기술 설계, 구현, 테스트, 문서화, 품질 검토에 어떻게 참여할 수 있는지 관찰하고, 그 과정에서 어떤 구조와 하네스가 유지보수 가능한 소프트웨어를 만드는 데 도움이 되는지 실험하기 위한 프로젝트다.

## 프로젝트 목적

이 저장소가 던지는 핵심 질문은 다음과 같다.

> AI를 활용해 개발할 때, 어떤 프로젝트 구조와 제약, 품질 게이트, 피드백 루프가 있어야 코드베이스를 안정적으로 키울 수 있는가?

YourTodo는 개인 Todo와 친구 협업이라는 익숙한 제품 도메인을 실험 대상으로 사용한다. 제품은 충분히 현실적인 데이터, 화면, 동기화, 인증, 협업 흐름을 갖고 있고, 그 위에서 AI-assisted development의 실제 장단점을 관찰할 수 있다.

이 프로젝트는 특히 다음에 집중한다.

- 제품 아이디어를 PRD/TRD와 구현 가능한 개발 컨텍스트로 바꾸는 방식
- AI가 Android 멀티모듈 경계 안에서 작업하도록 만드는 구조
- 테스트, lint, coverage, hook, CI를 실행 가능한 품질 하네스로 사용하는 방식
- 긴 AI 작업을 문서와 repository convention으로 이어받게 만드는 방식
- 사람이 코드 줄 단위보다 제품 의미, 아키텍처 판단, 품질 기준에 더 집중하는 개발 방식

## 앱 개요

YourTodo는 개인 할 일 관리와 친구 협업을 결합한 Android 생산성 앱이다.

| 영역 | 제공 기능 |
|---|---|
| 개인 Todo | 할 일 추가, 수정, 완료, 우선순위, 카테고리, 날짜/시간 관리 |
| Calendar | 월간 캘린더와 선택일 할 일 확인 |
| Calendar Widget | Android 홈 화면에서 할 일 요약 확인 |
| Friends | 친구 요청, 친구 목록, 받은/보낸 요청 관리 |
| Shared Todo | 친구에게 할 일을 보내고 받은 할 일을 수락/거절 |
| Direct Assignment | 신뢰 관계의 친구가 보낸 할 일을 자동 수락 |
| AI Todo Draft | 한국어 자연어 입력을 편집 가능한 Todo 초안으로 변환 |
| Profile/Auth | 계정 상태, 알림, push token, 로그아웃 흐름 관리 |

앱의 핵심은 여러 화면이 같은 task 현실을 바라보도록 만드는 것이다. 개인 Todo와 Assigned Todo는 domain 계층에서 합성되고, Todo, Calendar, Widget, Friends 화면은 이를 각자 UI 모델로 변환해 표현한다.

## 아키텍처

YourTodo는 Kotlin 기반 Android 멀티모듈 프로젝트다. 주요 기술은 Jetpack Compose, Hilt, Room, DataStore, Retrofit, WorkManager, Firebase Messaging, Glance다.

```text
app
  - 앱 셸, navigation, profile entry
  - FCM, notification, WorkManager, widget 연결

feature:*:api
  - 공개 route와 feature 계약

feature:*:entry
  - app 셸에 feature를 연결하는 진입 바인딩

feature:*:impl
  - Compose UI, ViewModel, UiState, Action, SideEffect

feature:calendar:widget
  - Android 홈 화면 위젯 구현

core:domain
  - use case, repository contract, 앱 정책

core:data
  - repository 구현과 data source 조합

core:database / core:datastore / core:network
  - Room, Preferences DataStore, Retrofit 경계

core:model / core:ui / core:designsystem / core:testing
  - 순수 모델, 공용 UI, 디자인 토큰, 테스트 유틸리티
```

의존 방향은 명확하게 제한한다.

```text
app -> feature:*:api, feature:*:entry, core:*
feature:*:entry -> feature:*:api, feature:*:impl
feature:*:impl -> feature:*:api, core:*
core:data -> core:domain + storage/network modules
core:* does not depend on feature:*
```

이 구조는 app 셸이 feature 구현 세부사항에 직접 묶이지 않게 하고, 공통 core 계층이 feature 모듈에 끌려가지 않게 한다.

## AI 활용 개발 인사이트

이 저장소에서 AI는 단순 코드 자동완성 도구가 아니라 개발 협업자에 가깝게 사용됐다.

AI는 다음 작업에 활용됐다.

- 제품 요구사항을 PRD/TRD로 정리
- 구현 전 코드베이스와 모듈 구조 탐색
- 멀티모듈 변경 범위 설계
- DTO, Room Entity, domain model, mapper, sync payload 모델링
- `UiState + Action + SideEffect` 기반 UDF 화면 설계
- 테스트와 회귀 시나리오 검토
- 아키텍처 문서와 외부 공유용 PDF 생성

중요한 점은 AI 작업을 repository-level 구조 안에 묶었다는 것이다. 이 프로젝트는 AI가 반복해서 읽고 따를 수 있는 규칙과 산출물을 저장소 안에 둔다.

- `AGENTS.md`는 모듈 경계, 리뷰 기준, 테스트 기대치, 작업 제약을 정의한다.
- `docs/agent/*`는 feature, UI, data, quality 작업 플레이북을 제공한다.
- PRD/TRD 문서는 제품 의도와 기술 판단을 세션을 넘어 이어준다.
- hook과 CI는 일부 규칙을 문서가 아니라 실행 가능한 검증 장치로 만든다.

## 하네스와 품질 게이트

YourTodo에는 AI와 사람이 같은 품질 기준으로 작업하도록 돕는 하네스가 포함되어 있다.

| 하네스 | 역할 |
|---|---|
| `.husky/pre-commit` | `main`/`master` 직접 commit을 막고 최신 `origin/main` 기준 작업을 확인 |
| `.husky/pre-push` | `main`/`master` 직접 push를 막고 변경 영향 모듈 lint 또는 전체 lint 실행 |
| `scripts/quality/product-harness-check.sh` | 모듈별 `AGENTS.md`, 루트 모듈 인덱스, Gradle 의존 방향, 금지 import를 검사 |
| `scripts/codex-hooks/tdd-guard.sh` | 운영 Kotlin/Java 코드 변경 전에 같은 모듈 테스트 변경을 요구 |
| GitHub Actions Android CI | 단위 테스트, 디버그 빌드, strict lint, coverage verification 실행 |
| `coverageVerifyAll` | 핵심 non-view 모듈의 coverage threshold 검증 |
| Strict Android lint | warning을 error로 취급하고 dependency lint까지 확인 |
| PR template | 범위, 설계, 테스트, 커버리지, 리스크, 마이그레이션 근거를 PR에 남기게 함 |

이 프로젝트의 핵심 인사이트는 다음과 같다.

> 저장소 자체가 하네스가 되어야 한다. 문서, 테스트, hook, CI, coverage, 리뷰 기준이 함께 있어야 AI가 빠르게 움직이면서도 아키텍처 일관성을 잃지 않는다.

## 검증 명령어

공통 검증 명령어:

```sh
scripts/quality/product-harness-check.sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lint
./gradlew coverageVerifyAll
```

모듈 단위 검증:

```sh
./gradlew :<module>:testDebugUnitTest
./gradlew :<module>:lintDebug
```

기기 또는 에뮬레이터가 준비된 경우:

```sh
./gradlew connectedDebugAndroidTest
```

## 문서

프로젝트 이해를 위한 주요 문서:

- [앱 구조 소개서](docs/YOURTODO-APP-OVERVIEW.md)
- [AI 기반 개발 사례 소개서](docs/YOURTODO-AI-DEVELOPMENT-STORY.md)
- [데이터 아키텍처 리포트](docs/DATA-ARCHITECTURE-REPORT.md)
- [Agent 작업 가이드](AGENTS.md)
- [자동화 하네스](docs/agent/automation-harness.md)
- [품질 게이트](docs/agent/quality-gates.md)
- [TDD Guard Hook](docs/agent/tdd-guard.md)

외부 공유용 PDF 문서도 `docs/` 아래에 함께 제공된다.

## 작업 정책

구현 작업은 전용 브랜치와 별도 Git worktree에서 수행한다. `main` 직접 push는 피하고, 변경은 PR을 통해 검증한다. 구현 변경은 영향 모듈 테스트와 lint를 함께 고려한다.

AI-assisted work의 기본 흐름은 다음과 같다.

```text
AGENTS.md 확인
  -> 영향 모듈 식별
  -> 관련 모듈 가이드만 추가 확인
  -> 동작 변경이 있으면 테스트 추가/수정
  -> 모듈 경계 안에서 구현
  -> 영향 모듈 테스트와 lint 실행
  -> PR에 검증 결과와 리스크 기록
```

## 이 프로젝트가 남기는 것

YourTodo는 Android 앱으로도 의미가 있지만, 더 중요한 결과물은 그 주변의 개발 시스템이다.

이 프로젝트는 AI를 활용한 개발에서 다음 요소가 중요하다는 점을 보여준다.

- 명확한 아키텍처
- 작은 모듈 경계
- 실행 가능한 품질 게이트
- 회귀 중심 테스트
- 지속적으로 읽히는 프로젝트 지시문
- 제품 의미와 아키텍처 판단에 집중하는 human-in-the-loop

이 조합이 이 프로젝트의 실험이다. AI와 함께 빠르게 만들되, 코드베이스를 이해 가능하고 테스트 가능하며 유지보수 가능한 상태로 유지하는 것. 그것이 YourTodo의 핵심 목적이다.
