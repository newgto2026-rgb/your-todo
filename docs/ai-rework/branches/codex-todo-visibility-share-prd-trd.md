# Rework Metrics: codex/todo-visibility-share-prd-trd

## Branch Summary

| Field | Value |
|---|---|
| Branch | `codex/todo-visibility-share-prd-trd` |
| PR | `https://github.com/newgto2026-rgb/your-todo/pull/105` |
| Primary AI Model | `GPT-5` |
| Task Type | `Feature implementation` |
| Rework Count | `7` |
| P0 Issues | `0` |
| P1 Issues | `1` |
| P2 Issues | `6` |
| Automation Possible Issues | `4` |
| Automation Added Issues | `0` |
| Open Events | `0` |

## Rework Events

### R1: Pre-push lint failed on unused friend setting subtitle strings

| Field | Value |
|---|---|
| Source | `pre-push hook: ./gradlew lint` |
| Severity | `P1` |
| Attribution | `AI` |
| Status | `verified` |
| Fix Scope | Removed unused friend quick-setting subtitle strings from English and Korean resources after the compact control redesign stopped rendering them. |
| Fix Size | `Small` |
| Rework Commit | `6df4fbf` |
| Verification | `./gradlew :app:lintDebug` and pre-push full lint hook |
| Lesson | When simplifying UI copy, remove retired localized resources before relying on the full pre-push lint gate. |
| Automation Possible | `Yes` |
| Automation Added | `No: existing lint/pre-push gate already catches this class of issue` |

### R2: Review thread - App sync performed workspace and visibility refresh sequentially

| Field | Value |
|---|---|
| Source | `PRRT_kwDOSAf4v86CpEMG` |
| Severity | `P2` |
| Attribution | `AI` |
| Status | `verified` |
| Fix Scope | Parallelized `RefreshWorkspaceUseCase` and `RefreshPersonVisibilityUseCase` in `AppSyncViewModel`. |
| Fix Size | `Small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :app:testDebugUnitTest` |
| Lesson | Independent sync work should start concurrently at orchestration boundaries. |
| Automation Possible | `No` |
| Automation Added | `No: this is a performance design review rather than a deterministic static rule` |

### R3: Review thread - Person visibility repository used direct time and lacked singleton annotation

| Field | Value |
|---|---|
| Source | `PRRT_kwDOSAf4v86CpEMJ` |
| Severity | `P2` |
| Attribution | `AI` |
| Status | `verified` |
| Fix Scope | Added `PersonVisibilityTimeProvider`, injected it into the repository, and annotated the repository implementation with `@Singleton`. |
| Fix Size | `Small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :app:testDebugUnitTest` |
| Lesson | Cache timestamps should be injectable before tests need to assert them. |
| Automation Possible | `Yes` |
| Automation Added | `No: no project-wide static check currently enforces repository clock injection` |

### R4: Review thread - DataStore auth session flow missed distinctUntilChanged

| Field | Value |
|---|---|
| Source | `PRRT_kwDOSAf4v86CpEMM` |
| Severity | `P2` |
| Attribution | `AI` |
| Status | `verified` |
| Fix Scope | Added `distinctUntilChanged()` before `flatMapLatest` in person visibility grant and observed todo flows. |
| Fix Size | `Small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :app:testDebugUnitTest` |
| Lesson | DataStore-derived session flows should suppress duplicate values before downstream DAO subscriptions. |
| Automation Possible | `No` |
| Automation Added | `No: flow optimization depends on intent and source semantics` |

### R5: Review thread - Unused purge method in repository implementation

| Field | Value |
|---|---|
| Source | `PRRT_kwDOSAf4v86CpEMU` |
| Severity | `P2` |
| Attribution | `AI` |
| Status | `verified` |
| Fix Scope | Removed the unused `purgeObservedTodosByGrantId` method from `PersonVisibilityRepositoryImpl`; DAO purge support remains because revoke uses it. |
| Fix Size | `Small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :app:testDebugUnitTest` |
| Lesson | Keep implementation-only helpers private to an actual call path or remove them before review. |
| Automation Possible | `Yes` |
| Automation Added | `No: current lint did not flag this public implementation-only method` |

### R6: Review thread - Person visibility use case refreshed grants and observed todos sequentially

| Field | Value |
|---|---|
| Source | `PRRT_kwDOSAf4v86CpEMZ` |
| Severity | `P2` |
| Attribution | `AI` |
| Status | `verified` |
| Fix Scope | Parallelized grant refresh and observed todo sync inside `RefreshPersonVisibilityUseCase`, with a unit test proving the observed sync starts while grant refresh is suspended. |
| Fix Size | `Medium` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :app:testDebugUnitTest` |
| Lesson | Use case orchestration needs tests for concurrency when independent repository calls are intentionally parallel. |
| Automation Possible | `No` |
| Automation Added | `No: covered by targeted unit test instead` |

### R7: Review thread - Calendar ViewModel used system default zone directly

| Field | Value |
|---|---|
| Source | `PRRT_kwDOSAf4v86CpEMc` |
| Severity | `P2` |
| Attribution | `AI` |
| Status | `verified` |
| Fix Scope | Added a Hilt-provided calendar `ZoneId` and passed it through selected-date todo mapping. |
| Fix Size | `Medium` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :app:testDebugUnitTest` |
| Lesson | UI mappers that format time should receive timezone context explicitly from the ViewModel. |
| Automation Possible | `Yes` |
| Automation Added | `No: no project-wide static check currently blocks ZoneId.systemDefault() in UI code` |

## External Event Coverage

### Review Threads

- `PRRT_kwDOSAf4v86CpEMG` -> R2
- `PRRT_kwDOSAf4v86CpEMJ` -> R3
- `PRRT_kwDOSAf4v86CpEMM` -> R4
- `PRRT_kwDOSAf4v86CpEMU` -> R5
- `PRRT_kwDOSAf4v86CpEMZ` -> R6
- `PRRT_kwDOSAf4v86CpEMc` -> R7

### Actionable PR Comments

No top-level actionable PR comments recorded.

### Check Failures

- `pre-push hook: ./gradlew lint` reported `UnusedResources` for four retired friend setting subtitle strings -> R1.

## Non-Rework Follow-up Commits

No non-rework follow-up commits recorded yet.
