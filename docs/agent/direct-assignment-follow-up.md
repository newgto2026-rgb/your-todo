# Direct Assignment Follow-up Context

Last updated: 2026-05-15

## User goal
- Add a consent-based direct assignment feature.
- Debate heavily with planner, server/data, design, and QA agents before implementation.
- Produce PRD first, implement the feature, validate Todo/Calendar/Friends/assigned-todo consistency, then create a PR.
- Keep important context in docs so later context summaries can continue without losing decisions.

## Branch and workspace
- Worktree: `/Users/kimtaenyun/.codex/worktrees/b074/MyFirstApp`
- Branch: `codex/force-assignment`
- Do not work on `main` directly.

## Agent debate decisions
- User-facing wording is `자동 할당`, not `강제 할당`.
- Code/API mode is `AssignmentMode.DIRECT`.
- Direct assignment is not a new lifecycle status. It is a creation mode.
- `REQUEST` creates `PENDING_ACCEPTANCE`; `DIRECT` should create `ACCEPTED`.
- Direct consent is directional:
  - `grantedToMe=ACTIVE`: I can direct-assign to this friend.
  - `grantedByMe=ACTIVE`: this friend can direct-assign to me.
- Server remains the source of truth. Android may block obvious invalid actions, but server must enforce permission.
- `DIRECT + ACCEPTED` must not display as `수락됨`; use `할당됨` or `자동 할당`.
- Todo row and Calendar agenda must use the same source/mode label.
- Calendar month grid and widget do not need mode colors, but they must include direct due-date items and exclude pending request items.
- Pending decision UI must show only `REQUEST + PENDING_ACCEPTANCE`.
- Round 5 added by user: profile and friends tab must both support auto-assignment allow/cancel, and notifications must exist.
- Profile role: global management of friends who can add tasks directly to my list.
- Friends role: relationship-specific permission state and assignment execution.
- Push role: separate permission events from direct assigned todo arrival.
- Keep using agents for implementation review; do not treat this as a solo implementation task.
- Final review decision: direct consent request notifications must open the Profile permission surface, not only the Friends tab.
- Final review decision: server must dedupe DIRECT create retries by idempotency and semantic duplicate policy; Android does not persist a stable manual-retry operation id in this MVP.
- Final review decision: consent request/accept/reject/revoke endpoints must also define idempotent replay and conflict semantics because Android sends per-action `Idempotency-Key` values.
- Final review decision: `AssignmentRepository` implementers must handle the mode-aware create method directly so DIRECT cannot silently fall back to REQUEST.
- Final review decision: Friends copy must use neutral sent/received wording because those sections contain both REQUEST and DIRECT items.

## Current implementation scope
- `core:model`
  - Added `AssignmentMode`.
  - Added `AssignedTodo.assignmentMode`.
  - Added `Friend.directAssignment`.
- `core:domain`
  - Added `assignmentMode` path to create assignment.
  - Added `ManageDirectAssignmentConsentUseCase`.
- `core:network`
  - Added create request mode, assigned todo mode, friend consent summary, consent action endpoints.
- `core:data`
  - Maps mode and consent summary.
  - Preserves existing mode for partial mutation responses.
- `core:database`
  - Version 11 with `assigned_todo.assignmentMode`.
- `feature:friends:impl`
  - Friend detail permission controls.
  - Send sheet mode selection.
  - Directional direct send guard.
  - Mode chip in assignment cards.
  - Pending list excludes direct items.
- `feature:todo:impl`
  - Row source labels distinguish request/direct.
  - Editor state preserves assignment mode.
- `feature:calendar:impl`
  - Agenda source label distinguishes request/direct.
  - Tests should cover direct inclusion and pending exclusion.
- `feature:calendar:widget`
  - Tests should cover direct inclusion and pending exclusion.
- `app`
  - Profile menu shows incoming direct-assignment permission requests and active permissions.
  - Profile menu can allow, decline, and turn off permission using the same consent use case as Friends.
  - Push local formatting includes direct-assignment consent and direct-received events.
  - Push routing sends DIRECT received events to Todo, not incoming assignment decision.
  - Push routing opens Profile permissions for DIRECT consent request events.

## Strict cross-surface review required
Before final PR, review with agents and locally check:
- Todo, Calendar, Calendar widget all use the same visible received assigned todo policy.
- REQUEST pending is excluded from all task surfaces.
- DIRECT received is included as accepted and never opens decision UI.
- Friends and Profile use the same server-backed consent state and action use case.
- Permission direction is not inverted between profile, friends detail, and send sheet.
- Profile permission changes must make Friends direct-send state stale-safe after refresh.
- Push events must not reuse REQUEST semantics for DIRECT.
- Implementation must not add a tangled app-shell dependency on feature internals; shared behavior belongs in `core:domain`/`core:model`.
- Any temporary Android-side assumption about server APIs must be documented as contract, not silently inferred.
- Server contract to preserve: DIRECT create replay must be idempotent and duplicate-safe. If backend behavior changes, re-check Todo/Calendar/Widget duplicate exposure.
- Server contract to preserve: consent actions replay same-key/same-action safely, conflict same-key/different-action, and return latest summary for already-converged states.

## Final review fixes tracked in code
- DIRECT received push with an assignment-bundle deep link routes to Todo and has an instrumentation regression test.
- DIRECT consent request push sets `openProfileMenuOnLaunch` so the user can allow/decline from Profile immediately.
- Cold-start consent push keeps Profile drawer open until signed-in permission state is loaded, instead of closing on the first unauthenticated placeholder state.
- Profile menu has allow, reject, and revoke ViewModel tests.
- Friends ViewModel has request, accept, reject, and revoke consent refresh tests.
- Friends detail copy uses neutral sent/received wording so DIRECT and REQUEST history do not contradict each other.
- Todo clear-completed now refreshes Calendar widget when only completed assigned todos are hidden.
- REQUEST vs DIRECT user copy is separated across Friends, Todo, Calendar, and push text.

## Files to watch
- PRD: `docs/PRD-Direct-Assignment-MVP.md`
- TRD: `docs/TRD-Direct-Assignment-MVP.md`
- Follow-up context: `docs/agent/direct-assignment-follow-up.md`
- Room schema: `core/database/schemas/com.neo.yourtodo.core.database.AppDatabase/11.json` must exist before PR.
- PR template: `.github/pull_request_template.md`

## Required checks before PR
- Completed: `./gradlew :core:domain:test :core:network:testDebugUnitTest :core:database:testDebugUnitTest :core:data:testDebugUnitTest :feature:friends:impl:testDebugUnitTest :feature:todo:impl:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :feature:calendar:widget:testDebugUnitTest :app:testDebugUnitTest`
- Completed: `./gradlew :app:lintDebug :core:network:lintDebug :core:database:lintDebug :core:data:lintDebug :feature:friends:impl:lintDebug :feature:todo:impl:lintDebug :feature:calendar:impl:lintDebug :feature:calendar:widget:lintDebug`
- Completed: `./gradlew assembleDebug :app:assembleDebugAndroidTest`
- Completed on `Medium_Phone_API_36(AVD) - 16`: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.neo.yourtodo.PushNotificationLaunchUiTest`
- Completed after final cold-start/Profile drawer and Friends copy fixes: `./gradlew :app:testDebugUnitTest :feature:friends:impl:testDebugUnitTest :app:lintDebug :feature:friends:impl:lintDebug assembleDebug`

## PR acceptance checklist
- PRD/TRD included.
- DIRECT mode survives network, data, DB, UI mapper, and app restart cache paths.
- REQUEST behavior remains unchanged.
- DIRECT cannot be sent with inverse or missing consent.
- DIRECT server failure leaves editor open and shows error.
- DIRECT never appears in received request decision UI.
- REQUEST pending never appears in Todo, Calendar, or Widget.
- Todo/Calendar/Friends all show compatible labels.
- Profile and Friends both expose permission allow/cancel where relevant.
- Direct-assignment notification types and click routing are tested.
- User-visible strings are in `values` and `values-ko`.
