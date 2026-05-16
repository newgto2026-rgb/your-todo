# 프로젝트 에이전트 가이드 (인덱스)

## 목적
- 이 문서는 에이전트 작업의 단일 진입점이다.
- 코드 변경 시 필요한 필수 규칙과 체크리스트를 이 문서에서 바로 확인한다.

## 코드 변경 시작 전 필수 체크
1. 현재 작업 경로가 기본 체크아웃이 아닌지 확인한다.
2. 전용 브랜치 + 별도 Git worktree에서 작업한다.
3. 변경 대상 Gradle 모듈을 식별하고 대상 모듈 `AGENTS.md`를 연다.
4. 모듈 경계/의존 방향 위반 가능성을 먼저 점검한다.

## 코드 변경 완료 전 필수 체크
1. 영향 모듈 단위 테스트를 실행한다.
2. 영향 모듈 린트를 실행한다.
3. 버그 수정은 해당 버그를 재현하는 회귀 테스트를 반드시 추가/갱신하고 실행한다.
4. 필요 시 앱 빌드 또는 통합 테스트를 실행한다.
5. 테스트 결과와 변경 모듈을 PR 설명에 명시한다.

## 전역 필수 정책
### 작업/브랜치 정책
- 구현 작업은 전용 브랜치 + 별도 Git worktree에서 수행한다.
- 기본 체크아웃에서 직접 구현하지 않는다.
- `main`에 직접 푸시하지 않는다.
- 푸시 시 반드시 PR을 생성한다.

### 변경 범위/품질 정책
- 변경은 최소 범위, 테스트 가능, 기능 중심으로 유지한다.
- 가능한 구현 작업은 실패하는 테스트를 먼저 추가/갱신한 뒤 통과시키는 TDD 흐름으로 진행한다.
- 모든 구현 변경은 같은 PR에 자동화 테스트(단위/통합)를 포함한다.
- 버그 수정 PR은 실패 시나리오를 고정하는 회귀 테스트를 포함해야 한다. 단위 테스트로 충분하지 않은 사용자 플로우 버그는 UI 테스트 또는 계측 테스트를 추가/갱신한다.
- non-view 레이어(Compose/View UI 제외) 커버리지 80% 이상을 목표로 한다.
- PR 커밋 메시지는 구조적이고 설명 가능하게 작성한다.
- use case/ViewModel 단위 테스트를 우선한다.
- 핵심 사용자 플로우에는 UI 테스트를 추가한다.
- Flow 테스트에는 Turbine, 공용 fake/rule은 `core:testing`을 재사용한다.
- 전체 테스트(`connectedDebugAndroidTest`, 전체 앱 빌드/통합 테스트 등 시간이 오래 걸리는 검증)는 PR 직전 최종 게이트에서만 실행한다. 구현 중에는 영향 모듈 단위 테스트, 린트, 필요한 단일/타깃 UI 테스트만 실행한다.

### 아키텍처/의존 정책
- 모듈 경계와 의존 방향을 지킨다.
- `core:* -> feature:*` 의존을 추가하지 않는다.
- API 계약(`feature:*:api`)과 구현(`feature:*:impl`) 변경 범위를 분리한다.
- UI는 UDF(`UiState + ViewModel + Action/SideEffect`)를 우선한다.
- 사이드 이펙트는 컴포저블 내부가 아닌 ViewModel/use case 레이어에서 처리한다.

### 로직 책임 용어
- 비즈니스 로직은 서비스가 제공해야 하는 의미, 가능/불가능 판단, 상태 전이, 정책 계산을 뜻한다.
- 애플리케이션 로직은 기능 수행 절차를 조율하는 로직이다. 예: repository에서 필요한 데이터를 가져오고, 현재 시간/사용자 조건을 준비한 뒤 도메인 규칙을 실행한다.
- 프레젠테이션 로직은 도메인/use case 결과를 `UiState`, `UiModel`, `SideEffect`로 바꾸는 로직이다. 예: `isCancelable`을 버튼 활성화, 안내 문구, 토스트 이벤트로 바꾼다.
- 데이터 로직은 DTO/DB Entity/DataStore 값을 읽고 쓰고 동기화하며 도메인 모델로 매핑하는 로직이다.
- 같은 use case 안에 애플리케이션 로직과 비즈니스 로직이 함께 있을 수 있다. 단, 화면 표현 문구/색상/컴포넌트 상태는 use case가 아니라 ViewModel/UI mapper에서 결정한다.
- Repository에는 데이터 접근/매핑/캐시/동기화 로직만 둔다. 취소 가능 여부, 배송 가능 여부, 권한, 할인, 상태 전이 같은 서비스 정책은 entity/use case/domain service에 둔다.
- Entity/domain model은 단순 데이터 컨테이너로 제한하지 않는다. 자기 필드만으로 판단 가능한 규칙은 계산 속성/메서드로 둘 수 있으며, 외부 I/O나 repository 호출은 하지 않는다.
- View/Composable은 `UiState`를 렌더링하고 사용자 이벤트를 전달한다. View에는 프레젠테이션 로직, 비즈니스 로직, 애플리케이션 로직을 추가하지 않는다.
- 화면 문구 선택, 버튼 활성화, 리스트 섹션 구성, 토스트/네비게이션 `SideEffect` 결정은 ViewModel/UI mapper에서 `UiState`로 미리 가공한다.

### UI/리소스 정책
- 사용자 노출 문자열은 `values`, `values-ko` 리소스로 관리한다.
- 최상위 탭은 구현된 feature 라우트에만 매핑한다.
- 탭 아이콘은 텍스트 글리프 대체가 아닌 명시적 아이콘 에셋을 사용한다.
- 모든 뷰는 기능 단위로 분리하고, 화면 조립(`ui/screen`)과 하위 구성요소(`ui/components`)를 구분해 배치한다.

### PR 설명 정책
- 모든 PR 설명은 `.github/pull_request_template.md`를 따른다.

## Review guidelines
- PR 리뷰는 실제 동작 오류, 회귀 위험, 아키텍처/모듈 경계 위반, 테스트 누락을 우선한다.
- PR 리뷰 코멘트 대응 시 기본적으로 각 unresolved thread에 수정 내용 또는 판단 근거를 답글로 남기고, 처리가 끝난 thread는 resolved 처리한다.
- `core:* -> feature:*` 의존 추가, `feature:*:api`와 `feature:*:impl` 책임 혼합, 앱 셸에서 구현 상세 직접 참조는 P1로 지적한다.
- ViewModel/use case/domain/data 변경에 대응 단위 테스트가 없거나, 핵심 사용자 플로우 변경에 UI 테스트가 없으면 P1로 지적한다.
- 사용자 노출 문자열이 `values`, `values-ko` 리소스가 아닌 코드에 하드코딩되면 P1로 지적한다.
- Compose UI에서 사이드 이펙트가 ViewModel/use case 레이어가 아닌 컴포저블 내부에 구현되면 P1로 지적한다.
- 최상위 탭이 미구현 feature 라우트에 연결되거나, 탭 아이콘이 명시적 아이콘 에셋이 아닌 텍스트 글리프로 대체되면 P1로 지적한다.
- Room 마이그레이션, DataStore 스키마, 네트워크 API 계약 변경은 하위 호환성/마이그레이션 검증 누락을 P1로 지적한다.
- 문서/주석/포맷 변경만 있는 경우에는 동작 리스크가 없으면 차단성 리뷰를 남기지 않는다.
- 스타일 취향보다 재현 가능한 버그, 정책 위반, 유지보수 비용 증가를 중심으로 코멘트한다.

## 기본 검증 명령어
- 앱 빌드: `./gradlew assembleDebug`
- 단위 테스트: `./gradlew testDebugUnitTest`
- 계측 테스트: `./gradlew connectedDebugAndroidTest`
- 모듈 단위 테스트: `./gradlew :<module>:testDebugUnitTest`
- 모듈 린트: `./gradlew :<module>:lintDebug`

## 빠른 사용 순서
1. 이 루트 가이드를 읽는다.
2. 대상 Gradle 모듈을 식별한다.
3. 대상 모듈의 `AGENTS.md`만 연다.
4. 루트의 `코드 변경 시작 전 필수 체크`를 통과한 뒤 작업을 시작한다.
5. 루트의 `코드 변경 완료 전 필수 체크`를 통과한 뒤 작업을 종료한다.

## 분리 문서
- 인덱싱/로드 규칙: `docs/agent/indexing.md`
- 테스트/커버리지/검증 커맨드: `docs/agent/quality-gates.md`
- 구현 플레이북: `docs/agent/playbooks/feature-impl.md`
- UI/Compose 플레이북: `docs/agent/playbooks/ui-compose.md`
- Data/Domain/Network 플레이북: `docs/agent/playbooks/data-layer.md`
- 위 문서들은 상세 참고용이다. 실행 필수 항목은 이 루트 문서를 기준으로 판단한다.

## 기술 기준선
- Kotlin + Coroutines
- Jetpack Compose + Material 3
- Hilt DI
- Navigation Compose
- Room + DataStore
- Retrofit + OkHttp
- WorkManager

## 모듈 인덱스
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
| `:feature:todo:api` | `feature/todo/api/AGENTS.md` | Todo 공개 계약/라우트 |
| `:feature:todo:impl` | `feature/todo/impl/AGENTS.md` | Todo UI + ViewModel + 기능 로직 |
| `:feature:todo:entry` | `feature/todo/entry/AGENTS.md` | 앱 연결용 todo 진입 바인딩 |
| `:feature:calendar:api` | `feature/calendar/api/AGENTS.md` | Calendar 공개 계약/라우트 |
| `:feature:calendar:impl` | `feature/calendar/impl/AGENTS.md` | Calendar UI + 기능 로직 |
| `:feature:calendar:entry` | `feature/calendar/entry/AGENTS.md` | 앱 연결용 calendar 진입 바인딩 |

## 현재 의존 구조
- `app -> feature:*:api, feature:*:entry, core:*`
- `feature:*:entry -> feature:*:api, feature:*:impl`
- `feature:*:impl -> feature:*:api, core:*`
- `core:data -> core:domain + storage modules`
- `core:*`는 `feature:*`에 의존하지 않음
