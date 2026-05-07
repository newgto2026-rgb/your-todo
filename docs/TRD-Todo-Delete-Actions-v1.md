# TRD - Todo 삭제 및 완료 항목 정리 v1

## 1. 문서 정보
- 문서명: 기술 요구사항 문서 - Todo 삭제 및 완료 항목 정리 v1
- 기준 PRD: Todo 삭제 및 완료 항목 정리 1차 구현
- 대상 프로젝트: `YourTodo` (Kotlin, Compose, Room, Hilt, UDF)
- 작성일: 2026-05-06
- 범위: 로컬 Todo 개별 삭제, 완료 항목 전체 정리, 삭제 확인 UX

## 2. 목표 및 범위

### 2.1 목표
- Todo 리스트 row에서 삭제 진입점을 제공한다.
- 편집 바텀시트 삭제와 리스트 삭제가 동일한 확인 흐름을 사용한다.
- 완료 탭에서 완료된 Todo 전체를 정리할 수 있다.
- 삭제 시 기존 `DeleteTodoUseCase` 경로를 사용해 리마인더 취소를 누락하지 않는다.

### 2.2 비목표
- 삭제 undo
- 휴지통/복구함
- soft delete 데이터 모델
- Room bulk delete 쿼리
- domain/data/database 계약 확장

## 3. 아키텍처 반영 계획

### 3.1 모듈 변경 범위
- `feature:todo:impl`
  - 삭제 확인 상태, 액션, ViewModel 처리
  - 리스트 row 스와이프 삭제 wrapper
  - row overflow 삭제 메뉴
  - 완료 항목 전체 정리 액션
  - 문자열 리소스 및 ViewModel 테스트
- `core:ui`
  - `TodoItemRow`에 삭제 정책과 무관한 범용 trailing content slot만 추가

### 3.2 레이어 원칙
- 삭제 가능 여부, 삭제 확인 대상, 완료 전체 삭제 대상 id 계산은 `feature:todo:impl`의 UDF 상태에서 관리한다.
- `core:ui`는 삭제/완료 정책을 알지 않는다.
- `core:data`와 `core:database`는 1차에서 변경하지 않는다.
- `core:* -> feature:*` 의존은 추가하지 않는다.

## 4. UDF 설계

### 4.1 UiState 추가
```kotlin
data class TodoListUiState(
    ...,
    val completedTodoIds: List<Long> = emptyList(),
    val deleteConfirmation: TodoDeleteConfirmation? = null
)
```

`completedTodoIds`는 현재 우선순위 필터와 무관하게 전체 Todo 중 완료된 항목의 id를 보관한다. 완료 항목 정리는 항상 완료 전체를 대상으로 한다.

### 4.2 삭제 확인 모델
```kotlin
sealed interface TodoDeleteConfirmation {
    val ids: List<Long>

    data class Single(val id: Long) : TodoDeleteConfirmation
    data class Completed(override val ids: List<Long>) : TodoDeleteConfirmation
}
```

### 4.3 Action 추가
- `OnDeleteRequest(id)`
- `OnDeleteCancel`
- `OnDeleteConfirm`
- `OnClearCompletedClick`

## 5. UI 설계

### 5.1 리스트 row 삭제
- `SwipeToDismissBox`는 `feature:todo:impl`에서 `TodoItemRow`를 감싼다.
- 왼쪽 스와이프(end-to-start) 시 바로 삭제하지 않고 `OnDeleteRequest(id)`를 보낸다.
- row trailing overflow 메뉴에 `삭제` 액션을 제공한다.
- 상시 휴지통 아이콘과 long press 삭제는 1차에서 제외한다.

### 5.2 편집 시트 삭제
- 기존 삭제 버튼은 유지한다.
- 클릭 시 편집 시트를 닫지 않고 삭제 확인 다이얼로그를 먼저 띄운다.
- 확인 성공 시 편집 시트를 닫는다.
- 취소 시 편집 상태를 유지한다.

### 5.3 완료 항목 정리
- `TodoFilter.COMPLETED`에서 `completedTodoIds`가 비어 있지 않을 때만 액션을 표시한다.
- 확인 다이얼로그에는 삭제될 완료 항목 수를 표시한다.
- 우선순위 필터가 적용되어 있어도 완료 전체를 삭제한다.

## 6. 삭제 처리 정책
- 모든 삭제는 `DeleteTodoUseCase`를 id별로 반복 호출한다.
- 각 삭제 성공 후 `TodoReminderScheduler.cancel(id)`을 호출한다.
- 일부 삭제 실패 시 실패 snackbar를 표시한다.
- 1차에서는 삭제 확인 다이얼로그를 유지한다. undo 도입 후 개별 삭제 확인 제거를 재검토한다.

## 7. 테스트 계획
- 개별 삭제 요청 시 확인 상태가 열린다.
- 삭제 취소 시 항목과 편집 상태가 유지된다.
- 삭제 확인 시 항목이 제거되고 리마인더가 취소된다.
- 편집 중 삭제 확인 성공 시 편집 시트가 닫힌다.
- 완료 항목 정리는 우선순위 필터와 무관하게 완료 전체를 삭제한다.

## 8. 검증 명령어
- `./gradlew :feature:todo:impl:testDebugUnitTest`
- `./gradlew :feature:todo:impl:lintDebug`
- `./gradlew :core:ui:lintDebug`
