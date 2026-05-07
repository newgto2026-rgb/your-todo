# TRD — TODO 앱 카테고리 기능 (MVP)

## 1. 문서 정보
- 문서명: 기술 요구사항 문서 — 카테고리 기능 (MVP)
- 기준 PRD: "PRD — TODO App Category 기능 (MVP)"
- 대상 프로젝트: `YourTodo` (Kotlin, Compose, Room, DataStore, Hilt, UDF)
- 작성일: 2026-04-09
- 범위: 로컬 단말 저장 기반 카테고리 기능 추가 (서버 동기화 없음)

## 2. 목표 및 범위

### 2.1 목표
- TODO 생성/수정 시 카테고리 지정 가능
- 카테고리 CRUD 제공
- 상태 필터(`ALL/TODAY/COMPLETED`) + 카테고리 필터 조합 조회
- 마지막 선택 카테고리 필터를 앱 재실행 후 복원

### 2.2 비목표
- 카테고리 공유/협업
- 서버 연동 및 동기화
- 다중 카테고리 선택 필터(AND/OR)
- 중첩 카테고리

## 3. 아키텍처 반영 계획

### 3.1 모듈 변경 범위
- `core:model`: Category 도메인 모델, TodoItem 확장
- `core:database`: Room Entity/DAO/DB version/migration
- `core:datastore`: selected category filter 저장/복원
- `core:data`: repository 구현, mapper, 정규화 로직
- `core:domain`: repository contract 확장 + category/filter use case 추가
- `core:testing`: FakeRepository/FakeDataSource 확장
- `feature:todo:impl`: UiState/Action/ViewModel/UI 반영
- `feature:todo:api`, `app`: MVP에서는 공개 계약 변경 없음

### 3.2 레이어 의존성
- 기존 의존 방향 유지: `feature -> core:domain -> core:data -> core:database/datastore`
- `core:*`는 `feature:*`에 의존하지 않음

## 4. 데이터 모델 설계

### 4.1 도메인 모델

```kotlin
data class Category(
    val id: Long,
    val name: String,
    val colorHex: String?,
    val icon: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

- `TodoItem` 확장

```kotlin
data class TodoItem(
    val id: Long,
    val title: String,
    val isDone: Boolean,
    val dueDate: LocalDate?,
    val createdAt: Long,
    val updatedAt: Long,
    val categoryId: Long? // null == Uncategorized
)
```

### 4.2 정책 모델
- `selectedCategoryId: Long?`
- `null` = 모든 카테고리(All)
- TODO 저장 시 카테고리 미선택은 `null`로 저장하고 UI에서 `미분류`로 표기

## 5. Room 설계

### 5.1 테이블
- `category`
  - `id` PK auto increment
  - `name` TEXT NOT NULL (case-insensitive unique)
  - `colorHex` TEXT NULL
  - `icon` TEXT NULL
  - `createdAt` INTEGER NOT NULL
  - `updatedAt` INTEGER NOT NULL
- `todo` 확장
  - `categoryId` INTEGER NULL
  - FK: `category(id)` with `ON DELETE SET NULL`

### 5.2 Entity 초안

```kotlin
@Entity(
    tableName = "category",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(...)

@Entity(
    tableName = "todo",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId")]
)
data class TodoEntity(
    ...,
    val categoryId: Long?
)
```

### 5.3 마이그레이션
- DB version: `1 -> 2`
- 마이그레이션 SQL
  1. `category` 테이블 생성
  2. `todo` 신규 스키마 생성(`categoryId` 포함, FK 포함)
  3. 기존 `todo` 데이터 복사 (`categoryId = NULL`)
  4. old table drop/rename
  5. index 생성
- 기존 TODO는 자동으로 `Uncategorized`에 매핑되는 효과

## 6. DataStore 설계

### 6.1 Key
- `selected_todo_category_filter` (`Long` nullable 표현)

### 6.2 저장 규칙
- `null`(All)은 키 제거(remove)로 표현
- 특정 카테고리 선택 시 해당 id 저장

### 6.3 예외 처리
- 저장된 category id가 존재하지 않으면 런타임에서 `null`(All)로 fallback 및 DataStore 정리

## 7. Repository 및 UseCase 설계

### 7.1 리포지토리 계약 변경 (`core:domain`)
- 기존 `TodoRepository`에 아래 추가

```kotlin
fun observeCategories(): Flow<List<Category>>
suspend fun addCategory(name: String, colorHex: String?, icon: String?): Result<Long>
suspend fun updateCategory(id: Long, name: String, colorHex: String?, icon: String?): Result<Unit>
suspend fun deleteCategory(id: Long): Result<Unit>

fun observeSelectedCategoryFilter(): Flow<Long?>
suspend fun setSelectedCategoryFilter(categoryId: Long?): Result<Unit>
```

- TODO API 확장

```kotlin
suspend fun addTodo(title: String, dueDate: LocalDate?, categoryId: Long?): Result<Long>
suspend fun updateTodo(id: Long, title: String, dueDate: LocalDate?, categoryId: Long?): Result<Unit>
```

### 7.2 Validation 규칙
- 이름 trim 후 빈 문자열 금지
- 이름 중복 금지(case-insensitive)
- 색상값은 `#RRGGBB` 또는 `#AARRGGBB`만 허용(선택)
- categoryId 유효성
  - add/update 시 존재하지 않는 id면 실패 또는 null 정규화 중 하나로 통일
  - MVP 권장: 실패 반환(조기 오류 탐지)

### 7.3 Delete 정책
- MVP 정책: 옵션 A (연결 TODO를 Uncategorized로 이동)
- 구현 방식
  - Room FK `SET NULL` + delete transaction
  - 삭제된 카테고리가 현재 선택 필터면 `setSelectedCategoryFilter(null)` 실행

### 7.4 신규 UseCase
- `ObserveCategoriesUseCase`
- `AddCategoryUseCase`
- `UpdateCategoryUseCase`
- `DeleteCategoryUseCase`
- `ObserveSelectedCategoryFilterUseCase`
- `UpdateSelectedCategoryFilterUseCase`

## 8. ViewModel/UDF 설계

### 8.1 UiState 변경 (`feature:todo:impl`)

```kotlin
data class TodoListUiState(
    ...,
    val categories: List<CategoryUiModel> = emptyList(),
    val selectedCategoryId: Long? = null // null = All
)
```

### 8.2 Flow 조합
- 기존
  - `observeTodos()` + `observeSelectedTodoFilter()` + local state
- 변경
  - `observeTodos()`
  - `observeSelectedTodoFilter()`
  - `observeCategories()`
  - `observeSelectedCategoryFilter()`
  - local state
- 필터링
  1. 상태 필터 적용 (`ALL/TODAY/COMPLETED`)
  2. 카테고리 필터 적용 (`selectedCategoryId == null || item.categoryId == selectedCategoryId`)

### 8.3 Action/SideEffect 추가
- Action
  - `OnCategoryFilterChange(categoryId: Long?)`
  - `OnCategorySelectedInEditor(categoryId: Long?)`
  - `OnManageCategoriesClick`
  - `OnCategoryCreate/Update/Delete` (관리 시트)
- SideEffect
  - 유효성 에러 스낵바
  - 카테고리 삭제 후 현재 필터 fallback 안내

## 9. UI/UX 구현 요구사항

### 9.1 목록 화면
- 상단 필터 바에 카테고리 칩 추가
  - `All`, `Uncategorized`, 동적 category 목록
- TODO row에 카테고리 배지(이름 + 색상)

### 9.2 편집 바텀시트
- 카테고리 선택 드롭다운/칩 그룹 추가
- `Manage categories` 진입 버튼 추가

### 9.3 카테고리 관리 UI
- 최소 MVP: 모달 바텀시트
  - 목록 + 추가/수정/삭제
  - 삭제 시 확인 다이얼로그
- 초기 상태 보장
  - 사용자 정의 카테고리 0개여도 `Uncategorized`는 항상 UI에서 선택 가능

## 10. 예외/정합성 처리
- 공백/중복 이름 차단
- 존재하지 않는 categoryId를 가진 TODO는 로딩 시 `null`로 정규화
- 현재 선택된 category가 삭제되면 즉시 `All`로 전환
- DataStore에 남아 있는 stale categoryId는 자동 정리

## 11. 테스트 전략

### 11.1 단위 테스트
- `core:domain`
  - 카테고리 CRUD validation
  - 삭제 정책(연결 TODO null 이동)
- `feature:todo:impl` ViewModel
  - 상태필터 + 카테고리필터 조합
  - 삭제 후 fallback
  - side effect emission

### 11.2 데이터/DB 테스트
- `core:data`
  - mapper(categoryId round-trip)
  - repository category filter persistence
- `core:database`
  - migration 1->2 검증
  - FK `ON DELETE SET NULL` 검증

### 11.3 UI 테스트
- 카테고리 선택 후 TODO 저장/표시
- 카테고리 필터 전환 시 목록 반영
- 카테고리 삭제 시 TODO 유지 + Uncategorized 이동 확인

## 12. 구현 순서 (권장)
1. `core:model`: Category/TodoItem 확장
2. `core:database`: entity/dao/db/migration
3. `core:datastore`: selected category filter API
4. `core:domain`: repository contract + use cases
5. `core:data`: 구현/매퍼/정규화
6. `feature:todo:impl`: UiState/ViewModel/UI
7. 테스트 추가 및 회귀 검증

## 13. 수용 기준 (기술 수용 기준)
- 카테고리 CRUD가 앱 재시작 후에도 유지
- TODO 생성/수정 시 category 지정/변경 가능
- 상태 필터 + category 필터 조합이 정확히 동작
- category 삭제 시 관련 TODO가 삭제되지 않고 `Uncategorized`로 유지
- 현재 선택 category 삭제 시 필터가 `All`로 fallback
- 단위/데이터/UI 테스트 모두 통과, category 관련 crash 0

## 14. 리스크 및 대응
- 리스크: migration 실패로 기존 데이터 손실
  - 대응: migration instrumentation test 필수
- 리스크: 중복 이름 처리의 locale 이슈
  - 대응: `lowercase(Locale.ROOT)` 기준으로 비교
- 리스크: stale filter id로 빈 화면 오인
  - 대응: startup 시 유효성 검사 후 fallback

## 15. 오픈 이슈
- 색상/아이콘 허용 범위(고정 팔레트 vs 자유 입력) 최종 확정 필요
- `Uncategorized`를 DB row로 둘지(null 기반 가상 항목) 확정 필요
  - 본 TRD 권장안: null 기반 가상 항목
