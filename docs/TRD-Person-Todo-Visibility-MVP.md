# TRD - Person Todo Visibility MVP

## 1. Document Info
- Document: Technical Requirements - Person Todo Visibility MVP
- Product: `YourTodo` Android app + `yourtodo-server`
- Android branch: `codex/todo-visibility-share-prd-trd`
- Date: 2026-05-17
- Status: PRD reflected, pre-implementation contract

## 2. Technical Goals
- Implement person-level `VisibilityGrant`, not item-level Todo sharing.
- Use `VisibilityGrant.status == ACTIVE` as the MVP access condition.
- Provide owner active personal Todo projection as read-only `ObservedTodo`.
- Keep ObservedTodo separate from owned Todo storage, UI actions, metrics, assignment flows, and Widget.
- Render friend todos inside per-friend expandable active friend rows when displayable items exist.
- Render Calendar sections conditionally instead of exposing a complex source filter, while preserving expanded month and compact week calendar states.
- Immediately block server observed read paths after revoke commit.
- Purge Android observed cache on purge event, foreground, sync, or reconnect.

## 3. Core Technical Decisions
- User-facing viewer label: `친구 할일`.
- Owner-facing switch label: `내 할일 보여주기`.
- Internal namespace: `personvisibility`.
- Core models: `VisibilityGrant`, `ObservedTodo`.
- Do not introduce `TodoShare`, `AssignedTodo`, `AssignmentBundle`, `assignmentMode`, or assignment acceptance states.
- Do not require a viewer `보기 시작`/acceptance state in MVP.
- Do not implement Todo-level public/private policy in MVP.
- Do not show ObservedTodo in Widget in MVP.
- No new Android feature module is required for MVP UI. Friends owns the per-friend expandable row UI surface; core layers own the data contract.

## 4. Domain Model
### 4.1 VisibilityGrant
```text
VisibilityGrant
- id
- ownerUserId
- viewerUserId
- status: ACTIVE | REVOKED
- createdAt
- revokedAt nullable
- version
```

Policy:
- Directional permission from Owner to Viewer.
- A -> B and B -> A are separate grants.
- One active grant per owner/viewer pair.
- Owner creates/revokes grant through Friends.
- Active grant allows the server to return allowed ObservedTodo projections to Viewer.

### 4.2 ObservedTodo
```text
ObservedTodo
- observedTodoId
- sourceTodoId
- grantId
- ownerUserId
- ownerNickname
- ownerAvatarUrl nullable
- title
- dueDate nullable
- dueTime nullable
- isDone
- recurrenceOccurrenceId nullable
- projectionVersion
- updatedAt
```

Policy:
- Server-side read-only projection.
- Viewer mutation endpoint does not exist.
- `sourceTodoId` is for server validation and sync identity only; viewer UI must not use it as an owned Todo id.
- Rows always expose source owner identity for UI distinction.

### 4.3 CalendarItemSource
```text
CalendarItemSource
- OWNED
- OBSERVED
```

Calendar projections must retain source so UI can section items as `내 할일` and `친구 할일`.

## 5. Server API Contract
All endpoints require `Authorization: Bearer <accessToken>`.

### 5.1 Visibility Grants
```http
GET /api/person-visibility/grants
```

Response:
```json
{
  "given": [
    {
      "id": "grant-id",
      "ownerUserId": "me",
      "viewerUserId": "friend-id",
      "status": "ACTIVE",
      "createdAt": "2026-05-17T00:00:00.000Z",
      "version": 3
    }
  ],
  "received": [
    {
      "id": "grant-id-2",
      "ownerUserId": "friend-id",
      "viewerUserId": "me",
      "status": "ACTIVE",
      "createdAt": "2026-05-17T00:00:00.000Z",
      "version": 1
    }
  ]
}
```

Policy:
- `given` drives owner-side switches.
- `received` determines which owner projections can appear in Friends/Calendar.
- No Todo payload is included in this endpoint.

### 5.2 Create Visibility Grant
```http
POST /api/person-visibility/grants
Idempotency-Key: <client-generated-uuid>
```

Request:
```json
{
  "viewerUserId": "friend-id"
}
```

Policy:
- Caller is the owner.
- Viewer must be an active friend.
- Duplicate active owner/viewer grant returns the existing active grant.
- Same idempotency key with different payload returns `409 IDEMPOTENCY_CONFLICT`.
- Response contains grant metadata only.

### 5.3 Revoke Visibility Grant
```http
DELETE /api/person-visibility/grants/{grantId}
Idempotency-Key: <client-generated-uuid>
```

Policy:
- Owner only.
- Revoke commit immediately blocks all observed read paths for this grant.
- Grant version increments.
- Observed sync token is invalidated.
- Purge event is emitted.
- Already revoked grants return idempotent success.

Response:
```json
{
  "grantId": "grant-id",
  "status": "REVOKED",
  "version": 4,
  "revokedAt": "2026-05-17T00:01:00.000Z"
}
```

### 5.4 ObservedTodo Sync
```http
GET /api/observed-todos/sync?cursor=<cursor>&windowStart=<yyyy-mm-dd>&windowEnd=<yyyy-mm-dd>
```

Access condition:
```text
VisibilityGrant.status == ACTIVE
AND VisibilityGrant.viewerUserId == currentUserId
```

Response:
```json
{
  "items": [
    {
      "observedTodoId": "observed-id",
      "sourceTodoId": "todo-id",
      "grantId": "grant-id",
      "owner": {
        "id": "owner-id",
        "nickname": "민지",
        "avatarUrl": null
      },
      "title": "병원 예약",
      "dueDate": "2026-05-20",
      "dueTime": "10:30",
      "isDone": false,
      "recurrenceOccurrenceId": null,
      "projectionVersion": 11,
      "updatedAt": "2026-05-17T00:00:00.000Z"
    }
  ],
  "deleted": [
    {
      "observedTodoId": "observed-id-old",
      "projectionVersion": 12
    }
  ],
  "purgedGrantIds": [
    "revoked-grant-id"
  ],
  "nextCursor": "cursor-2",
  "hasMore": false
}
```

Policy:
- Server allowlist is owner-owned active personal Todo.
- Deleted/archived Todo is excluded.
- Assignment/direct assignment/Todo received from others is excluded.
- Todo-level public/private judgment is not applied in MVP.
- Recurring Todo returns occurrence projections only.
- Reminder settings, read receipt, viewer activity, and full history are excluded.
- If sync returns zero items for a friend, Android should not show a `친구 할일` expand affordance on that friend row.
- If sync returns one or more items for a friend, Android should render a compact affordance on that active friend row and expand that friend's list inline on user action.
- Expanded friend rows must render all fetched displayable ObservedTodo for that owner without an arbitrary preview cap. Data pagination is allowed, but the UI must not hide items behind a fixed top-N limit.

### 5.5 Calendar Items
```http
GET /api/calendar/items?windowStart=<yyyy-mm-dd>&windowEnd=<yyyy-mm-dd>
```

Response includes owned and observed sources:
```json
{
  "items": [
    {
      "id": "owned-calendar-id",
      "source": "OWNED",
      "todoId": "todo-id",
      "title": "장보기",
      "dueDate": "2026-05-20",
      "dueTime": null,
      "allowedActions": ["COMPLETE", "EDIT"]
    },
    {
      "id": "observed-calendar-id",
      "source": "OBSERVED",
      "observedTodoId": "observed-id",
      "owner": {
        "id": "owner-id",
        "nickname": "민지",
        "avatarUrl": null
      },
      "title": "병원 예약",
      "dueDate": "2026-05-20",
      "dueTime": "10:30",
      "allowedActions": []
    }
  ]
}
```

Policy:
- Android sections by `source`.
- No MVP source filter is required in UI.
- Observed calendar item access uses the same active grant condition as observed sync.
- Date/occurrence-less ObservedTodo is not included in Calendar.
- Observed item `allowedActions` is empty.
- If selected window has no observed items, Android does not show `친구 할일` in Calendar agenda.

## 6. Server Read Path Blocking
After revoke commit, these paths must deny access immediately:

```text
observed-todos sync
observed todo detail
calendar observed source
deep link resolution
notification route resolution
search-like observed query
```

Unauthorized access should return generic 404 or generic unavailable. Do not expose whether an owner has a Todo or whether a grant existed.

## 7. Event Contract
```text
VisibilityGrantCreated
VisibilityGrantRevoked
ObservedTodoUpserted
ObservedTodoDeleted
ObservedTodoPurgedByGrant
```

Allowed notification/event usage:
- In-app state update when visibility is turned on/off.
- Purge event for revoked grant.

Disallowed:
- Friend Todo created push.
- Friend Todo updated push.
- Friend Todo completed push.
- Friend reminder push.
- Read receipt event.

Push payload must not include Todo title, due date, memo, status, or other Todo content.

## 8. Android Architecture Impact
### 8.1 Feature Modules
MVP does not require a new `:feature:personvisibility:*` module. The UI is intentionally shallow.

Affected modules:
- `:feature:friends:impl`: owner-side `내 할일 보여주기` switch and viewer-side per-friend expandable `친구 할일` rows.
- `:feature:calendar:impl`: conditional source sections for owned/observed calendar items.
- `:feature:todo:impl`: no ObservedTodo display.
- `:feature:calendar:widget`: no ObservedTodo display in MVP.

If the feature grows into a larger independent surface later, it can be split into `:feature:personvisibility:*`.

### 8.2 Core Modules
- `:core:model`: `VisibilityGrant`, `ObservedTodo`, `CalendarItemSource`.
- `:core:domain`: `PersonVisibilityRepository`, `ObserveVisibilityGrantsUseCase`, `SetVisibilityGrantUseCase`, `RevokeVisibilityGrantUseCase`, `SyncObservedTodosUseCase`.
- `:core:network`: person visibility API/DTO and observed Todo DTO.
- `:core:data`: repository implementation, DTO/entity/domain mapper, sync/purge policy.
- `:core:database`: observed cache tables and grant state.
- `:core:testing`: fake repository and sync helpers when shared by feature tests.

Dependency direction:
- `feature:friends:impl` and `feature:calendar:impl` depend on core contracts/use cases.
- No core module depends on a feature module.
- App shell does not know observed implementation details.

## 9. Android Cache Schema Draft
```text
visibility_grants
- currentUserId
- grantId
- ownerUserId
- viewerUserId
- status
- version
- createdAtEpochMillis
- revokedAtEpochMillis nullable

observed_todos
- currentUserId
- observedTodoId
- sourceTodoId
- grantId
- ownerUserId
- ownerNickname
- ownerAvatarUrl nullable
- title
- dueDateEpochDay nullable
- dueTimeMinutes nullable
- isDone
- recurrenceOccurrenceId nullable
- projectionVersion
- updatedAtEpochMillis
- cacheUpdatedAtEpochMillis

observed_sync_state
- currentUserId
- cursor
- syncedAtEpochMillis
```

Policy:
- Cache is partitioned by `currentUserId`.
- `VisibilityGrantRevoked` deletes observed rows by `grantId`.
- Foreground/sync/token invalidation/TTL removes stale observed cache.
- Recommended TTL for stale observed cache is 24 hours.
- No observed calendar preference table is needed in MVP because Calendar does not expose per-source filters.

## 10. UI State Rules
- Friends keeps the existing YourTodo/Material3 visual language.
- Owner-side `내 할일 보여주기` is rendered as friend/person permission state, not as Todo rows.
- Pending friend request rows keep their existing `수락`/`거절` actions and do not render visibility controls.
- Active friend rows render `자동수락` and `내 할일 보여주기` as compact list-level relationship settings.
- `할일 보내기` continues to open the existing Todo share/assignment dialog; this MVP must not redesign or intercept that dialog.
- `자동수락` and `내 할일 보여주기` must use separate controls because their directions differ:
  - `자동수락`: incoming assigned/shared Todo behavior; can add sent Todos to my list.
  - `내 할일 보여주기`: outgoing read-only visibility; never adds Todos to the friend's list.
- The friend list appears before any friend Todo rows.
- `친구 할일` appears inside each active friend's expandable row only when that friend has observed items or when user explicitly opens that friend context.
- Collapsed friend row contains the normal friend identity/actions plus a compact friend Todo affordance such as count/nearest due summary.
- Expanded friend row renders that friend's observed list inline without navigating away from Friends.
- Expanded friend row renders all fetched displayable ObservedTodo for that friend without a fixed preview limit.
- Main Friends screen should not show a separated global friend Todo area.
- ObservedTodo rows match existing Todo row density but remove checkbox/action affordances.
- Every ObservedTodo row shows owner identity.
- ObservedTodo rows do not show a persistent `보기만` badge in list/calendar surfaces.
- ObservedTodo detail is read-only.
- Outgoing visibility and incoming observed rows must not share the same component:
  - outgoing uses friend avatar/name, switch, and grant status,
  - incoming uses observed row title, owner identity, due metadata, and no owned actions.
- Calendar agenda sections are data-driven:
  - calendar body supports expanded month and compact selected-week states,
  - show `내 할일` only when owned items exist,
  - show `친구 할일` only when observed items exist.
- Calendar `친구 할일` section may use the same collapsed/expanded state pattern when observed rows would crowd the selected agenda.
- Calendar body expansion state is independent from `친구 할일` section expansion state.
- Color alone is not enough to distinguish observed rows.
- Widget UI remains owned-only in MVP.
- User-facing strings must be in `values` and `values-ko`.

## 11. Widget Policy
MVP excludes ObservedTodo from `:feature:calendar:widget`.

Implementation expectation:
- Widget queries owned Todo source only.
- Widget does not read `observed_todos`.
- Widget rows keep existing owned Todo actions only.
- Any future friend Todo widget must be a separate opt-in surface with no complete/edit actions and owner identity on every row.

## 12. Test Strategy
### 12.1 Server Tests
- Active friend grant create succeeds.
- Non-friend grant create fails.
- Duplicate owner/viewer grant is idempotent.
- Observed sync succeeds only for active grant viewer.
- Observed sync returns owner-owned active personal Todo only.
- Assignment/direct assignment/deleted/archived Todo is excluded.
- Observed sync with no items returns empty array without error.
- Revoke blocks observed sync/detail/calendar/deep link immediately.
- Purge event is emitted on revoke.
- Push payload contains no Todo content.
- No read receipt state/event/API exists.

### 12.2 Android Unit Tests
- Active friend visibility switch maps to create/revoke grant actions.
- Pending incoming request rows do not expose `내 할일 보여주기`.
- Active friend list rows expose `자동수락` and `내 할일 보여주기` without replacing friendship acceptance or assignment actions.
- `할일 보내기` still opens the existing Todo share/assignment dialog.
- Friend list is ordered before friend Todo rows.
- Per-friend `친구 할일` affordance appears only on active friend rows with observed items.
- Collapsed friend row exposes count/nearest summary only for that friend.
- Expanded friend row renders all fetched observed rows for that friend inline.
- Main Friends screen does not show a separated global friend Todo area when observed list is empty.
- ObservedTodo row has no toggle/edit/delete/reminder/assignment actions.
- Calendar sections show owned/observed sections only when each source has items.
- Calendar supports expanded month and compact selected-week body states.
- Friend Todo presence does not force Calendar into week-only mode.
- Calendar does not show empty `친구 할일` header.
- Calendar collapsed `친구 할일` summary contains count/nearest due and expands inline.
- Widget source excludes ObservedTodo.
- Todo/Today/Completed metrics exclude ObservedTodo.
- Assignment/direct assignment UI state excludes ObservedTodo.
- Revoke event purges observed cache.
- Offline stale state is removed on reconnect/sync.

### 12.3 Android UI Tests
- Active friend `내 할일 보여주기` switch on/off.
- Incoming friend request row still shows `수락`/`거절` without visibility controls.
- Active friend row shows `자동수락` and `내 할일 보여주기` as separate compact controls.
- Active friend `할일 보내기` still opens the existing Todo share/assignment dialog.
- Friends per-friend `친구 할일` expand/collapse.
- Friend list stays above any expanded friend Todo rows.
- No global `친구 할일` main section when observed list is empty.
- Expanded friend row shows all displayable observed rows for that friend.
- ObservedTodo rows do not render a persistent `보기만` badge.
- ObservedTodo detail sheet is read-only.
- Calendar agenda renders `내 할일` and `친구 할일` sections conditionally.
- Calendar expanded month and compact week states both keep owned/observed distinction.
- Calendar `친구 할일` expand/collapse preserves owned item actions and observed no-action rows.
- Calendar observed rows show owner identity and no complete action.
- Widget does not display friend Todo rows.
- Revoke removes observed rows from Friends and Calendar.

### 12.4 Verification Commands
Affected module baseline:
```bash
./gradlew :core:model:testDebugUnitTest
./gradlew :core:domain:testDebugUnitTest
./gradlew :core:data:testDebugUnitTest
./gradlew :core:database:testDebugUnitTest
./gradlew :feature:friends:impl:testDebugUnitTest
./gradlew :feature:calendar:impl:testDebugUnitTest
./gradlew :feature:calendar:widget:testDebugUnitTest
./gradlew :core:data:lintDebug
./gradlew :core:database:lintDebug
./gradlew :feature:friends:impl:lintDebug
./gradlew :feature:calendar:impl:lintDebug
./gradlew :feature:calendar:widget:lintDebug
scripts/quality/product-harness-check.sh
```

## 13. P0 Red Lines
- Observed payload is returned without active grant.
- Revoke leaves observed list/detail/calendar accessible online.
- ObservedTodo appears in Todo/Today/Completed/Widget/productivity metrics.
- ObservedTodo appears in assignment/direct assignment lists, counts, or notifications.
- Calendar shows friend Todo as if it were owned Todo.
- Calendar becomes permanently week-only because friend todos exist.
- Calendar shows an empty `친구 할일` section in the normal agenda.
- Pending friend request rows show visibility controls before friendship is active.
- ObservedTodo can be completed, edited, deleted, reminded, or assigned by Viewer.
- Todo content appears in push payload/notification preview.
- Existing users or existing Todos become visible due to migration/cache without grant.
