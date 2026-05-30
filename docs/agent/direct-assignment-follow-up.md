# Direct Assignment Follow-up Context

Last updated: 2026-05-15

## User goal
- Add a consent-based direct assignment feature.
- Debate heavily with planner, server/data, design, and QA agents before implementation.
- Produce PRD first, implement the feature, validate Todo/Calendar/Friends/assigned-todo consistency, then create a PR.
- Keep important context in docs so later context summaries can continue without losing decisions.

## Branch and workspace
- Worktree: `<local-worktree>`
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
- Round 6 user correction: Profile/popup auto-assignment permissions are awkward and malfunctioning; remove them.
- New UX direction: Friends list is the only auto-accept permission surface.
- Receiver-driven permission: I turn `자동수락` on/off per friend; that friend can then add tasks to my list without per-task acceptance.
- No request/allow/decline permission UX. Android should use `setDirectAssignmentOptIn(friendUserId, enabled)` only.
- Friends role: relationship-specific auto-accept permission state and assignment execution.
- Push role: separate permission events from direct assigned todo arrival.
- Keep using agents for implementation review; do not treat this as a solo implementation task.
- Superseded decision: direct consent request notifications opening Profile are no longer valid because Profile no longer owns this UX.
- Final review decision: server must dedupe DIRECT create retries by idempotency and semantic duplicate policy; Android does not persist a stable manual-retry operation id in this MVP.
- Revised server decision: consent opt-in endpoint must define idempotent replay and conflict semantics because Android sends per-action `Idempotency-Key` values.
- Final review decision: `AssignmentRepository` implementers must handle the mode-aware create method directly so DIRECT cannot silently fall back to REQUEST.
- Final review decision: Friends copy must use neutral sent/received wording because those sections contain both REQUEST and DIRECT items.
- 2026-05-15 server correction: Android default debug server is `https://<your-dev-url>/`; this ngrok forwards to local Docker `yourtodo-server-1` on port 8080. The old container was 5 days stale and returned 404 for the opt-in endpoint. It was rebuilt/restarted from the server worktree and now returns `401 AUTH_REQUIRED` for `PUT /api/friends/{friendUserId}/direct-assignment-opt-in`, proving the route exists.
- 2026-05-15 login correction: emulator Google sign-in reached `POST /api/auth/google` but server returned `401` because local Docker was missing ignored `infra/env/server.env` and therefore had no `GOOGLE_WEB_CLIENT_ID`. Galaxy could still appear healthy when it reused an existing app session/refresh token instead of calling Google sign-in. Local server env was created from `infra/env/server.env.example` with `GOOGLE_WEB_CLIENT_ID` matching Android `default_web_client_id`, then `yourtodo-server-1` was force-recreated.
- 2026-05-15 auto-accept correction: a stale or alternate Android create path could still send `assignmentMode=REQUEST` after the receiver enabled auto-accept, causing `PENDING_ACCEPTANCE` and showing in the received request list. Server now treats receiver opt-in as the final source of truth and promotes REQUEST/omitted mode creates to `DIRECT/ACCEPTED` when allowed. Android AI todo save also now sends `AssignmentMode.DIRECT` for friends whose `canDirectAssignToFriend` is active.
- 2026-05-15 push correction: recent `ASSIGNMENT_BUNDLE_RECEIVED` event failed because Docker env lacked FCM credentials even though push tokens existed. The ignored server env was synced from the push-notifications worktree, `yourtodo-server-1` now has FCM project/client env injected, and future notification events should dispatch instead of failing with `Firebase Cloud Messaging credentials are not configured`.

## Current implementation scope
- `core:model`
  - Added `AssignmentMode`.
  - Added `AssignedTodo.assignmentMode`.
  - Added `Friend.directAssignment`.
- `core:domain`
  - Added `assignmentMode` path to create assignment.
  - Added `SetDirectAssignmentOptInUseCase`.
- `core:network`
  - Added create request mode, assigned todo mode, friend consent summary, and direct-assignment opt-in endpoint.
- `core:data`
  - Maps mode and consent summary.
  - Preserves existing mode for partial mutation responses.
- `core:database`
  - Version 11 added `assigned_todo.assignmentMode`.
- `feature:friends:impl`
  - Friend list auto-accept controls.
  - Send sheet infers mode from `grantedToMe`; no manual mode selection.
  - Directional direct send guard.
  - Mode chip in assignment cards.
  - Pending list excludes direct items.
- `feature:todo:impl`
  - Row source labels distinguish request/direct.
  - Editor state preserves assignment mode.
  - AI friend-assigned drafts send `AssignmentMode.DIRECT` when the selected friend has allowed direct assignment to the current user.
- `feature:calendar:impl`
  - Agenda source label distinguishes request/direct.
  - Tests should cover direct inclusion and pending exclusion.
- `feature:calendar:widget`
  - Tests should cover direct inclusion and pending exclusion.
- `app`
  - Profile menu must not show direct-assignment permission requests or active permissions.
  - Profile menu must not mutate direct consent.
  - Push local formatting includes direct-received events.
  - Push routing sends DIRECT received events to Todo, not incoming assignment decision.
  - Direct opt-in request/accept/reject push flows are superseded; do not route them to Profile.

## Server implementation scope
- Server worktree: `<server-worktree>`
- Server branch: `codex/direct-assignment-opt-in`, based on `origin/main` at `14f1784`.
- Added Prisma `AssignmentMode` and `DIRECT_ASSIGNMENT_RECEIVED`.
- Added directional auto-accept booleans to `Friendship`:
  - `userAAllowsUserBDirectAssignment`
  - `userBAllowsUserADirectAssignment`
- Added migration `prisma/migrations/20260515010000_direct_assignment_opt_in/migration.sql`.
- Added `PUT /api/friends/{friendUserId}/direct-assignment-opt-in` with body `{ "enabled": true|false }`.
- `GET /api/friends` returns `directAssignment.canFriendDirectAssignToMe` and `canDirectAssignToFriend`.
- `POST /api/assignment-bundles` accepts `assignmentMode`.
  - `REQUEST` keeps existing `SENT + PENDING_ACCEPTANCE` behavior.
  - `DIRECT` requires receiver opt-in, creates `ACCEPTED` bundle/items immediately, and emits `DIRECT_ASSIGNMENT_RECEIVED`.
- Friend removal clears both direct-assignment opt-in directions.

## Strict cross-surface review required
Before final PR, review with agents and locally check:
- Todo, Calendar, Calendar widget all use the same visible received assigned todo policy.
- REQUEST pending is excluded from all task surfaces.
- DIRECT received is included as accepted and never opens decision UI.
- Friends is the only consent mutation surface; Profile should not read or display consent state.
- Permission direction is not inverted between Friends list and send sheet.
- Friends list permission changes must make send sheet mode stale-safe after refresh.
- Push events must not reuse REQUEST semantics for DIRECT.
- Implementation must not add a tangled app-shell dependency on feature internals; shared behavior belongs in `core:domain`/`core:model`.
- Any temporary Android-side assumption about server APIs must be documented as contract, not silently inferred.
- Server contract to preserve: DIRECT create replay must be idempotent and duplicate-safe. If backend behavior changes, re-check Todo/Calendar/Widget duplicate exposure.
- Server contract to preserve: opt-in actions replay same-key/same-enabled safely, conflict same-key/different-enabled, and return latest summary for already-converged states.

## Current correction work tracked in code
- Remove Profile direct assignment section, ViewModel dependencies, and tests.
- Remove `openProfileMenuOnLaunch` routing for direct consent.
- Replace request/accept/reject/revoke UX with opt-in enabled/disabled.
- Add Friends list tests for auto-accept enabled/disabled.
- Keep DIRECT received push routing to Todo and notification copy.
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
- Android passed:
  - `./gradlew :core:domain:test :core:network:testDebugUnitTest :core:database:testDebugUnitTest :core:data:testDebugUnitTest :feature:friends:impl:testDebugUnitTest :feature:todo:impl:testDebugUnitTest :feature:calendar:impl:testDebugUnitTest :feature:calendar:widget:testDebugUnitTest :app:testDebugUnitTest`
  - `./gradlew :app:lintDebug :core:network:lintDebug :core:database:lintDebug :core:data:lintDebug :feature:friends:impl:lintDebug :feature:todo:impl:lintDebug :feature:calendar:impl:lintDebug :feature:calendar:widget:lintDebug`
  - `./gradlew assembleDebug :app:assembleDebugAndroidTest`
  - Galaxy device: full `connectedDebugAndroidTest` 69/69 passed.
  - Emulator: full `connectedDebugAndroidTest` had one Todo UI timeout, then `TodoUiTest#detailAdd_highPriorityAppearsOnlyInHighPriorityFilter` passed when rerun alone.
- Server passed:
  - `npm run db:generate`
  - `npx vitest run src/lib/friends/service.test.ts src/lib/assignments/service.test.ts src/lib/push/service.test.ts 'src/app/api/friends/[id]/direct-assignment-opt-in/route.test.ts'`
  - `DATABASE_URL=postgresql://user:pass@localhost:5432/yourtodo npm run db:validate`
  - `npm test`
  - `npm run build`
  - `npm audit --omit=dev`
- Deployment/test server status:
  - `docker compose -f infra/compose.yaml up --build -d` completed.
  - `yourtodo-server-1` is healthy on port 8080.
  - ngrok opt-in route now returns `401 AUTH_REQUIRED`, not stale 404.
  - Google login requires the ignored server env file; without it `POST /api/auth/google` logs `GOOGLE_WEB_CLIENT_ID is not configured` and returns `401`.
  - Push dispatch also requires FCM env in the same ignored server env; without it `notification_events.last_error` becomes `Firebase Cloud Messaging credentials are not configured`.
  - New server migration `20260515020000_direct_assignment_consent_notifications` has been applied locally.
  - Latest Android debug APK installed on Galaxy and emulator.

## PR acceptance checklist
- PRD/TRD included.
- DIRECT mode survives network, data, DB, UI mapper, and app restart cache paths.
- REQUEST behavior remains unchanged.
- DIRECT cannot be sent with inverse or missing consent.
- REQUEST/omitted create is promoted to DIRECT by the server when receiver opt-in is already active, covering stale clients and AI paths.
- DIRECT server failure leaves editor open and shows error.
- DIRECT never appears in received request decision UI.
- REQUEST pending never appears in Todo, Calendar, or Widget.
- Todo/Calendar/Friends all show compatible labels.
- Friends list exposes permission allow/cancel; Profile exposes none.
- Direct-assignment notification types and click routing are tested.
- User-visible strings are in `values` and `values-ko`.
