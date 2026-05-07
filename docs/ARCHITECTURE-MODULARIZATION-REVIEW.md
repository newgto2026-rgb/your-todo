# 아키텍처 / 모듈화 리뷰

## 일자
- 2026-04-12

## 범위
- `app`, `core:*`, `feature:*`의 현재 모듈화 품질을 점검한다.
- 공백(gap)을 식별하고 개선 우선순위를 제안한다.
- 우선순위 1~4를 단계별 브랜치/PR로 실행한다.

## 현재 강점
- `feature:todo:api` + `feature:todo:impl` 분리가 이미 존재한다.
- 화면 아키텍처가 UDF 스타일(`UiState`, `Action`, `SideEffect`, `ViewModel`)을 따른다.
- Compose UI가 작은 컴포넌트(헤더/필터/바텀시트/리스트 행)로 분해되어 있다.

## 주요 공백
1. `feature:todo:api`가 typealias 중심이어서 명시적 기능 계약이 약했다.
2. `app`이 `feature:todo:impl`에 직접 의존했다.
3. 앱 시작 목적지 로직에 기능 전용 라우트 상수가 포함되어 있었다.
4. 내비게이션 라우트 상수가 app과 feature impl에 중복되어 있었다.
5. 많은 UI 문자열이 리소스가 아닌 Kotlin 코드에 하드코딩되어 있었다.
6. `TodoRepository`가 여러 관심사(todo/category/filter preference)를 동시에 소유했다.
7. 리마인더 도메인이 독립 모델과 todo 내장 필드로 이원화되어 있었다.

## 우선순위 계획
### P1. 기능 API 계약 강화
- `feature:todo:api`의 typealias를 명시적 `TodoFeatureEntry` 계약 인터페이스로 교체한다.
- Todo 라우트 상수를 API 계약에 중앙화한다.

### P2. App -> Feature Impl 직접 결합 축소
- `feature:todo:entry` 모듈을 도입해 impl과 app 진입점 set 연결을 담당하게 한다.
- Hilt 멀티바인딩 모듈을 impl에서 entry로 이동한다.
- app 의존을 `:feature:todo:impl`에서 `:feature:todo:entry`로 전환한다.
- app의 하드코딩 todo 라우트를 제거하고 entry 계약 플래그로 시작 목적지를 선택한다.

## 실행된 변경 사항 (P1~P2)
- 명시적 API 계약 추가:
  - `feature/todo/api/src/main/java/com/neo/yourtodo/feature/todo/api/TodoFeatureEntry.kt`
- 앱 기능 계약에 범용 시작 목적지 플래그 추가:
  - `core/ui/src/main/java/com/neo/yourtodo/core/ui/navigation/AppFeatureEntry.kt`
- Todo 기능 진입 구현을 API 계약 + 시작 목적지 플래그 기반으로 업데이트:
  - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/navigation/TodoFeatureEntryImpl.kt`
- 신규 연결 모듈 추가:
  - `feature/todo/entry/build.gradle.kts`
  - `feature/todo/entry/src/main/AndroidManifest.xml`
  - `feature/todo/entry/src/main/java/com/neo/yourtodo/feature/todo/entry/di/TodoFeatureEntryModule.kt`
- 기존 impl 내부 바인딩 모듈 제거:
  - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/di/TodoFeatureModule.kt`
- 모듈 그래프 업데이트:
  - `settings.gradle.kts`
  - `app/build.gradle.kts`
- app 하드코딩 라우트 의존 제거:
  - `app/src/main/java/com/neo/yourtodo/app/AppNavHost.kt`

## 실행된 변경 사항 (P3~P4)
- P3. 사용자 노출 문자열 리소스 이관
  - todo 기능 문자열 리소스 추가:
    - `feature/todo/impl/src/main/res/values/strings.xml`
  - 하드코딩 UI 라벨/메시지를 다음 파일에서 리소스로 전환:
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoListScreen.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoFilterBar.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/components/TodoPriorityFilterBar.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/editor/TodoEditorPrioritySection.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoEditorReminderSection.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoEditBottomSheet.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoHeader.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoEmptyState.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/CategoryManagerBottomSheet.kt`
  - validation/snackbar 메시지를 raw 문자열에서 `@StringRes` id 기반으로 전환:
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoListInputValidator.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoListSideEffect.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoListUiState.kt`
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/TodoListViewModel.kt`
    - `feature/todo/impl/src/test/java/com/neo/yourtodo/feature/todo/impl/ui/TodoListViewModelTest.kt`
  - 앱 레벨 리마인더 알림 제목 리소스 추가:
    - `app/src/main/res/values/strings.xml`
    - `app/src/main/java/com/neo/yourtodo/app/todo/reminder/TodoReminderNotificationHelper.kt`

- P4. Todo 기능 진입에 타입 안전 내비게이션 적용
  - 직렬화 가능한 typed route 계약 추가:
    - `feature/todo/api/src/main/java/com/neo/yourtodo/feature/todo/api/TodoFeatureEntry.kt`
  - Todo 기능 등록을 `composable<TodoRoute>`로 전환:
    - `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/navigation/TodoFeatureEntryImpl.kt`
  - 라우트 계약 모듈에 Kotlin serialization 플러그인/의존 추가:
    - `feature/todo/api/build.gradle.kts`
    - `gradle/libs.versions.toml` (`kotlinx.serialization` 버전을 Kotlin 2.0.21과 정렬)

## 검증
- 단위 테스트:
  - `./gradlew :feature:todo:impl:testDebugUnitTest` (PASS)
- 린트:
  - `./gradlew :feature:todo:api:lintDebug :feature:todo:impl:lintDebug :app:lintDebug` (PASS)

## 실행된 변경 사항 (P5~P6)
- P5. 책임 단위 리포지토리 계약 분리
  - 단일 `TodoRepository`를 관심사별 도메인 계약으로 분리:
    - `TodoItemRepository`
    - `TodoCategoryRepository`
    - `TodoFilterRepository`
    - `TodoReminderRepository`
  - todo/category/filter 관련 유스케이스를 새로운 분리 계약 주입 구조로 업데이트.
  - 데이터 바인딩 모듈에서 하나의 구현(`TodoRepositoryImpl`)을 여러 분리 계약에 바인딩.
  - 테스트 fake 및 기능 테스트를 분리 계약 기준으로 업데이트.

- P6. Todo 리마인더 도메인 방향 정리
  - todo 내장 리마인더 접근 경로를 전용 계약/유스케이스로 명확화:
    - `TodoReminderRepository`
    - `GetActiveTodoRemindersUseCase` (`GetTodosWithActiveReminderUseCase`에서 이름 변경)
  - 앱 스케줄러 연결을 `GetActiveTodoRemindersUseCase`로 업데이트.
  - 결과: 독립 리마인더 도메인(`ReminderRepository`)은 범용 리마인더용으로 유지하고, todo 리마인더 스케줄링은 명시적 todo 리마인더 계약에서 조회한다.

## 검증 (P5~P6)
- 단위 테스트:
  - `./gradlew :core:domain:test` (PASS)
  - `./gradlew :core:data:testDebugUnitTest` (PASS)
  - `./gradlew :feature:todo:impl:testDebugUnitTest` (PASS)
- 빌드:
  - `./gradlew :app:assembleDebug` (PASS)
