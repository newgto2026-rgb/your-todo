# Navigation 3 Routing and Scope Report

## 목적

이 문서는 앱 셸의 Navigation 3 구조를 확장 가능한 계약으로 고정하기 위한 보고서다. 새 탭, 새 하위 route, 새 bottom sheet route를 추가하는 다음 구현자가 `AppNavHost` 내부 동작을 추측하지 않고 필요한 선언과 검증 포인트를 따라갈 수 있도록 한다.

## 현재 구조

- `:app`은 최상위 탭 순서, 탭별 back stack, transient overlay stack, 즉시 렌더링 규칙을 소유한다.
- `feature:*:api`는 외부에서 참조 가능한 `NavKey` route를 정의한다.
- `feature:*:impl`은 `AppFeatureEntry.register()`에서 route를 실제 화면으로 연결한다.
- `feature:*:entry`는 Hilt multibinding으로 `AppFeatureEntry`를 앱에 제공한다.
- `core:ui`의 `AppFeatureEntry`는 feature가 소유한 최상위 route 목록을 앱 셸에 알려주는 계약이다.
- bottom sheet처럼 화면 stack 소유권과 생명주기가 짧은 route는 별도 transient stack으로 분리한다.

핵심 파일:

- `app/src/main/java/com/neo/yourtodo/app/AppNavigationGraph.kt`
- `app/src/main/java/com/neo/yourtodo/app/AppNavHost.kt`
- `app/src/main/java/com/neo/yourtodo/app/AppNavigationState.kt`
- `app/src/main/java/com/neo/yourtodo/app/AppNavigator.kt`
- `core/ui/src/main/java/com/neo/yourtodo/core/ui/navigation/AppFeatureEntry.kt`

## 확장 계약

### 최상위 탭 route 추가

1. `feature:<name>:api`에 `NavKey`를 추가한다.
2. 앱 탭으로 노출할 경우 `AppTabDestination.tabs`에 route와 라벨 리소스를 추가한다.
3. 해당 feature의 `AppFeatureEntry.topLevelRoutes`에 route를 포함한다.
4. `AppFeatureEntry.register()`에서 route를 `entry<Route>`로 화면에 연결한다.
5. `AppNavigationGraphTest`에 누락/중복 검증 케이스를 추가한다.
6. 탭 전환 UI 테스트에 새 탭을 포함한다.

`buildAppNavigationGraph()`는 앱 탭에 등록된 route가 어떤 feature entry에도 소유 선언되지 않으면 즉시 실패한다. 이 실패는 새 탭을 추가할 때 놓친 연결을 빠르게 드러내기 위한 의도된 안전장치다.

### 하위 route 추가

1. route는 보통 feature API에 둔다. 앱 밖에서 절대 참조하지 않는 내부 route라면 impl 내부에 둘 수 있지만, 다른 feature나 앱 셸에서 이동해야 하면 API 계약으로 승격한다.
2. 화면 이동은 `AppRouteActions.navigate(route)`로 요청한다.
3. 하위 route는 `topLevelRoutes`에 넣지 않는다.
4. 다른 탭으로 이동해도 각 탭의 하위 stack은 유지한다. 같은 탭을 다시 선택할 때만 해당 탭의 하위 stack을 최상위 route까지 접는다.

### Bottom sheet route 추가

1. route entry metadata에 `BottomSheetRouteMetadata.bottomSheet()`를 추가한다.
2. 닫기는 `AppRouteActions.closeCurrentEntry()`로 요청한다.
3. route 타입을 feature entry의 `transientRouteTypes`에 추가한다.
4. bottom sheet는 `transientStack`의 마지막 entry가 sheet metadata를 가질 때만 렌더링되어야 한다.
5. 탭 전환 시 transient route는 닫혀야 하며, 이전 탭에 남아 재등장하면 안 된다.
6. `SceneStrategyTest`와 UI 테스트에 inactive sheet가 렌더링되지 않는 케이스와 overlay 대상이 활성 entries로 제한되는 케이스를 추가한다.

## Navigation 3 적용 방식

Navigation 3 공식 multiple back stacks 레시피와 Now in Android 최신 main의 `NavigationState` 구현을 기준으로, 앱 셸은 두 계층의 back stack을 소유한다.

- `topLevelStack`: 사용자가 방문한 최상위 탭 route만 담는다.
- `backStacks`: 각 최상위 탭 route에 대응하는 실제 화면 stack을 담는다.
- `transientStack`: bottom sheet 같은 임시 route만 담는다. 이 stack은 탭 stack과 별도이며 탭 전환/닫기에서 명시적으로 비운다.

`AppNavigationState.toEntries()`는 각 탭 route마다 `AppNavEntryStackState`를 두고, 그 안에 Navigation 3 decorator provider를 route scope로 고정한다.

- 해당 stack 전용 `rememberSaveableStateHolderNavEntryDecorator`와 `rememberViewModelStoreNavEntryDecorator`

Transient stack은 `NavBackStack`이 아니라 별도 state list로 관리되므로, 살아있는 transient route의 `NavEntry` cache도 같은 state 객체 안에 보관한다.

그 뒤 `orderedTopLevelRoutes` 순서대로 모든 top-level entries를 안정적으로 flatten하고, 마지막에 `transientStack` entries를 붙인다. 이 구조가 중요한 이유는 탭 전환 때 current route를 마지막으로 보내기 위해 entries를 재정렬하면 Navigation 3 decorator가 이전 content를 pop처럼 해석할 여지가 생기기 때문이다. 최종 구조에서 `navigationState.toEntries(appEntryProvider)`는 recomposition과 탭 선택 상태가 바뀌어도 같은 top-level route 순서와 같은 decorated entry state를 유지한다. 현재 화면은 entries 순서가 아니라 `ImmediateNavDisplay(activeContentKey = navigationState.currentStack.last().toString())`로 선택한다.

최상위 탭 전환은 `NavDisplay`의 기본 scene fade를 사용하지 않는다. Navigation 3 Android 기본 전환은 이전 scene과 새 scene을 함께 그리는 구간이 있어 bottom navigation 탭에서는 이전 탭 content가 순간적으로 보일 수 있다. 이 앱은 탭 선택을 즉시 상태 전환으로 취급하므로 `ImmediateNavDisplay`가 active entry를 `zIndex(1f)`로 보이고 inactive entries를 `alpha(0f)`, `Lifecycle.State.CREATED`, empty semantics로 유지한다. 이 retained composition은 entry-scoped `ViewModelStoreOwner`가 tab switch 중 dispose/pop 경로를 타지 않게 하기 위한 의도된 정책이다. Bottom sheet 자체의 Material animation은 `BottomSheetSceneStrategy` 안에서 별도로 유지한다.

이번 Calendar freeze의 실제 root cause도 이 지점이었다. 로그상 `CalendarRoute`가 pop된 것이 아니라, bottom sheet open/close 중 `ViewModelStoreNavEntryDecorator` provider call-site가 재생성되어 `CalendarViewModel`이 `onCleared()` 되었고 `selectedDate`가 오늘로 초기화됐다. 최종 구조는 Calendar route가 stack에 남아 있는 동안 같은 `NavEntry`, 같은 decorator provider, 같은 entry-scoped `ViewModelStoreOwner`를 유지한다.

비교 기준:

- Navigation 3 공식 multiple back stacks recipe: `NavigationState`가 top-level route별 back stack을 소유하고 stack별 decorated entries를 합쳐 `NavDisplay(entries = ...)`에 넘기는 패턴이다.
- Now in Android 최신 main: `topLevelStack + subStacks` 구조를 사용하고, `NavigationState.toEntries()`가 stack별 `rememberDecoratedNavEntries()` 결과를 `topLevelStack.flatMap(...)`으로 합쳐 `NavDisplay(entries = ...)`에 넘긴다. `Navigator`는 같은 top-level key를 다시 선택하면 현재 sub stack을 root까지 접고, 다른 top-level key는 `topLevelStack`에서 remove 후 add 한다.
- 이 앱의 보강점: Now in Android 패턴의 per-stack state hoisting은 유지하되, Calendar/Todo 탭의 ViewModel scope가 탭 전환과 bottom sheet overlay 중 clear되지 않도록 모든 top-level entries를 stable order로 retained composition에 둔다. transient overlay가 top-level stack recomposition을 유발해도 provider가 빠지지 않도록 route별 entry/decorator state를 `rememberAppNavigationState()` 단계에서 hoist한다.

이 entries 조합은 다음 검증으로 보호한다.

- `AppNavigationGraphTest`: 탭 route 소유권, 중복 소유, start route 계약 검증
- `SceneStrategyTest`: inactive route와 inactive bottom sheet가 렌더링되지 않는지 검증
- `TodoUiTest`: 탭 왕복 후 현재 탭 화면만 보이는지 검증
- `CalendarUiTest`: 캘린더 탭 상호작용 회귀 검증

## ViewModel Scope 정책

- 각 top-level route의 원본 `NavBackStack`은 `rememberNavBackStack(route)`로 생성한다.
- 각 top-level route는 별도 `AppNavEntryStackState`를 가진다.
- `AppNavEntryStackState`는 `rememberSaveableStateHolderNavEntryDecorator<NavKey>()`, `rememberViewModelStoreNavEntryDecorator<NavKey>()`를 보관한다. Top-level entries는 Navigation 3의 `rememberDecoratedNavEntries(backStack = ...)` 경로로 생성한다.
- Transient route는 `entryProvider(route)`를 다시 호출하지 않도록 `AppNavEntryStackState`의 `NavEntry` cache를 사용한다. route가 transient stack에서 제거될 때만 cache에서 제거한다.
- `NavDisplay`에는 복사된 key stack이 아니라 decorated `NavEntry` 목록을 넘긴다. 이 목록 안의 `contentKey`가 `hiltViewModel()`의 entry-scoped `ViewModelStoreOwner`를 안정적으로 유지한다.
- bottom sheet는 `transientStack`의 마지막 entry에 `BottomSheetRouteMetadata.bottomSheet()`가 있을 때만 overlay로 렌더링한다.
- bottom sheet는 content 크기 기준으로 올라온다. `BottomSheetSceneStrategy`는 `ModalBottomSheet`에 `wrapContentHeight()`, `skipPartiallyExpanded = true`, `dragHandle = null`, `contentWindowInsets = WindowInsets(0)`을 적용한다. `skipPartiallyExpanded`는 content가 화면 절반을 넘을 때 Material3가 부분 확장 anchor에 멈춰 editor 하단이 잘리는 문제를 막기 위한 설정이다.
- 탭 전환 시 transient route는 모든 탭 stack에서 제거한다. 열린 bottom sheet가 비활성 탭에 남아 재등장하거나 이전 화면의 ViewModel scope를 흔드는 일을 막기 위한 정책이다.
- Calendar의 선택 날짜는 `CalendarViewModel`의 `SavedStateHandle` 기반 state flow에 저장한다. 이는 configuration/process state 복원을 위한 보조 장치이며, 화면 composable의 `rememberSaveable`로 ViewModel 재생성을 가리는 방식은 사용하지 않는다.
- Todo의 `All/Today/Completed`는 서로 다른 top-level route이므로 route의 preset filter가 첫 UI state부터 ViewModel과 일치해야 한다. `TodoListRoute`는 `uiState`를 collect하기 전에 `TodoListViewModel.setRouteFilter(presetFilter)`를 동기 적용한다. `LaunchedEffect`로 filter를 나중에 바꾸면 Today/Completed 진입 첫 프레임이 `ALL` content로 렌더링될 수 있으므로 사용하지 않는다.

## 의도적으로 선택한 동작

- 탭 간 이동 시 이전 탭의 하위 stack은 유지한다.
- 같은 탭을 다시 선택하면 그 탭의 하위 stack은 root까지 접힌다.
- 최상위 탭 화면의 saveable state와 ViewModelStore는 유지한다.
- `AppNavHost`는 feature 구현 상세를 직접 참조하지 않고 `AppFeatureEntry`만 조립한다.
- 앱 셸에서 feature route를 새로 알게 되는 지점은 `AppTabDestination`과 feature API route뿐이다.
- bottom sheet route는 임시 route로 선언하고, top-level route와 같은 타입으로 선언할 수 없다.

## 테스트 체크리스트

구조 변경 후 최소 검증:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :feature:todo:impl:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest
./gradlew :app:lintDebug :feature:todo:impl:lintDebug :feature:calendar:impl:lintDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.neo.yourtodo.TodoUiTest#calendar_closeButton_afterAddTaskForDate_keepsSelectedDate,com.neo.yourtodo.TodoUiTest#calendar_dateSelection_afterBottomSheetDismiss_updatesAgendaLabel,com.neo.yourtodo.TodoUiTest#calendar_dateSelection_afterBottomSheetOpenAndTabSwitch_updatesAgendaLabel,com.neo.yourtodo.TodoUiTest#calendar_dateSelection_afterRepeatedTabRoundTrips_updatesAgendaLabel,com.neo.yourtodo.TodoUiTest#bottomSheet_openThenSwitchTab_closesOverlayAndDoesNotReappear,com.neo.yourtodo.TodoUiTest#bottomSheet_afterRepeatedTabRoundTrips_opensAndCloses'
./gradlew :app:connectedDebugAndroidTest
```

새 route를 추가할 때 추가 검증:

- 새 탭이면 `AppNavigationGraphTest`에 소유권 테스트를 추가한다.
- 새 bottom sheet면 `SceneStrategyTest`에 활성/비활성 overlay 테스트를 추가한다.
- 새 사용자 플로우면 해당 feature UI 테스트에 탭 이동 후 상호작용 검증을 추가한다.

## 남은 리스크와 후속 과제

- Navigation 3 API가 아직 빠르게 변할 수 있으므로 의존성 업그레이드 시 `NavDisplay(entries = ...)`, `SceneStrategy`, decorator API 변경을 먼저 확인한다.
- 현재 탭 전환 정책은 하위 화면을 접는 UX다. 탭별 하위 stack까지 완전히 보존하는 UX로 바꾸려면 `AppNavigator.navigateToTopLevel()`의 collapse 정책과 UI 테스트 기대값을 함께 변경해야 한다.
- 장기적으로 Todo의 `All/Today/Completed`는 하나의 feature가 여러 top-level route를 소유하는 대표 사례다. 필터가 더 늘어나면 route/filter 모델을 명시적인 sealed hierarchy로 재정리하는 편이 좋다.
- 메모리 릭 검증은 현재 단위 테스트와 UI 테스트 중심이다. LeakCanary 또는 heap 기반 instrumentation 검증은 별도 품질 게이트로 추가할 수 있다.
