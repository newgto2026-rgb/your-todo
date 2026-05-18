# 프로젝트 에이전트 가이드

이 문서는 YourTodo 저장소에서 에이전트가 작업할 때 가장 먼저 읽는 단일 진입점이다. 세부 문서는 필요한 순간에만 추가로 연다.

## 1. 기본 행동 원칙

이 섹션은 YourTodo 작업 방식의 기본 원칙이다. 단순 오타 수정 같은 작은 작업에는 과하게 적용하지 말고, 구현/리팩터링/버그 수정/다중 파일 변경에는 기본값으로 적용한다.

### 1.1 Think Before Coding
- 추측으로 바로 구현하지 않는다.
- 요구가 모호하면 가능한 해석과 tradeoff를 먼저 드러낸다.
- 더 단순한 접근이 있으면 말한다.
- 불확실한 상태에서 파일을 고치지 말고, 필요한 경우 짧게 질문한다.

### 1.2 Simplicity First
- 요청된 문제를 해결하는 최소 구현을 선택한다.
- 요청되지 않은 기능, 설정값, 확장 포인트, 추상화를 추가하지 않는다.
- 단일 사용처를 위한 새 abstraction은 기본적으로 피한다.
- 200줄로 쓴 구현이 50줄로 가능하면 줄인다.

### 1.3 Surgical Changes
- 사용자의 요청과 직접 연결되는 파일/라인만 바꾼다.
- 인접 코드, 주석, 포맷을 "개선"한다는 이유로 건드리지 않는다.
- 기존 스타일과 패턴을 따른다.
- 내 변경으로 생긴 unused import/변수/함수는 정리한다.
- 기존에 있던 dead code나 관련 없는 문제는 삭제하지 말고 언급만 한다.

### 1.4 Goal-Driven Execution
- 작업을 검증 가능한 목표로 바꾼다.
- 버그 수정은 재현 테스트를 먼저 추가/갱신한 뒤 통과시킨다.
- 리팩터링은 변경 전/후 동작 동일성을 검증한다.
- 다단계 작업은 `단계 -> 검증` 형태의 짧은 계획으로 진행한다.
- 검증이 불가능하면 이유와 남은 위험을 명시한다.

## 2. 필수 작업 루프

### 2.1 시작 전
1. 현재 작업 경로가 기본 체크아웃이 아닌지 확인한다.
2. 전용 브랜치 + 별도 Git worktree에서 작업한다.
3. 변경 대상 Gradle 모듈을 식별한다.
4. 대상 모듈의 `AGENTS.md`만 추가로 연다.
5. `docs/agent/rework-metrics.md`와 현재 branch metrics 문서가 있으면 최근 피드백/lesson을 확인한다.
6. 다단계 작업이면 `tmp/agent-context-<sanitized-branch>.md`를 만들거나 갱신해 최신 사용자 지시, 핵심 결정, 현재 상태, 다음 액션을 남긴다.
7. 모듈 경계/의존 방향 위반 가능성을 먼저 점검한다.
8. 구현 작업이면 성공 기준과 검증 명령을 먼저 정한다.

### 2.2 구현 중
1. 변경은 최소 범위, 테스트 가능, 기능 중심으로 유지한다.
2. 동작 변경은 가능한 한 테스트를 먼저 추가/갱신한다.
3. 기존 helper/API/pattern을 우선 사용한다.
4. feature 구현에서 data 구현체를 직접 참조하지 않는다.
5. 화면은 `UiState + Action + SideEffect + ViewModel` 흐름을 유지한다.
6. 사용자 노출 문자열은 리소스로 분리한다.

### 2.3 완료 전
1. 영향 모듈 단위 테스트를 실행한다.
2. 영향 모듈 린트를 실행한다.
3. 제품 하네스 구조 검사를 실행한다.
4. 재작업/리뷰/CI 실패/사용자 피드백이 있었다면 branch metrics 문서와 PR 본문 `AI Rework Metrics`를 갱신한다.
5. 버그 수정은 해당 버그를 재현하는 회귀 테스트를 반드시 추가/갱신하고 실행한다.
6. 필요 시 앱 빌드 또는 통합 테스트를 실행한다.
7. 테스트 결과와 변경 모듈을 PR 설명에 명시한다.

### 2.4 Compound Engineering 피드백 루프
- 피드백은 commit 시점에 사후 기록하지 않는다. 피드백이 들어온 순간 `docs/agent/rework-metrics.md` 기준으로 branch metrics event를 만들고 Feedback Signal, Product/Engineering Impact, Root Cause Hypothesis, System Gap, Automation Hypothesis, Decision을 먼저 채운다.
- 사용자 피드백은 원문을 길게 옮기지 말고 핵심 신호만 요약한다. GitHub review, 리뷰봇, 다른 에이전트의 피드백은 thread/comment id와 짧은 요약을 남기고, 수정이나 정책 반영 여부를 반드시 추적한다.
- 수정 전에는 해당 event를 작업 입력으로 다시 읽고, 같은 문제가 생기지 않도록 테스트/문서/hook/CI/AGENTS 정책 중 어디에 학습을 넣을지 결정한다.
- 수정 후에는 event에 Fix Scope, Verification, Lesson을 채운다. 반복 가능성이 있는 Lesson은 이 `AGENTS.md` 또는 `docs/agent/*` 정책 문서로 승격한다.
- PR 전에는 `scripts/quality/rework-metrics-check.sh --local`을 실행해 외부 피드백과 문서가 어긋나지 않는지 확인한다.
- 주기적으로 `scripts/quality/rework-metrics-report.py`로 branch metrics를 요약하고, 반복되는 root cause나 system gap은 AGENTS 규칙/플레이북/자동화 후보로 반영한다.

### 2.5 컨텍스트 압축 대비
- 자동 컨텍스트 압축, 세션 재개, 긴 대기 작업에 대비해 다단계 작업은 `tmp/agent-context-<sanitized-branch>.md`에 임시 handoff를 남긴다.
- 이 파일에는 최신 사용자 명령, 변하지 말아야 할 요구사항, 현재 브랜치/PR, 주요 변경 파일, 검증 상태, 진행 중인 명령, 남은 다음 액션을 적는다.
- 사용자의 새 피드백이나 요구사항이 들어오면 해당 임시 파일도 함께 갱신한다.
- 비밀값, 토큰, 개인 인증 정보는 임시 파일에도 쓰지 않는다.
- 이 규칙에서 말하는 임시 산출물은 컨텍스트 압축 대비로 만든 `tmp/agent-context-*.md` 파일을 뜻한다.
- 사용자가 작업트리 정리나 임시 산출물 삭제를 요청하면 `tmp/agent-context-*.md`를 함께 정리한다.
- 임시 파일은 PR 산출물이 아니므로 commit하지 않는다. 공유가 필요한 내용은 `docs/agent/*`나 branch metrics 문서로 승격한다.

## 3. 전역 필수 정책

### 3.1 작업/브랜치
- 구현 작업은 전용 브랜치 + 별도 Git worktree에서 수행한다.
- 기본 체크아웃에서 직접 구현하지 않는다.
- `main`에 직접 push하지 않는다.
- push 시 반드시 PR을 생성한다.
- 새 작업은 최신 `origin/main` 기준에서 시작한다.

### 3.2 변경 범위/품질
- 변경은 최소 범위, 테스트 가능, 기능 중심으로 유지한다.
- 가능한 구현 작업은 실패하는 테스트를 먼저 추가/갱신한 뒤 통과시키는 TDD 흐름으로 진행한다.
- 모든 구현 변경은 같은 PR에 자동화 테스트(단위/통합)를 포함한다.
- 버그 수정 PR은 실패 시나리오를 고정하는 회귀 테스트를 포함해야 한다.
- 단위 테스트로 충분하지 않은 사용자 플로우 버그는 UI 테스트 또는 계측 테스트를 추가/갱신한다.
- non-view 레이어(Compose/View UI 제외) 커버리지 80% 이상을 목표로 한다.
- PR 커밋 메시지는 구조적이고 설명 가능하게 작성한다.
- use case/ViewModel 단위 테스트를 우선한다.
- 핵심 사용자 플로우에는 UI 테스트를 추가한다.
- Flow 테스트에는 Turbine, 공용 fake/rule은 `core:testing`을 재사용한다.
- 전체 테스트(`connectedDebugAndroidTest`, 전체 앱 빌드/통합 테스트 등 시간이 오래 걸리는 검증)는 PR 직전 최종 게이트에서만 실행한다. 구현 중에는 영향 모듈 단위 테스트, 린트, 필요한 단일/타깃 UI 테스트만 실행한다.

### 3.3 아키텍처/의존
- 모듈 경계와 의존 방향을 지킨다.
- `core:* -> feature:*` 의존을 추가하지 않는다.
- API 계약(`feature:*:api`)과 구현(`feature:*:impl`) 변경 범위를 분리한다.
- `feature:*:entry`에는 app 연결용 바인딩만 둔다.
- 앱 셸은 feature 구현 상세를 직접 참조하지 않는다.
- UI는 UDF(`UiState + ViewModel + Action/SideEffect`)를 우선한다.
- 사이드 이펙트는 컴포저블 내부가 아닌 ViewModel/use case 레이어에서 처리한다.

### 3.4 로직 책임
- 비즈니스 로직은 서비스가 제공해야 하는 의미, 가능/불가능 판단, 상태 전이, 정책 계산을 뜻한다.
- 애플리케이션 로직은 기능 수행 절차를 조율하는 로직이다. 예: repository에서 필요한 데이터를 가져오고, 현재 시간/사용자 조건을 준비한 뒤 도메인 규칙을 실행한다.
- 프레젠테이션 로직은 도메인/use case 결과를 `UiState`, `UiModel`, `SideEffect`로 바꾸는 로직이다. 예: `isCancelable`을 버튼 활성화, 안내 문구, 토스트 이벤트로 바꾼다.
- 데이터 로직은 DTO/DB Entity/DataStore 값을 읽고 쓰고 동기화하며 도메인 모델로 매핑하는 로직이다.
- 같은 use case 안에 애플리케이션 로직과 비즈니스 로직이 함께 있을 수 있다. 단, 화면 표현 문구/색상/컴포넌트 상태는 use case가 아니라 ViewModel/UI mapper에서 결정한다.
- Repository에는 데이터 접근/매핑/캐시/동기화 로직만 둔다. 취소 가능 여부, 배송 가능 여부, 권한, 할인, 상태 전이 같은 서비스 정책은 entity/use case/domain service에 둔다.
- Entity/domain model은 단순 데이터 컨테이너로 제한하지 않는다. 자기 필드만으로 판단 가능한 규칙은 계산 속성/메서드로 둘 수 있으며, 외부 I/O나 repository 호출은 하지 않는다.
- View/Composable은 `UiState`를 렌더링하고 사용자 이벤트를 전달한다. View에는 프레젠테이션 로직, 비즈니스 로직, 애플리케이션 로직을 추가하지 않는다.
- 화면 문구 선택, 버튼 활성화, 리스트 섹션 구성, 토스트/네비게이션 `SideEffect` 결정은 ViewModel/UI mapper에서 `UiState`로 미리 가공한다.

### 3.5 UI/리소스
- 사용자 노출 문자열은 `values`, `values-ko` 리소스로 관리한다.
- 최상위 탭은 구현된 feature route에만 매핑한다.
- 탭 아이콘은 텍스트 글리프 대체가 아닌 명시적 아이콘 에셋을 사용한다.
- 모든 뷰는 기능 단위로 분리하고, 화면 조립(`ui/screen`)과 하위 구성요소(`ui/components`)를 구분해 배치한다.

### 3.6 PR 설명
- 모든 PR 설명은 `.github/pull_request_template.md`를 따른다.
- 변경 모듈, 테스트 결과, 커버리지 영향, 리스크, 마이그레이션 여부를 명시한다.

## 4. 리뷰 기준

- PR 리뷰는 실제 동작 오류, 회귀 위험, 아키텍처/모듈 경계 위반, 테스트 누락을 우선한다.
- PR 리뷰 코멘트 대응 시 기본적으로 각 unresolved thread에 수정 내용 또는 판단 근거를 답글로 남기고, 처리가 끝난 thread는 resolved 처리한다.
- `core:* -> feature:*` 의존 추가, `feature:*:api`와 `feature:*:impl` 책임 혼합, 앱 셸에서 구현 상세 직접 참조는 P1로 지적한다.
- ViewModel/use case/domain/data 변경에 대응 단위 테스트가 없거나, 핵심 사용자 플로우 변경에 UI 테스트가 없으면 P1로 지적한다.
- 사용자 노출 문자열이 `values`, `values-ko` 리소스가 아닌 코드에 하드코딩되면 P1로 지적한다.
- Compose UI에서 사이드 이펙트가 ViewModel/use case 레이어가 아닌 컴포저블 내부에 구현되면 P1로 지적한다.
- 최상위 탭이 미구현 feature route에 연결되거나, 탭 아이콘이 명시적 아이콘 에셋이 아닌 텍스트 글리프로 대체되면 P1로 지적한다.
- Room 마이그레이션, DataStore 스키마, 네트워크 API 계약 변경은 하위 호환성/마이그레이션 검증 누락을 P1로 지적한다.
- 문서/주석/포맷 변경만 있는 경우에는 동작 리스크가 없으면 차단성 리뷰를 남기지 않는다.
- 스타일 취향보다 재현 가능한 버그, 정책 위반, 유지보수 비용 증가를 중심으로 코멘트한다.

## 5. 검증 명령어

- 앱 빌드: `./gradlew assembleDebug`
- 제품 하네스 구조 검사: `scripts/quality/product-harness-check.sh`
- 재작업 metrics 로컬 검사: `scripts/quality/rework-metrics-check.sh --local`
- 재작업 metrics PR 검사: `scripts/quality/rework-metrics-check.sh --pr <number> --repo <owner/name>`
- 단위 테스트: `./gradlew testDebugUnitTest`
- 계측 테스트: `./gradlew connectedDebugAndroidTest`
- 모듈 단위 테스트: `./gradlew :<module>:testDebugUnitTest`
- 모듈 린트: `./gradlew :<module>:lintDebug`
- 전체 lint: `./gradlew lint`
- 핵심 non-view coverage gate: `./gradlew coverageVerifyAll`

## 6. 문서 로드 규칙

1. 항상 이 루트 `AGENTS.md`를 먼저 읽는다.
2. 변경 대상 Gradle 모듈을 식별한다.
3. 대상 모듈의 `AGENTS.md`만 추가로 연다.
4. 다중 모듈 변경이면 변경되는 모듈 문서만 추가한다.
5. 구현/테스트/릴리즈 상세 절차가 필요할 때만 분리 문서를 연다.
6. 작업과 무관한 모듈 문서는 열지 않는다.

상세 참고 문서:
- 인덱싱/로드 규칙: `docs/agent/indexing.md`
- 자동화 하네스: `docs/agent/automation-harness.md`
- AI 재작업 측정: `docs/agent/rework-metrics.md`
- 테스트/커버리지/검증 커맨드: `docs/agent/quality-gates.md`
- TDD Guard Hook: `docs/agent/tdd-guard.md`
- 구현 플레이북: `docs/agent/playbooks/feature-impl.md`
- UI/Compose 플레이북: `docs/agent/playbooks/ui-compose.md`
- Data/Domain/Network 플레이북: `docs/agent/playbooks/data-layer.md`

## 7. 기술 기준선

- Kotlin + Coroutines
- Jetpack Compose + Material 3
- Hilt DI
- Navigation Compose
- Room + DataStore
- Retrofit + OkHttp
- WorkManager
- Firebase Messaging
- Glance Widget

## 8. 모듈 인덱스

| Gradle 모듈 | 가이드 경로 | 책임 |
|---|---|---|
| `:app` | `app/AGENTS.md` | 앱 셸, 시작 구성, 최상위 연결 |
| `:core:model` | `core/model/AGENTS.md` | 순수 도메인/데이터 모델 |
| `:core:domain` | `core/domain/AGENTS.md` | 유스케이스 + 리포지토리 계약 |
| `:core:data` | `core/data/AGENTS.md` | 리포지토리 구현 + 매퍼 |
| `:core:database` | `core/database/AGENTS.md` | Room DB/Entity/DAO/마이그레이션 |
| `:core:datastore` | `core/datastore/AGENTS.md` | 환경설정 데이터 소스 레이어 |
| `:core:network` | `core/network/AGENTS.md` | 원격 API/네트워킹 계약 |
| `:core:ui` | `core/ui/AGENTS.md` | 재사용 UI 프리미티브 |
| `:core:designsystem` | `core/designsystem/AGENTS.md` | 테마/타이포/디자인 토큰 |
| `:core:testing` | `core/testing/AGENTS.md` | 공용 테스트 fake/rule/helper |
| `:feature:auth:api` | `feature/auth/api/AGENTS.md` | Auth 공개 계약/라우트 |
| `:feature:auth:impl` | `feature/auth/impl/AGENTS.md` | Auth UI + ViewModel + 플랫폼 인증 연동 |
| `:feature:auth:entry` | `feature/auth/entry/AGENTS.md` | 앱 연결용 auth 진입 바인딩 |
| `:feature:todo:api` | `feature/todo/api/AGENTS.md` | Todo 공개 계약/라우트 |
| `:feature:todo:impl` | `feature/todo/impl/AGENTS.md` | Todo UI + ViewModel + 기능 로직 |
| `:feature:todo:entry` | `feature/todo/entry/AGENTS.md` | 앱 연결용 todo 진입 바인딩 |
| `:feature:friends:api` | `feature/friends/api/AGENTS.md` | Friends 공개 계약/라우트 |
| `:feature:friends:impl` | `feature/friends/impl/AGENTS.md` | Friends UI + ViewModel + 기능 로직 |
| `:feature:friends:entry` | `feature/friends/entry/AGENTS.md` | 앱 연결용 friends 진입 바인딩 |
| `:feature:calendar:api` | `feature/calendar/api/AGENTS.md` | Calendar 공개 계약/라우트 |
| `:feature:calendar:impl` | `feature/calendar/impl/AGENTS.md` | Calendar UI + 기능 로직 |
| `:feature:calendar:entry` | `feature/calendar/entry/AGENTS.md` | 앱 연결용 calendar 진입 바인딩 |
| `:feature:calendar:widget` | `feature/calendar/widget/AGENTS.md` | Glance 기반 Calendar 홈 화면 위젯 |

## 9. 현재 의존 구조

- `app -> feature:*:api, feature:*:entry, core:*`
- `feature:*:entry -> feature:*:api, feature:*:impl`
- `feature:*:impl -> feature:*:api, core:*`
- `feature:calendar:widget -> feature:calendar:api, core:domain, core:model`
- `core:data -> core:domain + core:database/core:datastore/core:network`
- `core:*`는 `feature:*`에 의존하지 않음
