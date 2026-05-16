# Friends MVP TRD

## Goal
- Add the first friend-management surface that will support friend-assigned Todo later.
- Keep friend state online-only and server-authored for MVP.
- Make request/send/accept/remove flows clear enough that users know the current relationship state.

## Product Decisions
- Entry point: top-level `친구` tab.
- Add friend by exact nickname.
- Incoming requests require accept or decline.
- Same-direction duplicate request does not create another pending item.
- Reverse pending request becomes an active friendship on the server.
- Removing a friend is soft on the server; past records remain.

## Cache Policy
- Friends and friend requests are online-only for the MVP.
- `FriendRepository` list calls fetch the current server snapshot and do not fall back to Room, DataStore, or the previous in-memory response.
- A network/auth failure is not treated as an empty friend list. Initial load failure keeps the screen in an unavailable state with retry, while an already loaded in-memory snapshot can remain visible until the next successful server refresh.
- When an already loaded in-memory snapshot remains visible after a friends or request feed failure, the UI marks it as stale/unavailable and offers retry instead of presenting it as current.
- Assigned todo feeds have their own Room cache policy and are intentionally separate from the Friends/requests policy.

## Android Architecture
- `feature:friends:api`: route and feature entry contract.
- `feature:friends:entry`: Hilt multibinding into the app shell.
- `feature:friends:impl`: Compose screen, ViewModel, UI state, side effects.
- `core:model`: friend and request models.
- `core:domain`: repository contract and use cases.
- `core:network`: Retrofit API and DTOs.
- `core:data`: repository implementation and DTO-to-domain mapping.

Dependency direction stays:
- `app -> feature:*:api, feature:*:entry, core:*`
- `feature:friends:impl -> feature:friends:api, core:*`
- `core:*` never depends on `feature:*`

## Screen Behavior
- On entry, load friends, incoming requests, and outgoing requests.
- The screen shows incoming requests first only when present.
- Friend list uses compact rows with initial avatars and overflow/remove action.
- Add friend opens a lightweight nickname input sheet/area.
- Outgoing requests are shown as pending rows with no cancel action in MVP.
- Every mutation disables its button while running and refetches all lists on success.
- Network/auth failures show stable messages and do not fake success. Mutation failures keep the previous visible state and expose a retryable action path instead of clearing lists.

## Server Contract
- `GET /api/friends`
- `POST /api/friend-requests`
- `GET /api/friend-requests/incoming`
- `GET /api/friend-requests/outgoing`
- `POST /api/friend-requests/{id}/accept`
- `POST /api/friend-requests/{id}/decline`
- `DELETE /api/friends/{friendshipId}`

Common user DTO:
```json
{
  "id": "user-id",
  "nickname": "monday"
}
```

## Test Strategy
- ViewModel tests cover load, send, duplicate/self error mapping, accept, decline, remove, auth failure, network failure.
- Compose UI tests cover tab discovery, add request form, incoming accept/decline controls, outgoing pending rows, empty states.
- Server tests cover auth, onboarding, self, duplicate, reverse pending, permissions, list, remove, and isolation.
- Final gate includes affected module unit tests, lint, app build, targeted UI tests, and server tests.
