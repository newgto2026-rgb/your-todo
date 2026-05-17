# Rework Metrics: codex/friends-visibility-ux-clarity

## Branch Summary

| Field | Value |
|---|---|
| Branch | `codex/friends-visibility-ux-clarity` |
| PR | `#106` |
| Primary AI Model | `GPT-5` |
| Task Type | `UX clarity + API data fix` |
| Rework Count | `3` |
| P0 Issues | `0` |
| P1 Issues | `1` |
| P2 Issues | `2` |
| Automation Possible Issues | `2` |
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

## External Event Coverage

### Review Threads

No review threads recorded yet.

### Actionable PR Comments

No GitHub PR comments recorded yet. This branch is a post-merge improvement branch created from direct user feedback in the Codex thread.

### Check Failures

Local all-device `:app:connectedDebugAndroidTest` runs failed on `SM-S906N - 16` while waiting for the initial `app_tab_friends` test tag, before exercising the changed UI. The same Friends UI test class passed on `Medium_Phone_API_36(AVD) - 16` with `ANDROID_SERIAL=emulator-5554`; the shared setup wait was extended to reduce device-start flakiness.

## Non-Rework Follow-up Commits

- `HEAD`: Recorded the created PR number in this metrics document.
