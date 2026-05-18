# Rework Metrics: codex/fix-todo-calendar-bugs

## Branch Summary

| Field | Value |
|---|---|
| Branch | `codex/fix-todo-calendar-bugs` |
| PR | `109` |
| Primary AI Model | `GPT-5` |
| Task Type | `bugfix` |
| Rework Count | `6` |
| P0 Issues | `1` |
| P1 Issues | `4` |
| P2 Issues | `1` |
| Automation Possible Issues | `5` |
| Automation Added Issues | `4` |
| Open Events | `0` |

## Rework Events

### R20260518143000-1 - Round 2 review: missing Compose dp import

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `subagent: Maxwell` |
| Severity | `P0` |
| Attribution | `AI` |
| Root Cause Category | `compile coverage during UI refactor` |
| Automation Possible | `Yes` |
| Automation Added | `No: existing Kotlin compile gate catches this` |
| Fix Scope | `feature/todo/impl/src/main/java/com/neo/yourtodo/feature/todo/impl/ui/screen/TodoListRows.kt` |
| Fix Size | `Tiny` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :feature:todo:impl:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :feature:friends:impl:testDebugUnitTest :core:data:testDebugUnitTest :core:database:testDebugUnitTest :core:ui:compileDebugKotlin :app:compileDebugAndroidTestKotlin --no-daemon`; affected module lint |

#### Feedback Summary

The swipe-to-delete removal refactor left `dp` usages without the required Compose import, which would break Kotlin compilation.

#### Fix Summary

Restored the missing `androidx.compose.ui.unit.dp` import and reran compile/test/lint gates.

#### Lesson

UI refactors that remove several Compose imports need an immediate module compile before broader behavior review.

### R20260518143000-2 - Round 2 review: friend observed completed ordering and layer boundary

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `subagent: Ptolemy/Herschel` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `presentation ordering and architecture boundary` |
| Automation Possible | `Yes` |
| Automation Added | `Yes` |
| Fix Scope | `feature/friends/impl/src/main/java/com/neo/yourtodo/feature/friends/impl/ui/FriendsViewModel.kt`, `feature/friends/impl/src/test/java/com/neo/yourtodo/feature/friends/impl/ui/FriendsViewModelTest.kt` |
| Fix Size | `Small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :feature:friends:impl:testDebugUnitTest :feature:friends:impl:lintDebug --no-daemon` |

#### Feedback Summary

Friend-visible owned personal todos needed completed items at the bottom, and that ordering should live in presentation state rather than core data.

#### Fix Summary

Added `FriendsViewModel` ordering for observed friend todos and a regression test proving incomplete rows render before completed rows.

#### Lesson

User-facing section ordering belongs in feature presentation code unless the domain contract explicitly defines it.

### R20260518143000-3 - Round 2 review: stale observed cache after visibility grant refresh

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `subagent: Herschel` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `sync cache invalidation` |
| Automation Possible | `Yes` |
| Automation Added | `Yes` |
| Fix Scope | `core/data/src/main/java/com/neo/yourtodo/core/data/repository/PersonVisibilityRepositoryImpl.kt`, `core/database/src/main/java/com/neo/yourtodo/core/database/dao/PersonVisibilityDao.kt`, `core/data/src/test/java/com/neo/yourtodo/core/data/repository/PersonVisibilityRepositoryImplTest.kt`, `core/database/src/test/java/com/neo/yourtodo/core/database/dao/PersonVisibilityDaoTest.kt` |
| Fix Size | `Medium` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :core:data:testDebugUnitTest :core:database:testDebugUnitTest :core:data:lintDebug :core:database:lintDebug --no-daemon` |

#### Feedback Summary

Grant refresh could leave stale `observed_todos` rows cached when a received active grant disappeared or became inactive.

#### Fix Summary

Replaced grant refresh with a transaction that also prunes observed rows outside active received grant ids, plus repository and DAO regression tests.

#### Lesson

Relationship/visibility cache updates should prune dependent caches in the same transaction that replaces the parent grants.

### R20260518152400-4 - Round 3 review: steady-state deleted tombstone coverage

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `subagent: Nash` |
| Severity | `P1` |
| Attribution | `AI` |
| Root Cause Category | `missing regression coverage` |
| Automation Possible | `Yes` |
| Automation Added | `Yes` |
| Fix Scope | `/Users/kimtaenyun/server/src/lib/person-visibility/service.test.ts` |
| Fix Size | `Small` |
| Rework Commit | `HEAD` |
| Verification | `npx vitest run src/lib/person-visibility/service.test.ts && npm run typecheck` |

#### Feedback Summary

The server had tests for changed-grant replacement and deleted tombstones, but not the steady-state case where the grant is unchanged and only the owner's personal todo is deleted after the cursor.

#### Fix Summary

Added a regression test that keeps the grant updatedAt before the cursor, deletes the owner todo after the cursor, and verifies a tombstone is returned without a grant purge.

#### Lesson

Sync tests need both relationship-change snapshots and item-only cursor deltas; they catch different data consistency failures.

### R20260518154500-5 - PR review: Today planner date determinism

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P1` |
| Attribution | `Mixed` |
| Root Cause Category | `instrumentation clock control` |
| Automation Possible | `Yes` |
| Automation Added | `No: app Today UI currently uses the device clock and would need a broader injectable clock seam` |
| Fix Scope | `app/src/androidTest/java/com/neo/yourtodo/TodoUiTest.kt` |
| Fix Size | `None` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.neo.yourtodo.TodoUiTest#todayPlanner_showsOverdueTimedAndDueTodaySections --no-daemon`; GitHub Actions Android CI |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86CvVgX`
- Review Comment: `3256835272`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/109#discussion_r3256835272
- Review Path: `app/src/androidTest/java/com/neo/yourtodo/TodoUiTest.kt:1541`
- Resolved At Capture: `false`
- Outdated At Capture: `false`

#### Feedback Summary

The reviewer suggested replacing `LocalDate.now()` with a fixed absolute date to avoid non-deterministic Today planner UI test behavior.

#### Fix Summary

No code change was applied because the production Today planner currently derives today's date from the device clock. A fixed absolute date in the instrumentation fixture would become a future/past date on later CI runs and fail to exercise the Today surface. The thread was answered with this tradeoff and the existing targeted AVD/CI verification.

#### Lesson

Deterministic Today UI tests need an injectable app clock before absolute fixed dates can be used safely.

### R20260518154500-6 - PR review: simplify completed-last calendar ordering

| Field | Value |
|---|---|
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Severity | `P2` |
| Attribution | `AI` |
| Root Cause Category | `minor implementation efficiency` |
| Automation Possible | `Yes` |
| Automation Added | `Yes` |
| Fix Scope | `feature/calendar/impl/src/main/java/com/neo/yourtodo/feature/calendar/impl/ui/CalendarMonthUiMapper.kt` |
| Fix Size | `Tiny` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :feature:calendar:impl:testDebugUnitTest :feature:calendar:impl:lintDebug --no-daemon` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86CvVgk`
- Review Comment: `3256835287`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/109#discussion_r3256835287
- Review Path: `feature/calendar/impl/src/main/java/com/neo/yourtodo/feature/calendar/impl/ui/CalendarMonthUiMapper.kt:103`
- Resolved At Capture: `false`
- Outdated At Capture: `false`

#### Feedback Summary

The reviewer suggested replacing two list passes with a stable `sortedBy { it.isDone }` implementation.

#### Fix Summary

Updated the calendar agenda completed-last helper to use stable `sortedBy { it.isDone }` while preserving the existing mapper regression test.

#### Lesson

Small presentation ordering helpers should prefer standard stable sort helpers when they express the intent directly.

## External Event Coverage

### Review Threads

- `PRRT_kwDOSAf4v86CvVgX` -> `R20260518154500-5`
- `PRRT_kwDOSAf4v86CvVgk` -> `R20260518154500-6`

### Actionable PR Comments

No actionable PR comments recorded yet.

### Check Failures

No check failures recorded yet.

## Non-Rework Follow-up Commits

No non-rework follow-up commits recorded yet.
