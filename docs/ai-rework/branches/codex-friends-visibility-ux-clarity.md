# Rework Metrics: codex/friends-visibility-ux-clarity

## Branch Summary

| Field | Value |
|---|---|
| Branch | `codex/friends-visibility-ux-clarity` |
| PR | `#106` |
| Primary AI Model | `GPT-5` |
| Task Type | `UX clarity + API data fix` |
| Rework Count | `6` |
| P0 Issues | `0` |
| P1 Issues | `1` |
| P2 Issues | `5` |
| Automation Possible Issues | `3` |
| Automation Added Issues | `2` |
| Open Events | `0` |

## Rework Events

### R1 - Shared Todo Monitor Entry Was Implicit

| Field | Value |
|---|---|
| Source | `user feedback` |
| Priority | `P2` |
| Status | `closed` |
| Automation Possible | `yes` |
| Automation Added | `yes` |

The friend row previously relied on an implicit friend identity click to open the shared-todo monitor, so the entry point was easy to miss. The row now exposes a compact `주고받은 일` action beside `할 일 보내기`, and `FriendsUiTest.friendsTabOpensSharedTodoMonitorFromExplicitAction` covers the explicit path.

### R2 - Auto-Accept Target Was Ambiguous

| Field | Value |
|---|---|
| Source | `user feedback` |
| Priority | `P2` |
| Status | `closed` |
| Automation Possible | `no` |
| Automation Added | `no` |

The short label `자동수락` did not say what would be accepted. The setting now uses compact copy, `받은 일 자동수락`, with matching snackbar and send-mode copy so the UI stays clear without adding long explanatory text.

### R3 - Observed Friend Todos Missed Due Time

| Field | Value |
|---|---|
| Source | `user feedback` |
| Priority | `P1` |
| Status | `closed` |
| Automation Possible | `yes` |
| Automation Added | `yes` |

Observed owner todos preserved date but not time in the server projection. The server now returns `dueTime` for observed todos, Android network/data tests assert the field is parsed and cached, and the Friends UI instrumentation test verifies the time is visible in the friend-todo list.

### R4 - FriendPrimaryActionsRow Modifier Was Closed

| Field | Value |
|---|---|
| Source | `GitHub review thread PRRT_kwDOSAf4v86CpgpQ` |
| Priority | `P2` |
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Attribution | `AI` |
| Root Cause Category | `Compose reusability` |
| Automation Possible | `no` |
| Automation Added | `no` |
| Fix Scope | `feature:friends:impl UI` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :feature:friends:impl:lintDebug :app:compileDebugAndroidTestKotlin` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86CpgpQ`
- Review Comment: `3254778170`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/106#discussion_r3254778170
- Review Path: `feature/friends/impl/src/main/java/com/neo/yourtodo/feature/friends/impl/ui/FriendsRouteScreen.kt:559`

#### Feedback Summary

`FriendPrimaryActionsRow` accepted no caller `Modifier`, reducing layout flexibility compared with normal Compose component conventions.

#### Fix Summary

Added `modifier: Modifier = Modifier` and applied it to the row before `fillMaxWidth()`.

#### Lesson

New private composables that represent reusable UI rows should still expose `Modifier` by default unless there is a concrete reason to keep layout fixed.

### R5 - Friend Row Action Tags Used Mixed Identifiers

| Field | Value |
|---|---|
| Source | `GitHub review thread PRRT_kwDOSAf4v86CpgpU` |
| Priority | `P2` |
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Attribution | `AI` |
| Root Cause Category | `Testability convention` |
| Automation Possible | `no` |
| Automation Added | `no` |
| Fix Scope | `feature:friends:impl UI test tags` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :feature:friends:impl:lintDebug :app:compileDebugAndroidTestKotlin` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86CpgpU`
- Review Comment: `3254778175`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/106#discussion_r3254778175
- Review Path: `feature/friends/impl/src/main/java/com/neo/yourtodo/feature/friends/impl/ui/FriendsRouteScreen.kt:571`

#### Feedback Summary

The newly exposed friend-row actions used `userId`, while the remove row action still used `friendshipId`, making selector naming inconsistent.

#### Fix Summary

Changed the remove row action tag to `friends_remove_${friend.userId}` so the visible row actions share one identifier convention.

#### Lesson

When adding adjacent UI actions, check existing tags in the same row and keep the selector identity stable across actions.

### R6 - Primary Action Buttons Used Fixed Height

| Field | Value |
|---|---|
| Source | `GitHub review thread PRRT_kwDOSAf4v86CpgpV` |
| Priority | `P2` |
| Status | `verified` |
| Detected Phase | `review` |
| Feedback Source | `gemini-code-assist` |
| Attribution | `AI` |
| Root Cause Category | `Accessibility sizing` |
| Automation Possible | `yes` |
| Automation Added | `no - large font-scale UI coverage would need a broader app-level harness; this fix is covered by Compose compile/lint here` |
| Fix Scope | `feature:friends:impl UI sizing` |
| Fix Size | `small` |
| Rework Commit | `HEAD` |
| Verification | `./gradlew :feature:friends:impl:lintDebug :app:compileDebugAndroidTestKotlin` |

#### External Refs

- Review Thread: `PRRT_kwDOSAf4v86CpgpV`
- Review Comment: `3254778176`
- Review URL: https://github.com/newgto2026-rgb/your-todo/pull/106#discussion_r3254778176
- Review Path: `feature/friends/impl/src/main/java/com/neo/yourtodo/feature/friends/impl/ui/FriendsRouteScreen.kt:570`

#### Feedback Summary

The compact friend-row buttons used a fixed `38.dp` height, which could clip text when system font size grows.

#### Fix Summary

Replaced fixed heights with `heightIn(min = 38.dp)` so the compact baseline remains while text can grow vertically.

#### Lesson

Compact action rows should prefer minimum sizing over fixed sizing when they contain localized text.

## External Event Coverage

### Review Threads

- `PRRT_kwDOSAf4v86CpgpQ`: covered by R4.
- `PRRT_kwDOSAf4v86CpgpU`: covered by R5.
- `PRRT_kwDOSAf4v86CpgpV`: covered by R6.

### Actionable PR Comments

No GitHub PR comments recorded yet. This branch is a post-merge improvement branch created from direct user feedback in the Codex thread.

### Check Failures

Local all-device `:app:connectedDebugAndroidTest` runs failed on `SM-S906N - 16` while waiting for the initial `app_tab_friends` test tag, before exercising the changed UI. The same Friends UI test class passed on `Medium_Phone_API_36(AVD) - 16` with `ANDROID_SERIAL=emulator-5554`; the shared setup wait was extended to reduce device-start flakiness.

## Non-Rework Follow-up Commits

- `7d0bbf6828d5d482e41c621e696850eed0b83b16`: Recorded the created PR number in this metrics document.
