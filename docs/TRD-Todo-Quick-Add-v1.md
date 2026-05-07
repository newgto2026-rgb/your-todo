# TRD - Todo 빠른 추가 v1

## 1. 문서 정보
- 문서명: 기술 요구사항 문서 - Todo 빠른 추가 v1
- 기준 기획: planner-agent 논의 기반 빠른 추가/상세 추가 분리안
- 대상 프로젝트: `MyFirstApp` (Kotlin, Compose, Room, Hilt, Navigation 3, UDF)
- 작성일: 2026-05-07
- 범위: Todo 목록 화면에서 제목만으로 Todo를 즉시 추가하는 빠른 입력 UX

## 2. 목표 및 범위

### 2.1 목표
- 사용자가 별도 추가 화면을 열지 않고 Todo 제목만 입력해 즉시 저장할 수 있게 한다.
- 기존 상세 추가 바텀시트는 기본 생성 경로로 유지한다.
- 빠른 추가는 현재 보고 있는 리스트 안의 맥락형 보조 액션으로 제공한다.
- `Today` 탭에서는 현재 화면 맥락에 맞게 오늘 할 일로 빠르게 추가한다.
- `All` 탭에서는 날짜 없는 일반 Todo를 빠르게 추가한다.
- 우선순위 필터가 켜진 상태에서도 저장 직후 Todo가 현재 목록에서 사라지지 않게 한다.
- 연속 입력이 끊기지 않도록 저장 후 빠른 입력 슬롯을 유지한다.

### 2.2 비목표
- Calendar 탭 빠른 추가
- Calendar 선택 날짜 기반 빠른 추가
- 빠른 추가 슬롯 내 날짜, 시간, 우선순위, 리마인더 편집
- Inbox 탭 또는 날짜 없는 Todo 전용 수집함 추가
- FAB long press 같은 숨은 상세 추가 진입
- 입력값이 있는 상태에서 닫을 때 확인 다이얼로그
- 테스트 DB 격리 정책 변경

## 3. 제품 동작 정책

### 3.1 추가 경로 분리
| 화면 | FAB 동작 | 빠른 추가 동작 | 저장 기본값 |
|---|---|---|---|
| `All` | 상세 추가 바텀시트 오픈 | 리스트 상단 버튼으로 빠른 추가 슬롯 오픈 | `dueDate = null` |
| `Today` | 상세 추가 바텀시트 오픈, 오늘 날짜 prefill | 리스트 상단 버튼으로 빠른 추가 슬롯 오픈 | `dueDate = LocalDate.now()` |
| `Completed` | 상세 추가 바텀시트 오픈 | 미노출 | 저장 없음 |

핵심 정책은 다음과 같다.

> FAB는 늘 완전한 할 일 추가를 연다. 빠른 추가는 리스트 맥락 안에서만 제공한다.

### 3.2 저장 후 동작
- 저장 성공 시 입력값을 비운다.
- 빠른 추가 슬롯은 열린 상태로 유지한다.
- 입력 포커스는 빠른 추가 제목 입력에 유지한다.
- 저장 성공 snackbar는 기본적으로 표시하지 않는다. 화면에 새 Todo가 반영되는 것을 성공 피드백으로 사용한다.

### 3.3 빈 입력 처리
- 빈 문자열 또는 공백만 있는 제목은 저장하지 않는다.
- 입력 슬롯은 닫지 않는다.
- 기존 제목 필수 오류 리소스를 재사용한다.

### 3.4 Completed 탭 정책
- 완료 목록은 "새 완료 항목을 빠르게 추가"하는 맥락이 약하므로 빠른 추가 버튼과 슬롯을 노출하지 않는다.
- FAB는 상세 추가 바텀시트 진입으로 유지한다.
- ViewModel은 방어적으로 `Completed` 상태의 빠른 추가 요청을 저장 없이 snackbar로 처리한다.

## 4. 아키텍처 반영 계획

### 4.1 모듈 변경 범위
- `feature:todo:impl`
  - 빠른 추가 UiState, Action, ViewModel 처리
  - Todo 목록 화면 빠른 추가 버튼/슬롯 UI
  - 문자열 리소스 `values`, `values-ko`
  - ViewModel 단위 테스트
- `app`
  - 핵심 사용자 플로우 UI 테스트 수정 및 추가
- `core:ui`
  - 불필요한 앱 전역 Todo 탭 이동 액션을 추가하지 않는다.
- 변경 없음
  - `core:domain`: 기존 `AddTodoUseCase` 재사용
  - `core:data`, `core:database`: 저장 계약 변경 없음
  - `feature:todo:api`: 기존 상세 추가 route 유지

### 4.2 레이어 원칙
- 빠른 추가 저장 절차와 탭별 기본 날짜 결정은 `TodoListViewModel`에서 처리한다.
- FAB 상세 추가 route 결정은 화면 route 콜백으로 연결한다.
- Todo 생성 유효성의 최종 방어선은 기존 `AddTodoUseCase`를 유지한다.
- Composable은 `UiState`를 렌더링하고 사용자 이벤트만 `Action`으로 전달한다.
- 사용자 노출 문자열은 코드에 하드코딩하지 않는다.
- `core:* -> feature:*` 의존은 추가하지 않는다.

## 5. UDF 설계

### 5.1 UiState 추가
```kotlin
data class TodoListUiState(
    ...,
    val isQuickAddVisible: Boolean = false,
    val quickAddTitle: String = "",
    @StringRes val quickAddErrorMessageRes: Int? = null
)
```

`isQuickAddVisible`은 현재 탭 화면의 빠른 입력 슬롯 노출 여부를 나타낸다.

`quickAddTitle`은 빠른 추가 제목 입력값이다.

`quickAddErrorMessageRes`는 빠른 추가 입력 오류를 표현한다. 편집 바텀시트 draft 오류와 책임이 섞이지 않게 별도 필드를 둔다.

### 5.2 Action 추가
```kotlin
sealed interface TodoListAction {
    ...
    data object OnQuickAddClick : TodoListAction
    data class OnQuickAddTitleChange(val value: String) : TodoListAction
    data object OnQuickAddSubmit : TodoListAction
    data object OnQuickAddDismiss : TodoListAction
}
```

`OnQuickAddClick`은 `All`, `Today`에서 빠른 입력 슬롯을 연다.

`OnQuickAddSubmit`은 현재 route filter와 priority filter를 반영해 `AddTodoUseCase`를 호출한다.

`OnQuickAddDismiss`는 빠른 입력 draft와 오류 상태를 초기화한다.

### 5.3 SideEffect
기존 snackbar side effect를 재사용한다. 빠른 추가에서 별도 상세 추가 navigation side effect는 만들지 않는다. 상세 추가는 FAB가 담당한다.

Undo snackbar와 충돌하지 않도록 snackbar action enum은 기존 undo action만 유지한다.

## 6. ViewModel 처리 정책

### 6.1 빠른 추가 오픈
- `TodoFilter.ALL`, `TodoFilter.TODAY`
  - `isQuickAddVisible = true`
  - `quickAddErrorMessageRes = null`
- `TodoFilter.COMPLETED`
  - 빠른 입력 상태를 변경하지 않는다.
  - `ShowSnackbar(todo_quick_add_completed_unavailable)`를 emit한다.

### 6.2 빠른 추가 저장
- `quickAddTitle.trim()`이 빈 값이면 저장하지 않고 오류 상태를 설정한다.
- 기본값:
  - `selectedFilter == TodoFilter.TODAY`: `dueDate = LocalDate.now()`
  - 그 외: `dueDate = null`
  - `categoryId = null`
  - `dueTimeMinutes = null`
  - `reminderAtEpochMillis = null`
  - `isReminderEnabled = false`
  - `reminderRepeatType = ReminderRepeatType.NONE`
  - `reminderRepeatDaysMask = 0`
  - `reminderLeadMinutes = null`
  - `priority = selectedPriorityFilter`가 특정 우선순위이면 해당 우선순위, `ALL`이면 `TodoPriority.MEDIUM`
- `AddTodoUseCase` 성공 시 `quickAddTitle = ""`, `quickAddErrorMessageRes = null`, `isQuickAddVisible = true`를 유지한다.
- 실패 시 기존 저장 실패 snackbar를 emit한다.

### 6.3 상세 추가 진입
- FAB 클릭은 ViewModel 빠른 추가 액션으로 보내지 않는다.
- `TodoFilter.TODAY`: `onAddRequested(LocalDate.now())`
- `TodoFilter.ALL`, `TodoFilter.COMPLETED`: `onAddRequested(null)`

### 6.4 닫기와 Back 처리
- 닫기 버튼 또는 Back 동작은 `OnQuickAddDismiss`로 전달한다.
- 1차에서는 입력값이 있어도 확인 다이얼로그 없이 닫는다.
- 닫기 시 `quickAddTitle = ""`, `quickAddErrorMessageRes = null`, `isQuickAddVisible = false`로 초기화한다.

## 7. UI 설계

### 7.1 빠른 추가 위치
- Header, 요약, 우선순위 필터 아래에 배치한다.
- 닫힌 상태에서는 `빠른 추가` 버튼을 노출한다.
- 버튼 클릭 시 같은 위치에서 빠른 추가 슬롯으로 전환한다.
- `Completed` 탭에서는 버튼과 슬롯을 모두 노출하지 않는다.

### 7.2 슬롯 구성
- 제목 입력 필드
- 추가 아이콘 버튼
- 닫기 아이콘 버튼

상세 추가 텍스트 버튼은 슬롯 안에 두지 않는다. 상세 추가는 FAB의 책임이다.

### 7.3 접근성 및 테스트 태그
- 빠른 추가 오픈 버튼: `quick_add_open`
- 빠른 추가 컨테이너: `quick_add_slot`
- 제목 입력: `quick_add_title_input`
- 추가 버튼: `quick_add_submit`
- 닫기 버튼: `quick_add_close`
- 상세 추가 FAB: `add_fab`

### 7.4 키보드 동작
- 빠른 추가 슬롯이 열린 뒤 제목 입력에 자동 포커스한다.
- IME action은 Done으로 설정한다.
- IME Done은 `OnQuickAddSubmit`을 호출한다.

## 8. 문자열 리소스

### 8.1 `values/strings.xml`
- `todo_quick_add_open`: `Quick add`
- `todo_quick_add_placeholder_all`: `Add a new task`
- `todo_quick_add_placeholder_today`: `Add a task for today`
- `todo_quick_add_completed_unavailable`: `New tasks can be added from All.`

### 8.2 `values-ko/strings.xml`
- `todo_quick_add_open`: `빠른 추가`
- `todo_quick_add_placeholder_all`: `새 할 일 추가`
- `todo_quick_add_placeholder_today`: `오늘 할 일 추가`
- `todo_quick_add_completed_unavailable`: `새 할 일은 전체 목록에서 추가할 수 있어요.`

## 9. 테스트 계획

### 9.1 ViewModel 단위 테스트
- 기존 `OnAddClick`은 상세 추가 editor state를 연다.
- `All`에서 빠른 추가 저장 시 `dueDate = null` Todo가 생성된다.
- `Today`에서 빠른 추가 저장 시 `dueDate = LocalDate.now()` Todo가 생성된다.
- 특정 우선순위 필터에서 빠른 추가 저장 시 해당 우선순위로 생성되어 현재 목록에 남는다.
- 저장 성공 후 `quickAddTitle`이 비워지고 `isQuickAddVisible`은 유지된다.
- 빈 제목과 공백 제목은 저장되지 않고 오류 상태가 설정된다.
- 빠른 추가 닫기 시 입력값과 오류 상태가 초기화된다.
- `Completed`에서 빠른 추가 요청 시 저장하지 않고 snackbar side effect를 emit한다.

### 9.2 UI 테스트
- `All` 탭에서 빠른 추가 버튼 클릭 후 빠른 추가 슬롯이 표시된다.
- 빠른 추가 제목 입력 후 추가 버튼 클릭 시 Todo가 목록에 표시된다.
- `Today` 탭에서 빠른 추가한 Todo가 Today 목록에 표시된다.
- `Completed` 탭에서는 빠른 추가 버튼과 슬롯이 표시되지 않는다.
- FAB 클릭 시 기존 상세 추가 바텀시트가 열린다.

### 9.3 실제 DB 보존 확인
테스트 DB 격리 정책 변경 대신 실제 앱 데이터 보존 여부를 아래 절차로 검증한다.

1. Debug 앱을 실행한다.
2. 실제 Todo 데이터를 하나 추가한다. 예: `DB persistence sentinel`.
3. 앱을 종료한다.
4. 단위 테스트와 필요한 UI 테스트를 실행한다.
5. 앱을 다시 실행한다.
6. 테스트 전에 추가한 Todo가 그대로 남아 있는지 확인한다.

판정:
- Todo가 남아 있으면 현재 테스트 격리는 실제 앱 DB를 지우지 않는 것으로 본다.
- Todo가 사라지면 빠른 추가 구현 범위가 아니라 앱 재설치, 데이터 클리어, 테스트 디바이스 분리 정책을 별도 이슈로 점검한다.

### 9.4 계측 테스트 후 APK 보존 설정
`connectedDebugAndroidTest`가 테스트 후 대상 APK를 제거하면 실제 앱 데이터도 함께 사라질 수 있다. 실제 앱 데이터 보존 검증을 위해 프로젝트 Gradle properties에 아래 설정을 둔다.

```properties
android.injected.androidTest.leaveApksInstalledAfterRun=true
```

## 10. 검증 명령어
- `./gradlew :feature:todo:impl:testDebugUnitTest`
- `./gradlew :feature:todo:impl:jacocoCoverageVerification`
- `./gradlew :feature:todo:impl:lintDebug`
- `./gradlew :core:ui:lintDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
- `./gradlew :app:connectedDebugAndroidTest`
- `git diff --check`
