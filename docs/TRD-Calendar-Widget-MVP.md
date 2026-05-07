# TRD - 월간 캘린더 홈 위젯 MVP

## 1. 문서 정보
- 문서명: 기술 요구사항 문서 - 월간 캘린더 홈 위젯 MVP
- 기준 기획: planner-agent 논의 기반 캘린더형 Android 홈 화면 위젯
- 대상 프로젝트: `MyFirstApp` (Kotlin, Compose, Glance, Hilt, Room, DataStore, WorkManager, Navigation 3)
- 작성일: 2026-05-07
- 범위: 월간 캘린더 위젯 추가, 이전/다음 월 이동, 날짜별 Todo 표시, 날짜 탭 앱 진입

## 2. 목표 및 범위

### 2.1 목표
- 사용자가 Android 홈 화면에서 현재 월 캘린더를 볼 수 있게 한다.
- 사용자가 앱을 열지 않고 위젯에서 이전/다음 월로 한 달씩 이동할 수 있게 한다.
- Todo가 있는 날짜를 점 또는 개수로 표시한다.
- 오늘 날짜를 시각적으로 구분한다.
- 위젯 날짜를 탭하면 앱이 열리고 해당 날짜가 선택된 캘린더 화면으로 진입한다.
- 기존 앱 내부 Calendar 탭은 제거하거나 축소하지 않는다.
- 위젯 구현은 앱 셸, 캘린더 화면 구현, 도메인 데이터 계약과 느슨하게 결합한다.

### 2.2 비목표
- 위젯 안에서 날짜 선택 상태 유지
- 위젯 안에서 Todo 목록 펼치기
- 위젯 안에서 Todo 완료/삭제/수정
- 위젯 설정 화면
- 월 선택 피커, 연도 점프, 스와이프 제스처
- 모든 위젯 크기별 완성형 레이아웃
- 기존 `CalendarScreen` Compose UI를 Glance 위젯에 직접 재사용
- 서버/외부 캘린더 연동

## 3. 제품 동작 정책

### 3.1 위젯 역할
위젯은 작은 캘린더 앱이 아니라 "월간 Todo 분포를 보여주고 앱 캘린더로 보내는 진입점"이다.

위젯 책임:
- 표시 중인 월 날짜 그리드 표시
- 이전/다음 월 버튼 처리
- 위젯별 표시 월 상태 저장
- 날짜별 Todo 존재 여부 또는 개수 표시
- 오늘 날짜 강조
- 날짜 탭 시 앱 진입 intent 생성

앱 Calendar 화면 책임:
- 탭한 날짜 선택 상태 반영
- 선택일 Todo 목록 표시
- Todo 추가/수정/완료/삭제
- 상세 월 이동 및 Todo 상호작용

### 3.2 첫 PR 절단선
첫 PR은 `월간 그리드 + 이전/다음 월 이동 + 날짜별 Todo 표시 + 날짜 탭 딥링크`까지만 구현한다.

월 이동은 포함하되 범위를 좁힌다:
- 이전/다음 월 버튼만 제공하고 피커/연도 점프/스와이프는 제외한다.
- 표시 월은 상대 offset이 아니라 `YearMonth` 절대값으로 위젯별 Glance state에 저장한다.
- 표시 월이 깨졌거나 없으면 현재 월로 fallback한다.
- 날짜 선택 상태, Todo 상세 조작, 목록 펼치기는 앱 Calendar 화면에 위임한다.

## 4. 아키텍처 반영 계획

### 4.1 모듈 변경 범위
- `:feature:calendar:widget` 신규 추가
  - Glance `GlanceAppWidget`
  - `GlanceAppWidgetReceiver`
  - 위젯 전용 presenter/state/model
  - 위젯 날짜 셀 계산
  - 위젯 업데이트 요청 API
  - 위젯 리소스 및 테스트
- `:feature:calendar:api`
  - 기존 top-level `CalendarRoute` 유지
  - 선택 날짜를 표현할 수 있는 `CalendarDateRoute` 계약 추가
  - 위젯이 앱으로 넘길 date argument 계약 정의
  - widget intent action/extra key 상수 정의
- `:feature:calendar:impl`
  - 선택 날짜 argument를 `CalendarViewModel` 초기 상태로 반영
  - 기존 Calendar 화면 UI/UDF 구조 유지
- `:app`
  - widget 모듈 의존 추가
  - manifest merge 또는 receiver 등록 확인
  - widget intent를 앱 navigation 초기 route로 연결
- `:core:domain`
  - 기존 `ObserveMonthlyTodoSummariesUseCase` 재사용 우선
  - 위젯에 필요한 조회 계약이 부족할 때만 작은 use case 추가
- `:core:data`, `:core:database`
  - MVP에서는 스키마 변경 없음
  - 기존 due date range 조회 재사용

### 4.2 의존 방향
목표 의존 방향:

```text
app -> feature:calendar:api
app -> feature:calendar:entry
app -> feature:calendar:widget

feature:calendar:entry -> feature:calendar:api
feature:calendar:entry -> feature:calendar:impl

feature:calendar:impl -> feature:calendar:api
feature:calendar:impl -> core:domain

feature:calendar:widget -> feature:calendar:api
feature:calendar:widget -> core:domain
feature:calendar:widget -> core:model

core:* -> feature:* 의존 금지
```

`feature:calendar:widget`은 `feature:calendar:impl`에 의존하지 않는다. 위젯이 화면 구현을 알게 되면 캘린더 화면 리팩터링이 위젯까지 전파되고, Glance 제약이 Compose 화면 설계에 역류한다.

### 4.3 응집도와 결합도 원칙
- 위젯 날짜 계산, 표시 모델, Glance UI는 `feature:calendar:widget` 안에 응집시킨다.
- Todo 조회 정책은 `core:domain` use case에 둔다.
- 앱 진입 계약은 `feature:calendar:api`에 둔다.
- 앱 셸은 intent 수신과 navigation 연결만 담당한다.
- `CalendarViewModel`은 선택 날짜 argument를 받아 초기 상태로 반영하되, 위젯 구현 세부사항을 알지 않는다.
- 위젯 업데이트 트리거는 작은 인터페이스로 감싼다. Todo 저장/수정 코드가 Glance 클래스를 직접 알지 않게 한다.

## 5. Route 및 딥링크 설계

### 5.1 Calendar route 계약
기존 top-level route는 유지한다.

```kotlin
@Serializable
data object CalendarRoute : NavKey
```

MVP에서는 선택 날짜를 담는 별도 route를 추가한다.

```kotlin
@Serializable
data class CalendarDateRoute(
    val selectedDate: String
) : NavKey
```

정책:
- `selectedDate`는 ISO-8601 `LocalDate.toString()` 값이다. 예: `2026-05-07`
- 일반 Calendar 탭 진입은 기존 `CalendarRoute`를 사용하고 오늘 날짜를 선택한다.
- 위젯 날짜 탭 진입은 `CalendarDateRoute(selectedDate)`를 사용한다.
- 파싱 실패 시 오늘 날짜로 fallback한다.

이 설계의 이유:
- 현재 앱 navigation은 top-level route를 `NavKey` equality로 판별한다.
- `CalendarRoute(selectedDate)`처럼 top-level route 자체에 argument를 넣으면 `AppTabDestination.fromRoute`와 `AppNavigator.navigate`가 날짜별 route를 top-level Calendar 탭으로 인식하지 못할 수 있다.
- `CalendarRoute`는 탭 identity로 유지하고, `CalendarDateRoute`는 Calendar feature 내부 route로 두면 탭 선택 상태와 날짜 argument가 분리된다.

앱 진입 시 navigation 순서:

```text
1. top-level route를 CalendarRoute로 전환
2. Calendar stack의 날짜 route를 CalendarDateRoute(selectedDate)로 replace
```

Calendar feature entry는 두 route를 모두 등록한다.

```text
CalendarRoute -> CalendarRouteScreen(initialSelectedDate = null)
CalendarDateRoute(selectedDate) -> CalendarRouteScreen(initialSelectedDate = selectedDate)
```

### 5.2 위젯 앱 진입 intent
위젯 날짜 탭은 앱 launch intent에 calendar action/extra를 실어 보낸다.

```text
action = com.example.myfirstapp.action.OPEN_CALENDAR_DATE
extra  = selected_date: yyyy-MM-dd
```

`feature:calendar:widget`은 `MainActivity::class.java`를 직접 참조하지 않는다. widget 모듈이 app 모듈을 알게 되면 `feature -> app` 역방향 의존이 생기기 때문이다.

권장 구현:

```text
context.packageManager.getLaunchIntentForPackage(context.packageName)
  + action OPEN_CALENDAR_DATE
  + extra selected_date
```

action/extra key는 `feature:calendar:api`에 둔다.

앱 셸은 이 intent를 navigation 초기 요청으로 변환한다.

```text
OPEN_CALENDAR_DATE + selected_date=2026-05-07
-> CalendarRoute top-level 전환
-> CalendarDateRoute(selectedDate = "2026-05-07")
```

MVP에서는 외부 브라우저 딥링크 URI보다 명시적 앱 intent를 우선한다. 홈 위젯에서만 발생하는 내부 진입 계약이므로 Android intent extra가 더 단순하고 노출면이 작다.

## 6. Widget 모듈 설계

### 6.1 주요 클래스
```text
feature/calendar/widget/
  src/main/java/.../widget/
    CalendarMonthWidget.kt
    CalendarMonthWidgetReceiver.kt
    CalendarMonthWidgetPresenter.kt
    CalendarMonthWidgetState.kt
    CalendarMonthWidgetPreferences.kt
    CalendarMonthWidgetActionParameters.kt
    CalendarMonthWidgetMonthNavigationCallback.kt
    CalendarMonthWidgetUpdater.kt
    CalendarMonthWidgetIntentFactory.kt
    CalendarMonthWidgetDateGrid.kt
```

### 6.2 책임
- `CalendarMonthWidget`
  - Glance UI 렌더링
  - 상태별 loading/content/error/empty fallback 표시
  - Glance state에서 표시 월을 읽어 presenter에 전달
  - 날짜 셀 클릭 액션 연결
- `CalendarMonthWidgetReceiver`
  - Android widget receiver 진입점
  - manifest에 등록되는 클래스
- `CalendarMonthWidgetPresenter`
  - 표시 월 기준 summary 조회
  - 표시 월이 없으면 `YearMonth.now()` 기준 현재 월 사용
  - 도메인 summary를 위젯 state로 변환
  - 날짜 정렬, 오늘 강조, 표시 count 결정
- `CalendarMonthWidgetPreferences`
  - 위젯별 표시 월 `YearMonth` 저장/파싱/fallback 담당
- `CalendarMonthWidgetMonthNavigationCallback`
  - 이전/다음 월 액션 수신
  - Glance state의 표시 월을 갱신하고 해당 위젯만 update
- `CalendarMonthWidgetActionParameters`
  - callback에 전달되는 월 이동 delta key 정의
- `CalendarMonthWidgetState`
  - Glance UI가 소비하는 불변 표시 모델
- `CalendarMonthWidgetUpdater`
  - 앱의 Todo 변경 후 위젯 갱신 요청을 캡슐화
  - 내부에서 `CalendarMonthWidget().updateAll(context)` 호출
- `CalendarMonthWidgetIntentFactory`
  - 날짜 탭 intent 생성
  - `feature:calendar:api`의 action/extra key 사용
  - app activity class를 직접 참조하지 않음
- `CalendarMonthWidgetDateGrid`
  - 월간 7열 그리드 셀 계산
  - 이전/다음 달 trailing cell 포함 여부 결정

### 6.3 Hilt 및 의존성 주입
Glance widget 객체는 stateless/passive하게 유지한다. 위젯 데이터 로딩은 Hilt entry point 또는 receiver 주입을 통해 presenter에 접근한다.

권장 방향:
- receiver 또는 presenter factory는 Hilt 진입점으로 의존성을 얻는다.
- `CalendarMonthWidget` 자체에는 장기 상태를 보관하지 않는다.
- widget state는 매 update마다 도메인 데이터에서 재계산한다.
- 월 이동 state는 Glance preferences state에 위젯별로 저장한다.
- 여러 위젯이 설치되어도 각 Glance id별 표시 월이 독립적으로 유지되어야 한다.

## 7. 위젯 UI 요구사항

### 7.1 기본 레이아웃
- 권장 크기: 4x3 또는 4x4 한 가지를 중심으로 최적화
- 헤더: 이전 월 버튼, 표시 월 라벨 (`May 2026`, `2026년 5월`), 다음 월 버튼
- 요일 행: locale 기반 요일 축약 표시
- 날짜 그리드: 7열, 5~6행
- 오늘 날짜: 배경 또는 테두리 강조
- Todo 있는 날짜: 점 또는 작은 숫자 표시

### 7.2 날짜 셀 표시 정책
- 표시 월 날짜는 기본 텍스트 색상으로 표시한다.
- 이전/다음 월 보조 셀은 MVP에서 숨김 또는 비활성 약한 색상 중 하나로 통일한다.
- Todo 개수는 최대 표시값을 둔다. 예: `9+`
- 완료된 Todo 포함 여부는 기존 Calendar summary 정책을 따른다. 정책 변경이 필요하면 `core:domain`에서 결정한다.

### 7.3 클릭 정책
- 표시 월 날짜 셀은 모두 클릭 가능하다.
- 보조 셀을 표시하는 경우 MVP에서는 클릭 비활성화한다.
- 이전/다음 월 버튼은 앱을 열지 않고 위젯의 표시 월만 갱신한다.
- Todo 점/숫자만 클릭 target으로 두지 않고 날짜 셀 전체를 클릭 target으로 둔다.

### 7.4 문자열 리소스
사용자 노출 문자열은 `values`, `values-ko`에 모두 추가한다.

예상 키:
- `calendar_widget_name`
- `calendar_widget_description`
- `calendar_widget_empty`
- `calendar_widget_error`
- `calendar_widget_previous_month`
- `calendar_widget_next_month`
- `calendar_widget_previous_month_symbol`
- `calendar_widget_next_month_symbol`
- `calendar_widget_open_calendar`

## 8. 업데이트 정책

### 8.1 즉시 업데이트
Todo 생성/수정/완료/삭제 이후 위젯 갱신을 요청한다.

권장 방식:
- `core:domain`에 Android 의존성을 추가하지 않는다.
- Todo 변경 use case가 widget을 직접 알지 않는다.
- 앱 또는 feature 레벨에서 Todo 변경 성공 후 `CalendarMonthWidgetUpdater`를 호출한다.
- 장기적으로는 앱 이벤트 dispatcher를 둘 수 있지만 MVP에서는 명시 호출을 최소 지점에 둔다.

### 8.2 주기 업데이트
MVP에서는 과도한 주기 업데이트를 넣지 않는다.

필수 대응:
- 시스템 widget update 요청 시 표시 월을 다시 렌더링한다.
- 저장된 표시 월이 있으면 시스템 widget update에서도 해당 월을 다시 렌더링한다.
- 저장된 표시 월이 없거나 파싱 실패하면 현재 월로 복구한다.
- 날짜가 바뀐 뒤 오늘 강조가 갱신될 수 있도록 `updatePeriodMillis`는 Android 제한을 고려해 보수적으로 설정한다.

비필수 대응:
- 15분 단위 WorkManager 주기 갱신
- 매일 자정 정확 갱신 worker

### 8.3 부팅/앱 업데이트
AppWidgetProvider/Glance receiver 기본 동작과 시스템 update를 우선 사용한다. 별도 boot receiver는 MVP에서 추가하지 않는다.

## 9. 테스트 전략

홈 화면 위젯은 일반 Compose 화면과 테스트 방식이 다르다. MVP 테스트는 "순수 로직은 촘촘하게, 플랫폼 통합은 얇게, 실제 런처 동작은 명시 수동 QA로" 나눈다.

### 9.1 단위 테스트 - 날짜 그리드
대상: `:feature:calendar:widget:testDebugUnitTest`

검증:
- 2026-05 같은 5주/6주 월의 cell 개수와 위치가 안정적이다.
- locale first day of week에 따라 시작 요일 offset이 계산된다.
- 오늘 날짜가 현재 월 안에 있을 때만 `isToday = true`다.
- 이전/다음 월 보조 셀은 MVP 정책대로 비활성 상태다.
- 월말/윤년/2월 케이스를 포함한다.

### 9.2 단위 테스트 - Presenter
대상: `:feature:calendar:widget:testDebugUnitTest`

검증:
- `ObserveMonthlyTodoSummariesUseCase` 결과가 날짜별 count/hasTodo 표시 모델로 변환된다.
- 표시 월이 주입되면 현재 월이 아니라 해당 월 기준으로 summary와 grid를 만든다.
- Todo가 없는 날짜는 indicator가 없다.
- count가 표시 한도를 넘으면 `9+` 같은 overflow label로 변환된다.
- 도메인 조회 실패 또는 빈 결과에서 fallback state가 만들어진다.
- `Clock` 또는 `LocalDateProvider`를 주입해 오늘 날짜 테스트가 고정된다.

### 9.3 단위 테스트 - Intent Factory
대상: `:feature:calendar:widget:testDebugUnitTest`

검증:
- 날짜 탭 intent action이 `OPEN_CALENDAR_DATE`다.
- `selected_date` extra가 ISO date로 들어간다.
- intent는 app 모듈 클래스를 직접 참조하지 않고 package launch intent 기반으로 만들어진다.
- invalid date는 factory에서 만들 수 없게 타입을 `LocalDate`로 제한한다.

### 9.4 앱 단위 테스트 - Navigation intent 해석
대상: `:app:testDebugUnitTest`

검증:
- `OPEN_CALENDAR_DATE` intent + valid date가 `CalendarRoute` top-level 전환과 `CalendarDateRoute(selectedDate)` replace 요청으로 변환된다.
- date extra가 없거나 파싱 실패하면 `CalendarRoute`만 여는 동작으로 fallback한다.
- 일반 launcher intent는 기존 start route를 깨지 않는다.
- 기존 `AppTabDestination.fromRoute(CalendarRoute)` 동작은 유지한다.
- `CalendarDateRoute`가 현재 route여도 bottom tab은 Calendar로 표시되어야 한다.

### 9.5 Calendar ViewModel 단위 테스트
대상: `:feature:calendar:impl:testDebugUnitTest`

검증:
- `SavedStateHandle` 또는 route argument에 selected date가 있으면 초기 선택 날짜로 사용한다.
- 선택 날짜의 월이 현재 월과 다르면 해당 월로 초기화한다.
- invalid date fallback은 오늘 날짜다.
- 기존 월 이동/날짜 선택 동작은 유지된다.

### 9.6 Glance UI 트리 테스트
대상: `:feature:calendar:widget:testDebugUnitTest`

목표:
- `runGlanceAppWidgetUnitTest`로 위젯 composable을 JVM에서 렌더링한다.
- 월 헤더, 날짜 label, Todo count label, error fallback 문구가 표시된다.
- 이전/다음 월 버튼에는 `ActionCallback` click action과 월 delta parameter가 연결된다.
- 현재 월 날짜 셀에는 앱 진입 `StartActivity` click action이 연결된다.
- 이전/다음 월 보조 셀은 click action을 갖지 않는다.

### 9.7 위젯 표시 월 상태 테스트
대상: `:feature:calendar:widget:testDebugUnitTest`

검증:
- 저장된 `YearMonth` 문자열을 파싱한다.
- invalid 저장값은 현재 월로 fallback한다.
- 이전/다음 월 이동은 연도 경계를 넘어도 정상 계산된다.
- 표시 월 저장값은 ISO `yyyy-MM` 형태로 유지된다.

구현 메모:
- 위젯 UI에는 테스트 전용 semantic tag만 얇게 붙인다. 태그는 런타임 동작과 사용자 노출 UI에 영향을 주지 않는다.
- `LocalContext`와 string resource 검증이 필요한 테스트는 Robolectric으로 context를 제공한다.
- 실제 Android 런처가 위젯을 배치하고 resize하는 동작은 기기/런처 의존성이 높으므로 수동 QA로 남긴다.

### 9.8 Manifest/resource 통합 검증
대상: `:feature:calendar:widget:lintDebug`, `:app:assembleDebug`

검증:
- 위젯 receiver가 manifest에 등록되어 설치 가능한 widget provider로 노출된다.
- widget provider XML과 초기 layout/resource 참조가 빌드에서 깨지지 않는다.
- 앱 패키징에 widget 모듈 manifest가 병합된다.

### 9.9 실제 홈 화면 수동 QA
자동화가 어려운 런처 동작은 PR 설명에 수동 검증 결과를 남긴다.

체크리스트:
1. Debug 앱 설치 후 Android 런처의 위젯 선택 화면에 `월간 캘린더` 위젯이 보인다.
2. 홈 화면에 위젯을 추가할 수 있다.
3. 현재 월과 요일/날짜가 표시된다.
4. 오늘 날짜가 강조된다.
5. Todo가 있는 날짜에 점 또는 개수가 표시된다.
6. 이전/다음 월 버튼을 탭하면 앱이 열리지 않고 위젯 월만 이동한다.
7. 이동한 월의 Todo count가 해당 월 기준으로 표시된다.
8. Todo 추가/수정/완료 후 위젯 표시가 갱신된다.
9. 날짜 셀을 탭하면 앱이 열린다.
10. 앱 Calendar 탭에서 탭한 날짜가 선택되어 있다.
11. 앱을 강제 종료한 뒤에도 위젯을 탭하면 앱이 정상 진입한다.
12. 기기 언어를 한국어/영어로 바꿨을 때 위젯 이름과 주요 문구가 리소스에 따라 표시된다.

### 9.10 검증 명령어
- `./gradlew :feature:calendar:widget:testDebugUnitTest`
- `./gradlew :feature:calendar:widget:lintDebug`
- `./gradlew :feature:calendar:impl:testDebugUnitTest`
- `./gradlew :feature:calendar:impl:lintDebug`
- `./gradlew :feature:calendar:api:testDebugUnitTest`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`
- `./gradlew :app:assembleDebug`
- `git diff --check`

## 10. 구현 체크리스트

### 10.1 모듈/빌드
- `settings.gradle.kts`에 `:feature:calendar:widget` 추가
- version catalog에 Glance appwidget 의존성 추가
- widget 모듈 `build.gradle.kts` 작성
- `app`이 widget 모듈에 의존하도록 추가

### 10.2 Calendar API
- `CalendarRoute`를 selected date 지원 형태로 확장
- route equality와 top-level tab 매칭 테스트 갱신
- selected date argument key를 API 레벨 상수 또는 타입으로 관리

### 10.3 App 연결
- `MainActivity` intent parsing 또는 navigation bootstrap 확장
- `AppNavHost`가 초기 navigation 요청을 받을 수 있도록 설계
- 일반 launcher intent 기존 동작 보존

### 10.4 Widget 구현
- presenter/state/date grid 작성
- 표시 월 Glance state 저장/파싱/fallback 작성
- 이전/다음 월 ActionCallback 작성
- Glance UI 작성
- receiver 및 widget provider XML 작성
- 날짜 click action intent 연결
- widget update helper 작성

### 10.5 Calendar 화면 반영
- `CalendarViewModel` 초기 selected date/month 반영
- `CalendarRouteScreen` argument 전달
- 기존 Calendar UI 테스트/단위 테스트 갱신

### 10.6 테스트
- widget date grid 테스트
- widget presenter 테스트
- widget 표시 월 상태 테스트
- widget Glance UI 트리 테스트
- widget intent factory 테스트
- app intent-to-route 테스트
- CalendarViewModel selected date 초기화 테스트
- manifest/resource 통합 검증

## 11. 리스크 및 대응

### 11.1 Glance API/테스트 API 버전 리스크
Glance는 Compose UI와 직접 호환되지 않고 테스트 API도 프로젝트 버전에 영향을 받을 수 있다.

대응:
- 기존 Compose Calendar UI를 재사용하지 않는다.
- 위젯 UI는 얇게 두고 로직을 presenter/date grid로 밀어 단위 테스트 가능하게 만든다.
- Glance UI 트리 테스트를 우선 사용하고, 런처 resize/배치처럼 host 의존성이 큰 동작만 수동 QA로 분리한다.

### 11.2 Route 계약 변경 리스크
날짜 argument를 top-level `CalendarRoute`에 직접 넣으면 탭 route 비교와 navigation stack이 깨질 수 있다.

대응:
- `CalendarRoute`는 top-level identity로 유지한다.
- `CalendarDateRoute`를 Calendar feature 내부 route로 추가한다.
- app intent parser는 `CalendarRoute` 전환과 `CalendarDateRoute` push를 분리한다.
- `CalendarDateRoute`가 현재 route여도 Calendar tab이 선택되는 정책을 테스트로 고정한다.

### 11.3 Widget 업데이트 누락 리스크
Todo 변경 후 위젯이 즉시 갱신되지 않으면 사용자가 오래된 정보를 본다.

대응:
- Todo 변경 성공 지점에서 widget updater 호출 위치를 명시한다.
- 첫 PR에서는 핵심 변경 경로(add/update/toggle/delete)를 우선 연결한다.
- update 실패는 앱 기능 실패로 전파하지 않고 로그/비치명 처리한다.
- Todo 변경 update는 위젯별 저장 표시 월을 유지한 채 해당 월 기준으로 다시 렌더링해야 한다.

### 11.4 모듈 결합 증가 리스크
위젯 구현이 `app` 또는 `feature:calendar:impl`에 섞이면 향후 유지보수 비용이 커진다.

대응:
- widget 전용 모듈을 둔다.
- app은 receiver 등록/intent routing만 담당한다.
- impl은 route argument를 받아 UI 상태로 변환하는 책임만 갖는다.

## 12. PR 설명 반영 포인트
- 변경 모듈: `feature:calendar:widget`, `feature:calendar:api`, `feature:calendar:impl`, `app`
- In scope: 월간 캘린더 위젯, 이전/다음 월 이동, 날짜별 Todo 표시, 날짜 탭 앱 진입
- Out of scope: 월 선택 피커, 연도 점프, 스와이프, 위젯 내 Todo 조작, 설정 화면, 전체 크기 대응
- Tests: 단위 테스트, lint, assemble, 홈 화면 수동 QA 결과
- Coverage Impact: widget presenter/date grid/intent factory 및 CalendarViewModel 초기 상태 테스트로 non-view 로직 커버리지 확보
- Risks/Rollback: widget 모듈 의존 제거, manifest receiver 제거, route 계약 rollback 절차
